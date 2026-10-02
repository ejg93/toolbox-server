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

    @Override
    public Def def() {
        return def;
    }

    @Override
    public List<Finding> apply(Source s) {
        String code = RegexRule.stripComments(s.text(), s.lang());
        String[] raw = s.text().split("\n", -1);
        List<Finding> out = new ArrayList<>();
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
        String body = rest.toString();
        m.reset();
        while (m.find()) {
            List<String> unused = new ArrayList<>();
            for (String name : names(m.group(1))) {
                if (!Pattern.compile("(?<![\\w$.])" + Pattern.quote(name) + "(?![\\w$])").matcher(body).find()) {
                    unused.add(name);
                }
            }
            if (!unused.isEmpty()) {
                int line = lineOf(code, m.start());
                out.add(new Finding(s.rel(), line, def.group(), def.id(), def.severity(), Finding.excerpt(raw[line - 1])));
            }
        }
        return out;
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

    private static int lineOf(String s, int pos) {
        int line = 1;
        for (int i = 0; i < pos; i++) {
            if (s.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
