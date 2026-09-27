package kr.ejg.toolbox.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.DbCorpus;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.sqlrun.SqlRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * 컨테이너 실물 표본(번들 10) — 방언별 클래스 넷이 이 베이스를 상속하고 컨테이너는 클래스마다 **한 번** 뜬다(메모리 때문에 한 번에 하나, 1-3).
 * {@code @BeforeAll} 이 표본 원본 DDL 을 넣고(변환 없음, {@link DbCorpus}), 절은 순서대로 돈다:
 * (1) INSERT 실행 — 빈 표(V-10) · (2) 데이터 적재(V-9) · (3) 메타(V-9) · (4) 품질(V-9) · (5) 스니펫(V-8).
 */
@Tag("db")
@Tag("corpus")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class DbCorpusBase {

    @TempDir
    Path tmp;

    protected Connection conn;
    protected final Map<String, Object> golden = new TreeMap<>();

    abstract DbCorpus.Dialect dialect();

    abstract Connection open() throws SQLException;

    String name() {
        return dialect().name().toLowerCase(Locale.ROOT);
    }

    @BeforeAll
    void loadDdl() throws Exception {
        CorpusFiles.verify();
        conn = open();
        List<String> failed = new ArrayList<>();
        int ok = 0;
        for (Path p : DbCorpus.ddl(dialect())) {
            DbCorpus.Loaded l = DbCorpus.load(conn, p, dialect(), DbCorpus.Kind.DDL);
            ok += l.ok();
            failed.addAll(l.failed());
        }
        // 시퀀스 스니펫이 볼 것 — PG·Maria·MSSQL 은 만들고, Oracle CURRVAL 은 세션에서 NEXTVAL 을 먼저 불러야 한다(ORA-8002)
        try (Statement s = conn.createStatement()) {
            if (dialect() == DbCorpus.Dialect.ORACLE) {
                s.execute("SELECT EMPLOYEES_SEQ.NEXTVAL FROM DUAL");
            } else {
                s.execute("CREATE SEQUENCE corpus_seq");
                if (dialect() == DbCorpus.Dialect.POSTGRES) {
                    s.execute("SELECT nextval('corpus_seq')"); // currval 도 세션에서 한 번 뽑은 뒤에만
                }
            }
        }
        golden.put("ddlOk", ok);
        golden.put("ddlFailed", failed.size());
        CorpusFiles.conformance("db-" + name() + "-ddl-failed", failed); // 원본 스크립트가 이 컨테이너에서 못 넣는 문장 — 목록만(바뀌면 빨강)
    }

    @AfterAll
    void close() throws Exception {
        GoldenFiles.assertJson("corpus/db-" + name() + ".json", golden);
        if (conn != null) {
            conn.close();
        }
    }

    // ---------------------------------------------------------------- 방언별 이름 — 메타 스키마·chinook Invoice

    record Names(String schema, String invoice, String invoiceId, String customerId, String country) {
    }

    Names names() {
        return switch (dialect()) {
            case POSTGRES -> new Names("public", "invoice", "invoice_id", "customer_id", "billing_country");
            case MARIA -> new Names("test", "Invoice", "InvoiceId", "CustomerId", "BillingCountry");
            case MSSQL -> new Names("dbo", "Invoice", "InvoiceId", "CustomerId", "BillingCountry");
            case ORACLE -> new Names("TEST", "INVOICE", "INVOICEID", "CUSTOMERID", "BILLINGCOUNTRY");
        };
    }

    List<kr.ejg.toolbox.core.meta.Schema> snapshot(String schema) throws SQLException {
        return kr.ejg.toolbox.core.dialect.MetaSources.forDialect(dialect().meta, conn)
                .collect(new kr.ejg.toolbox.core.meta.Scope(List.of(schema), null, null, null));
    }

    static List<kr.ejg.toolbox.core.meta.Table> tablesOnly(List<kr.ejg.toolbox.core.meta.Schema> snap) {
        return snap.stream().flatMap(s -> s.tables().stream())
                .filter(t -> t.type() == null || !t.type().toUpperCase(Locale.ROOT).contains("VIEW")).toList();
    }

    // ---------------------------------------------------------------- (1) INSERT·MERGE 실행(V-10)

    /** 여러 컬럼 CHECK 등 결정적 값이 못 맞추는 제약 — B */
    boolean checkViolation(SQLException e) {
        int code = e.getErrorCode();
        return switch (dialect()) {
            case POSTGRES -> "23514".equals(e.getSQLState());
            case MARIA -> code == 4025;
            case MSSQL -> code == 547 && String.valueOf(e.getMessage()).contains("CHECK");
            case ORACLE -> code == 2290;
        };
    }

    /** FK 위반 — 순환·자기 참조 표에서만 B(부모 값을 못 얻는다) */
    boolean fkViolation(SQLException e) {
        int code = e.getErrorCode();
        return switch (dialect()) {
            case POSTGRES -> "23503".equals(e.getSQLState());
            case MARIA -> code == 1452;
            case MSSQL -> code == 547 && String.valueOf(e.getMessage()).contains("FOREIGN KEY");
            case ORACLE -> code == 2291;
        };
    }

    /** NOT NULL 위반 — 이진 컬럼이 든 표에서만 B(생성기가 이진 값을 안 만든다 — 방언이 못 받는 타입) */
    boolean notNullViolation(SQLException e) {
        int code = e.getErrorCode();
        return switch (dialect()) {
            case POSTGRES -> "23502".equals(e.getSQLState());
            case MARIA -> code == 1048;
            case MSSQL -> code == 515;
            case ORACLE -> code == 1400;
        };
    }

    static boolean binary(kr.ejg.toolbox.core.meta.Table t) {
        return t.columns().stream().anyMatch(c -> String.valueOf(c.nativeType()).toUpperCase(Locale.ROOT)
                .matches(".*(BLOB|BYTEA|BINARY|IMAGE|RAW).*"));
    }

    /** 생성문 → 실행 문장. MSSQL MERGE 는 끝 ; 가 있어야 한다(10713) */
    List<String> executable(String sql) {
        List<String> out = new ArrayList<>();
        for (String st : DbCorpus.statements(sql, DbCorpus.Dialect.POSTGRES)) {
            out.add(dialect() == DbCorpus.Dialect.MSSQL ? st + ";" : st);
        }
        return out;
    }

    /** 표 하나의 문장들 — 표마다 세이브포인트(PG 는 오류 뒤 트랜잭션이 막힌다). 첫 실패를 돌려준다 */
    SQLException runAll(List<String> sts) throws SQLException {
        java.sql.Savepoint sp = conn.setSavepoint();
        try (Statement s = conn.createStatement()) {
            for (String st : sts) {
                s.execute(st);
            }
        } catch (SQLException e) {
            conn.rollback(sp);
            return e;
        }
        return null;
    }

    /**
     * 데이터 적재 전, DDL 만 든 빈 표에 — 스냅샷 표를 부모 먼저 순서로 {@code InsertGen.generate}(3행, 부모 값은 같은 커넥션에서 조회 —
     * 미커밋 부모 행을 본다) 실행 A, 넣은 첫 행을 CSV 로 되돌려 {@code fromCsv} MERGE 1행 실행 A. 끝에 롤백
     */
    @Test
    @Order(1)
    void insertRuns() throws Exception {
        StringBuilder ddlText = new StringBuilder();
        for (Path p : DbCorpus.ddl(dialect())) {
            ddlText.append(kr.ejg.toolbox.core.text.Csv.decode(java.nio.file.Files.readAllBytes(p))).append('\n');
        }
        Map<String, List<String>> allowed = kr.ejg.toolbox.core.gen.InsertGen.checkIns(ddlText.toString());
        Set<String> cyclic = new java.util.TreeSet<>();
        List<kr.ejg.toolbox.core.meta.Table> order = DbCorpus.parentsFirst(tablesOnly(snapshot(names().schema())), cyclic);
        kr.ejg.toolbox.core.gen.InsertGen.Options ins = new kr.ejg.toolbox.core.gen.InsertGen.Options(dialect().meta, 3, false, false,
                java.time.LocalDate.of(2026, 1, 31));
        kr.ejg.toolbox.core.gen.InsertGen.Options up = new kr.ejg.toolbox.core.gen.InsertGen.Options(dialect().meta, 1, true, false,
                java.time.LocalDate.of(2026, 1, 31));
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        int inserted = 0;
        int merged = 0;
        conn.setAutoCommit(false);
        try {
            for (kr.ejg.toolbox.core.meta.Table t : order) {
                String key = t.name().toUpperCase(Locale.ROOT);
                boolean selfRef = t.fks().stream().anyMatch(f -> f.refTable().equalsIgnoreCase(t.name()));
                String sql = kr.ejg.toolbox.core.gen.InsertGen.generate(t, allowed, ins, (fk, max) -> InsertRoutes.fkValues(conn, fk, max)).sql();
                SQLException e = runAll(executable(sql));
                if (e != null) {
                    String id = key + " INSERT [" + e.getSQLState() + "/" + e.getErrorCode() + "]";
                    if (checkViolation(e) || fkViolation(e) && selfRef || notNullViolation(e) && binary(t)) {
                        b.add(id);
                    } else {
                        a.add(id + " " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
                    }
                    continue;
                }
                inserted++;
                if (t.pk() == null || t.pk().columns().isEmpty()) {
                    continue; // MERGE 는 PK 로 맞춘다
                }
                String csv = firstRowCsv(t);
                SQLException m = runAll(executable(kr.ejg.toolbox.core.gen.InsertGen.fromCsv(t, csv, up).sql()));
                if (m != null) {
                    a.add(key + " MERGE [" + m.getSQLState() + "/" + m.getErrorCode() + "] " + String.valueOf(m.getMessage()).lines().findFirst().orElse(""));
                } else {
                    merged++;
                }
            }
        } finally {
            conn.rollback();
            conn.setAutoCommit(true);
        }
        cyclic.forEach(c -> b.add(c + " 순환 FK"));
        golden.put("insert", new LinkedHashMap<>(Map.of("tables", order.size(), "inserted", inserted, "merged", merged, "cyclic", cyclic.size())));
        CorpusFiles.conformance("db-" + name() + "-insert-b", b);
        CorpusFiles.none("INSERT·MERGE " + dialect().meta + "(표 " + order.size() + ")", a, order.size());
    }

    /** 방금 넣은 표의 첫 행 → CSV(머리 = 컬럼명). 날짜·시각은 'T' 를 뺀 모양으로(fromCsv 가 날짜로 알아본다) */
    String firstRowCsv(kr.ejg.toolbox.core.meta.Table t) throws SQLException {
        List<String> cols = t.columns().stream().map(kr.ejg.toolbox.core.meta.Column::name).toList();
        String from = (dialect() == DbCorpus.Dialect.MSSQL || dialect() == DbCorpus.Dialect.POSTGRES ? names().schema() + "." : "") + t.name();
        kr.ejg.toolbox.core.sqlrun.ResultTable r = SqlRunner.run(conn, "SELECT " + String.join(", ", cols) + " FROM " + from, List.of(), 1, 30);
        StringBuilder sb = new StringBuilder(String.join(",", cols)).append('\n');
        List<Object> row = r.rows().get(0);
        for (int i = 0; i < row.size(); i++) {
            Object v = row.get(i);
            String s = v == null ? "" : v.toString().replaceFirst("^(\\d{4}-\\d{2}-\\d{2})T", "$1 ");
            sb.append(i > 0 ? "," : "").append('"').append(s.replace("\"", "\"\"")).append('"');
        }
        return sb.append('\n').toString();
    }

    // ---------------------------------------------------------------- (2) 데이터 적재(V-9)

    @Test
    @Order(2)
    void loadData() throws Exception {
        List<String> failed = new ArrayList<>();
        int ok = 0;
        for (Path p : DbCorpus.data(dialect())) {
            DbCorpus.Loaded l = DbCorpus.load(conn, p, dialect(), DbCorpus.Kind.DATA);
            ok += l.ok();
            failed.addAll(l.failed());
        }
        golden.put("dataOk", ok);
        golden.put("dataFailed", failed.size());
        CorpusFiles.conformance("db-" + name() + "-data-failed", failed);
    }

    // ---------------------------------------------------------------- (3) 메타(V-9)

    /**
     * 벤더 MetaSource 스냅샷 대 같은 DDL 을 DdlReader 로 읽은 것 — 테이블이 있고 컬럼 수·PK 컬럼이 같아야 A.
     * FK·코멘트 수는 방언마다 DDL 모양이 달라(ALTER 로 따로·별도 주석 파일) B 목록
     */
    @Test
    @Order(3)
    void meta() throws Exception {
        Map<String, kr.ejg.toolbox.core.meta.Table> ddl = new LinkedHashMap<>();
        for (Path p : DbCorpus.ddl(dialect())) {
            for (kr.ejg.toolbox.core.meta.Table t : kr.ejg.toolbox.core.gen.DdlReader.read(
                    kr.ejg.toolbox.core.text.Csv.decode(java.nio.file.Files.readAllBytes(p))).tables()) {
                ddl.put(t.name().toUpperCase(Locale.ROOT), t);
            }
        }
        Map<String, kr.ejg.toolbox.core.meta.Table> got = new LinkedHashMap<>();
        tablesOnly(snapshot(names().schema())).forEach(t -> got.put(t.name().toUpperCase(Locale.ROOT), t));
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        for (Map.Entry<String, kr.ejg.toolbox.core.meta.Table> e : ddl.entrySet()) {
            kr.ejg.toolbox.core.meta.Table want = e.getValue();
            kr.ejg.toolbox.core.meta.Table t = got.get(e.getKey());
            if (t == null) {
                a.add(e.getKey() + " 스냅샷에 없음");
                continue;
            }
            if (t.columns().size() != want.columns().size()) {
                a.add(e.getKey() + " 컬럼 " + t.columns().size() + " ≠ DDL " + want.columns().size());
            }
            Set<String> pk = upper(t.pk() == null ? List.of() : t.pk().columns());
            Set<String> wantPk = upper(want.pk() == null ? List.of() : want.pk().columns());
            if (!wantPk.isEmpty() && !pk.equals(wantPk)) {
                a.add(e.getKey() + " PK " + pk + " ≠ DDL " + wantPk);
            }
            long wantComments = want.columns().stream().filter(c -> c.comment() != null && !c.comment().isBlank()).count();
            long gotComments = t.columns().stream().filter(c -> c.comment() != null && !c.comment().isBlank()).count();
            if (t.fks().size() != want.fks().size() || (wantComments > 0 && gotComments != wantComments)) {
                b.add(e.getKey());
            }
        }
        golden.put("metaDdlTables", ddl.size());
        golden.put("metaSnapshotTables", got.size());
        CorpusFiles.none("메타 " + dialect().meta + "(테이블 " + ddl.size() + ")", a, ddl.size());
        CorpusFiles.conformance("db-" + name() + "-meta-b", b);
    }

    static Set<String> upper(List<String> xs) {
        Set<String> s = new java.util.TreeSet<>();
        xs.forEach(x -> s.add(x.toUpperCase(Locale.ROOT)));
        return s;
    }

    /** 논리명 → COMMENT DDL(3-5) 생성문을 실제로 실행(3-9) — 사전은 동봉 공통표준단어 */
    @Test
    @Order(3)
    void commentDdlRuns() throws Exception {
        kr.ejg.toolbox.core.logical.Dialect ld = switch (dialect()) {
            case POSTGRES -> kr.ejg.toolbox.core.logical.Dialect.POSTGRESQL;
            case MARIA -> kr.ejg.toolbox.core.logical.Dialect.MARIADB;
            case MSSQL -> kr.ejg.toolbox.core.logical.Dialect.MSSQL;
            case ORACLE -> kr.ejg.toolbox.core.logical.Dialect.ORACLE;
        };
        kr.ejg.toolbox.core.logical.LogicalRun.Result r;
        try (kr.ejg.toolbox.core.db.Db db = kr.ejg.toolbox.core.db.Db.open(tmp.resolve("dict"))) {
            kr.ejg.toolbox.core.dict.DictStore store = new kr.ejg.toolbox.core.dict.DictStore(db);
            store.importMoi();
            r = kr.ejg.toolbox.core.logical.LogicalRun.run(kr.ejg.toolbox.core.logical.ColumnInputs.fromSchemas(snapshot(names().schema())),
                    store.load(), List.of("TB"), true);
        }
        List<String> lines = kr.ejg.toolbox.core.logical.CommentDdl.executableLines(r, ld, true);
        List<String> a = new ArrayList<>();
        conn.setAutoCommit(false);
        try (Statement s = conn.createStatement()) {
            for (String line : lines) {
                String st = line.strip().replaceAll(";\\s*$", "");
                try {
                    s.execute(st);
                } catch (SQLException e) {
                    String first = st.length() > 60 ? st.substring(0, 60) : st;
                    a.add(first + " [" + e.getSQLState() + "/" + e.getErrorCode() + "]");
                }
            }
        } finally {
            conn.rollback();
            conn.setAutoCommit(true);
        }
        golden.put("commentLines", lines.size());
        CorpusFiles.none("COMMENT DDL " + ld + "(문장 " + lines.size() + ")", a, lines.size());
    }

    /** PG 만 — eGov 두 판(v5.0.5 → v5.0.6) DDL 을 두 스키마에 넣어 스냅샷 diff(1-6) */
    @Test
    @Order(3)
    void snapshotDiffTwoReleases() throws Exception {
        if (dialect() != DbCorpus.Dialect.POSTGRES) {
            return;
        }
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE SCHEMA prev");
            s.execute("SET search_path TO prev");
            for (Path p : CorpusFiles.files("egov-prev", "*.sql")) {
                String rel = CorpusFiles.rel(p);
                if (rel.startsWith("egov-prev/script/ddl/postgres/") || rel.startsWith("egov-prev/script/comment/postgres/")) {
                    DbCorpus.load(conn, p, dialect(), DbCorpus.Kind.DDL);
                }
            }
            s.execute("SET search_path TO public");
        }
        kr.ejg.toolbox.core.meta.SnapshotDiff.Result d = kr.ejg.toolbox.core.meta.SnapshotDiff.compare(snapshot("prev"), snapshot("public"), true);
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("added", d.addedTables().size());
        g.put("removed", d.removedTables().size());
        g.put("changed", d.changedTables().size());
        golden.put("egovDiff", g);
    }

    // ---------------------------------------------------------------- (4) 품질(V-9)

    /** 사람이 채우는 자리표시자(2-6 Rendered) — 이것이 남은 문장은 실행하지 않는다 */
    static final Pattern MANUAL = Pattern.compile("업무테이블|공통코드테이블|해당그룹");

    /**
     * 품질 진단 SQL 8종(2-6)을 chinook Invoice 에 렌더해 실행 — 건수는 골든. 실패는 순수본 90번 템플릿 결함이라(d90.json 은 복사본)
     * 스니펫처럼 알려진 목록으로 얼린다 — MariaDB format 은 MySQL 8 의 6인자 REGEXP_REPLACE(1582)
     */
    @Test
    @Order(4)
    void qualitySqlRuns() throws Exception {
        Names n = names();
        Map<String, Object> rows = new TreeMap<>();
        List<String> a = new ArrayList<>();
        int manual = 0;
        for (kr.ejg.toolbox.core.quality.QualitySql.Kind k : kr.ejg.toolbox.core.quality.QualitySql.kinds()) {
            kr.ejg.toolbox.core.quality.QualitySql.Rendered r = kr.ejg.toolbox.core.quality.QualitySql.render(k.id(), dialect().meta,
                    n.schema(), n.invoice(), n.country(), List.of(n.invoiceId(), n.customerId()), List.of(n.schema()));
            // 템플릿은 GO 없이 ; 로 문장을 잇는다 — MSSQL 도 ; 로 나눈다(묶음째 돌리면 keydup 둘째 결과·codes 첫 문장을 잃는다)
            List<String> sts = DbCorpus.statements(r.sql(), DbCorpus.Dialect.POSTGRES);
            int got = 0;
            for (String st : sts) {
                if (MANUAL.matcher(st).find()) {
                    manual++;
                    continue;
                }
                try {
                    got += SqlRunner.run(conn, st, List.of(), 1000, 60).rows().size();
                } catch (SQLException e) {
                    a.add(k.id() + " [" + e.getSQLState() + "/" + e.getErrorCode() + "] " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
                }
            }
            rows.put(k.id(), got);
        }
        golden.put("qualityRows", rows);
        golden.put("qualityManual", manual);
        CorpusFiles.conformance("db-" + name() + "-quality-a", a);
    }

    // ---------------------------------------------------------------- (5) 스니펫(V-8)

    static JsonNode rendered;

    /** 순수본 SNIPPETS 전부를 한 번 렌더(앱 + corpus-snippets.js) — 탭별로 나눠 쓴다 */
    synchronized JsonNode rendered() throws Exception {
        if (rendered == null) {
            rendered = new ObjectMapper().readTree(CorpusNode.run("corpus-snippets.js", "corpus-snippets", tmp).out().resolve("summary.json").toFile());
        }
        return rendered;
    }

    static final Pattern QUERY = Pattern.compile("(?is)^(SELECT|WITH|SHOW|DESC|DESCRIBE|EXPLAIN)\\b.*");
    static final Pattern DML = Pattern.compile("(?is)^(INSERT|UPDATE|DELETE|MERGE)\\b.*");
    static final Pattern PLSQL = Pattern.compile("(?is).*\\b(BEGIN|DECLARE)\\b.*\\bEND\\b.*");
    /** 시스템 뷰·컨테이너에 없는 확장·우리가 안 만든 자리 표시 객체 — 이 이름이 든 문장의 「없는 객체」 는 B */
    static final Pattern SYSTEM = Pattern.compile("(?i)(pg_stat|pg_catalog|information_schema|performance_schema|mysql\\.|sys\\.|"
            + "\\bdba_|\\bv\\$|\\bgv\\$|\\bx\\$|\\ball_|\\buser_|dbms_|corpus_obj|corpus_seq|msdb\\.|master\\.)");

    enum Outcome { RUN, PARSED, SKIPPED, A, B }

    record Verdict(Outcome outcome, String code, String msg) {
        Verdict(Outcome outcome, String code) {
            this(outcome, code, "");
        }
    }

    /** 방언별 오류 코드 → A(스니펫이 틀렸다)·B(권한·없는 시스템 객체·확장). 없는 **컬럼**(PG 42703·Maria 1054·MSSQL 207·Oracle 904)은
     * 시스템 뷰라도 A — 컬럼은 권한과 무관하게 있어야 한다(첫 판 PG tbl_size·idx_bloat·seq_list 가 B 로 새던 것) */
    Verdict judge(SQLException e, String st) {
        Verdict v = judge0(e, st);
        String m = String.valueOf(e.getMessage()).lines().findFirst().orElse("").strip();
        return new Verdict(v.outcome(), v.code(), m.length() > 160 ? m.substring(0, 160) : m);
    }

    Verdict judge0(SQLException e, String st) {
        String state = String.valueOf(e.getSQLState());
        int code = e.getErrorCode();
        boolean sys = SYSTEM.matcher(st).find();
        return switch (dialect()) {
            case POSTGRES -> state.equals("42501") ? new Verdict(Outcome.B, state)
                    : (state.equals("42P01") || state.equals("42883") || state.equals("42704")) && sys ? new Verdict(Outcome.B, state)
                    : new Verdict(Outcome.A, state);
            case MARIA -> (code == 1142 || code == 1227 || code == 1044 || code == 1045 || code == 1143) ? new Verdict(Outcome.B, "" + code)
                    : (code == 1146 || code == 1305 || code == 1109) && sys ? new Verdict(Outcome.B, "" + code)
                    : new Verdict(Outcome.A, "" + code);
            case MSSQL -> (code == 229 || code == 297 || code == 300 || code == 262 || code == 15247) ? new Verdict(Outcome.B, "" + code)
                    : (code == 208 || code == 2812 || code == 4121) && sys ? new Verdict(Outcome.B, "" + code)
                    : new Verdict(Outcome.A, "" + code);
            case ORACLE -> code == 1031 ? new Verdict(Outcome.B, "ORA-" + code)
                    : (code == 942 || code == 4043 || code == 2289) && sys ? new Verdict(Outcome.B, "ORA-" + code)
                    : new Verdict(Outcome.A, "ORA-" + code);
        };
    }

    /** 문장 하나 — 조회는 실행(10행·30초), DML 은 파싱만, 그 밖(DDL·세션·PL/SQL)은 실행하지 않는다(MSSQL 은 PARSEONLY 로 전부 파싱) */
    Verdict one(String st) {
        String s = st.strip();
        try (Statement x = conn.createStatement()) {
            if (QUERY.matcher(s).matches()) {
                SqlRunner.run(conn, s, List.of(), 10, 30);
                return new Verdict(Outcome.RUN, "");
            }
            switch (dialect()) {
                case MSSQL -> {
                    x.execute("SET PARSEONLY ON");
                    try {
                        x.execute(s);
                    } finally {
                        x.execute("SET PARSEONLY OFF");
                    }
                    return new Verdict(Outcome.PARSED, "");
                }
                case POSTGRES, MARIA -> {
                    if (!DML.matcher(s).matches()) {
                        return new Verdict(Outcome.SKIPPED, "");
                    }
                    x.execute("EXPLAIN " + s);
                    return new Verdict(Outcome.PARSED, "");
                }
                case ORACLE -> {
                    if (!DML.matcher(s).matches()) {
                        return new Verdict(Outcome.SKIPPED, "");
                    }
                    x.execute("EXPLAIN PLAN FOR " + s);
                    return new Verdict(Outcome.PARSED, "");
                }
                default -> throw new IllegalStateException();
            }
        } catch (SQLException e) {
            return judge(e, s);
        } finally {
            try {
                conn.rollback();
            } catch (SQLException e) {
                // 자동 커밋이면 롤백할 것이 없다
            }
        }
    }

    @Test
    @Order(5)
    void snippets() throws Exception {
        JsonNode items = rendered().get("tabs").get(dialect().tab);
        conn.setAutoCommit(false);
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<Outcome, Integer> count = new TreeMap<>();
        int total = 0;
        for (JsonNode it : items) {
            String key = it.get("key").asText();
            String sql = it.path("sql").asText("");
            List<String> sts = PLSQL.matcher(sql).matches() ? List.of() : DbCorpus.statements(sql, dialect() == DbCorpus.Dialect.MSSQL
                    ? DbCorpus.Dialect.MSSQL : DbCorpus.Dialect.POSTGRES); // 스니펫 글은 SQL*Plus 줄이 없다 — ; 나누기만
            if (sts.isEmpty()) {
                skipped.add(key + " (PL/SQL·빈 글)");
                count.merge(Outcome.SKIPPED, 1, Integer::sum);
                continue;
            }
            for (int i = 0; i < sts.size(); i++) {
                total++;
                Verdict v = one(sts.get(i));
                count.merge(v.outcome(), 1, Integer::sum);
                String id = key + "#" + i + (v.code().isEmpty() ? "" : " " + v.code());
                switch (v.outcome()) {
                    case A -> a.add(id + " — " + v.msg()); // 공개 스니펫의 오류 문구 — portfolio 로 넘길 결함 목록
                    case B -> b.add(id);
                    case SKIPPED -> skipped.add(key + "#" + i);
                    default -> { }
                }
            }
        }
        conn.setAutoCommit(true);
        golden.put("snippets", new LinkedHashMap<>(Map.of("items", items.size(), "statements", total, "count", count)));
        CorpusFiles.conformance("db-" + name() + "-snippets-skipped", skipped);
        CorpusFiles.conformance("db-" + name() + "-snippets-b", b);
        // 스니펫 A 는 순수본 결함 — 고치는 곳이 portfolio 라 번들 안에서 안 고친다(설계 5). 알려진 목록으로 얼리고 새 A 가 생기면 빨강
        CorpusFiles.conformance("db-" + name() + "-snippets-a", a);
    }

    Set<String> unused() {
        return Set.of();
    }
}
