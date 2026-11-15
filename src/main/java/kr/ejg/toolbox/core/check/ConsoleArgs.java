package kr.ejg.toolbox.core.check;

import java.util.Set;

/** console 문장 인자 판정(설계 70 §3-7) — 지우면 동작이 바뀌는 꼴 */
final class ConsoleArgs {
    static final String SLASH = "인자에 / — 정규식 리터럴일 수 있어 괄호 셈을 못 믿는다";
    /** 마무리 리뷰 M2 — 인자 코드 자리의 괄호 짝이 안 맞는다(겹친 템플릿 글의 괄호로 끝이 늘어남) */
    static final String PAREN = "인자 괄호 짝이 안 맞는다 — console 끝을 못 믿어 손으로";
    static final String SIDE_EFFECT = "인자에 호출·증감·대입 — 지우면 부수 효과가 사라질 수 있어 손으로";
    /** 호출로 안 세는 이름 — 늘리지 않는다 */
    private static final Set<String> PURE = Set.of("stringify", "String", "Number", "Boolean", "parseInt", "parseFloat", "toString", "toFixed", "typeof", "function");
    private static final Set<String> WORDS = Set.of("await", "yield", "delete", "new");
    private static final String SCRIPT = "<script";

    private ConsoleArgs() {
    }

    /** sl[from] 의 open(여는 괄호) 뒤부터 sl[end] 의 마지막 ) 앞까지 — 줄은 \n 으로 잇는다 */
    static String text(String[] sl, int from, int open, int end) {
        if (from == end) {
            return sl[from].substring(open + 1, sl[end].lastIndexOf(')'));
        }
        StringBuilder sb = new StringBuilder(sl[from].substring(open + 1));
        for (int i = from + 1; i < end; i++) {
            sb.append('\n').append(sl[i]);
        }
        return sb.append('\n').append(sl[end], 0, sl[end].lastIndexOf(')')).toString();
    }

    /** 지우면 안 되는 사유, 지워도 되면 null — Zones.js 의 코드 자리만 본다. / 가 먼저 */
    static String risk(String args) {
        boolean[] code = Zones.js(args).code();
        for (int k = 0; k < args.length(); k++) {
            if (code[k] && args.charAt(k) == '/') {
                return SLASH;
            }
        }
        if (!balanced(args, code)) {
            return PAREN;
        }
        for (int k = 0; k < args.length(); k++) {
            if (code[k] && sideEffect(args, code, k)) {
                return SIDE_EFFECT;
            }
        }
        return null;
    }

    /** 코드 자리 ( ) 깊이가 음수로 안 가고 0 으로 끝난다 */
    private static boolean balanced(String s, boolean[] code) {
        int depth = 0;
        for (int k = 0; k < s.length() && depth >= 0; k++) {
            if (code[k]) {
                depth += s.charAt(k) == '(' ? 1 : s.charAt(k) == ')' ? -1 : 0;
            }
        }
        return depth == 0;
    }

    private static boolean sideEffect(String s, boolean[] code, int k) {
        char c = s.charAt(k);
        char next = k + 1 < s.length() ? s.charAt(k + 1) : 0;
        return switch (c) {
            case '+', '-' -> next == c;
            case '=' -> (k == 0 || "=!<>".indexOf(s.charAt(k - 1)) < 0) && next != '=' && next != '>';
            case '(' -> call(s, k);
            default -> word(c) && (k == 0 || !word(s.charAt(k - 1)) || !code[k - 1]) && WORDS.contains(wordAt(s, k));
        };
    }

    /** ( 앞(공백 건너)이 ) ] 이거나 PURE 밖 이름이면 호출 */
    private static boolean call(String s, int k) {
        int j = k - 1;
        while (j >= 0 && Character.isWhitespace(s.charAt(j))) {
            j--;
        }
        if (j < 0) {
            return false;
        }
        char p = s.charAt(j);
        if (p == ')' || p == ']') {
            return true;
        }
        if (!word(p)) {
            return false;
        }
        int from = j;
        while (from > 0 && word(s.charAt(from - 1))) {
            from--;
        }
        return !PURE.contains(s.substring(from, j + 1));
    }

    private static String wordAt(String s, int k) {
        int end = k;
        while (end < s.length() && word(s.charAt(end))) {
            end++;
        }
        return s.substring(k, end);
    }

    private static boolean word(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /** 끝 공백 뗀 앞 줄이 <script …> 로 끝나나(대소문자 무시, 손 훑기 — 선형) */
    static boolean scriptOpen(String prev) {
        String p = prev.stripTrailing();
        if (!p.endsWith(">")) {
            return false;
        }
        int from = p.lastIndexOf('>', p.length() - 2) + 1;
        for (int k = from; k + SCRIPT.length() <= p.length(); k++) {
            if (p.regionMatches(true, k, SCRIPT, 0, SCRIPT.length())) {
                int after = k + SCRIPT.length();
                if (after < p.length() && !word(p.charAt(after))) {
                    return true;
                }
            }
        }
        return false;
    }
}
