package kr.ejg.toolbox.core.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;

/**
 * CREATE TABLE 붙여넣기 → 테이블(1-9). 완전한 SQL 파서가 아니다 — DTO 에 필요한 컬럼 이름·타입·NULL·PK·코멘트만 읽는다.
 * 괄호 깊이를 세며 최상위 쉼표로 줄을 나누고(`NUMBER(10,2)` 안전), 제약 줄은 PK·FK·UNIQUE·CHECK 만 줍는다(FK·UNIQUE 는 제약 줄·컬럼·ALTER 셋 — 1-10·4-26).
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
            "^CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:(?:GLOBAL|LOCAL)\\s+)?(?:PRIVATE\\s+)?(?:TEMPORARY\\s+|TEMP\\s+|UNLOGGED\\s+)?TABLE\\s+"
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
    /**
     * 제약 줄·ALTER 의 UNIQUE(4-26) — 이름은 CONSTRAINT 뒤(g1) 또는 MySQL `UNIQUE KEY 이름 (c)`(g2), 컬럼 g3. 중첩 반복 없이(REDOS)
     */
    private static final Pattern UQ = Pattern.compile(
            "(?:CONSTRAINT\\s+(\\S+)\\s+)?\\bUNIQUE\\b(?:\\s+(?:KEY|INDEX))?(?:\\s+(?:NON)?CLUSTERED)?(?:\\s+([^\\s(]+))?\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);
    /** 컬럼 줄의 인라인 UNIQUE(대문자로 바꾼 나머지에) */
    private static final Pattern INLINE_UQ = Pattern.compile("\\bUNIQUE\\b");
    /** 따옴표 글 — 인라인 UNIQUE 를 볼 때 비운다. `''` 이스케이프는 두 조각으로 비워져 결과가 같다(교대 반복 없이 — SpotBugs REDOS) */
    private static final Pattern QUOTED = Pattern.compile("'[^']*'");
    /** MySQL 큰따옴표 글(`COMMENT "…"`) — 인라인 UNIQUE 를 볼 때만 비운다(제약 줄 UQ 는 큰따옴표 이름을 읽어야 해서 안 비운다) */
    private static final Pattern DQUOTED = Pattern.compile("\"[^\"]*\"");
    /** 4-25a 리뷰 — 컬럼 줄 머리의 ENUM·SET 여는 괄호 */
    private static final Pattern ENUM_HEAD = Pattern.compile("(ENUM|SET)\\s*\\(", Pattern.CASE_INSENSITIVE);
    /** 컬럼 줄의 REFERENCES p(c) */
    private static final Pattern COL_REF = Pattern.compile("\\bREFERENCES\\s+([^\\s(]+)\\s*(?:\\(([^)]*)\\))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_COMMENT = Pattern.compile("COMMENT\\s*=?\\s*'((?:[^']++|'')*+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSTRAINT_LINE = Pattern.compile(
            "^(CONSTRAINT|PRIMARY\\s+KEY|UNIQUE|FOREIGN\\s+KEY|KEY|INDEX|CHECK|FULLTEXT|SPATIAL|PERIOD|EXCLUDE|LIKE)\\b",
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
    /** CHECK 제약(1-31a) — 이름이 있으면 CONSTRAINT 뒤 이름, 괄호 안이 조건 */
    private static final Pattern CHECK_AT = Pattern.compile("(?:\\bCONSTRAINT\\s+(\\S+)\\s+)?\\bCHECK\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_TYPE = Pattern.compile(
            "NUMBER|NUMERIC|DECIMAL|DEC|FLOAT|DOUBLE|REAL|INT|MONEY", Pattern.CASE_INSENSITIVE);

    /** 이름 바로 뒤가 질의면 CTAS(1-66) — {@code AS …}·MariaDB {@code SELECT …}·{@code AS (WITH …} */
    private static final Pattern CTAS_HEAD = Pattern.compile("(?i)(?:AS|SELECT|WITH)\\b");
    /** 이름 뒤 어딘가의 {@code AS [(] SELECT|WITH} — NOLOGGING·STORAGE (…)·ON COMMIT … 뒤, 컬럼 이름 목록 뒤(따옴표 글은 비우고 본다) */
    private static final Pattern CTAS_ANY = Pattern.compile("(?i)\\bAS\\s*\\(*\\s*(?:SELECT|WITH)\\b");
    private static final Pattern PARTITION_OF = Pattern.compile("(?i)PARTITION\\s+OF\\b");
    /** 1-67 — {@code CREATE TABLE y LIKE x} 머리와 괄호 안 {@code (LIKE x INCLUDING …)} 항목. {@code LIKE_CNT} 같은 컬럼 이름은 안 맞는다 */
    private static final Pattern LIKE_AT = Pattern.compile("(?i)LIKE\\s");
    private static final String LIKE = "like";
    /** 컬럼 없이 이름만 읽은 표의 notes 문구(1-66·1-67) */
    private static final Map<String, String> NAME_ONLY = Map.of("ctas", "AS SELECT — 컬럼을 못 읽는다", "partition", "PARTITION OF 부모 — 컬럼은 부모 표",
            LIKE, "LIKE 원본 — 컬럼은 원본 표");

    /** 문장 가운데 줄머리에 묻힌 CREATE TABLE */
    private static final Pattern BURIED_CREATE = Pattern.compile(
            "(?im)^[ \\t]*CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:GLOBAL\\s+|LOCAL\\s+)?(?:PRIVATE\\s+)?(?:TEMPORARY\\s+|TEMP\\s+|UNLOGGED\\s+)?TABLE\\b"); // 중첩 반복 없이(ReDoS 판정)

    /**
     * SQL*Plus 명령 줄 — 세미콜론 없이 문장 앞에 붙어 CREATE 를 가린다(V-4 실물 표본: Oracle 샘플 스키마 co_create.sql 을 0 테이블로 읽음).
     * SET 은 SQL*Plus 옵션일 때만(UPDATE … SET 줄과 가른다)
     */
    private static final Pattern SQLPLUS = Pattern.compile("(?im)^[ \\t]*(?:REM(?:ARK)?\\b|PROMPT\\b|SPOOL\\b|WHENEVER\\b|DEFINE\\b|UNDEFINE\\b"
            + "|COLUMN\\b|TTITLE\\b|BTITLE\\b|CONNECT\\b|CONN\\b|SHOW\\b|PAUSE\\b|ACCEPT\\b|HOST\\b|EXIT\\b|QUIT\\b|@"
            + "|SET[ \\t]+(?:ECHO|DEFINE|FEEDBACK|HEADING|LINESIZE|PAGESIZE|SERVEROUTPUT|TERMOUT|VERIFY|TIMING|SQLBLANKLINES|TRIMSPOOL"
            + "|LONG|SCAN|ESCAPE|CONCAT|AUTOCOMMIT|NULL|NUMWIDTH|WRAP|COLSEP|TAB|SQLPROMPT)\\b)[^\\n]*$");

    private DdlReader() {
    }

    private static final class Draft {
        final String schema;
        final String name;
        String comment;
        final List<Column> cols = new ArrayList<>();
        final List<String> pk = new ArrayList<>();
        final List<ForeignKey> fks = new ArrayList<>();
        final List<Check> checks = new ArrayList<>();
        final List<UniqueKey> uqs = new ArrayList<>();
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
            if (head && nameOnly(stmt, m, tables)) {
                continue;
            }
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
                if ("TABLE".equalsIgnoreCase(c.group(1))) {
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
                Matcher u = UQ.matcher(QUOTED.matcher(stmt.substring(at.end())).replaceAll("''"));
                while (d != null && u.find()) {
                    d.uqs.add(uq(u));
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
            out.add(Table.of(d.schema, d.name, "TABLE", d.comment).withColumns(cols).withConstraints(pk, d.fks, List.copyOf(d.uqs))
                    .withChecks(List.copyOf(d.checks)));
            if (!d.notes.isEmpty()) {
                notes.put(d.name.toUpperCase(Locale.ROOT), Map.copyOf(d.notes));
            }
        }
        return new Result(out, unreadable, notes);
    }

    /** CTAS·PARTITION OF — 컬럼 없이 이름만 표로, notes 한 줄(1-66 — 사용자 2026-10-31: unreadable 로 빼지 않는다) */
    private static boolean nameOnly(String stmt, Matcher m, Map<String, Draft> tables) {
        String rest = stmt.substring(m.end());
        int end = nameEnd(rest);
        if (end <= 0) {
            return false;
        }
        String after = rest.substring(end).stripLeading();
        String key;
        if (PARTITION_OF.matcher(after).lookingAt()) {
            key = "partition";
        } else if (LIKE_AT.matcher(after).lookingAt()) {
            key = LIKE;
        } else if (CTAS_HEAD.matcher(after).lookingAt() || CTAS_ANY.matcher(DQUOTED.matcher(QUOTED.matcher(after).replaceAll("''")).replaceAll("\"\"")).find()) {
            key = "ctas";
        } else {
            return false;
        }
        List<String> name = nameParts(rest.substring(0, end).trim());
        Draft d = new Draft(name.size() > 1 ? name.get(name.size() - 2) : "", name.get(name.size() - 1));
        d.notes.put(key, NAME_ONLY.get(key));
        tables.put(d.name.toUpperCase(Locale.ROOT), d);
        return true;
    }

    /** CREATE 머리 뒤 표 이름의 끝 — 따옴표·백틱·대괄호 밖 공백 또는 {@code (} 앞 */
    private static int nameEnd(String rest) {
        char quote = 0;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (quote != 0) {
                if (c == quote || quote == '[' && c == ']') {
                    quote = 0;
                }
            } else if (c == '"' || c == '`' || c == '[') {
                quote = c;
            } else if (c == '(' || Character.isWhitespace(c)) {
                return i;
            }
        }
        return rest.length();
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
            Matcher u = UQ.matcher(QUOTED.matcher(item).replaceAll("''")); // 4-26 리뷰 — COMMENT·CHECK 글 안의 「UNIQUE (x)」 는 키가 아니다
            if (u.find()) {
                d.uqs.add(uq(u));
            }
            checks(d, item);
            likeItem(d, item);
            return;
        }
        String[] head = firstToken(item);
        String name = head == null ? null : unquote(head[0]);
        String rest = head == null ? "" : head[1];
        // 4-25a 리뷰 — ENUM·SET 나열 값엔 'not null'·'check' 같은 글이 들 수 있다. 괄호는 따옴표를 아는 matching 으로 닫고, 속성은 그 뒤에서만 본다
        Matcher en = ENUM_HEAD.matcher(rest);
        int enumClose = en.lookingAt() ? matching(rest, en.end() - 1) : -1;
        String type;
        if (enumClose > 0) {
            type = rest.substring(0, enumClose + 1).trim();
            rest = rest.substring(enumClose + 1);
        } else {
            Matcher end = TYPE_END.matcher(" " + rest);
            type = (end.find() ? (" " + rest).substring(0, end.start()) : rest).trim();
        }
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
        String base = enumClose > 0 ? en.group(1).toUpperCase(Locale.ROOT) : type.replaceAll("\\([^)]*\\)", " ").trim().replaceAll("\\s+", " ");
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
        // 4-25a — ENUM·SET 은 나열 값이 타입의 일부다. 원문(`ENUM('A','B')`, MySQL SHOW COLUMNS 꼴) 통째로 둔다
        String nativeType = base.toUpperCase(Locale.ROOT).matches("ENUM|SET") ? type.trim() : base;
        d.cols.add(new Column(name, d.cols.size() + 1, nativeType, null, length, precision, scale, !notNull, def, comment, null));
        if (inlinePk) {
            d.pk.add(name);
        }
        if (INLINE_UQ.matcher(DQUOTED.matcher(QUOTED.matcher(upper).replaceAll("''")).replaceAll("\"\"")).find()) { // 코멘트·기본값 글 안의 UNIQUE 는 안 본다
            d.uqs.add(new UniqueKey(null, List.of(name)));
        }
        Matcher ref = COL_REF.matcher(rest);
        if (ref.find()) {
            d.fks.add(fk(d, null, name, ref.group(1), ref.group(2)));
        }
        checks(d, rest);
    }

    /** 1-67 — 괄호 안 {@code LIKE x [INCLUDING …]} 은 컬럼이 아니라 원본 표 표시(notes like). 제약 줄 가지에서 부른다 */
    private static void likeItem(Draft d, String item) {
        if (LIKE_AT.matcher(item).lookingAt()) {
            d.notes.put(LIKE, NAME_ONLY.get(LIKE));
        }
    }

    /** 표 단위 줄·컬럼 항목 안의 CHECK (…) — 괄호 균형으로 자른다(1-31a) */
    private static void checks(Draft d, String text) {
        Matcher m = CHECK_AT.matcher(text);
        int from = 0;
        while (from < text.length() && m.find(from)) {
            int open = m.end() - 1;
            int close = matching(text, open);
            if (close < 0) {
                return;
            }
            d.checks.add(new Check(m.group(1) == null ? null : unquote(m.group(1)), text.substring(open + 1, close).trim()));
            from = close + 1;
        }
    }

    /** UQ 매치 → 유니크 키(이름 없으면 null) */
    private static UniqueKey uq(Matcher u) {
        String n = u.group(1) != null ? u.group(1) : u.group(2);
        return new UniqueKey(n == null ? null : unquote(n), names(u.group(3)));
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
    private static List<String> statements(String s) {
        List<String> out = new ArrayList<>();
        String plain = stripSqlPlus(s).replaceAll("(?m)^[ \\t]*/[ \\t]*$", ";");
        for (String st : splitTop(plain.replaceAll("(?im)^\\s*GO\\s*$", ";"), ';')) {
            String t = st.trim();
            if (!t.isEmpty()) {
                out.addAll(splitCreates(t));
            }
        }
        return out;
    }

    /** 줄머리 CREATE TABLE 에서 가른다 — 조각이 CREATE 머리로 시작할 때만(tsql 의 ; 없는 연속 CREATE, 1-66). 「;s」 꼴은 그대로 둬 unreadable 로 */
    private static List<String> splitCreates(String stmt) {
        if (!CREATE.matcher(stmt).lookingAt()) {
            return List.of(stmt);
        }
        List<String> out = new ArrayList<>();
        Matcher b = BURIED_CREATE.matcher(stmt);
        int start = 0;
        while (b.find()) {
            if (b.start() > start && balanced(stmt.substring(start, b.start()))) {
                out.add(stmt.substring(start, b.start()).trim());
                start = b.start();
            }
        }
        out.add(stmt.substring(start).trim());
        return out;
    }

    /** 괄호가 다 닫히고 따옴표 밖에서 끝나는가 */
    private static boolean balanced(String s) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote || quote == '[' && c == ']') {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"' || c == '`' || c == '[') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
        }
        return depth == 0 && quote == 0;
    }

    /**
     * SQL*Plus 명령 줄을 빈 줄로 — 괄호·따옴표 밖(문장 경계)에서만. CREATE TABLE 안의 컬럼 줄 {@code remark varchar(500)}·{@code host}·
     * {@code column_x} 는 명령이 아니다(V-29 실물 표본 RuoYi: 표마다 마지막 컬럼 remark 를 지워 컬럼이 하나씩 모자랐다)
     */
    static String stripSqlPlus(String s) {
        String[] lines = s.split("\n", -1);
        int depth = 0;
        boolean q = false;
        for (int i = 0; i < lines.length; i++) {
            if (depth == 0 && !q && SQLPLUS.matcher(lines[i]).find()) {
                lines[i] = "";
                continue;
            }
            for (int k = 0; k < lines[i].length(); k++) {
                char c = lines[i].charAt(k);
                if (c == '\'') {
                    q = !q;
                } else if (!q && c == '(') {
                    depth++;
                } else if (!q && c == ')') {
                    depth = Math.max(0, depth - 1);
                }
            }
        }
        return String.join("\n", lines);
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
