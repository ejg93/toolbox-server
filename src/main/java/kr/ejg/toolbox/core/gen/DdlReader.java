package kr.ejg.toolbox.core.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Table;

/**
 * CREATE TABLE 붙여넣기 → 테이블(1-9). 완전한 SQL 파서가 아니다 — DTO 에 필요한 컬럼 이름·타입·NULL·PK·코멘트만 읽는다.
 * 괄호 깊이를 세며 최상위 쉼표로 줄을 나누고(`NUMBER(10,2)` 안전), 제약 줄은 PK·FK 만 줍는다(FK 는 제약 줄·컬럼 REFERENCES·ALTER 셋 — 1-10).
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
    private static final Pattern ALTER_TABLE = Pattern.compile(
            "^ALTER\\s+TABLE\\s+(?:ONLY\\s+)?(\\S+)\\s+(?:WITH\\s+(?:NO)?CHECK\\s+)?ADD\\b", Pattern.CASE_INSENSITIVE);
    /**
     * 제약 줄·ALTER 의 FK — MySQL 은 `FOREIGN KEY 인덱스이름 (c)` 도 받는다(eGov maria). 참조 이름은 공백 없는 한 덩어리
     * (`[dbo].[Artist]`·`"s"."t"`), 참조 컬럼은 생략될 수 있다
     */
    private static final Pattern FK = Pattern.compile(
            "(?:CONSTRAINT\\s+(\\S+)\\s+)?FOREIGN\\s+KEY\\s*(?:([^\\s(]+)\\s*)?\\(([^)]*)\\)\\s*REFERENCES\\s+([^\\s(]+)\\s*(?:\\(([^)]*)\\))?",
            Pattern.CASE_INSENSITIVE);
    /** 컬럼 줄의 REFERENCES p(c) */
    private static final Pattern COL_REF = Pattern.compile("\\bREFERENCES\\s+([^\\s(]+)\\s*(?:\\(([^)]*)\\))?",
            Pattern.CASE_INSENSITIVE);
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
        final List<ForeignKey> fks = new ArrayList<>();
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
            if (!head && BURIED_CREATE.matcher(stmt).find()) {
                // CREATE TABLE 앞에 다른 글이 붙은 문장(원본 오타 — 실물 eGov PG DDL 의 「;s」) — 조용히 버리지 않고 드러낸다
                String first = stmt.lines().findFirst().orElse("").strip();
                unreadable.add(new Unreadable("", first.length() > 80 ? first.substring(0, 80) : first));
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
            Matcher at = ALTER_TABLE.matcher(stmt);
            if (at.lookingAt()) {
                List<String> parts = nameParts(at.group(1));
                Draft d = tables.get(parts.get(parts.size() - 1).toUpperCase(Locale.ROOT));
                Matcher f = FK.matcher(stmt.substring(at.end()));
                while (d != null && f.find()) {
                    d.fks.add(fk(d, f.group(1) != null ? f.group(1) : f.group(2), f.group(3), f.group(4), f.group(5)));
                }
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
            out.add(Table.of(d.schema, d.name, "TABLE", d.comment).withColumns(cols).withConstraints(pk, d.fks, List.of()));
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
            Matcher f = FK.matcher(item);
            if (f.find()) {
                d.fks.add(fk(d, f.group(1) != null ? f.group(1) : f.group(2), f.group(3), f.group(4), f.group(5)));
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
        Matcher ref = COL_REF.matcher(rest);
        if (ref.find()) {
            d.fks.add(fk(d, null, name, ref.group(1), ref.group(2)));
        }
    }

    /** 참조 표가 DDL 에 없어도 그대로 둔다. 참조 스키마는 표 스키마와 다를 때만(JdbcMetaSource 와 같게) */
    private static ForeignKey fk(Draft d, String name, String cols, String ref, String refCols) {
        List<String> parts = nameParts(ref);
        String refSchema = parts.size() > 1 ? parts.get(parts.size() - 2) : null;
        return new ForeignKey(name == null ? null : unquote(name), names(cols),
                refSchema == null || refSchema.equalsIgnoreCase(d.schema) ? null : refSchema, parts.get(parts.size() - 1),
                refCols == null ? List.of() : names(refCols));
    }

    private static List<String> names(String list) {
        List<String> out = new ArrayList<>();
        for (String p : splitTop(list, ',')) {
            if (!p.isBlank()) {
                out.add(unquote(p.trim().split("\\s+")[0]));
            }
        }
        return out;
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
    /**
     * SQL*Plus 명령 줄 — 세미콜론 없이 문장 앞에 붙어 CREATE 를 가린다(V-4 실물 표본: Oracle 샘플 스키마 co_create.sql 을 0 테이블로 읽음).
     * SET 은 SQL*Plus 옵션일 때만(UPDATE … SET 줄과 가른다)
     */
    /** 문장 가운데 줄머리에 묻힌 CREATE TABLE */
    private static final Pattern BURIED_CREATE = Pattern.compile(
            "(?im)^[ \\t]*CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:GLOBAL\\s+|LOCAL\\s+)?(?:TEMPORARY\\s+|TEMP\\s+|UNLOGGED\\s+)?TABLE\\b"); // 중첩 반복 없이(ReDoS 판정)

    private static final Pattern SQLPLUS = Pattern.compile("(?im)^[ \\t]*(?:REM(?:ARK)?\\b|PROMPT\\b|SPOOL\\b|WHENEVER\\b|DEFINE\\b|UNDEFINE\\b"
            + "|COLUMN\\b|TTITLE\\b|BTITLE\\b|CONNECT\\b|CONN\\b|SHOW\\b|PAUSE\\b|ACCEPT\\b|HOST\\b|EXIT\\b|QUIT\\b|@"
            + "|SET[ \\t]+(?:ECHO|DEFINE|FEEDBACK|HEADING|LINESIZE|PAGESIZE|SERVEROUTPUT|TERMOUT|VERIFY|TIMING|SQLBLANKLINES|TRIMSPOOL"
            + "|LONG|SCAN|ESCAPE|CONCAT|AUTOCOMMIT|NULL|NUMWIDTH|WRAP|COLSEP|TAB|SQLPROMPT)\\b)[^\\n]*$");

    private static List<String> statements(String s) {
        List<String> out = new ArrayList<>();
        String plain = SQLPLUS.matcher(s).replaceAll("").replaceAll("(?m)^[ \\t]*/[ \\t]*$", ";");
        for (String st : splitTop(plain.replaceAll("(?im)^\\s*GO\\s*$", ";"), ';')) {
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
