package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 줄 단위 정규식 규칙(ㄱ·ㄹ·ㅁ·ㅇ). {@code skipComments} 면 언어별 주석을 공백으로 지운 글에 맞춘다 — 줄 번호는 그대로.
 * {@code params.max} 가 있으면 건수 규칙이다: 파일 안 맞은 수가 max 를 넘을 때 첫 자리에 한 건(JSP 스크립틀릿 블록 수).
 * {@code params.when} 이 있으면 파일 게이트다(5-44a): 원문에 그 정규식이 없는 파일은 안 본다(예: MyBatis 매퍼만 — pom·logback 의 {@code ${…}} 제외).
 */
public final class RegexRule implements Rule {

    /** 텍스트 블록 경계 */
    private static final String TRIPLE_QUOTE = "\"\"\"";

    private final Def def;
    private final Pattern pattern;
    private final Pattern when;

    public RegexRule(Def def) {
        this.def = def;
        this.pattern = def.pattern();
        Object w = def.params().get("when");
        this.when = w == null || w.toString().isBlank() ? null : AbsentRule.when(def);
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public List<Finding> apply(Source s) {
        if ("java".equals(s.lang()) && RuleSet.hybrid(def)) {
            return List.of(); // 설계 42 §3-1 — .java 는 JavaRules 의 오염·상수 판정이 본다(못 읽은 파일은 JavaRules 가 이 정규식으로 되돌린다)
        }
        return applyText(s);
    }

    /** 정규식 판정 그대로(하이브리드 건너뛰기 없이) */
    List<Finding> applyText(Source s) {
        if (when != null && !when.matcher(s.text()).find()) {
            return List.of();
        }
        String[] raw = s.text().split("\n", -1);
        String[] lines = Boolean.TRUE.equals(def.skipComments()) ? skipped(s).split("\n", -1) : raw;
        List<Finding> out = new ArrayList<>();
        Object max = def.params().get("max");
        if (max != null) {
            int limit = Integer.parseInt(max.toString().trim()); // 5-130 — validate 의 maxIsInt 와 같게
            int count = 0;
            int first = -1;
            for (int i = 0; i < lines.length; i++) {
                Matcher m = pattern.matcher(lines[i]);
                while (m.find()) {
                    count++;
                    first = first < 0 ? i : first;
                }
            }
            if (count > limit) {
                out.add(new Finding(s.rel(), first + 1, def.group(), def.id(), def.severity(), count + "개 (기준 " + limit + ")"));
            }
            return out;
        }
        for (int i = 0; i < lines.length; i++) {
            if (pattern.matcher(lines[i]).find()) {
                out.add(new Finding(s.rel(), i + 1, def.group(), def.id(), def.severity(), Finding.excerpt(raw[i])));
            }
        }
        return out;
    }

    /**
     * 주석을 지운 글. jsp 꼴은 주석만 든 스크립틀릿도 지운다(5-22) — stripComments 는 프로그램 분석도 써서 그대로 둔다.
     * jsp·html 꼴은 {@code <script>} 몸의 주석도(D-jspScriptComment, 백엔드만 — 순수본 JS 는 옛 동작)
     */
    private static String skipped(Source s) {
        String lang = s.lang() == null ? "" : s.lang();
        String t = stripComments(markup(lang) ? scriptWrappers(s.text()) : s.text(), s.lang()); // 5-128 — <script> 몸의 <!-- 감싸개
        if ("jsp".equals(lang) || "jspf".equals(lang) || "tag".equals(lang)) {
            return scriptComments(blankCommentScriptlets(t));
        }
        return "html".equals(lang) || "htm".equals(lang) || "xhtml".equals(lang) ? scriptComments(t) : t;
    }

    /** jsp·jspf·tag·html·htm·xhtml 꼴 — {@code <script>} 몸을 JS 로 본다 */
    static boolean markup(String lang) {
        return switch (lang == null ? "" : lang) {
            case "jsp", "jspf", "tag", "html", "htm", "xhtml" -> true;
            default -> false;
        };
    }

    /**
     * 5-22 — 몸통이 주석·공백뿐인 스크립틀릿(eGov JSP 머리 「<% /** @Class Name … *&#47; %>」)을 공백으로. 줄바꿈은 남긴다.
     * 정규식 대신 한 번 훑는다 — 중첩 반복 정규식은 닫는 %> 가 없는 큰 JSP 에서 역추적이 길어진다(SpotBugs REDOS)
     */
    static String blankCommentScriptlets(String text) {
        StringBuilder sb = null;
        int i = 0;
        while ((i = text.indexOf("<%", i)) >= 0) {
            int body = i + 2;
            if (body < text.length() && "@=!-".indexOf(text.charAt(body)) >= 0) {
                i = body;
                continue;
            }
            int end = text.indexOf("%>", body);
            if (end < 0) {
                break;
            }
            if (commentOnly(text, body, end)) {
                if (sb == null) {
                    sb = new StringBuilder(text);
                }
                for (int k = i; k < end + 2; k++) {
                    if (text.charAt(k) != '\n') {
                        sb.setCharAt(k, ' ');
                    }
                }
            }
            i = end + 2;
        }
        return sb == null ? text : sb.toString();
    }

    /** [from, to) 가 공백·블록 주석·줄 주석뿐인가 */
    private static boolean commentOnly(String t, int from, int to) {
        int k = from;
        while (k < to) {
            char c = t.charAt(k);
            if (Character.isWhitespace(c)) {
                k++;
            } else if (t.startsWith("/*", k)) {
                int e = t.indexOf("*/", k + 2);
                if (e < 0 || e + 2 > to) {
                    return false;
                }
                k = e + 2;
            } else if (t.startsWith("//", k)) {
                int e = t.indexOf('\n', k);
                k = e < 0 || e > to ? to : e;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 주석을 공백으로 — 줄바꿈은 남긴다. java·js 꼴은 {@code //}·블록 주석(따옴표 셋 안은 둔다), jsp 는 {@code <%-- --%>}·{@code <!-- -->},
     * xml·html 은 {@code <!-- -->}, properties·yaml 은 줄머리 {@code #}(properties 는 {@code !} 도), sql 은 {@code --}·블록 주석.
     * 정규식 리터럴은 모른다 — 그 안의 {@code //} 를 주석으로 볼 수 있다(실패 사다리: 그 규칙만 skipComments 끔).
     * 프로그램 분석(6-1)도 SQL 주석을 이것으로 지운다
     */
    public static String stripComments(String text, String lang) {
        return switch (lang == null ? "" : lang) {
            case "java", "js", "jsx", "ts", "tsx", "mjs", "cjs", "css", "scss", "kt", "groovy" -> cLike(text, true);
            case "sql" -> cLike(text, false);
            case "jsp", "jspf", "tag" -> blank(blank(text, "<%--", "--%>"), "<!--", "-->");
            case "xml", "html", "htm", "xhtml", "vue" -> blank(text, "<!--", "-->");
            case "properties" -> lineComments(text, "#!");
            case "yml", "yaml", "sh" -> lineComments(text, "#");
            default -> text;
        };
    }

    /** {@code <script>} 몸의 {@code //}·{@code /* *}{@code /} 주석을 같은 길이 빈칸으로(줄바꿈은 남긴다) — 따옴표·백틱 글 안은 그대로. jsp·html 꼴에서 쓴다(D-jspScriptComment · R-C-I3) */
    public static String scriptComments(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int from = 0;
        for (int[] b : scriptBodies(text)) {
            sb.append(text, from, b[0]).append(cLike(text.substring(b[0], b[1]), true));
            from = b[1];
        }
        return sb.append(text, from, text.length()).toString();
    }

    /**
     * {@code <script>} 몸 구간 {@code {시작, 끝}} — 한 번 훑기(5-128 D-19: 정규식은 닫는 태그 없는 여는 태그가 되풀이되면 제곱).
     * 여는 태그는 {@code <script} 뒤가 낱말 글자가 아닌 것, 몸 끝은 그 뒤 첫 {@code </script}. 여는 태그의 {@code >} 나 닫는 태그가 없으면 거기서 끝
     */
    static List<int[]> scriptBodies(String text) {
        List<int[]> out = new ArrayList<>();
        int i = text.indexOf('<');
        while (i >= 0) {
            if (!scriptOpen(text, i)) {
                i = text.indexOf('<', i + 1);
                continue;
            }
            int gt = text.indexOf('>', i + 7);
            int end = gt < 0 ? -1 : scriptClose(text, gt + 1);
            if (end < 0) {
                break;
            }
            out.add(new int[] {gt + 1, end});
            i = text.indexOf('<', end + 8);
        }
        return out;
    }

    private static boolean scriptOpen(String text, int i) {
        int after = i + 7;
        return text.regionMatches(true, i, "<script", 0, 7) && (after >= text.length() || !Character.isLetterOrDigit(text.charAt(after)) && text.charAt(after) != '_');
    }

    /** from 부터 첫 {@code </script}(대소문자 무시) 자리, 없으면 -1 */
    private static int scriptClose(String text, int from) {
        int k = text.indexOf("</", from);
        while (k >= 0 && !text.regionMatches(true, k, "</script", 0, 8)) {
            k = text.indexOf("</", k + 2);
        }
        return k;
    }

    /**
     * 5-128(D-01) — {@code <script>} 몸 안 {@code <!--} 를 같은 길이 {@code "//  "} 로(JS 에서 {@code <!--} 는 한 줄 주석의 시작).
     * 몸을 {@code <!-- … //-->} 로 감싼 옛 꼴이 HTML 주석으로 통째 비지 않게 한다. 없으면 원문 그대로
     */
    public static String scriptWrappers(String text) {
        StringBuilder sb = null;
        for (int[] b : scriptBodies(text)) {
            for (int k = b[0]; k + 4 <= b[1]; k++) {
                if (text.startsWith("<!--", k)) {
                    sb = sb == null ? new StringBuilder(text) : sb;
                    sb.replace(k, k + 4, "//  ");
                }
            }
        }
        return sb == null ? text : sb.toString();
    }

    private static String cLike(String s, boolean slashes) {
        StringBuilder sb = new StringBuilder(s.length());
        char quote = 0;
        int quoteAt = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            char n = i + 1 < s.length() ? s.charAt(i + 1) : 0;
            if (quote != 0) {
                sb.append(c);
                if (slashes && c == '\\' && n != 0) {
                    sb.append(n);
                    i += 2;
                    continue;
                }
                if (c == quote || c == '\n' && quote != '`') {
                    lineCut(sb, c, quoteAt, slashes);
                    quote = 0;
                }
                i++;
            } else if (slashes && c == '"' && s.startsWith(TRIPLE_QUOTE, i)) {
                i = textBlock(s, i, sb); // D-textBlock — 텍스트 블록 안은 그대로(백엔드만)
            } else if (c == '"' || c == '\'' || c == '`' && slashes) {
                quote = c;
                quoteAt = sb.length();
                sb.append(c);
                i++;
            } else if (slashes && c == '/' && n == '/' || !slashes && c == '-' && n == '-') {
                i = lineComment(s, i, sb);
            } else if (c == '/' && n == '*') {
                i = blockComment(s, i, sb);
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** 텍스트 블록({@code """…"""}) — 닫는 {@code """}(앞이 역슬래시 아님)까지 그대로 옮기고 그 다음 위치(안 닫히면 글 끝) */
    private static int textBlock(String s, int i, StringBuilder sb) {
        sb.append(TRIPLE_QUOTE);
        int k = i + 3;
        while (k < s.length()) {
            if (s.startsWith(TRIPLE_QUOTE, k) && backslashesBefore(s, k) % 2 == 0) { // 5-128 D-17 — \\""" 는 닫는다
                sb.append(TRIPLE_QUOTE);
                return k + 3;
            }
            sb.append(s.charAt(k));
            k++;
        }
        return k;
    }

    /** k 바로 앞에 이어진 역슬래시 수 */
    private static int backslashesBefore(String s, int k) {
        int n = 0;
        while (k - n - 1 >= 0 && s.charAt(k - n - 1) == '\\') {
            n++;
        }
        return n;
    }

    /**
     * 5-128(D-10) — 따옴표가 줄바꿈으로 끊겼으면(C 꼴에 없는 안 닫힌 문자열 — JSX 글 아포스트로피 · 정규식 리터럴) 그 줄에서 따옴표 뒤
     * 첫 {@code //} 부터 줄 끝까지를 빈칸으로(줄 주석으로 본다)
     */
    private static void lineCut(StringBuilder sb, char c, int quoteAt, boolean slashes) {
        if (c != '\n' || !slashes) {
            return;
        }
        int p = sb.indexOf("//", quoteAt + 1);
        for (int k = p; k >= 0 && k < sb.length() - 1; k++) {
            sb.setCharAt(k, ' ');
        }
    }

    /** 줄 주석 — 줄 끝까지 빈칸. 다음 위치 */
    private static int lineComment(String s, int i, StringBuilder sb) {
        int k = i;
        while (k < s.length() && s.charAt(k) != '\n') {
            sb.append(' ');
            k++;
        }
        return k;
    }

    /** 블록 주석 — 닫는 곳까지 빈칸(줄바꿈은 남긴다). 다음 위치 */
    private static int blockComment(String s, int i, StringBuilder sb) {
        int e = s.indexOf("*/", i + 2);
        int end = e < 0 ? s.length() : e + 2;
        for (int k = i; k < end; k++) {
            sb.append(s.charAt(k) == '\n' ? '\n' : ' ');
        }
        return end;
    }

    private static String blank(String s, String open, String close) {
        StringBuilder sb = new StringBuilder(s);
        int from = 0;
        int o;
        while ((o = s.indexOf(open, from)) >= 0) {
            int e = s.indexOf(close, o + open.length());
            int end = e < 0 ? s.length() : e + close.length();
            for (int i = o; i < end; i++) {
                if (s.charAt(i) != '\n') {
                    sb.setCharAt(i, ' ');
                }
            }
            from = end;
        }
        return sb.toString();
    }

    private static String lineComments(String s, String marks) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            String t = line.stripLeading();
            sb.append(!t.isEmpty() && marks.indexOf(t.charAt(0)) >= 0 ? " ".repeat(line.length()) : line).append('\n');
        }
        return sb.substring(0, sb.length() - 1);
    }
}
