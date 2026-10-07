package kr.ejg.toolbox.core.gen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;

/**
 * 테이블 제약 → Bean Validation 어노테이션·javadoc 조각(1-31, 설계 16 D7·D8). DTO 생성기(1-31b)와 CRUD VO(1-31d)가 같이 쓴다.
 * NOT NULL → {@code @NotNull}(문자열은 {@code @NotBlank}, PK·DEFAULT 컬럼은 뺀다 — 나중에 채워진다) · 문자 길이 → {@code @Size} ·
 * 수 정밀도 → {@code @Digits} · CHECK 단순 꼴(비교·BETWEEN·IN·OR 등식·PG ANY(ARRAY)) → {@code @Min}/{@code @Max}·
 * {@code @DecimalMin}/{@code @DecimalMax}·{@code @Pattern}. 나머지 CHECK·FK·UNIQUE 는 javadoc 조각으로 남긴다.
 * double·float 는 규격이 Digits·DecimalMin 을 안 받아(근사) note 만.
 */
public final class Validation {

    public enum Ns {
        JAVAX("javax.validation.constraints"), JAKARTA("jakarta.validation.constraints");

        public final String pkg;

        Ns(String pkg) {
            this.pkg = pkg;
        }
    }

    /** annotations 는 단순 이름(@Size(max = 30)), imports 는 FQN. notes 는 javadoc 에 「 · 」 로 잇는 조각, todos 는 // TODO 글 */
    public record Result(List<String> annotations, List<String> notes, List<String> todos, Set<String> imports) {

        public Result {
            annotations = List.copyOf(annotations);
            notes = List.copyOf(notes);
            todos = List.copyOf(todos);
            imports = Set.copyOf(imports);
        }

        public static final Result NONE = new Result(List.of(), List.of(), List.of(), Set.of());
    }

    /** 범위 — min·max 는 없을 수 있다 */
    record Range(String column, BigDecimal min, boolean minIncl, BigDecimal max, boolean maxIncl) {
    }

    /** 허용 값 — quoted 면 문자열 */
    record InSet(String column, List<String> values, boolean quoted) {
    }

    private static final Set<String> INTEGRAL = Set.of("Integer", "Long", "Short", "Byte", "BigInteger", "int", "long", "short", "byte");
    private static final Set<String> DIGITS = Set.of("BigDecimal", "BigInteger", "Integer", "Long", "Short", "Byte", "int", "long",
            "short", "byte");
    private static final Set<String> BYTE_DIALECTS = Set.of("oracle", "tibero");
    /** Oracle 이 NOT NULL 마다 스스로 만드는 꼴 — 어노테이션도 note 도 안 낸다 */
    private static final Pattern NOT_NULL_ONLY = Pattern.compile("(?i)^\\s*\\w+\\s+IS\\s+NOT\\s+NULL\\s*$");
    private static final Pattern CAST = Pattern.compile("::\\s*[A-Za-z_]\\w*(?:\\s+varying)?(?:\\s*\\[\\])?");
    private static final Pattern ANY_ARRAY = Pattern.compile("(?i)=\\s*ANY\\s*\\(\\s*\\(?\\s*ARRAY\\s*\\[(.*?)\\]\\s*\\)?\\s*\\)");
    private static final Pattern BETWEEN = Pattern.compile("(?i)\\b(\\w+)\\s+BETWEEN\\s+(\\S+)\\s+AND\\s+(\\S+)");
    private static final Pattern CMP = Pattern.compile("^(.+?)\\s*(<=|>=|<>|!=|=|<|>)\\s*(.+)$");
    private static final Pattern IN = Pattern.compile("(?i)^(\\w+)\\s+IN\\s*\\((.*)\\)$");
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][\\w$#]*$");
    private static final Pattern PAREN_ATOM = Pattern.compile("(?<![\\w$#])\\((-?[\\w.]+|'(?:[^']|'')*')\\)");
    private static final Pattern NUM =Pattern.compile("^-?\\d+(?:\\.\\d+)?$");

    private Validation() {
    }

    /** 프로필 framework → 네임스페이스. egov5 만 jakarta */
    public static Ns nsOf(String framework) {
        return framework != null && framework.trim().equalsIgnoreCase("egov5") ? Ns.JAKARTA : Ns.JAVAX;
    }

    /** ns 가 null 이면 끔 — 빈 결과 */
    public static Result of(Table t, Column c, String javaType, String dialect, Ns ns) {
        if (ns == null) {
            return Result.NONE;
        }
        String type = javaType == null ? "" : javaType.substring(javaType.lastIndexOf('.') + 1);
        boolean string = type.equals("String");
        String notNull = null;
        String size = null;
        String digits = null;
        String min = null;
        String max = null;
        String pattern = null;
        List<String> notes = new ArrayList<>();
        List<String> todos = new ArrayList<>();

        boolean inPk = t.pk() != null && t.pk().columns().stream().anyMatch(p -> p.equalsIgnoreCase(c.name()));
        if (!c.nullable() && !inPk && (c.defaultValue() == null || c.defaultValue().isBlank())) {
            notNull = string ? "@NotBlank" : "@NotNull";
        }
        if (string && c.length() != null && c.length() > 0 && c.length() < 100000) {
            size = "@Size(max = " + c.length() + ")";
            if (dialect != null && BYTE_DIALECTS.contains(dialect.toLowerCase(Locale.ROOT))) {
                notes.add("DB 길이 " + c.length() + " 은 바이트일 수 있다(한글 1자 = 3바이트)");
            }
        }
        if (DIGITS.contains(type) && c.precision() != null && c.precision() > 0 && (c.scale() == null || c.scale() >= 0)) {
            int s = c.scale() == null ? 0 : c.scale();
            digits = "@Digits(integer = " + Math.max(0, c.precision() - s) + ", fraction = " + s + ")";
        }
        for (Check k : t.checks()) {
            String cond = k.condition() == null ? "" : k.condition().strip();
            List<String> cols = columnsIn(cond, t);
            if (cond.isEmpty() || cols.stream().noneMatch(x -> x.equalsIgnoreCase(c.name())) || NOT_NULL_ONLY.matcher(normalize(cond)).matches()) {
                continue;
            }
            Object rule = cols.size() == 1 ? parse(cond) : null;
            boolean used = false;
            if (rule instanceof Range r && r.column().equalsIgnoreCase(c.name())) {
                if (INTEGRAL.contains(type)) {
                    if (r.min() != null && min == null) {
                        min = "@Min(" + lower(r.min(), r.minIncl()) + ")";
                    }
                    if (r.max() != null && max == null) {
                        max = "@Max(" + upper(r.max(), r.maxIncl()) + ")";
                    }
                    used = true;
                } else if (type.equals("BigDecimal")) {
                    if (r.min() != null && min == null) {
                        min = decimal("@DecimalMin", r.min(), r.minIncl());
                    }
                    if (r.max() != null && max == null) {
                        max = decimal("@DecimalMax", r.max(), r.maxIncl());
                    }
                    used = true;
                }
            } else if (rule instanceof InSet in && in.column().equalsIgnoreCase(c.name())) {
                notes.add("허용 값: " + String.join(", ", in.values().stream().map(v -> in.quoted() ? "'" + v + "'" : v).toList()));
                if (string && in.quoted() && pattern == null) {
                    pattern = "@Pattern(regexp = \"" + javaString("^(" + String.join("|", in.values().stream().map(Validation::regexQuote).toList())
                            + ")$") + "\")";
                }
                continue;
            }
            if (!used) {
                notes.add("CHECK: " + cond);
                todos.add("CHECK 를 코드로 옮긴다");
            }
        }
        for (ForeignKey fk : t.fks()) {
            if (fk.columns().stream().anyMatch(x -> x.equalsIgnoreCase(c.name()))) {
                String ref = fk.refTable() + "(" + String.join(", ", fk.refColumns()) + ")";
                notes.add(fk.columns().size() == 1 ? "FK → " + ref : "FK(" + String.join(", ", fk.columns()) + ") → " + ref);
            }
        }
        for (UniqueKey u : t.uniques()) {
            if (u.columns().stream().anyMatch(x -> x.equalsIgnoreCase(c.name()))) {
                notes.add(u.columns().size() == 1 ? "UNIQUE" : "UNIQUE(" + String.join(", ", u.columns()) + ")");
            }
        }
        List<String> annotations = new ArrayList<>();
        Set<String> imports = new TreeSet<>();
        for (String a : new String[] {notNull, size, digits, min, max, pattern}) {
            if (a != null) {
                annotations.add(a);
                int end = a.indexOf('(');
                imports.add(ns.pkg + "." + a.substring(1, end < 0 ? a.length() : end));
            }
        }
        return new Result(List.copyOf(annotations), List.copyOf(notes), todos.isEmpty() ? List.of() : List.of(todos.get(0)), imports);
    }

    /** 조건 글 → Range·InSet. 둘 이상 컬럼·함수·LIKE·<> 처럼 못 푸는 꼴은 null */
    static Object parse(String condition) {
        String s = normalize(condition);
        Matcher b = BETWEEN.matcher(s);
        s = b.replaceAll("$1 >= $2 AND $1 <= $3");
        List<String> ors = splitTop(s, "OR");
        List<String> ands = splitTop(s, "AND");
        if (ors.size() > 1 && ands.size() > 1) {
            return null;
        }
        if (ors.size() > 1) {
            String col = null;
            List<String> vals = new ArrayList<>();
            Boolean quoted = null;
            for (String part : ors) {
                Object[] cmp = compare(strip(part));
                if (cmp == null || !cmp[1].equals("=")) {
                    return null;
                }
                if (col != null && !col.equalsIgnoreCase((String) cmp[0])) {
                    return null;
                }
                col = (String) cmp[0];
                String lit = strip((String) cmp[2]);
                boolean q = lit.startsWith("'");
                if (quoted != null && quoted != q) {
                    return null;
                }
                quoted = q;
                vals.add(q ? unquote(lit) : lit);
            }
            return new InSet(col, vals, quoted);
        }
        if (ands.size() == 1) {
            Matcher in = IN.matcher(strip(s));
            if (in.matches()) {
                List<String> vals = new ArrayList<>();
                boolean quoted = true;
                for (String v : InsertGen.splitVals(in.group(2))) {
                    String lit = strip(v.trim());
                    if (lit.startsWith("'")) {
                        vals.add(unquote(lit));
                    } else if (NUM.matcher(lit).matches()) {
                        vals.add(lit);
                        quoted = false;
                    } else {
                        return null;
                    }
                }
                return new InSet(in.group(1), vals, quoted);
            }
        }
        String col = null;
        BigDecimal min = null;
        BigDecimal max = null;
        boolean minIncl = true;
        boolean maxIncl = true;
        for (String part : ands) {
            Object[] cmp = compare(strip(part));
            if (cmp == null) {
                return null;
            }
            String op = (String) cmp[1];
            String lit = strip((String) cmp[2]);
            if (col != null && !col.equalsIgnoreCase((String) cmp[0])) {
                return null;
            }
            col = (String) cmp[0];
            if (op.equals("=") && ands.size() == 1) {
                return new InSet(col, List.of(lit.startsWith("'") ? unquote(lit) : lit), lit.startsWith("'"));
            }
            if (!NUM.matcher(lit).matches()) {
                return null;
            }
            BigDecimal v = new BigDecimal(lit);
            switch (op) {
                case ">=", ">" -> {
                    min = v;
                    minIncl = op.equals(">=");
                }
                case "<=", "<" -> {
                    max = v;
                    maxIncl = op.equals("<=");
                }
                default -> {
                    return null;
                }
            }
        }
        return col == null ? null : new Range(col, min, minIncl, max, maxIncl);
    }

    /** 이 조건이 가리키는 표 컬럼(이름이 낱말로 든 것 — Definitions.constraints 와 같은 꼴) */
    static List<String> columnsIn(String condition, Table t) {
        List<String> out = new ArrayList<>();
        String bare = condition.replaceAll("'(?:[^']|'')*'", "''");
        for (Column c : t.columns()) {
            if (Pattern.compile("(?i)(?<![\\w$#])" + Pattern.quote(c.name()) + "(?![\\w$#])").matcher(bare).find()) {
                out.add(c.name());
            }
        }
        return out;
    }

    /** PG 캐스트·ANY(ARRAY)·식별자 따옴표를 걷고 바깥 괄호를 벗긴다 */
    static String normalize(String condition) {
        String s = CAST.matcher(condition).replaceAll("");
        s = ANY_ARRAY.matcher(s).replaceAll(" IN ($1)");
        StringBuilder b = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') {
                q = !q;
            }
            if (!q && (ch == '"' || ch == '`' || ch == '[' || ch == ']')) {
                continue;
            }
            b.append(ch);
        }
        // (status)·(0) 처럼 식별자·리터럴 하나를 감싼 괄호(PG·MSSQL) — 함수 호출 UPPER(X) 는 그대로
        String x = PAREN_ATOM.matcher(b.toString()).replaceAll("$1");
        return strip(x.trim().replaceAll("\\s+", " "));
    }

    /** 바깥을 다 감싼 괄호를 벗긴다 */
    static String strip(String s) {
        String x = s.trim();
        while (x.startsWith("(") && closes(x) == x.length() - 1) {
            x = x.substring(1, x.length() - 1).trim();
        }
        return x;
    }

    private static int closes(String s) {
        int depth = 0;
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') {
                q = !q;
            } else if (!q && ch == '(') {
                depth++;
            } else if (!q && ch == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** 괄호·따옴표 밖의 낱말 AND/OR 로 가른다 */
    private static List<String> splitTop(String s, String word) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean q = false;
        int from = 0;
        String up = s.toUpperCase(Locale.ROOT);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') {
                q = !q;
            } else if (!q && ch == '(') {
                depth++;
            } else if (!q && ch == ')') {
                depth--;
            } else if (!q && depth == 0 && up.startsWith(word, i) && (i == 0 || !wordChar(s.charAt(i - 1)))
                    && (i + word.length() >= s.length() || !wordChar(s.charAt(i + word.length())))) {
                out.add(s.substring(from, i).trim());
                from = i + word.length();
                i = from - 1;
            }
        }
        out.add(s.substring(from).trim());
        return out;
    }

    private static boolean wordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '#';
    }

    /** {컬럼, 연산자, 리터럴} — 리터럴이 왼쪽이면 뒤집는다. 컬럼이 식별자가 아니면 null */
    private static Object[] compare(String part) {
        Matcher m = CMP.matcher(part);
        if (!m.matches()) {
            return null;
        }
        String l = strip(m.group(1));
        String op = m.group(2);
        String r = strip(m.group(3));
        if (IDENT.matcher(l).matches() && !isLiteral(l)) {
            return isLiteral(r) ? new Object[] {l, op, r} : null;
        }
        if (IDENT.matcher(r).matches() && isLiteral(l)) {
            String flip = switch (op) {
                case "<" -> ">";
                case "<=" -> ">=";
                case ">" -> "<";
                case ">=" -> "<=";
                default -> op;
            };
            return new Object[] {r, flip, l};
        }
        return null;
    }

    private static boolean isLiteral(String s) {
        return NUM.matcher(s).matches() || (s.startsWith("'") && s.endsWith("'") && s.length() >= 2);
    }

    private static String unquote(String lit) {
        return lit.substring(1, lit.length() - 1).replace("''", "'");
    }

    private static String lower(BigDecimal v, boolean incl) {
        BigDecimal x = incl ? v.setScale(0, RoundingMode.CEILING) : v.setScale(0, RoundingMode.FLOOR).add(BigDecimal.ONE);
        return x.toPlainString();
    }

    private static String upper(BigDecimal v, boolean incl) {
        BigDecimal x = incl ? v.setScale(0, RoundingMode.FLOOR) : v.setScale(0, RoundingMode.CEILING).subtract(BigDecimal.ONE);
        return x.toPlainString();
    }

    private static String decimal(String name, BigDecimal v, boolean incl) {
        return incl ? name + "(\"" + v.toPlainString() + "\")" : name + "(value = \"" + v.toPlainString() + "\", inclusive = false)";
    }

    private static String regexQuote(String v) {
        StringBuilder b = new StringBuilder();
        for (char ch : v.toCharArray()) {
            if ("\\^$.|?*+()[]{}".indexOf(ch) >= 0) {
                b.append('\\');
            }
            b.append(ch);
        }
        return b.toString();
    }

    private static String javaString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
