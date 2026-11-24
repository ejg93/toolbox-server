package kr.ejg.toolbox.core.analyze;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.check.RegexRule;
import kr.ejg.toolbox.core.check.Source;

/**
 * JSP 한 장이 부르는 {@code .do} URL(6-6 영향도의 JSP 역추적). {@code <c:url value>}·{@code form action}·{@code location.href}·
 * {@code <c:import url>}·ajax {@code url:}·{@code href="javascript:fn('/x.do')"} 를 가리지 않고 {@code .do} 로 끝나는 토큰을 모은다.
 * <ul>
 *   <li>토큰 = {@code .do} 앞으로 {@code [A-Za-z0-9_$./-]} 와 {@code ${…}} 묶음이 이어진 것. {@code .do} 뒤가 영숫자·{@code _}·{@code .} 면 아니다</li>
 *   <li>{@code /} 나 {@code ${} 로 시작하는 토큰만 — 그 밖은 스크립트 주석·상대 경로(egov 실측 6)라 버린다</li>
 *   <li>컨텍스트 경로 접두({@code ${…contextPath}}·{@code ${ctx}} 같은 흔한 이름·c:set 으로 정한 이름, R-C-I3)는 떼고, 그래도 {@code ${} 가 남으면 {@code jspUrl} 미해결. detail 은 {@code ${…}} 를
 *       전부 {@code ${}} 로 비운 경로 모양 — EL 식(코드 조각)은 저장하지 않는다(규칙 3, 6-10)</li>
 * </ul>
 * 손 주사라 정규식 역추적이 없다. JSP 주석({@code <%-- --%>}·{@code <!-- -->})과 {@code <script>} 안 주석은 지운 뒤 본다. 글은 메모리에서만(규칙 3).
 */
public final class JspLinks {

    static final int CUE_WINDOW = 200;

    /** {@code ${…}} 한 덩이 — 안쪽에 {@code }} 가 없다(토큰 주사가 같은 꼴로 묶었다) */
    private static final Pattern EL = Pattern.compile("\\$\\{[^}]*}");

    /** 컨텍스트 경로를 담는 변수 이름 — 흔한 이름 + 이 JSP 가 c:set 으로 contextPath 를 넣은 이름 */
    private static final Set<String> CTX_NAMES = Set.of("ctx", "cp", "root", "contextpath", "ctxpath", "rootpath", "contextroot", "basepath", "webroot");
    /** c:set 태그 글 안의 var 이름 · 값이 ${…contextPath} 하나로 끝나는가(6-85 — 태그는 손 훑기로 자른다: 게으른 수량자 둘은 세제곱 시간) */
    private static final Pattern CTX_VAR = Pattern.compile("\\bvar\\s*+=\\s*+[\"']([\\w.]++)[\"']");
    /** 6-97(C2-11) — 값이 contextPath 식으로 끝난다(contextPath} · contextPath)} · getContextPath()%>) — 앞 글은 안 본다 */
    private static final Pattern CTX_VALUE = Pattern.compile("\\bvalue\\s*+=\\s*+[\"'][^\"']*(?:contextPath\\s*+\\)?\\s*+\\}|getContextPath\\(\\)\\s*+%>)\\s*+[\"']",
            Pattern.CASE_INSENSITIVE);
    /** 토큰을 거슬러 갈 상한(6-87) */
    private static final int TOKEN_MAX = 500;
    private static final Pattern CTX_HEAD =Pattern.compile("^\\$\\{\\s*([\\w.]+)\\s*}(?=/)");
    /** 단서 끝과 URL 글 사이에 올 수 있는 글자 — 공백·= ( + 따옴표(6-74). ${…} 는 {@link #EL} 로 먼저 지운다 — 묶음에 수량자를 안 단다(SpotBugs REDOS) */
    private static final Pattern GAP = Pattern.compile("[\\s=(+'\"]*+");
    /** 사이 글 끝의 &lt;c:url value= (spring:url·s:url 도, 마무리 리뷰 I1) 와 따옴표 하나(6-74) */
    private static final Pattern GAP_C_URL = Pattern.compile("<(?:c|spring|s):url\\s++value\\s*+=\\s*+[\"']\\z");
    /** 포함 단서(c:import·jsp:include·iframe — 태그 이름) 뒤 사이 글 머리의 속성 이름(마무리 리뷰 I1) */
    private static final Pattern GAP_INCLUDE_ATTR = Pattern.compile("^\\s++(?:src|url|page)\\s*+=");

    /**
     * links — (url, 꼴) 중복 없이 url → 꼴 순(같은 URL 을 여러 꼴로 부르면 꼴마다 하나). unresolved — 종류 {@code jspUrl}, 파일·줄·조각.
     * {@link #urls()} 는 URL 만 정렬·중복 없이(6-6 영향도·골든이 쓰는 꼴)
     */
    public record Result(List<Link> links, List<Unresolved> unresolved) {

        public Result {
            links = List.copyOf(links);
            unresolved = List.copyOf(unresolved);
        }

        public List<String> urls() {
            return links.stream().map(Link::url).distinct().sorted().toList();
        }
    }

    /** 어떤 꼴로 불렸나(추정 — 단서, 6-27) — kind 는 {@link LinkKind} 의 코드. include(c:import·jsp:include·iframe — 6-30)·link(href)·form(action)·popup(window.open)·ajax(url:·$.get/post/ajax·.load)·script(location)·other(단서 없음·변수에 담음) */
    public record Link(String url, String kind) {
    }

    /** 토큰 앞 글에서 가장 가까운 단서 하나(6-27, 설계 20 D3). c:url var 는 변수에 담는 꼴이라 other. location.href 는 한 덩이라 href 보다 앞에서 잡혀 script */
    private static final Pattern CUE = Pattern.compile(
            "c:url\\s+var|window\\.open\\s*\\(|\\.open\\(|\\baction\\s*=|location\\.(?:href|replace)|location\\s*=|\\bhref\\s*=|(?i:c:import|jsp:include|<iframe\\b)|url\\s*:|\\$\\.(?:get|post|ajax)\\(|\\.load\\(|\\bajax\\b");

    private JspLinks() {
    }

    static LinkKind kind(String text, int start) {
        String win = text.substring(Math.max(0, start - CUE_WINDOW), start);
        Matcher m = CUE.matcher(win);
        String last = null;
        while (m.find()) {
            last = m.group();
        }
        if (last == null || last.startsWith("c:url")) {
            return LinkKind.OTHER;
        }
        String tag = last.toLowerCase(java.util.Locale.ROOT);
        if ("c:import".equals(tag) || "jsp:include".equals(tag) || tag.startsWith("<iframe")) {
            return LinkKind.INCLUDE; // 6-30 — 서버 쪽 포함(지금까지 c:import 는 link 로 셌다)
        }
        if (last.startsWith("window.open") || ".open(".equals(last)) {
            return LinkKind.POPUP;
        }
        if (last.startsWith("action")) {
            return LinkKind.FORM;
        }
        if (last.startsWith("location")) {
            return LinkKind.SCRIPT;
        }
        if (last.startsWith("href")) {
            return LinkKind.LINK;
        }
        return LinkKind.AJAX;
    }

    /** start 앞 창에서 가장 가까운 단서가 start 와 맞닿았나 — 사이가 {@link #GAP} 글자·${…} 뿐, 끝에 &lt;c:url value= 하나 더(6-74) */
    static boolean cueTouches(String text, int start) {
        int from = Math.max(0, start - CUE_WINDOW);
        Matcher m = CUE.matcher(text.substring(from, start));
        int end = -1;
        String cue = "";
        while (m.find()) {
            end = m.end();
            cue = m.group().toLowerCase(java.util.Locale.ROOT);
        }
        if (end < 0) {
            return false;
        }
        String gap = GAP_C_URL.matcher(text.substring(from + end, start)).replaceFirst("");
        if (cue.startsWith("c:import") || cue.startsWith("jsp:include") || cue.startsWith("<iframe")) {
            gap = GAP_INCLUDE_ATTR.matcher(gap).replaceFirst("");
        }
        return GAP.matcher(EL.matcher(gap).replaceAll("")).matches();
    }

    public static Result extract(Source jsp) {
        return extract(jsp, Set.of());
    }

    /** programUrls — 컨트롤러 매핑 URL. .do 아닌 따옴표 글이 이것과 같으면 링크(6-51) */
    public static Result extract(Source jsp, Set<String> programUrls) {
        String text = RegexRule.bare(jsp.text() == null ? "" : jsp.text(), "jsp"); // 6-85 — <script> 몸의 <!-- 감싸개도(검사·정리와 같은 글)
        Set<String> ctxNames = ctxNames(text);
        int[] starts = lineStarts(text);
        java.util.Map<String, Set<String>> urls = new java.util.TreeMap<>();
        Set<Unresolved> unresolved = new LinkedHashSet<>();
        int from = 0;
        while (true) {
            int dot = text.indexOf(".do", from);
            if (dot < 0) {
                break;
            }
            from = dot + 3;
            if (from < text.length() && tokenChar(text.charAt(from))) {
                continue;
            }
            int start = start(text, dot);
            if (start == dot) {
                continue;
            }
            String token = text.substring(start, dot + 3);
            if (!token.startsWith("/") && !token.startsWith("${")) {
                continue;
            }
            String url = withoutContext(token, ctxNames);
            if (url.contains("${") || !url.startsWith("/")) {
                String shape = EL.matcher(token).replaceAll("\\$\\{}");
                unresolved.add(new Unresolved("jspUrl", jsp.rel(), lineOf(starts, start), shape.length() > 300 ? shape.substring(0, 300) : shape));
            } else {
                urls.computeIfAbsent(url, k -> new TreeSet<>()).add(kind(text, start).code()); // 6-27 — 같은 URL 의 다른 꼴은 전부
            }
        }
        if (!programUrls.isEmpty()) {
            matched(text, ctxNames, programUrls, urls);
        }
        List<Link> links = new ArrayList<>();
        urls.forEach((u, kinds) -> kinds.forEach(k -> links.add(new Link(u, k))));
        return new Result(links, new ArrayList<>(unresolved));
    }

    /** 6-51 — 따옴표 글이 (컨텍스트 접두를 떼고 ?·# 앞까지 잘라) 컨트롤러 매핑 URL 과 같으면 링크. .do 로 끝나는 것은 앞 훑기가 이미 봤다 */
    private static void matched(String text, Set<String> ctxNames, Set<String> programUrls, java.util.Map<String, Set<String>> urls) {
        for (int q = nextQuote(text, 0); q >= 0; q = nextQuote(text, q + 1)) {
            int close = quotedEnd(text, q);
            if (close < 0) {
                continue;
            }
            int at = q + 1;
            String c = withoutContext(text.substring(at, close), ctxNames);
            int cut = c.length();
            for (char x : new char[] {'?', '#'}) {
                int k = c.indexOf(x);
                cut = k >= 0 ? Math.min(cut, k) : cut;
            }
            c = c.substring(0, cut);
            if (c.startsWith("/") && !c.endsWith(".do") && programUrls.contains(c)) {
                LinkKind k = kind(text, at);
                if (c.length() > 1 || k != LinkKind.OTHER && cueTouches(text, at)) { // 6-67 · 6-74 — 「/」 한 글자는 맞닿은 단서가 있을 때만
                    urls.computeIfAbsent(c, x -> new TreeSet<>()).add(k.code());
                }
            }
        }
    }

    /** from 뒤 첫 {@code "}·{@code '} 자리, 없으면 -1 */
    private static int nextQuote(String text, int from) {
        int d = text.indexOf('"', from);
        int s = text.indexOf('\'', from);
        return d < 0 ? s : s < 0 ? d : Math.min(d, s);
    }

    /**
     * 6-87(C2-07) — q 의 따옴표를 여는 따옴표로 볼 때 같은 줄의 다음 같은 따옴표 자리(안 1~200자, 다른 따옴표·줄바꿈 없음), 아니면 -1.
     * 따옴표마다 여는 것으로 보고 한 번씩 재므로 앞의 짝(onsubmit="…'f'…")이 밀려도 뒤 속성 값을 놓치지 않는다
     */
    private static int quotedEnd(String text, int q) {
        char quote = text.charAt(q);
        int lim = Math.min(text.length(), q + 202);
        for (int k = q + 1; k < lim; k++) {
            char c = text.charAt(k);
            if (c == '\n' || c == '\r') {
                return -1;
            }
            if (c == '"' || c == '\'') {
                return c == quote && k > q + 1 ? k : -1;
            }
        }
        return -1;
    }

    /** 컨트롤러 매핑 URL 가운데 {@code /} 로 시작하고 {@code {}·{@code *} 가 없는 것 */
    public static Set<String> programUrls(List<JavaGraph.Program> programs) {
        Set<String> out = new java.util.HashSet<>();
        for (JavaGraph.Program p : programs) {
            String u = p.url();
            if (u != null && u.startsWith("/") && u.indexOf('{') < 0 && u.indexOf('*') < 0) {
                out.add(u);
            }
        }
        return out;
    }

    /** {@code .do} 뒤에 이어지면 다른 낱말({@code .done}·{@code .do_x}) */
    private static boolean tokenChar(char c) {
        return c < 128 && (Character.isLetterOrDigit(c) || c == '_' || c == '.');
    }

    /** 토큰에 드는 글자 — URL 경로와 EL 이름 */
    private static boolean pathChar(char c) {
        return c < 128 && (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.' || c == '/' || c == '-');
    }

    /** {@code end} 앞으로 경로 글자와 {@code ${…}} 묶음을 거슬러 토큰 시작 */
    private static int start(String text, int end) {
        int j = end - 1;
        while (j >= 0) {
            if (end - j > TOKEN_MAX) {
                return end; // 6-87 C2-13 — 경로 글자만 이어진 긴 줄: 그 토큰은 버린다(토큰마다 줄 머리까지 거슬러 제곱이 아니게)
            }
            char c = text.charAt(j);
            if (c == '}') {
                int open = elOpen(text, j); // j 의 } 를 닫는 ${ 의 위치, 없으면 -1 — 글 머리까지 훑지 않는다(C-JSP-QUAD)
                if (open < 0) {
                    break;
                }
                j = open - 1;
            } else if (pathChar(c)) {
                j--;
            } else {
                break;
            }
        }
        return j + 1;
    }

    /** close 의 } 를 닫는 ${ 의 위치 — 그 사이에 다른 } 나 줄바꿈이 있으면 -1(EL 안에는 } 가 없다) */
    private static int elOpen(String text, int close) {
        for (int k = close - 1; k > 0; k--) {
            char c = text.charAt(k);
            if (c == '}' || c == '\n') {
                return -1;
            }
            if (c == '{' && text.charAt(k - 1) == '$') {
                return k - 1;
            }
        }
        return -1;
    }

    /** 이 JSP 의 컨텍스트 경로 이름 — CTX_NAMES + c:set 으로 contextPath 를 넣은 이름(소문자) */
    static Set<String> ctxNames(String text) {
        Set<String> names = new java.util.HashSet<>(CTX_NAMES);
        int i = cSet(text, 0);
        while (i >= 0) {
            int gt = tagClose(text, i);
            if (gt < 0) {
                break; // 닫는 > 가 없으면 뒤에도 없다
            }
            String tag = text.substring(i, gt);
            Matcher v = CTX_VAR.matcher(tag);
            if (v.find() && CTX_VALUE.matcher(tag).find()) { // 6-85 C2-08 — 값이 ${…contextPath} 하나일 때만(뒤에 /admin 이 붙으면 접두가 아니다)
                names.add(v.group(1).toLowerCase(java.util.Locale.ROOT));
            }
            i = cSet(text, gt + 1);
        }
        return names;
    }

    /** 6-97 — 태그를 닫는 > — 따옴표 값 안(value="<%=…%>")은 건너뛴다. 따옴표가 안 닫히면 첫 > */
    private static int tagClose(String text, int from) {
        char q = 0;
        for (int k = from; k < text.length(); k++) {
            char c = text.charAt(k);
            if (q != 0) {
                q = c == q ? 0 : q;
            } else if (c == '"' || c == '\'') {
                q = c;
            } else if (c == '>') {
                return k;
            }
        }
        return text.indexOf('>', from);
    }

    /** from 뒤 첫 {@code <c:set} + 공백 자리(대소문자 무시), 없으면 -1 */
    private static int cSet(String text, int from) {
        int k = text.indexOf('<', from);
        while (k >= 0 && !(text.regionMatches(true, k, "<c:set", 0, 6) && k + 6 < text.length() && Character.isWhitespace(text.charAt(k + 6)))) {
            k = text.indexOf('<', k + 1);
        }
        return k;
    }

    /** 머리의 EL 하나가 컨텍스트 경로면 뗀다 — {@code ${…contextPath}}(공백 허용) · CTX_NAMES · c:set 으로 정한 이름. 아니면 그대로 */
    static String withoutContext(String token, Set<String> names) {
        Matcher m = CTX_HEAD.matcher(token);
        if (!m.find()) {
            return token;
        }
        String el = m.group(1).toLowerCase(java.util.Locale.ROOT);
        return el.endsWith("contextpath") || names.contains(el) ? token.substring(m.end()) : token;
    }

    /** 줄 시작 위치들 — 첫 줄은 0 */
    private static int[] lineStarts(String text) {
        List<Integer> s = new ArrayList<>();
        s.add(0);
        for (int i = text.indexOf('\n'); i >= 0; i = text.indexOf('\n', i + 1)) {
            s.add(i + 1);
        }
        return s.stream().mapToInt(Integer::intValue).toArray();
    }

    /** pos 가 든 줄 번호(1 부터) */
    private static int lineOf(int[] starts, int pos) {
        int i = java.util.Arrays.binarySearch(starts, pos);
        return i >= 0 ? i + 1 : -i - 1;
    }
}
