package kr.ejg.toolbox.core.check;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/** 줄 구역 판정(설계 70 §3-5) — 지우기·끼우기가 손대면 안 되는 자리. 파서가 아니다: 따옴표·템플릿·${ } 깊이만 센다. 정규식 리터럴은 모른다 */
final class Zones {
    static final String UNBALANCED = "백틱 짝이 안 맞아 템플릿 리터럴 판정을 못 믿는다";
    /** 스크립트 밖 본문 글 줄의 건너뜀 사유(5-137) */
    static final String OUTSIDE_SCRIPT = "<script> 밖 글 — 마크업 본문";
    private static final Set<String> MARKUP = Set.of("jsp", "jspf", "tag", "html", "htm", "xhtml", "vue");
    private static final List<String> RAW_TAGS = List.of("pre", "textarea");
    private static final char NL = '\n';
    /** zones 값 — 1 날글(pre·textarea) · 2 스크립트 밖 본문 */
    private static final int RAW_ZONE = 1;
    private static final int OUTSIDE_ZONE = 2;
    private static final char TICK = '`';
    private static final char OPEN = '{';
    private static final char CLOSE = '}';

    private Zones() {
    }

    /** code[k] — k 자리가 코드(' " 문자열·템플릿 글 밖) · inTemplate[i] — i 줄(0 기준) 첫 자리에서 템플릿이 열려 있다(${ } 안 코드 포함) · balanced — 끝에서 다 닫혔다 */
    record Js(boolean[] code, boolean[] inTemplate, boolean balanced) { }

    /** 주석을 공백으로 바꾼 글을 한 번 훑는다 */
    static Js js(String stripped) {
        Walk w = new Walk(stripped);
        w.run();
        return new Js(w.code, w.inTemplate, w.open == 0 && w.braces.isEmpty());
    }

    /** 마크업 꼴에서 <pre·<textarea 여는 줄부터 닫는 줄까지 참(안 닫히면 끝까지). 주석 안 태그는 안 센다. 마크업 꼴이 아니면 전부 거짓 */
    static boolean[] rawLines(String text, String lang) {
        return rawLines(text, lang, RAW_TAGS);
    }

    /**
     * 5-137(E-13) — 줄마다 구역: 0 코드 · 1 날글({@link #rawLines}) · 2 스크립트 밖 본문(jsp·html 꼴에서 {@code <script>} 몸에 걸치지 않는 줄).
     * 마크업 꼴이 아니면 날글만
     */
    static int[] zones(String text, String lang) {
        boolean[] raw = rawLines(text, lang);
        int[] z = new int[raw.length];
        boolean[] script = RegexRule.markup(lang) ? scriptLines(text, raw.length) : null;
        for (int i = 0; i < z.length; i++) {
            z[i] = raw[i] ? 1 : script != null && !script[i] ? 2 : 0;
        }
        return z;
    }

    /** {@code <script>} 몸이 걸친 줄 */
    private static boolean[] scriptLines(String text, int n) {
        boolean[] in = new boolean[n];
        int[] starts = new int[n];
        int k = 0;
        for (int i = 0; i < text.length() && k + 1 < n; i++) {
            if (text.charAt(i) == NL) {
                k++;
                starts[k] = i + 1;
            }
        }
        for (int[] b : RegexRule.scriptBodies(text)) {
            int from = lineOf(starts, b[0]);
            int to = lineOf(starts, Math.max(b[0], b[1] - 1));
            for (int i = from; i <= to && i < n; i++) {
                in[i] = true;
            }
        }
        return in;
    }

    private static int lineOf(int[] starts, int pos) {
        int i = java.util.Arrays.binarySearch(starts, pos);
        return i >= 0 ? i : -i - 2;
    }

    /** {@link #reason(Js, boolean[], int, String)} 의 구역 판 — 2 면 스크립트 밖 본문(5-137) */
    static String reason(Js js, int[] zones, int i, String word) {
        if (!js.balanced()) {
            return UNBALANCED;
        }
        if (js.inTemplate()[i]) {
            return "템플릿 리터럴 안 " + word;
        }
        if (zones[i] == RAW_ZONE) {
            return "<pre>·<textarea> 안 " + word;
        }
        return zones[i] == OUTSIDE_ZONE ? OUTSIDE_SCRIPT : null;
    }

    /** 5-129(D-15) — {@code <script>} 몸을 줄바꿈만 남기고 빈칸으로 — JS 문자열·주석 속 글자 태그가 날글 구역을 안 연다 */
    private static String blankScripts(String s) {
        List<int[]> bodies = RegexRule.scriptBodies(s);
        if (bodies.isEmpty()) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        for (int[] b : bodies) {
            for (int k = b[0]; k < b[1]; k++) {
                if (s.charAt(k) != NL) {
                    sb.setCharAt(k, ' ');
                }
            }
        }
        return sb.toString();
    }

    /**
     * 5-150(D4-08) — {@code <% … %>} 구간과 태그 안 따옴표 값을 같은 길이 빈칸으로(줄바꿈은 남긴다) — 그 안 {@code <textarea>}·{@code <pre>} 글자는
     * 태그가 아니다. 태그는 {@code <} 뒤가 글자일 때만, 값의 따옴표가 안 닫히면 그 태그는 그대로
     */
    static String quietMarkup(String s) {
        StringBuilder sb = new StringBuilder(s);
        int k = 0;
        while (k < s.length()) {
            if (s.startsWith("<%", k)) {
                int e = s.indexOf("%>", k + 2);
                int end = e < 0 ? s.length() : e + 2;
                blank(sb, s, k, end);
                k = end;
            } else if (s.charAt(k) == '<' && k + 1 < s.length() && Character.isLetter(s.charAt(k + 1))) {
                k = quietTag(sb, s, k + 1);
            } else {
                k++;
            }
        }
        return sb.toString();
    }

    /** 태그 안 — 따옴표 값 속을 비우고 태그 끝 {@code >} 뒤 자리. 안 닫힌 따옴표·다음 {@code <} 에서 멈춘다 */
    private static int quietTag(StringBuilder sb, String s, int from) {
        int k = from;
        while (k < s.length()) {
            char c = s.charAt(k);
            if (c == '>' || c == '<') {
                return c == '>' ? k + 1 : k;
            }
            if (c == '"' || c == '\'') {
                int close = s.indexOf(c, k + 1);
                if (close < 0) {
                    return k + 1;
                }
                blank(sb, s, k + 1, close);
                k = close;
            }
            k++;
        }
        return k;
    }

    private static void blank(StringBuilder sb, String s, int from, int to) {
        for (int i = from; i < to; i++) {
            if (s.charAt(i) != NL) {
                sb.setCharAt(i, ' ');
            }
        }
    }

    /** 5-116 — 날글로 볼 태그를 고른다(마크업 고침은 textarea 만 — pre 안은 마크업이다) */
    static boolean[] rawLines(String text, String lang, List<String> tags) {
        boolean[] raw = new boolean[lines(text)];
        if (!MARKUP.contains(lang == null ? "" : lang)) {
            return raw;
        }
        String s = blankScripts(quietMarkup(RegexRule.stripComments(text, lang)));
        int n = s.length();
        int line = 0;
        int k = 0;
        while (k < n) {
            char c = s.charAt(k);
            String tag = c == '<' ? openTag(s, k, tags) : null;
            if (tag == null) {
                line += c == '\n' ? 1 : 0;
                k++;
                continue;
            }
            int close = indexOfIgnoreCase(s, "</" + tag, k + 1 + tag.length());
            int end = close < 0 ? n : close;
            for (; k < end; k++) {
                raw[line] = true;
                line += s.charAt(k) == '\n' ? 1 : 0;
            }
            raw[line] = true;
            k = close < 0 ? n : close + 2;
        }
        return raw;
    }

    /** 건너뛸 사유(「템플릿 리터럴 안 debugger」 꼴), 손대도 되면 null. word 는 debugger·console */
    static String reason(Js js, boolean[] raw, int i, String word) {
        if (!js.balanced()) {
            return UNBALANCED;
        }
        if (js.inTemplate()[i]) {
            return "템플릿 리터럴 안 " + word;
        }
        if (raw[i]) {
            return "<pre>·<textarea> 안 " + word;
        }
        return null;
    }

    private static int lines(String s) {
        int n = 1;
        for (int k = 0; k < s.length(); k++) {
            n += s.charAt(k) == '\n' ? 1 : 0;
        }
        return n;
    }

    /** k 자리 < 뒤가 tags 가운데 하나이고 그 뒤가 공백·>·/·끝이면 그 태그 이름 */
    private static String openTag(String s, int k, List<String> tags) {
        for (String tag : tags) {
            int after = k + 1 + tag.length();
            if (s.regionMatches(true, k + 1, tag, 0, tag.length())
                    && (after == s.length() || Character.isWhitespace(s.charAt(after)) || s.charAt(after) == '>' || s.charAt(after) == '/')) {
                return tag;
            }
        }
        return null;
    }

    /** 소문자 변환 없이 — 길이가 바뀌는 글자가 있다 */
    private static int indexOfIgnoreCase(String s, String what, int from) {
        for (int k = from; k + what.length() <= s.length(); k++) {
            if (s.regionMatches(true, k, what, 0, what.length())) {
                return k;
            }
        }
        return -1;
    }

    /** 훑개 상태 — 열린 템플릿 수 · 템플릿 글 자리 · 따옴표(줄 끝에서 끊김) · ${ 마다의 { 깊이 */
    private static final class Walk {
        final String s;
        final boolean[] code;
        final boolean[] inTemplate;
        final Deque<Integer> braces = new ArrayDeque<>();
        int open;
        boolean text;
        char quote;
        int line;
        int k;

        Walk(String s) {
            this.s = s;
            this.code = new boolean[s.length()];
            this.inTemplate = new boolean[lines(s)];
        }

        void run() {
            for (k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                if (c == NL) {
                    quote = 0;
                    line++;
                    inTemplate[line] = open > 0;
                } else if (text) {
                    textStep(c);
                } else {
                    codeStep(c);
                }
            }
        }

        private boolean escapes() {
            return k + 1 < s.length() && s.charAt(k + 1) != '\n';
        }

        /** 템플릿 글 자리 — \ 는 다음 글자 건너뜀 · ` 는 닫음 · ${ 는 코드로 */
        private void textStep(char c) {
            if (c == '\\' && escapes()) {
                k++;
            } else if (c == TICK) {
                open--;
                text = false;
            } else if (c == '$' && k + 1 < s.length() && s.charAt(k + 1) == OPEN) {
                braces.push(0);
                text = false;
                k++;
            }
        }

        /** 코드 자리 — 따옴표 · 백틱 열기 · ${ } 안 중괄호 깊이 */
        private void codeStep(char c) {
            if (quote != 0) {
                if (c == '\\' && escapes()) {
                    k++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == TICK) {
                open++;
                text = true;
            } else if (c == CLOSE && !braces.isEmpty() && braces.peek() == 0) {
                braces.pop();
                text = true;
            } else {
                braceStep(c);
                code[k] = true;
            }
        }

        private void braceStep(char c) {
            if (braces.isEmpty()) {
                return;
            }
            if (c == OPEN) {
                braces.push(braces.pop() + 1);
            } else if (c == CLOSE) {
                braces.push(braces.pop() - 1);
            }
        }
    }
}
