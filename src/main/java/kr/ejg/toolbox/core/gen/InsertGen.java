package kr.ejg.toolbox.core.gen;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;
import kr.ejg.toolbox.core.text.Csv;

/**
 * INSERT·MERGE 생성(4-7, 2.3 A·3.5). 순수본 dev_tools INSERT 탭의 {@code genVal}·{@code buildUpsert}·{@code dateLit} 를
 * 규칙 순서 그대로 옮겼다. 다른 점 셋:
 * <ul>
 *   <li>입력이 메타모델 {@link Table}(스냅샷·{@link DdlReader}) — 타입 이름은 벤더 원문을 순수본이 아는 이름으로 맞춘다(int8 → BIGINT 등)</li>
 *   <li>난수 둘만 결정적으로 — 수는 {@code idx+1} 을 정밀도·소수 자리에 맞추고, 날짜는 기준일 − idx 일 00:00:00</li>
 *   <li>FK 는 {@link FkValues} 가 주는 부모 실존 값을 돌려 쓴다. 없으면 순번 + 경고</li>
 * </ul>
 * CSV 모드({@link #fromCsv})는 머리를 컬럼에 맞추고 타입·길이·NOT NULL 을 검사해 경고만 낸다 — 값은 그대로 싣는다.
 * 경고에는 값을 안 싣는다(행 번호·컬럼·사유만).
 */
public final class InsertGen {

    public static final Set<String> DIALECTS = Set.of("oracle", "tibero", "postgresql", "mariadb", "mssql", "sybase");
    public static final int MAX_ROWS = 1000;

    /** CHECK (col IN ( 까지 — 목록 끝은 DdlReader.matching 이 글자 안 괄호를 건너뛰며 찾는다(A-32) */
    static final Pattern CHECK_IN = Pattern.compile(
            "CHECK\\s*\\(\\s*[\"`\\[]?(\\w+)[\"`\\]]?\\s+IN\\s*\\(", Pattern.CASE_INSENSITIVE);
    static final Pattern NUMISH = Pattern.compile("(?i).*(NUMBER|NUMERIC|DECIMAL|INT|BIGINT|SMALLINT|FLOAT|DOUBLE|REAL|SERIAL|MONEY).*");
    private static final Pattern PARENS = Pattern.compile("\\(.*$");
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("INT8", "BIGINT"), Map.entry("INT4", "INTEGER"), Map.entry("INT2", "SMALLINT"),
            Map.entry("BPCHAR", "CHAR"), Map.entry("FLOAT8", "DOUBLE"), Map.entry("FLOAT4", "REAL"),
            Map.entry("BOOL", "BOOLEAN"), Map.entry("TIMESTAMPTZ", "TIMESTAMP"), Map.entry("DATETIME2", "DATETIME"),
            Map.entry("CHARACTER VARYING", "VARCHAR"), Map.entry("CHARACTER", "CHAR"), Map.entry("SERIAL8", "BIGSERIAL"),
            Map.entry("SERIAL4", "SERIAL"));

    private static final Pattern YN = Pattern.compile("(?i).*(_YN|_FLAG|_FL)$");
    private static final Pattern CHARISH = Pattern.compile("(?i).*(CHAR|VARCHAR|VARCHAR2|NVARCHAR|NVARCHAR2).*");

    private static final Pattern NUM = Pattern.compile("-?\\d+(\\.\\d+)?");
    /** 날짜 모양 셋 — 길이로 고른다(중첩 선택 그룹 없이, ReDoS 판정 피함) */
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern MIN = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
    private static final Pattern SEC = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

    /** exactNames — 스냅샷 모드: INSERT 문의 표·컬럼 이름은 대상이 접는 대소문자 섞인 이름을 정확 이름으로 감싼다(4-46, DdlGen 과 같게) */
    /** source — 4-52(A4-02): 스냅샷 원본 방언. 있으면 정확 이름은 그 방언의 접기로(DdlGen.exactIn), 없으면 대상 방언의 접기로(4-46) */
    public record Options(String dialect, int rows, boolean upsert, boolean commit, LocalDate baseDate, boolean exactNames, String source) {
        public Options(String dialect, int rows, boolean upsert, boolean commit, LocalDate baseDate, boolean exactNames) {
            this(dialect, rows, upsert, commit, baseDate, exactNames, null);
        }

        public Options(String dialect, int rows, boolean upsert, boolean commit, LocalDate baseDate) {
            this(dialect, rows, upsert, commit, baseDate, false);
        }
    }

    public record Result(String sql, List<String> warnings) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    /** 부모 테이블의 실존 값 — {@code SELECT DISTINCT refColumns FROM refTable ORDER BY …} 의 행들(상한 max) */
    @FunctionalInterface
    public interface FkValues {
        List<List<Object>> values(ForeignKey fk, int max) throws SQLException;
    }

    /** 순수본 genVal 이 보는 컬럼 모양. values = ENUM·SET 나열 리터럴(따옴표 포함, 4-25a) — 순수본 col.size 자리 */
    record Col(String name, String type, int len, int prec, int scale, boolean notNull, String def, List<String> allowed,
            boolean pk, boolean uq, boolean auto, List<String> values) {
        Col(String name, String type, int len, int prec, int scale, boolean notNull, String def, List<String> allowed,
                boolean pk, boolean uq, boolean auto) {
            this(name, type, len, prec, scale, notNull, def, allowed, pk, uq, auto, List.of());
        }
    }

    private InsertGen() {
    }

    // ---------------------------------------------------------------- 메타 → Col

    static String type(String nativeType) {
        String t = PARENS.matcher(nativeType == null ? "" : nativeType.trim().toUpperCase(Locale.ROOT)).replaceAll("").trim();
        if (t.startsWith("TIMESTAMP")) {
            return "TIMESTAMP";
        }
        return ALIAS.getOrDefault(t, t);
    }

    /** {@code 'Y'::bpchar} → {@code 'Y'} */
    static String def(String d) {
        if (d == null) {
            return "";
        }
        String s = d.trim();
        int cast = s.lastIndexOf("::");
        if (cast > 0 && s.startsWith("'") && s.lastIndexOf('\'') < cast) {
            s = s.substring(0, cast);
        }
        return s;
    }

    static List<Col> cols(Table t, Map<String, List<String>> allowed) {
        Set<String> pk = upper(t.pk() == null ? List.of() : t.pk().columns());
        Set<String> uq = new LinkedHashSet<>();
        for (UniqueKey u : t.uniques()) {
            uq.addAll(upper(u.columns()));
        }
        for (Index i : t.indexes()) {
            if (i.unique()) {
                uq.addAll(upper(i.columns()));
            }
        }
        List<Col> out = new ArrayList<>();
        for (Column c : t.columns()) {
            String up = c.name().toUpperCase(Locale.ROOT);
            String ty = type(c.nativeType());
            String d = def(c.defaultValue());
            boolean auto = d.toLowerCase(Locale.ROOT).contains("nextval(") || ty.endsWith("SERIAL")
                    || d.toUpperCase(Locale.ROOT).contains("IDENTITY") || DdlGen.auto(c); // A-08 — DDL·스냅샷이 읽은 자동증가
            int len = c.length() != null ? (int) Math.min(Integer.MAX_VALUE, c.length()) : 0;
            out.add(new Col(c.name(), ty, len, c.precision() == null ? 0 : c.precision(), c.scale() == null ? 0 : c.scale(),
                    !c.nullable(), d, allowed.getOrDefault(up, List.of()), pk.contains(up), uq.contains(up), auto,
                    enumValues(ty, c.nativeType())));
        }
        return out;
    }

    /** 4-25a — `ENUM('A','B')`·`set('x','y')` 의 나열 리터럴. 괄호가 없으면(JDBC TYPE_NAME 만) 빈 목록 */
    static List<String> enumValues(String type, String nativeType) {
        if (!type.matches("ENUM|SET") || nativeType == null) {
            return List.of();
        }
        int open = nativeType.indexOf('(');
        int close = nativeType.lastIndexOf(')');
        return open > 0 && close > open ? splitVals(nativeType.substring(open + 1, close)) : List.of();
    }

    private static Set<String> upper(List<String> names) {
        Set<String> s = new LinkedHashSet<>();
        names.forEach(n -> s.add(n.toUpperCase(Locale.ROOT)));
        return s;
    }

    // ---------------------------------------------------------------- CHECK IN(DDL 모드)

    /** DDL 의 {@code CHECK (col IN ('A','B'))} → 컬럼(대문자) → 값 리터럴들. 표가 하나인 DDL 용 — 여러 표면 {@link #checkIns(Table)}(4-35). 시험·표본 하네스가 쓴다 */
    public static Map<String, List<String>> checkIns(String ddl) {
        Map<String, List<String>> out = new HashMap<>();
        if (ddl == null) {
            return out;
        }
        InsertGenParts.collect(ddl, out);
        return out;
    }

    /** A-05 — 이 표의 CHECK 에서만. 컬럼(대문자) → 값 리터럴들 */
    public static Map<String, List<String>> checkIns(Table t) {
        Map<String, List<String>> out = new HashMap<>();
        for (Check k : t.checks()) {
            if (k.condition() != null && !InsertGenParts.inSet(k.condition(), out)) { // 4-47 — Validation.parse 한 자리, 못 풀면 지금 꼴
                InsertGenParts.collect("CHECK (" + k.condition() + ")", out);
            }
        }
        return out;
    }

    static List<String> splitVals(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') {
                if (q && i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                    cur.append("''");
                    i++;
                    continue;
                }
                q = !q;
                cur.append(ch);
                continue;
            }
            if (ch == ',' && !q) {
                out.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
        }
        if (!cur.toString().isBlank()) {
            out.add(cur.toString().trim());
        }
        out.removeIf(String::isEmpty);
        return out;
    }

    // ---------------------------------------------------------------- 값 규칙(순수본 genVal)

    /** 순수본 방언 키 — tibero 는 oracle, mariadb 는 mysql */
    static String db(String dialect) {
        String d = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
        return switch (d) {
            case "oracle", "tibero" -> "oracle";
            case "postgresql" -> "pg";
            case "mariadb", "mysql" -> "mysql";
            case "mssql" -> "mssql";
            case "sybase" -> "sybase";
            default -> throw new IllegalArgumentException("모르는 방언: " + dialect + " — " + String.join("·", DIALECTS.stream().sorted().toList()));
        };
    }

    static String dateLit(String db, String s, boolean ts) {
        return switch (db) {
            case "oracle" -> (ts ? "TO_TIMESTAMP(" : "TO_DATE(") + "'" + s + "','YYYY-MM-DD HH24:MI:SS')";
            case "mssql" -> "CONVERT(DATETIME, '" + s + "', 120)";
            case "sybase" -> "CONVERT(DATETIME, '" + s + "', 120) /* ⚠ 스타일 120 ASE 지원 버전 확인 요망 */";
            default -> "'" + s + "'";
        };
    }

    static String pad(int n, int w) {
        StringBuilder s = new StringBuilder(String.valueOf(n));
        while (s.length() < w) {
            s.insert(0, '0');
        }
        return s.toString();
    }

    static String genVal(Col c, int idx, String db, LocalDate base) {
        String t = c.type();
        int sz = c.len();
        if (!c.allowed().isEmpty()) {
            return c.allowed().get(idx % c.allowed().size());
        }
        if (t.matches("CHAR|NCHAR") && sz == 1 && YN.matcher(c.name()).matches()) {
            return idx % 2 != 0 ? "'N'" : "'Y'";
        }
        // PK·UNIQUE 는 기본값을 안 쓴다 — 행마다 같은 값이 되어 부딪친다(V-10 실물: MariaDB 는 NOT NULL 문자 PK 에 DEFAULT '' 를 둔다)
        if (!c.pk() && !c.uq() && c.def().matches("^'.*'$")) {
            return c.def();
        }
        if (!c.values().isEmpty()) { // 순수본 genVal 의 ENUM·SET 갈래 — 기본값 뒤, BOOL 앞(4-25a)
            return c.values().get(idx % c.values().size());
        }
        if (t.matches("BOOL|BOOLEAN")) {
            return "pg".equals(db) ? (idx % 2 != 0 ? "false" : "true") : (idx % 2 != 0 ? "0" : "1");
        }
        if ("BIT".equals(t)) {
            return InsertGenParts.bit(idx, db, c.len()); // 4-48 A2-10 — PG 는 B'1'(csvLiteral 과 같게) · 4-53 BIT(n)
        }
        if ("TINYINT".equals(t) && (sz == 1 || c.prec() == 1)) {
            return idx % 2 != 0 ? "0" : "1";
        }
        if ("TINYINT".equals(t)) {
            return String.valueOf((idx + 1) % 128); // 순수본은 0~127 난수
        }
        if (t.matches("UUID|UNIQUEIDENTIFIER")) {
            return "pg".equals(db) ? "gen_random_uuid()" : "mssql".equals(db) ? "NEWID()"
                    : "'" + pad(idx + 1, 8) + "-0000-0000-0000-000000000000'";
        }
        if (t.matches("JSONB?")) {
            return "'{\"k\":\"v" + (idx + 1) + "\"}'";
        }
        if (CHARISH.matcher(t).matches()) {
            return charVal(c, idx, sz);
        }
        if (InsertGenParts.numish(t)) {
            if (c.pk() || c.uq()) {
                return String.valueOf(idx + 1);
            }
            return number(idx + 1, c.prec() > 0 ? c.prec() : 4, c.scale());
        }
        String when = base.minusDays(idx) + " 00:00:00";
        if (t.contains("TIMESTAMP") || t.contains("DATETIME")) {
            return dateLit(db, when, true);
        }
        if (t.contains("DATE")) {
            return dateLit(db, when, false);
        }
        if (t.matches(".*(CLOB|TEXT|NTEXT).*")) {
            return "'SAMPLE_" + (idx + 1) + "'";
        }
        if (t.matches(".*(BLOB|BINARY|IMAGE|BYTEA).*")) {
            return "NULL";
        }
        return other(t, idx, db, base);
    }

    private static String charVal(Col c, int idx, int sz) {
        String seq = pad(idx + 1, 3);
        int lim = sz > 0 ? sz : 30;
        if (lim <= seq.length()) {
            return "'" + seq.substring(seq.length() - lim) + "'";
        }
        if ((c.pk() || c.uq()) && lim - seq.length() - 1 < 1) {
            return "'" + pad(idx + 1, lim) + "'"; // A-04 — 길이 4 키: 이름 한 글자 + 밑줄 + 순번이 잘려 같은 값이 되풀이됐다
        }
        String up = c.name().toUpperCase(Locale.ROOT);
        String b = up.substring(0, Math.min(up.length(), Math.max(1, lim - seq.length() - 1)));
        String v = b + "_" + seq;
        return "'" + v.substring(0, Math.min(v.length(), lim)) + "'";
    }

    /** A-07 — 끝 갈래: TIME·YEAR·INTERVAL, 그 밖은 'VAL_n' */
    private static String other(String t, int idx, String db, LocalDate base) {
        if ((t.startsWith("BIT VARYING") || t.startsWith("VARBIT")) && "pg".equals(db)) {
            return InsertGenParts.bit(idx, db, 1); // 4-48 A2-10
        }
        if (t.startsWith("TIME")) {
            return String.format(Locale.ROOT, "'%02d:%02d:00'", idx % 24, idx % 60);
        }
        if ("YEAR".equals(t)) {
            return String.valueOf(base.getYear() - idx % 100);
        }
        if (t.startsWith("INTERVAL")) {
            int n = InsertGenParts.leadingValue(idx); // 4-53(A4-16) — 앞자리 기본 정밀도 2 안
            return "oracle".equals(db) ? "INTERVAL '" + n + "' " + (t.contains("YEAR") ? "MONTH" : "DAY") + InsertGenParts.leading(n) : "'" + (idx + 1) + " days'";
        }
        return "'VAL_" + (idx + 1) + "'";
    }

    /** 순수본 난수 대신 — n 을 정수 자리(최대 9)에 맞춰 돌리고, 소수 자리는 n 을 0 채워서. 예 NUMERIC(12,2) 의 5 → 5.05 */
    static String number(int n, int prec, int scale) {
        if (scale > 0 && prec - scale <= 0) {
            // NUMBER(2,2) 처럼 정수 자리가 없다 — 0.xx(V-6 실물 HR commission_pct. 순수본 genVal 은 정수 자리를 최소 1 로 잡아 넘친다)
            int fs = Math.min(scale, 9);
            return "0." + pad((int) (n % (long) Math.pow(10, fs)), scale);
        }
        int intDigits = Math.max(1, Math.min(prec - scale, 9));
        long max = (long) Math.pow(10, intDigits) - 1;
        long ip = (n - 1) % max + 1;
        if (scale <= 0) {
            return String.valueOf(ip);
        }
        int fs = Math.min(scale, 9);
        long frac = n % (long) Math.pow(10, fs);
        return ip + "." + pad((int) frac, scale);
    }

    /**
     * 순수본 genFromCreate 의 키 겹침 경고(4-24) — genVal 이 좁은 컬럼을 idx % 값수로 돌려 조합 = 값수 LCM.
     * 묶음 = PK + UNIQUE 각 + 유니크 인덱스 각. JDBC 는 UNIQUE 와 그 인덱스를 둘 다 내서 컬럼 목록이 같은 묶음은 하나로 친다
     */
    static List<String> keyOverlap(Table t, List<Col> cols, int rows, Map<String, List<Object>> fkCol) {
        Map<String, Col> by = new HashMap<>();
        cols.forEach(c -> by.put(c.name().toUpperCase(Locale.ROOT), c));
        LinkedHashSet<List<String>> groups = new LinkedHashSet<>();
        if (t.pk() != null && !t.pk().columns().isEmpty()) {
            groups.add(upperList(t.pk().columns()));
        }
        t.uniques().forEach(u -> groups.add(upperList(u.columns())));
        t.indexes().stream().filter(Index::unique).forEach(i -> groups.add(upperList(i.columns())));
        List<String> out = new ArrayList<>();
        for (List<String> g : groups) {
            if (g.isEmpty() || !g.stream().allMatch(by::containsKey)) {
                continue;
            }
            long combos = 1;
            for (long d : InsertGenParts.factors(t, g, fkCol, n -> domain(by.get(n)))) { // 4-48 A2-08·4-53 A4-10 — FK 로 채운 키는 부모 값수·행 수
                if (d == 0) {
                    combos = 0;
                    break;
                }
                combos = combos / BigInteger.valueOf(combos).gcd(BigInteger.valueOf(d)).longValue() * d;
                if (combos >= rows) {
                    break;
                }
            }
            if (combos == 0 || rows <= combos) {
                continue;
            }
            String label = g.size() == 1 ? g.get(0) + ": 허용 값이 " + combos + "개인 키 컬럼이라"
                    : "(" + String.join(", ", g) + "): 값 조합이 " + combos + "가지인 키라";
            out.add(label + " " + rows + "행이면 값이 겹친다 — 행 수를 " + combos + " 이하로 두거나 값을 손으로 바꿀 것");
        }
        return out;
    }

    /** genVal 이 idx % n 으로 돌리는 좁은 컬럼의 값수 n. 그 밖 0 */
    static int domain(Col c) {
        String t = c.type();
        if (!c.allowed().isEmpty()) {
            return c.allowed().size();
        }
        if (t.matches("CHAR|NCHAR") && c.len() == 1 && YN.matcher(c.name()).matches()) {
            return 2;
        }
        if (!c.values().isEmpty()) {
            return c.values().size();
        }
        if (t.matches("BOOL|BOOLEAN|BIT") || "TINYINT".equals(t) && (c.len() == 1 || c.prec() == 1)) {
            return 2;
        }
        if (CHARISH.matcher(t).matches() && c.len() >= 1 && c.len() <= 2) { // A-04 — 짧은 문자 길이 1·2 → 10·100
            return c.len() == 1 ? 10 : 100;
        }
        return "TINYINT".equals(t) ? 128 : InsertGenParts.narrowNumber(c);
    }


    private static List<String> upperList(List<String> names) {
        return names.stream().map(n -> n.toUpperCase(Locale.ROOT)).toList();
    }

    // ---------------------------------------------------------------- 메타 모드

    public static Result generate(Table t, Map<String, List<String>> allowed, Options o, FkValues fkValues) {
        String db = db(o.dialect());
        if (t.columns().isEmpty()) {
            // V-49a — DDL 에서 이름만 읽은 표(1-66·1-67·1-69). 화면 오류 대신 경고 한 줄
            return new Result("", List.of("컬럼이 없다: " + t.name() + " — DDL 이 컬럼을 안 적은 표(OF 형식·AS SELECT·LIKE)"));
        }
        List<Col> cols = cols(t, allowed);
        List<String> warns = new ArrayList<>();
        int rows = InsertGenParts.rowCount(o.rows(), warns);
        Map<String, List<Object>> fkCol = new LinkedHashMap<>();
        for (ForeignKey fk : t.fks()) {
            List<List<Object>> vals = List.of();
            if (fkValues != null) {
                try {
                    vals = fkValues.values(fk, rows);
                } catch (SQLException | RuntimeException e) {
                    warns.add("FK " + String.join(", ", fk.columns()) + " → " + fk.refTable() + ": 부모 값 조회 실패 — 순번으로 채웠다");
                    vals = null;
                }
            }
            if (vals != null && vals.isEmpty()) {
                warns.add("FK " + String.join(", ", fk.columns()) + " → " + fk.refTable()
                        + (fkValues == null ? ": 부모 테이블의 실제 값으로 바꿀 것 — 접속을 주면 실존 값을 쓴다" : ": 부모에 행이 없다 — 순번으로 채웠다"));
            }
            if (vals == null || vals.isEmpty()) {
                continue;
            }
            for (int k = 0; k < fk.columns().size(); k++) {
                List<Object> colVals = new ArrayList<>();
                for (List<Object> row : vals) {
                    colVals.add(k < row.size() ? row.get(k) : null);
                }
                fkCol.put(fk.columns().get(k).toUpperCase(Locale.ROOT), colVals);
            }
        }
        List<Col> auto = cols.stream().filter(Col::auto).toList();
        if (!auto.isEmpty()) {
            warns.add("자동증가 컬럼: " + String.join(", ", auto.stream().map(c -> c.name().toUpperCase(Locale.ROOT)).toList())
                    + " — 값을 넣어 두었으니 필요하면 해당 컬럼을 지울 것" + ("mssql".equals(db) ? " (SQL Server는 SET IDENTITY_INSERT ON 필요)" : ""));
        }
        if (!o.upsert()) {
            warns.addAll(keyOverlap(t, cols, rows, fkCol));
        }
        if (o.upsert()) {
            return new Result(buildUpsert(db, t.name(), cols, c -> "#{" + DtoGenerator.fieldName(c.name()) + "}", warns), warns);
        }
        LocalDate base = o.baseDate() == null ? LocalDate.now() : o.baseDate();
        String colNames = String.join(",\n", cols.stream().map(c -> "\t" + InsertGenParts.ident(c.name(), db, o)).toList());
        List<String> inserts = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            int idx = i;
            String vals = String.join(",\n", cols.stream().map(c -> {
                List<Object> fv = fkCol.get(c.name().toUpperCase(Locale.ROOT));
                return "\t" + (fv != null ? literal(c, fv.get(idx % fv.size()), db) : genVal(c, idx, db, base));
            }).toList());
            inserts.add("INSERT INTO " + InsertGenParts.ident(t.name(), db, o) + "(\n" + colNames + "\n)\nVALUES(\n" + vals + "\n);"); // tb-ignore security.sqlConcat SQL 생성기 — 식별자는 메타·검증에서 온 것
        }
        commit(o, db, inserts);
        return new Result(String.join("\n\n", inserts), warns);
    }

    private static void commit(Options o, String db, List<String> out) {
        if (!o.commit()) {
            return;
        }
        if ("mssql".equals(db) || "sybase".equals(db)) {
            out.add("-- 명시 트랜잭션을 쓸 때만: BEGIN TRAN ... COMMIT TRAN\nCOMMIT TRAN;");
        } else if ("mysql".equals(db)) {
            out.add("-- autocommit 켜져 있으면 불필요\nCOMMIT;");
        } else {
            out.add("COMMIT;");
        }
    }

    /** 부모 실존 값 → 리터럴. 수 타입이면 그대로, 그 밖은 따옴표 */
    static String literal(Col c, Object v, String db) {
        if (v == null) {
            return "NULL";
        }
        if (NUMISH.matcher(c.type()).matches() && v instanceof Number) {
            return v instanceof BigDecimal b ? b.toPlainString() : v.toString();
        }
        return quote(v.toString(), db);
    }

    /** A-28 — mysql(mariadb) 은 역슬래시를 겹친다 */
    static String quote(String s, String db) {
        return DdlGen.lit(s, "mysql".equals(db));
    }

    // ---------------------------------------------------------------- MERGE(순수본 buildUpsert)

    interface Expr {
        String of(Col c);
    }

    static String buildUpsert(String db, String table, List<Col> cols, Expr expr, List<String> warns) {
        // 순수본은 표 이름을 대문자로 — MySQL·MariaDB 는 리눅스에서 표 이름이 대소문자를 가려 그대로 둔다(V-10 실물: chinook `Artist`)
        String tbl = "mysql".equals(db) ? ident(table, db) : uid(table, db);
        List<Col> pks = cols.stream().filter(Col::pk).toList();
        String pkNote = "";
        if (pks.isEmpty()) {
            pks = List.of(cols.get(0));
            pkNote = "-- ⚠ DDL에서 PRIMARY KEY를 찾지 못해 첫 컬럼(" + u(cols.get(0)) + ")을 키로 가정함\n";
            if (!warns.contains("PK 없음 — 첫 컬럼을 키로 가정")) {
                warns.add("PK 없음 — 첫 컬럼을 키로 가정");
            }
        }
        List<Col> keys = pks;
        List<Col> nonPk = cols.stream().filter(c -> !keys.contains(c)).toList();
        // 컬럼이 전부 키 — 갱신할 컬럼이 없다. 순수본 b6a87d5 와 같게 MERGE 는 WHEN MATCHED 생략, pg 는 DO NOTHING,
        // mysql 은 첫 키 = 첫 키(단일 컬럼 PK 표에서 빈 SET 이 문법 오류였다 — 0-40)
        boolean allKeys = nonPk.isEmpty();
        if (allKeys) {
            nonPk = cols.subList(1, cols.size());
        }
        String onCond = String.join(" AND ", keys.stream().map(p -> "T." + uid(p.name(), db) + " = S." + uid(p.name(), db)).toList());
        List<Col> set = nonPk;
        if ("oracle".equals(db) || "mssql".equals(db) || "sybase".equals(db)) {
            String src = "\tSELECT\n" + String.join(",\n", cols.stream().map(c -> "\t\t  " + expr.of(c) + " AS " + uid(c.name(), db)).toList())
                    + ("oracle".equals(db) ? "\n\tFROM DUAL" : "");
            StringBuilder s = new StringBuilder(pkNote);
            s.append("MERGE INTO ").append(tbl).append(" T\nUSING (\n").append(src).append("\n) S ON (").append(onCond).append(")\n");
            // 컬럼이 전부 키면 WHEN MATCHED 를 뺀다 — 키 컬럼을 UPDATE 하면 Oracle 이 거절한다(ORA-38104, V-10 실물: chinook PlaylistTrack)
            if (!allKeys) {
                s.append("WHEN MATCHED THEN\n\tUPDATE SET\n");
                for (int i = 0; i < set.size(); i++) {
                    s.append("\t\t").append(i > 0 ? ", " : "  ").append("T.").append(uid(set.get(i).name(), db)).append(" = S.").append(uid(set.get(i).name(), db)).append('\n');
                }
            }
            s.append("WHEN NOT MATCHED THEN\n\tINSERT (\n");
            s.append(String.join(",\n", cols.stream().map(c -> "\t\t  " + uid(c.name(), db)).toList())).append("\n\t) VALUES (\n");
            s.append(String.join(",\n", cols.stream().map(c -> "\t\t  S." + uid(c.name(), db)).toList())).append("\n\t);");
            if ("mssql".equals(db)) {
                s.append("\n-- SQL Server: MERGE 문 끝 세미콜론 필수");
            }
            if ("sybase".equals(db)) {
                s.append("\n-- Sybase ASE 16 미만은 MERGE 미지원 — IF EXISTS + UPDATE/INSERT 로 분기 필요");
            }
            return s.toString();
        }
        String head = pkNote + "INSERT INTO " + tbl + " (\n" + String.join(",\n", cols.stream().map(c -> "\t  " + uid(c.name(), db)).toList()) // tb-ignore security.sqlConcat SQL 생성기 — 식별자는 메타·검증에서 온 것
                + "\n)\nVALUES (\n" + String.join(",\n", cols.stream().map(c -> "\t  " + expr.of(c)).toList()) + "\n)\n";
        StringBuilder s = new StringBuilder(head);
        if ("pg".equals(db)) {
            s.append("ON CONFLICT (").append(String.join(", ", keys.stream().map(c -> uid(c.name(), db)).toList())).append(") ");
            if (allKeys) {
                s.append("DO NOTHING;\n");
            } else {
                s.append("DO UPDATE SET\n");
                for (int i = 0; i < set.size(); i++) {
                    s.append('\t').append(i > 0 ? ", " : "  ").append(uid(set.get(i).name(), db)).append(" = EXCLUDED.").append(uid(set.get(i).name(), db))
                            .append(i == set.size() - 1 ? ";\n" : "\n");
                }
            }
            s.append("-- PostgreSQL 9.5+ (ON CONFLICT). 대상 컬럼에 UNIQUE/PK 인덱스가 있어야 함");
            return s.toString();
        }
        s.append("ON DUPLICATE KEY UPDATE\n");
        if (allKeys) {
            s.append("\t  ").append(uid(keys.get(0).name(), db)).append(" = ").append(uid(keys.get(0).name(), db)).append(";\n");
        }
        for (int i = 0; !allKeys && i < set.size(); i++) {
            s.append('\t').append(i > 0 ? ", " : "  ").append(uid(set.get(i).name(), db)).append(" = VALUES(").append(uid(set.get(i).name(), db)).append(')')
                    .append(i == set.size() - 1 ? ";\n" : "\n");
        }
        s.append("-- MySQL 8.0.20+ 는 VALUES() 대신 별칭 권장: ... AS NEW ... = NEW.컬럼");
        return s.toString();
    }

    /** A-06 — SQL 로 나가는 이름. 예약어·특수 글자만 감싼다(DdlGen.reserved 와 같은 목록). 예약어는 접는 방언이면 접은 글자로 */
    static String ident(String name, String db) {
        boolean plain = DdlGen.plainName(name, db);
        if (plain && !DdlGen.reserved(name)) {
            return name;
        }
        String n = !plain ? name : "oracle".equals(db) ? name.toUpperCase(Locale.ROOT) : "pg".equals(db) ? name.toLowerCase(Locale.ROOT) : name;
        return switch (db) {
            case "mysql" -> "`" + n.replace("`", "``") + "`";
            case "mssql", "sybase" -> "[" + n.replace("]", "]]") + "]";
            default -> "\"" + n.replace("\"", "\"\"") + "\"";
        };
    }

    /**
     * 4-46(A2-02) — exact 이고 그 이름을 대상이 접으면(oracle 대문자 · pg 소문자와 다름) 접지 않고 그대로 감싼다(스냅샷의 따옴표로 만든 이름 —
     * DdlGen 의 정확 이름과 같게). 아니면 {@link #ident(String, String)}
     */
    static String ident(String name, String db, boolean exact) {
        boolean folds = "oracle".equals(db) ? !name.equals(name.toUpperCase(Locale.ROOT)) : "pg".equals(db) && !name.equals(name.toLowerCase(Locale.ROOT));
        return exact && folds ? "\"" + name.replace("\"", "\"\"") + "\"" : ident(name, db);
    }

    /** MERGE·ON CONFLICT 쪽 — 맨 이름은 지금처럼 대문자, 특수 글자 이름은 글자 그대로 */
    private static String uid(String name, String db) {
        return ident(DdlGen.plainName(name, db) ? name.toUpperCase(Locale.ROOT) : name, db);
    }

    private static String u(Col c) {
        return c.name().toUpperCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- CSV 모드(3.5 엑셀 → INSERT·MERGE)

    static boolean dateShape(String v) {
        return DAY.matcher(v).matches() || MIN.matcher(v).matches() || SEC.matcher(v).matches();
    }

    /**
     * CSV(머리 = 컬럼명) → 행마다 INSERT(또는 MERGE). 머리는 대소문자를 안 가리고, 테이블에 없는 머리는 {@link IllegalArgumentException}.
     * 타입·길이·NOT NULL 위반은 경고 — 값은 그대로 싣는다.
     */
    public static Result fromCsv(Table t, String csv, Options o) {
        String db = db(o.dialect());
        List<List<String>> rows = Csv.parse(csv == null ? "" : csv);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("CSV 에 머리와 값 행이 있어야 한다");
        }
        Map<String, Col> byName = new LinkedHashMap<>();
        cols(t, Map.of()).forEach(c -> byName.put(c.name().toUpperCase(Locale.ROOT), c));
        List<Col> head = new ArrayList<>();
        for (String h : rows.get(0)) {
            Col c = byName.get(h.trim().toUpperCase(Locale.ROOT));
            if (c == null) {
                throw new IllegalArgumentException("테이블 " + t.name() + " 에 없는 열: " + h.trim());
            }
            if (head.contains(c)) {
                throw new IllegalArgumentException("CSV 머리에 같은 열이 두 번: " + h.trim()); // A-26 — 어느 값을 쓸지 못 정한다
            }
            head.add(c);
        }
        List<String> warns = new ArrayList<>();
        InsertGenParts.requireKeys(byName, head, o);
        for (Col c : byName.values()) {
            if (c.notNull() && c.def().isEmpty() && !c.auto() && !head.contains(c)) {
                warns.add("NOT NULL 컬럼 " + u(c) + " 이 CSV 에 없다");
            }
        }
        List<String> out = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int line = r + 1;
            InsertGenParts.columnCount(row.size(), head.size(), line, warns); // 4-48 A2-04
            Map<Col, String> lit = new LinkedHashMap<>();
            for (int k = 0; k < head.size(); k++) {
                lit.put(head.get(k), csvLiteral(head.get(k), k < row.size() ? row.get(k) : "", db, line, warns));
            }
            if (o.upsert()) {
                out.add(buildUpsert(db, t.name(), head, lit::get, warns));
            } else {
                out.add("INSERT INTO " + InsertGenParts.ident(t.name(), db, o) + "(\n" + String.join(",\n", head.stream().map(c -> "\t" + InsertGenParts.ident(c.name(), db, o)).toList()) // tb-ignore security.sqlConcat SQL 생성기 — 식별자는 메타·검증에서 온 것
                        + "\n)\nVALUES(\n" + String.join(",\n", head.stream().map(c -> "\t" + lit.get(c)).toList()) + "\n);");
            }
        }
        commit(o, db, out);
        return new Result(String.join("\n\n", out), warns);
    }

    static String csvLiteral(Col c, String raw, String db, int line, List<String> warns) {
        String v = raw.trim();
        String where = line + "행 " + u(c) + ": ";
        if (v.isEmpty()) {
            if (c.notNull() && c.def().isEmpty() && !c.auto()) {
                warns.add(where + "NOT NULL 인데 비었다");
            }
            return "NULL";
        }
        String t = c.type();
        // V-29 — 불 꼴 컬럼(tinyint(1)·BIT·BOOLEAN)의 true/false — 드라이버가 tinyint(1) 을 Boolean 으로 돌려주고 엑셀도 TRUE 로 쓴다.
        // 감싸면 MariaDB 가 거절한다('true' 는 정수 아님). PostgreSQL boolean 만 글자 그대로
        boolean boolish = t.matches("BOOL|BOOLEAN|BIT") || "TINYINT".equals(t) && (c.len() == 1 || c.prec() == 1);
        if (boolish && v.matches("(?i)true|false")) {
            boolean b = "true".equalsIgnoreCase(v);
            if ("pg".equals(db) && "BIT".equals(t)) {
                return b ? "B'1'" : "B'0'"; // PostgreSQL bit 은 정수 글자를 안 받는다(마무리 리뷰)
            }
            return "pg".equals(db) && t.matches("BOOL|BOOLEAN") ? String.valueOf(b) : b ? "1" : "0";
        }
        if (!CHARISH.matcher(t).matches() && InsertGenParts.numish(t)) {
            if (!NUM.matcher(v).matches()) {
                warns.add(where + "숫자가 아니다");
                return quote(raw, db);
            }
            int intLen = v.replace("-", "").split("\\.")[0].length();
            if (c.prec() > 0 && intLen > c.prec() - c.scale()) {
                warns.add(where + "정수 자리 " + (c.prec() - c.scale()) + " 을 넘는다");
            }
            return v;
        }
        if (t.contains("TIMESTAMP") || t.contains("DATETIME") || t.contains("DATE")) {
            String bad = InsertGenParts.dateProblem(v);
            if (bad != null) {
                warns.add(where + bad);
                return quote(raw, db);
            }
            String s = v.length() == 10 ? v + " 00:00:00" : v.length() == 16 ? v + ":00" : v;
            return dateLit(db, s, t.contains("TIMESTAMP") || t.contains("DATETIME"));
        }
        if (CHARISH.matcher(t).matches() && c.len() > 0 && raw.length() > c.len()) {
            warns.add(where + "길이 " + c.len() + " 을 넘는다(" + raw.length() + "자)");
        }
        return quote(raw, db);
    }
}
