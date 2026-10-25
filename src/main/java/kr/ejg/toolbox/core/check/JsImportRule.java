package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ㅁ 미사용 import(JS·TS) — ES import 문의 바인딩 이름이 import 문 밖(주석 뺀 글)에 단어로 안 나오면 그 import 줄에 한 건.
 * 부수 효과 import(`import './a.css'`)는 이름이 없어 안 본다. 타입만 쓰는 이름도 단어로 나오면 쓰인 것으로 본다
 */
public final class JsImportRule implements Rule {

    private static final Pattern IMPORT = Pattern.compile("(?m)^[ \\t]*import\\s+(?:type\\s+)?([^;'\"]*?)\\s+from\\s+['\"]");
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_$][\\w$]*$");

    private final Def def;

    public JsImportRule(Def def) {
        this.def = def;
    }

    /**
     * import 문 하나(5-34a) — 줄(1부터)·절 범위·문장 끝(text 기준 offset, 닫는 따옴표와 이어지는 {@code ;} 뒤)·바인딩 이름·안 쓰이는 이름.
     * 코드 정리가 같은 판정으로 자리를 찾는다
     */
    public record Stmt(int startLine, int endLine, int clauseStart, int clauseEnd, int stmtEnd, List<String> names, List<String> unused) {
        public Stmt {
            names = List.copyOf(names);
            unused = List.copyOf(unused);
        }
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public List<Finding> apply(Source s) {
        String[] raw = s.text().split("\n", -1);
        List<Finding> out = new ArrayList<>();
        for (Stmt st : statements(s)) {
            if (!st.unused().isEmpty()) {
                out.add(new Finding(s.rel(), st.startLine(), def.group(), def.id(), def.severity(), Finding.excerpt(raw[st.startLine() - 1])));
            }
        }
        return out;
    }

    /** 파일의 import 문 전부(안 쓰이는 이름이 없는 것까지). 주석을 공백으로 바꾼 글은 길이가 같아 offset 이 원문과 맞는다 */
    public static List<Stmt> statements(Source s) {
        String code = RegexRule.stripComments(s.text(), s.lang());
        List<Stmt> out = new ArrayList<>();
        if (code.length() != s.text().length()) {
            return out;
        }
        Matcher m = IMPORT.matcher(code);
        StringBuilder rest = new StringBuilder(code);
        List<int[]> spans = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[] {m.start(), m.end()});
        }
        for (int[] sp : spans) {
            for (int i = sp[0]; i < sp[1]; i++) {
                if (rest.charAt(i) != '\n') {
                    rest.setCharAt(i, ' ');
                }
            }
        }
        // 스프레드·나머지 `...x` 는 아래 `.` 앞 막기에 걸려 안 쓰임이 됐다(5-49) — 공백 셋으로 바꿔 길이를 지킨다
        String body = rest.toString().replace("...", "   ");
        m.reset();
        while (m.find()) {
            List<String> names = names(m.group(1));
            List<String> unused = new ArrayList<>();
            for (String name : names) {
                if (!Pattern.compile("(?<![\\w$.])" + Pattern.quote(name) + "(?![\\w$])").matcher(body).find()) {
                    unused.add(name);
                }
            }
            int end = stmtEnd(code, m.end());
            out.add(new Stmt(lineOf(code, m.start()), lineOf(code, Math.max(m.start(), end - 1)), m.start(1), m.end(1), end, names, unused));
        }
        return out;
    }

    /** 여는 따옴표 바로 뒤에서 — 같은 따옴표를 닫고, 공백·탭 뒤 {@code ;} 가 있으면 그 뒤까지. 못 닫으면 from */
    private static int stmtEnd(String code, int from) {
        char q = code.charAt(from - 1);
        int close = code.indexOf(q, from);
        int nl = code.indexOf('\n', from);
        if (close < 0 || nl >= 0 && nl < close) {
            return from;
        }
        int e = close + 1;
        int k = e;
        while (k < code.length() && (code.charAt(k) == ' ' || code.charAt(k) == '\t')) {
            k++;
        }
        return k < code.length() && code.charAt(k) == ';' ? k + 1 : e;
    }

    /** `A, { b, c as d, type E }` · `* as ns` → 바인딩 이름 */
    static List<String> names(String clause) {
        List<String> out = new ArrayList<>();
        String c = clause.replaceAll("\\s+", " ").trim();
        int brace = c.indexOf('{');
        String head = brace < 0 ? c : c.substring(0, brace);
        String inner = brace < 0 ? "" : c.substring(brace + 1, c.indexOf('}', brace) < 0 ? c.length() : c.indexOf('}', brace));
        for (String part : head.split(",")) {
            String p = part.trim();
            if (p.startsWith("* as ")) {
                p = p.substring(5).trim();
            }
            if (IDENT.matcher(p).matches()) {
                out.add(p);
            }
        }
        for (String part : inner.split(",")) {
            String p = part.trim().replaceFirst("^type\\s+", "");
            int as = p.indexOf(" as ");
            p = as < 0 ? p : p.substring(as + 4).trim();
            if (IDENT.matcher(p).matches()) {
                out.add(p);
            }
        }
        return out;
    }

    static int lineOf(String s, int pos) {
        int line = 1;
        for (int i = 0; i < pos; i++) {
            if (s.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
