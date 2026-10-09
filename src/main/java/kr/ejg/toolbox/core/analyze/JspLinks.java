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
 *   <li>{@code ${pageContext.request.contextPath}} 접두는 떼고, 그래도 {@code ${} 가 남으면 {@code jspUrl} 미해결. detail 은 {@code ${…}} 를
 *       전부 {@code ${}} 로 비운 경로 모양 — EL 식(코드 조각)은 저장하지 않는다(규칙 3, 6-10)</li>
 * </ul>
 * 손 주사라 정규식 역추적이 없다. JSP 주석({@code <%-- --%>}·{@code <!-- -->})은 지운 뒤 본다. 글은 메모리에서만(규칙 3).
 */
public final class JspLinks {

    static final String CONTEXT_PATH = "${pageContext.request.contextPath}";

    /** {@code ${…}} 한 덩이 — 안쪽에 {@code }} 가 없다(토큰 주사가 같은 꼴로 묶었다) */
    private static final Pattern EL = Pattern.compile("\\$\\{[^}]*}");

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

    /** 어떤 꼴로 불렸나(추정 — 단서, 6-27) — link(href·c:import)·form(action)·popup(window.open)·ajax(url:·$.get/post/ajax·.load)·script(location)·other(단서 없음·변수에 담음) */
    public record Link(String url, String kind) {
    }

    /** 토큰 앞 글에서 가장 가까운 단서 하나(6-27, 설계 20 D3). c:url var 는 변수에 담는 꼴이라 other. location.href 는 한 덩이라 href 보다 앞에서 잡혀 script */
    private static final Pattern CUE = Pattern.compile(
            "c:url\\s+var|window\\.open|\\.open\\(|action|location\\.(?:href|replace)|location\\s*=|href|c:import|url\\s*:|\\$\\.(?:get|post|ajax)\\(|\\.load\\(|ajax");
    static final int CUE_WINDOW = 200;

    static String kind(String text, int start) {
        String win = text.substring(Math.max(0, start - CUE_WINDOW), start);
        Matcher m = CUE.matcher(win);
        String last = null;
        while (m.find()) {
            last = m.group();
        }
        if (last == null || last.startsWith("c:url")) {
            return "other";
        }
        if (last.startsWith("window.open") || last.equals(".open(")) {
            return "popup";
        }
        if (last.equals("action")) {
            return "form";
        }
        if (last.startsWith("location")) {
            return "script";
        }
        if (last.equals("href") || last.equals("c:import")) {
            return "link";
        }
        return "ajax";
    }

    private JspLinks() {
    }

    public static Result extract(Source jsp) {
        String text = RegexRule.stripComments(jsp.text() == null ? "" : jsp.text(), "jsp");
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
            String url = token.startsWith(CONTEXT_PATH) ? token.substring(CONTEXT_PATH.length()) : token;
            if (url.contains("${") || !url.startsWith("/")) {
                String shape = EL.matcher(token).replaceAll("\\$\\{}");
                unresolved.add(new Unresolved("jspUrl", jsp.rel(), line(text, start), shape.length() > 300 ? shape.substring(0, 300) : shape));
            } else {
                urls.computeIfAbsent(url, k -> new TreeSet<>()).add(kind(text, start)); // 6-27 — 같은 URL 의 다른 꼴은 전부
            }
        }
        List<Link> links = new ArrayList<>();
        urls.forEach((u, kinds) -> kinds.forEach(k -> links.add(new Link(u, k))));
        return new Result(links, new ArrayList<>(unresolved));
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
            char c = text.charAt(j);
            if (c == '}') {
                int open = text.lastIndexOf("${", j);
                if (open < 0 || text.indexOf('}', open) != j) {
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

    private static int line(String text, int pos) {
        int n = 1;
        for (int i = 0; i < pos; i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }
}
