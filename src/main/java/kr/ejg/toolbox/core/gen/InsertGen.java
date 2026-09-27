package kr.ejg.toolbox.core.gen;

import java.math.BigDecimal;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public record Options(String dialect, int rows, boolean upsert, boolean commit, LocalDate baseDate) {
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

    /** 순수본 genVal 이 보는 컬럼 모양 */
    record Col(String name, String type, int len, int prec, int scale, boolean notNull, String def, List<String> allowed,
            boolean pk, boolean uq, boolean auto) {
    }

    private InsertGen() {
    }

    // ---------------------------------------------------------------- 메타 → Col

    private static final Pattern PARENS = Pattern.compile("\\(.*$");
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("INT8", "BIGINT"), Map.entry("INT4", "INTEGER"), Map.entry("INT2", "SMALLINT"),
            Map.entry("BPCHAR", "CHAR"), Map.entry("FLOAT8", "DOUBLE"), Map.entry("FLOAT4", "REAL"),
            Map.entry("BOOL", "BOOLEAN"), Map.entry("TIMESTAMPTZ", "TIMESTAMP"), Map.entry("DATETIME2", "DATETIME"),
            Map.entry("CHARACTER VARYING", "VARCHAR"), Map.entry("CHARACTER", "CHAR"), Map.entry("SERIAL8", "BIGSERIAL"),
            Map.entry("SERIAL4", "SERIAL"));

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
                    || d.toUpperCase(Locale.ROOT).contains("IDENTITY");
            int len = c.length() != null ? (int) Math.min(Integer.MAX_VALUE, c.length()) : 0;
            out.add(new Col(c.name(), ty, len, c.precision() == null ? 0 : c.precision(), c.scale() == null ? 0 : c.scale(),
                    !c.nullable(), d, allowed.getOrDefault(up, List.of()), pk.contains(up), uq.contains(up), auto));
        }
        return out;
    }

    private static Set<String> upper(List<String> names) {
        Set<String> s = new LinkedHashSet<>();
        names.forEach(n -> s.add(n.toUpperCase(Locale.ROOT)));
        return s;
    }

    // ---------------------------------------------------------------- CHECK IN(DDL 모드)

    private static final Pattern CHECK_IN = Pattern.compile(
            "CHECK\\s*\\(\\s*[\"`\\[]?(\\w+)[\"`\\]]?\\s+IN\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);

    /** DDL 의 {@code CHECK (col IN ('A','B'))} → 컬럼(대문자) → 값 리터럴들. 메타모델엔 CHECK 가 없어 DDL 모드에서만 */
    public static Map<String, List<String>> checkIns(String ddl) {
        Map<String, List<String>> out = new HashMap<>();
        if (ddl == null) {
            return out;
        }
        Matcher m = CHECK_IN.matcher(ddl);
        while (m.find()) {
            out.putIfAbsent(m.group(1).toUpperCase(Locale.ROOT), splitVals(m.group(2)));
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

    private static final Pattern YN = Pattern.compile("(?i).*(_YN|_FLAG|_FL)$");
    private static final Pattern CHARISH = Pattern.compile("(?i).*(CHAR|VARCHAR|VARCHAR2|NVARCHAR|NVARCHAR2).*");
    private static final Pattern NUMISH = Pattern.compile("(?i).*(NUMBER|NUMERIC|DECIMAL|INT|BIGINT|SMALLINT|FLOAT|DOUBLE|REAL|SERIAL|MONEY).*");

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
        if (c.def().matches("^'.*'$")) {
            return c.def();
        }
        if (t.matches("BOOL|BOOLEAN")) {
            return db.equals("pg") ? (idx % 2 != 0 ? "false" : "true") : (idx % 2 != 0 ? "0" : "1");
        }
        if (t.equals("BIT")) {
            return idx % 2 != 0 ? "0" : "1";
        }
        if (t.equals("TINYINT") && (sz == 1 || c.prec() == 1)) {
            return idx % 2 != 0 ? "0" : "1";
        }
        if (t.equals("TINYINT")) {
            return String.valueOf((idx + 1) % 128); // 순수본은 0~127 난수
        }
        if (t.matches("UUID|UNIQUEIDENTIFIER")) {
            return db.equals("pg") ? "gen_random_uuid()" : db.equals("mssql") ? "NEWID()"
                    : "'" + pad(idx + 1, 8) + "-0000-0000-0000-000000000000'";
        }
        if (t.matches("JSONB?")) {
            return "'{\"k\":\"v" + (idx + 1) + "\"}'";
        }
        if (CHARISH.matcher(t).matches()) {
            String seq = pad(idx + 1, 3);
            int lim = sz > 0 ? sz : 30;
            if (lim <= seq.length()) {
                return "'" + seq.substring(seq.length() - lim) + "'";
            }
            String up = c.name().toUpperCase(Locale.ROOT);
            String b = up.substring(0, Math.min(up.length(), Math.max(1, lim - seq.length() - 1)));
            String v = b + "_" + seq;
            return "'" + v.substring(0, Math.min(v.length(), lim)) + "'";
        }
        if (NUMISH.matcher(t).matches()) {
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
        return "'VAL_" + (idx + 1) + "'";
    }

    /** 순수본 난수 대신 — n 을 정수 자리(최대 9)에 맞춰 돌리고, 소수 자리는 n 을 0 채워서. 예 NUMERIC(12,2) 의 5 → 5.05 */
    static String number(int n, int prec, int scale) {
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

    // ---------------------------------------------------------------- 메타 모드

    public static Result generate(Table t, Map<String, List<String>> allowed, Options o, FkValues fkValues) {
        String db = db(o.dialect());
        if (t.columns().isEmpty()) {
            throw new IllegalArgumentException("컬럼이 없다: " + t.name());
        }
        List<Col> cols = cols(t, allowed);
        List<String> warns = new ArrayList<>();
        int rows = Math.max(1, Math.min(MAX_ROWS, o.rows()));
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
                    + " — 값을 넣어 두었으니 필요하면 해당 컬럼을 지울 것" + (db.equals("mssql") ? " (SQL Server는 SET IDENTITY_INSERT ON 필요)" : ""));
        }
        if (o.upsert()) {
            return new Result(buildUpsert(db, t.name(), cols, c -> "#{" + camel(c.name()) + "}", warns), warns);
        }
        LocalDate base = o.baseDate() == null ? LocalDate.now() : o.baseDate();
        String colNames = String.join(",\n", cols.stream().map(c -> "\t" + c.name()).toList());
        List<String> inserts = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            int idx = i;
            String vals = String.join(",\n", cols.stream().map(c -> {
                List<Object> fv = fkCol.get(c.name().toUpperCase(Locale.ROOT));
                return "\t" + (fv != null ? literal(c, fv.get(idx % fv.size())) : genVal(c, idx, db, base));
            }).toList());
            inserts.add("INSERT INTO " + t.name() + "(\n" + colNames + "\n)\nVALUES(\n" + vals + "\n);");
        }
        commit(o, db, inserts);
        return new Result(String.join("\n\n", inserts), warns);
    }

    private static void commit(Options o, String db, List<String> out) {
        if (!o.commit()) {
            return;
        }
        if (db.equals("mssql") || db.equals("sybase")) {
            out.add("-- 명시 트랜잭션을 쓸 때만: BEGIN TRAN ... COMMIT TRAN\nCOMMIT TRAN;");
        } else if (db.equals("mysql")) {
            out.add("-- autocommit 켜져 있으면 불필요\nCOMMIT;");
        } else {
            out.add("COMMIT;");
        }
    }

    /** 부모 실존 값 → 리터럴. 수 타입이면 그대로, 그 밖은 따옴표 */
    static String literal(Col c, Object v) {
        if (v == null) {
            return "NULL";
        }
        if (NUMISH.matcher(c.type()).matches() && v instanceof Number) {
            return v instanceof BigDecimal b ? b.toPlainString() : v.toString();
        }
        return quote(v.toString());
    }

    static String quote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    static String camel(String snake) {
        String[] p = snake.toLowerCase(Locale.ROOT).split("_+");
        StringBuilder s = new StringBuilder(p.length == 0 ? "" : p[0]);
        for (int i = 1; i < p.length; i++) {
            if (!p[i].isEmpty()) {
                s.append(Character.toUpperCase(p[i].charAt(0))).append(p[i].substring(1));
            }
        }
        return s.toString();
    }

    // ---------------------------------------------------------------- MERGE(순수본 buildUpsert)

    interface Expr {
        String of(Col c);
    }

    static String buildUpsert(String db, String table, List<Col> cols, Expr expr, List<String> warns) {
        String tbl = table.toUpperCase(Locale.ROOT);
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
        if (nonPk.isEmpty()) {
            nonPk = cols.subList(1, cols.size());
        }
        String onCond = String.join(" AND ", keys.stream().map(p -> "T." + u(p) + " = S." + u(p)).toList());
        List<Col> set = nonPk;
        if (db.equals("oracle") || db.equals("mssql") || db.equals("sybase")) {
            String src = "\tSELECT\n" + String.join(",\n", cols.stream().map(c -> "\t\t  " + expr.of(c) + " AS " + u(c)).toList())
                    + (db.equals("oracle") ? "\n\tFROM DUAL" : "");
            StringBuilder s = new StringBuilder(pkNote);
            s.append("MERGE INTO ").append(tbl).append(" T\nUSING (\n").append(src).append("\n) S ON (").append(onCond).append(")\n");
            s.append("WHEN MATCHED THEN\n\tUPDATE SET\n");
            for (int i = 0; i < set.size(); i++) {
                s.append("\t\t").append(i > 0 ? ", " : "  ").append("T.").append(u(set.get(i))).append(" = S.").append(u(set.get(i))).append('\n');
            }
            s.append("WHEN NOT MATCHED THEN\n\tINSERT (\n");
            s.append(String.join(",\n", cols.stream().map(c -> "\t\t  " + u(c)).toList())).append("\n\t) VALUES (\n");
            s.append(String.join(",\n", cols.stream().map(c -> "\t\t  S." + u(c)).toList())).append("\n\t);");
            if (db.equals("mssql")) {
                s.append("\n-- SQL Server: MERGE 문 끝 세미콜론 필수");
            }
            if (db.equals("sybase")) {
                s.append("\n-- Sybase ASE 16 미만은 MERGE 미지원 — IF EXISTS + UPDATE/INSERT 로 분기 필요");
            }
            return s.toString();
        }
        String head = pkNote + "INSERT INTO " + tbl + " (\n" + String.join(",\n", cols.stream().map(c -> "\t  " + u(c)).toList())
                + "\n)\nVALUES (\n" + String.join(",\n", cols.stream().map(c -> "\t  " + expr.of(c)).toList()) + "\n)\n";
        StringBuilder s = new StringBuilder(head);
        if (db.equals("pg")) {
            s.append("ON CONFLICT (").append(String.join(", ", keys.stream().map(InsertGen::u).toList())).append(") DO UPDATE SET\n");
            for (int i = 0; i < set.size(); i++) {
                s.append('\t').append(i > 0 ? ", " : "  ").append(u(set.get(i))).append(" = EXCLUDED.").append(u(set.get(i)))
                        .append(i == set.size() - 1 ? ";\n" : "\n");
            }
            s.append("-- PostgreSQL 9.5+ (ON CONFLICT). 대상 컬럼에 UNIQUE/PK 인덱스가 있어야 함");
            return s.toString();
        }
        s.append("ON DUPLICATE KEY UPDATE\n");
        for (int i = 0; i < set.size(); i++) {
            s.append('\t').append(i > 0 ? ", " : "  ").append(u(set.get(i))).append(" = VALUES(").append(u(set.get(i))).append(')')
                    .append(i == set.size() - 1 ? ";\n" : "\n");
        }
        s.append("-- MySQL 8.0.20+ 는 VALUES() 대신 별칭 권장: ... AS NEW ... = NEW.컬럼");
        return s.toString();
    }

    private static String u(Col c) {
        return c.name().toUpperCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- CSV 모드(3.5 엑셀 → INSERT·MERGE)

    private static final Pattern NUM = Pattern.compile("-?\\d+(\\.\\d+)?");
    /** 날짜 모양 셋 — 길이로 고른다(중첩 선택 그룹 없이, ReDoS 판정 피함) */
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern MIN = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
    private static final Pattern SEC = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

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
            head.add(c);
        }
        List<String> warns = new ArrayList<>();
        for (Col c : byName.values()) {
            if (c.notNull() && c.def().isEmpty() && !c.auto() && !head.contains(c)) {
                warns.add("NOT NULL 컬럼 " + u(c) + " 이 CSV 에 없다");
            }
        }
        List<String> out = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int line = r + 1;
            Map<Col, String> lit = new LinkedHashMap<>();
            for (int k = 0; k < head.size(); k++) {
                lit.put(head.get(k), csvLiteral(head.get(k), k < row.size() ? row.get(k) : "", db, line, warns));
            }
            if (o.upsert()) {
                out.add(buildUpsert(db, t.name(), head, lit::get, warns));
            } else {
                out.add("INSERT INTO " + t.name() + "(\n" + String.join(",\n", head.stream().map(c -> "\t" + c.name()).toList())
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
        if (!CHARISH.matcher(t).matches() && NUMISH.matcher(t).matches()) {
            if (!NUM.matcher(v).matches()) {
                warns.add(where + "숫자가 아니다");
                return quote(raw);
            }
            int intLen = v.replace("-", "").split("\\.")[0].length();
            if (c.prec() > 0 && intLen > c.prec() - c.scale()) {
                warns.add(where + "정수 자리 " + (c.prec() - c.scale()) + " 을 넘는다");
            }
            return v;
        }
        if (t.contains("TIMESTAMP") || t.contains("DATETIME") || t.contains("DATE")) {
            if (!dateShape(v)) {
                warns.add(where + "날짜 모양(YYYY-MM-DD[ HH:MI[:SS]])이 아니다");
                return quote(raw);
            }
            String s = v.length() == 10 ? v + " 00:00:00" : v.length() == 16 ? v + ":00" : v;
            return dateLit(db, s, t.contains("TIMESTAMP") || t.contains("DATETIME"));
        }
        if (CHARISH.matcher(t).matches() && c.len() > 0 && raw.length() > c.len()) {
            warns.add(where + "길이 " + c.len() + " 을 넘는다(" + raw.length() + "자)");
        }
        return quote(raw);
    }
}
