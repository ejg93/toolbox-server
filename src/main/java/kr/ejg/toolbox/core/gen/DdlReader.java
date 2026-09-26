package kr.ejg.toolbox.core.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Table;

/**
 * CREATE TABLE 붙여넣기 → 테이블(1-9). 완전한 SQL 파서가 아니다 — DTO 에 필요한 컬럼 이름·타입·NULL·PK·코멘트만 읽는다.
 * 괄호 깊이를 세며 최상위 쉼표로 줄을 나누고(`NUMBER(10,2)` 안전), 제약 줄은 PK 만 줍는다.
 * 못 읽은 줄은 조용히 넘기지 않는다 — {@link Result#unreadable()} 과 컬럼 TODO 로 드러낸다.
 */
public final class DdlReader {

    public record Unreadable(String table, String line) {
    }

    /** notes: 테이블명(대문자) → (컬럼명(대문자) → TODO 문구) */
    public record Result(List<Table> tables, List<Unreadable> unreadable, Map<String, Map<String, String>> notes) {
        public Result {
            tables = List.copyOf(tables);
            unreadable = List.copyOf(unreadable);
            notes = Map.copyOf(notes);
        }
    }

    /**
     * CREATE 머리까지만 정규식 — 이름은 {@link #tableName} 이 손으로 훑는다. 이름을 반복 그룹으로 잡으면 「a.a.a…」 에서
     * 지수 백트래킹(CodeQL)·스택 넘침이 난다(번들 4 PR)
     */
    private static final Pattern CREATE = Pattern.compile(
            "^CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:(?:GLOBAL|LOCAL)\\s+)?(?:TEMPORARY\\s+|TEMP\\s+|UNLOGGED\\s+)?TABLE\\s+"
                    + "(?:IF\\s+NOT\\s+EXISTS\\s+)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_ON = Pattern.compile(
            "^COMMENT\\s+ON\\s+(TABLE|COLUMN)\\s+(\\S+)\\s+IS\\s+'((?:[^']++|'')*+)'", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ALTER_PK = Pattern.compile(
            "^ALTER\\s+TABLE\\s+(\\S+)\\s+ADD\\s+(?:CONSTRAINT\\s+\\S+\\s+)?PRIMARY\\s+KEY\\s*(?:CLUSTERED\\s+|NONCLUSTERED\\s+)?\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TABLE_COMMENT = Pattern.compile("COMMENT\\s*=?\\s*'((?:[^']++|'')*+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSTRAINT_LINE = Pattern.compile(
            "^(CONSTRAINT|PRIMARY\\s+KEY|UNIQUE|FOREIGN\\s+KEY|KEY|INDEX|CHECK|FULLTEXT|SPATIAL|PERIOD|EXCLUDE)\\b",
            Pattern.CASE_INSENSITIVE);
    /** 컬럼 줄에서 타입이 끝나는 자리 */
    private static final Pattern TYPE_END = Pattern.compile(
            "\\s+(NOT\\s+NULL|NULL|DEFAULT|CONSTRAINT|PRIMARY\\s+KEY|COMMENT|IDENTITY|AUTO_INCREMENT|GENERATED|REFERENCES|UNIQUE|"
                    + "CHECK|COLLATE|CHARACTER\\s+SET|ON\\s+UPDATE|ENABLE|DISABLE|SPARSE|ROWGUIDCOL)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFAULT = Pattern.compile("\\bDEFAULT\\s+('(?:[^']++|'')*+'|\\([^)]*\\)|[^\\s,]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_COMMENT = Pattern.compile("\\bCOMMENT\\s+'((?:[^']++|'')*+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("^[\\p{L}_][\\p{L}\\p{N}_$#@]*$");
    private static final Pattern NUMERIC_TYPE = Pattern.compile(
            "NUMBER|NUMERIC|DECIMAL|DEC|FLOAT|DOUBLE|REAL|INT|MONEY", Pattern.CASE_INSENSITIVE);

    private DdlReader() {
    }

    private static final class Draft {
        final String schema;
        final String name;
        String comment;
        final List<Column> cols = new ArrayList<>();
        final List<String> pk = new ArrayList<>();
        final Map<String, String> notes = new LinkedHashMap<>();

        Draft(String schema, String name) {
            this.schema = schema;
            this.name = name;
        }
    }

    public static Result read(String ddl) {
        Map<String, Draft> tables = new LinkedHashMap<>();
        List<Unreadable> unreadable = new ArrayList<>();
        for (String stmt : statements(stripComments(ddl == null ? "" : ddl))) {
            Matcher m = CREATE.matcher(stmt);
            boolean head = m.lookingAt();
            int open = head ? openParen(stmt, m.end()) : -1;
            if (head && open > m.end()) {
                List<String> name = nameParts(stmt.substring(m.end(), open).trim());
                Draft d = new Draft(name.size() > 1 ? name.get(name.size() - 2) : "", name.get(name.size() - 1));
                int close = matching(stmt, open);
                String body = stmt.substring(open + 1, close < 0 ? stmt.length() : close);
                String tail = close < 0 ? "" : stmt.substring(close + 1);
                Matcher tc = TABLE_COMMENT.matcher(tail);
                if (tc.find()) {
                    d.comment = unq(tc.group(1));
                }
                for (String item : splitTop(body, ',')) {
                    item(d, item.trim(), unreadable);
                }
                tables.put(d.name.toUpperCase(Locale.ROOT), d);
                continue;
            }
            Matcher c = COMMENT_ON.matcher(stmt);
            if (c.find()) {
                List<String> parts = nameParts(c.group(2));
                String text = unq(c.group(3));
                if (c.group(1).equalsIgnoreCase("TABLE")) {
                    Draft d = tables.get(parts.get(parts.size() - 1).toUpperCase(Locale.ROOT));
                    if (d != null) {
                        d.comment = text;
                    }
                } else if (parts.size() >= 2) {
                    Draft d = tables.get(parts.get(parts.size() - 2).toUpperCase(Locale.ROOT));
                    String col = parts.get(parts.size() - 1);
                    if (d != null) {
                        for (int i = 0; i < d.cols.size(); i++) {
                            if (d.cols.get(i).name().equalsIgnoreCase(col)) {
                                d.cols.set(i, d.cols.get(i).withComment(text));
                            }
                        }
                    }
                }
                continue;
            }
            Matcher a = ALTER_PK.matcher(stmt);
            if (a.find()) {
                List<String> parts = nameParts(a.group(1));
                Draft d = tables.get(parts.get(parts.size() - 1).toUpperCase(Locale.ROOT));
                if (d != null) {
                    d.pk.clear();
                    for (String p : splitTop(a.group(2), ',')) {
                        d.pk.add(unquote(p.trim().split("\\s+")[0]));
                    }
                }
            }
        }
        List<Table> out = new ArrayList<>();
        Map<String, Map<String, String>> notes = new LinkedHashMap<>();
        for (Draft d : tables.values()) {
            List<Column> cols = new ArrayList<>();
            for (Column col : d.cols) {
                boolean inPk = d.pk.stream().anyMatch(p -> p.equalsIgnoreCase(col.name()));
                cols.add(inPk && col.nullable() ? new Column(col.name(), col.ordinal(), col.nativeType(), col.jdbcType(), col.length(),
                        col.precision(), col.scale(), false, col.defaultValue(), col.comment(), col.domain()) : col);
            }
            PrimaryKey pk = d.pk.isEmpty() ? null : new PrimaryKey(null, List.copyOf(d.pk));
            out.add(Table.of(d.schema, d.name, "TABLE", d.comment).withColumns(cols).withConstraints(pk, List.of(), List.of()));
            if (!d.notes.isEmpty()) {
                notes.put(d.name.toUpperCase(Locale.ROOT), Map.copyOf(d.notes));
            }
        }
        return new Result(out, unreadable, notes);
    }

    private static void item(Draft d, String item, List<Unreadable> unreadable) {
        if (item.isEmpty()) {
            return;
        }
        Matcher cm = CONSTRAINT_LINE.matcher(item);
        if (cm.find()) {
            Matcher pk = Pattern.compile("PRIMARY\\s+KEY\\s*(?:CLUSTERED\\s+|NONCLUSTERED\\s+)?\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE)
                    .matcher(item);
            if (pk.find()) {
                d.pk.clear();
                for (String p : splitTop(pk.group(1), ',')) {
                    d.pk.add(unquote(p.trim().split("\\s+")[0]));
                }
            }
            return;
        }
        String[] head = firstToken(item);
        String name = head == null ? null : unquote(head[0]);
        String rest = head == null ? "" : head[1];
        Matcher end = TYPE_END.matcher(" " + rest);
        String type = (end.find() ? (" " + rest).substring(0, end.start()) : rest).trim();
        if (name == null || !NAME.matcher(name).matches() || type.isEmpty()) {
            unreadable.add(new Unreadable(d.name, item));
            String key = name != null && NAME.matcher(name).matches() ? name : "unreadable" + (d.cols.size() + 1);
            d.cols.add(new Column(key, d.cols.size() + 1, null, null, null, null, null, true, null, null, null));
            d.notes.put(key.toUpperCase(Locale.ROOT), "TODO 못 읽음: " + item.replaceAll("\\s+", " "));
            return;
        }
        String upper = rest.toUpperCase(Locale.ROOT);
        boolean inlinePk = Pattern.compile("\\bPRIMARY\\s+KEY\\b").matcher(upper).find();
        boolean notNull = Pattern.compile("\\bNOT\\s+NULL\\b").matcher(upper).find() || inlinePk;
        Matcher df = DEFAULT.matcher(rest);
        String def = df.find() ? df.group(1) : null;
        Matcher ic = INLINE_COMMENT.matcher(rest);
        String comment = ic.find() ? unq(ic.group(1)) : null;
        String base = type.replaceAll("\\([^)]*\\)", " ").trim().replaceAll("\\s+", " ");
        Long length = null;
        Integer precision = null;
        Integer scale = null;
        Matcher size = Pattern.compile("\\(\\s*(\\d+)\\s*(?:(?:,\\s*(-?\\d+))|\\s+(?:BYTE|CHAR))?\\s*\\)", Pattern.CASE_INSENSITIVE)
                .matcher(type);
        if (size.find()) {
            if (NUMERIC_TYPE.matcher(base).lookingAt() && !base.toUpperCase(Locale.ROOT).startsWith("INTERVAL")) {
                precision = Integer.parseInt(size.group(1));
                scale = size.group(2) == null ? 0 : Integer.parseInt(size.group(2));
            } else {
                length = Long.parseLong(size.group(1));
            }
        }
        d.cols.add(new Column(name, d.cols.size() + 1, base, null, length, precision, scale, !notNull, def, comment, null));
        if (inlinePk) {
            d.pk.add(name);
        }
    }

    /** [이름, 나머지] — 따옴표·백틱·대괄호 이름은 통째로 */
    private static String[] firstToken(String s) {
        if (s.isEmpty()) {
            return null;
        }
        char c = s.charAt(0);
        char close = c == '"' ? '"' : c == '`' ? '`' : c == '[' ? ']' : 0;
        if (close != 0) {
            int e = s.indexOf(close, 1);
            return e < 0 ? null : new String[] {s.substring(0, e + 1), s.substring(e + 1).trim()};
        }
        String[] p = s.split("\\s+", 2);
        return new String[] {p[0], p.length > 1 ? p[1].trim() : ""};
    }

    static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("`") && t.endsWith("`"))
                || (t.startsWith("[") && t.endsWith("]")))) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String unq(String sqlString) {
        return sqlString.replace("''", "'");
    }

    private static List<String> nameParts(String qualified) {
        List<String> out = new ArrayList<>();
        for (String p : splitTop(qualified.replaceAll("\\s*\\.\\s*", "."), '.')) {
            out.add(unquote(p));
        }
        return out;
    }

    /** 테이블 이름 뒤 첫 「(」 — 따옴표·백틱·대괄호 안은 건너뛴다. 이름 자리에 공백(따옴표 밖)이 두 번 끊기면 이름이 아니다 */
    static int openParen(String s, int from) {
        char quote = 0;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote || (quote == '[' && c == ']')) {
                    quote = 0;
                }
            } else if (c == '"' || c == '`' || c == '[') {
                quote = c;
            } else if (c == '(') {
                String name = s.substring(from, i).replaceAll("\\s*\\.\\s*", ".").trim();
                return name.isEmpty() || name.chars().anyMatch(Character::isWhitespace) && !name.contains("\"")
                        && !name.contains("[") && !name.contains("`") ? -1 : i;
            }
        }
        return -1;
    }

    private static int matching(String s, int open) {
        int depth = 0;
        boolean q = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                q = !q;
            } else if (!q && c == '(') {
                depth++;
            } else if (!q && c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** 괄호 깊이 0 이고 따옴표 밖인 구분자로만 나눈다 */
    static List<String> splitTop(String s, char sep) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote || (quote == '[' && c == ']')) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"' || c == '`' || c == '[') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == sep && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    /** 문장 나누기 — 세미콜론(따옴표·괄호 밖) 또는 줄 하나에 GO */
    private static List<String> statements(String s) {
        List<String> out = new ArrayList<>();
        for (String st : splitTop(s.replaceAll("(?im)^\\s*GO\\s*$", ";"), ';')) {
            String t = st.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** -- 줄 주석과 블록 주석을 지운다(따옴표 안은 둔다) */
    static String stripComments(String s) {
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                q = !q;
                sb.append(c);
            } else if (!q && c == '-' && i + 1 < s.length() && s.charAt(i + 1) == '-') {
                while (i < s.length() && s.charAt(i) != '\n') {
                    i++;
                }
                sb.append('\n');
            } else if (!q && c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int e = s.indexOf("*/", i + 2);
                i = e < 0 ? s.length() : e + 1;
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
