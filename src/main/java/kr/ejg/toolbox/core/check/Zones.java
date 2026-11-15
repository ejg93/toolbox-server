package kr.ejg.toolbox.core.check;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/** 줄 구역 판정(설계 70 §3-5) — 지우기·끼우기가 손대면 안 되는 자리. 파서가 아니다: 따옴표·템플릿·${ } 깊이만 센다. 정규식 리터럴은 모른다 */
final class Zones {
    static final String UNBALANCED = "백틱 짝이 안 맞아 템플릿 리터럴 판정을 못 믿는다";
    private static final Set<String> MARKUP = Set.of("jsp", "jspf", "tag", "html", "htm", "xhtml", "vue");
    private static final List<String> RAW_TAGS = List.of("pre", "textarea");
    private static final char NL = '\n';
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
        boolean[] raw = new boolean[lines(text)];
        if (!MARKUP.contains(lang == null ? "" : lang)) {
            return raw;
        }
        String s = RegexRule.stripComments(text, lang);
        int n = s.length();
        int line = 0;
        int k = 0;
        while (k < n) {
            char c = s.charAt(k);
            String tag = c == '<' ? openTag(s, k) : null;
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

    /** k 자리 < 뒤가 pre·textarea 이고 그 뒤가 공백·>·/·끝이면 그 태그 이름 */
    private static String openTag(String s, int k) {
        for (String tag : RAW_TAGS) {
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
