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

    abstract Connection open() throws Exception;

    /** 골든·목록 파일 이름 db-<goldenKey>… — 최신판은 방언 이름, 옛 판 클래스가 판 이름(oracle11 등)으로 덮는다(V-21) */
    String goldenKey() {
        return dialect().name().toLowerCase(Locale.ROOT);
    }

    /** 옛 판 클래스 — 벤더 SQL 물러섬(warnings)이 A 가 아니라 B 목록 */
    boolean legacy() {
        return !goldenKey().equals(dialect().name().toLowerCase(Locale.ROOT));
    }

    /** 끌 절(메서드 이름) — 옛 판 클래스가 아직 안 켠 절. 꺼진 절은 건너뜀으로 보고된다 */
    Set<String> skipped() {
        return Set.of();
    }

    void skipIfOff(String section) {
        org.junit.jupiter.api.Assumptions.assumeFalse(skipped().contains(section), section + " — 이 판에서 아직 안 켠 절");
    }

    // ---------------------------------------------------------------- 옛 판 접속 — 드라이버 후보(V-21)

    /** 드라이버 후보. jar 가 null 이면 테스트 classpath(DriverManager), 아니면 {@link DbCorpus#connectWith} */
    record Candidate(String driver, Path jar, String driverClass, String url) {
    }

    static final Path ALT = Path.of("drivers/alt");
    static final Path CONN_ERRORS = GoldenFiles.DIR.resolve("corpus/dbold-conn-errors.txt");

    static Candidate classpath(String driver, String url) {
        return new Candidate(driver, null, null, url);
    }

    /** drivers/alt 의 prefix*.jar — 없으면 없는 파일 prefix.jar(후보를 건너뛴다) */
    static Candidate alt(String prefix, String driverClass, String url) throws java.io.IOException {
        try (java.util.stream.Stream<Path> s = java.nio.file.Files.exists(ALT) ? java.nio.file.Files.list(ALT) : java.util.stream.Stream.empty()) {
            Path jar = s.filter(p -> p.getFileName().toString().startsWith(prefix) && p.getFileName().toString().endsWith(".jar"))
                    .sorted().findFirst().orElseGet(() -> ALT.resolve(prefix + ".jar"));
            return new Candidate(jar.getFileName().toString().replaceFirst("\\.jar$", ""), jar, driverClass, url);
        }
    }

    /**
     * 후보를 차례로 — 실패는 「판 | 드라이버 | SQLState | 벤더 코드 | 오류문 첫 줄」 로 {@code dbold-conn-errors.txt} 의 이 판 몫에(1-18 접속 안내의 입력),
     * 붙은 드라이버는 골든에. 오류문의 매핑 포트·접속 번호는 가린다(실행마다 바뀐다)
     */
    Connection firstOf(int port, String user, String pw, Candidate... cs) throws Exception {
        List<String> errors = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Candidate k : cs) {
            if (k.jar() != null && !java.nio.file.Files.exists(k.jar())) {
                missing.add(k.jar().toString());
                continue;
            }
            try {
                Connection c = k.jar() == null ? java.sql.DriverManager.getConnection(k.url(), user, pw)
                        : DbCorpus.connectWith(k.jar(), k.driverClass(), k.url(), user, pw);
                golden.put("driver", k.driver());
                connErrors(errors);
                return c;
            } catch (SQLException e) {
                String first = String.valueOf(e.getMessage()).lines().findFirst().orElse("").strip()
                        .replace(String.valueOf(port), "<port>").replaceAll("conn=\\d+", "conn=<n>");
                errors.add(goldenKey() + " | " + k.driver() + " | " + e.getSQLState() + " | " + e.getErrorCode() + " | " + first);
            }
        }
        throw new IllegalStateException(goldenKey() + ": 드라이버 후보가 다 떨어졌다 " + errors
                + (missing.isEmpty() ? "" : " · 없는 jar " + missing + " — bash scripts/bundle-fetch.sh 로 drivers/alt 를 채운다"));
    }

    /** 공유 목록에서 이 판 몫만 대조 — 갱신(-Dgolden.update=true)이면 이 판 줄만 바꿔 쓴다 */
    void connErrors(List<String> mine) throws java.io.IOException {
        String pre = goldenKey() + " | ";
        boolean exists = java.nio.file.Files.exists(CONN_ERRORS);
        List<String> all = exists ? java.nio.file.Files.readAllLines(CONN_ERRORS, java.nio.charset.StandardCharsets.UTF_8) : List.of();
        Set<String> was = new java.util.TreeSet<>(all.stream().filter(l -> l.startsWith(pre)).toList());
        Set<String> now = new java.util.TreeSet<>(mine);
        if (GoldenFiles.updating()) {
            Set<String> out = new java.util.TreeSet<>(all.stream().filter(l -> !l.isBlank() && !l.startsWith(pre)).toList());
            out.addAll(now);
            java.nio.file.Files.writeString(CONN_ERRORS, out.isEmpty() ? "" : String.join("\n", out) + "\n", java.nio.charset.StandardCharsets.UTF_8);
            return;
        }
        if (!exists) {
            org.junit.jupiter.api.Assertions.fail("dbold-conn-errors: baseline 이 없다 — -Dgolden.update=true 로 만들고 diff 를 이력에. 지금 " + now);
        }
        org.junit.jupiter.api.Assertions.assertEquals(was, now, "dbold-conn-errors(" + goldenKey() + ") 가 바뀌었다");
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
            } else if (!goldenKey().startsWith("mysql")) { // MySQL 은 시퀀스가 없다(MariaDB 10.3+ 만, V-21)
                s.execute("CREATE SEQUENCE corpus_seq");
                if (dialect() == DbCorpus.Dialect.POSTGRES) {
                    s.execute("SELECT nextval('corpus_seq')"); // currval 도 세션에서 한 번 뽑은 뒤에만
                }
            }
        }
        golden.put("ddlOk", ok);
        golden.put("ddlFailed", failed.size());
        CorpusFiles.conformance("db-" + goldenKey() + "-ddl-failed", failed); // 원본 스크립트가 이 컨테이너에서 못 넣는 문장 — 목록만(바뀌면 빨강)
    }

    @AfterAll
    void close() throws Exception {
        GoldenFiles.assertJson("corpus/db-" + goldenKey() + ".json", golden);
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
        skipIfOff("insertRuns");
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
        CorpusFiles.conformance("db-" + goldenKey() + "-insert-b", b);
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
        skipIfOff("loadData");
        List<String> failed = new ArrayList<>();
        int ok = 0;
        for (Path p : DbCorpus.data(dialect())) {
            DbCorpus.Loaded l = DbCorpus.load(conn, p, dialect(), DbCorpus.Kind.DATA);
            ok += l.ok();
            failed.addAll(l.failed());
        }
        golden.put("dataOk", ok);
        golden.put("dataFailed", failed.size());
        CorpusFiles.conformance("db-" + goldenKey() + "-data-failed", failed);
    }

    // ---------------------------------------------------------------- (3) 메타(V-9)

    /**
     * 벤더 MetaSource 스냅샷 대 같은 DDL 을 DdlReader 로 읽은 것 — 테이블이 있고 컬럼 수·PK 컬럼이 같아야 A.
     * FK·코멘트 수는 방언마다 DDL 모양이 달라(ALTER 로 따로·별도 주석 파일) B 목록
     */
    @Test
    @Order(3)
    void meta() throws Exception {
        skipIfOff("meta");
        Map<String, kr.ejg.toolbox.core.meta.Table> ddl = new LinkedHashMap<>();
        for (Path p : DbCorpus.ddl(dialect())) {
            for (kr.ejg.toolbox.core.meta.Table t : kr.ejg.toolbox.core.gen.DdlReader.read(
                    kr.ejg.toolbox.core.text.Csv.decode(java.nio.file.Files.readAllBytes(p))).tables()) {
                ddl.put(t.name().toUpperCase(Locale.ROOT), t);
            }
        }
        Map<String, kr.ejg.toolbox.core.meta.Table> got = new LinkedHashMap<>();
        kr.ejg.toolbox.core.meta.MetaSource src = kr.ejg.toolbox.core.dialect.MetaSources.forDialect(dialect().meta, conn);
        List<kr.ejg.toolbox.core.meta.Schema> snap = src.collect(new kr.ejg.toolbox.core.meta.Scope(List.of(names().schema()), null, null,
                null));
        tablesOnly(snap).forEach(t -> got.put(t.name().toUpperCase(Locale.ROOT), t));
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        metaMore(snap, src, a);
        // 벤더 SQL 이 물러서면(1-12·1-13) 코멘트·통계·UNIQUE 가 JDBC 값으로 줄어든다 — 최신판에선 0 이어야 A
        // 옛 판은 물러선 종류가 B 목록(U-12 — 판에 없는 딕셔너리 뷰·컬럼)
        if (legacy()) {
            CorpusFiles.conformance("db-" + goldenKey() + "-meta-warnings", src.warnings().stream().map(String::valueOf).toList());
        } else if (!src.warnings().isEmpty()) {
            a.add("벤더 SQL 물러섬 " + src.warnings());
        }
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
        CorpusFiles.conformance("db-" + goldenKey() + "-meta-b", b);
    }

    static final Set<String> RULES = Set.of("CASCADE", "SET NULL", "SET DEFAULT", "RESTRICT", "NO ACTION");

    /**
     * 수집 보강(V-23) — FK 규칙·인덱스 정렬·CHECK·용량. A: 규칙 값이 다섯 낱말·null 중 하나 · 정렬이 ASC·DESC·"" 중 하나 ·
     * CHECK 수 = 딕셔너리 COUNT(그 판에서 checks 가 물러서지 않았을 때). B: 건수는 골든 metaMore
     */
    void metaMore(List<kr.ejg.toolbox.core.meta.Schema> snap, kr.ejg.toolbox.core.meta.MetaSource src, List<String> a) throws SQLException {
        Map<String, Integer> delete = new TreeMap<>();
        Map<String, Integer> update = new TreeMap<>();
        Map<String, Integer> sorts = new TreeMap<>();
        int checks = 0;
        for (kr.ejg.toolbox.core.meta.Table t : tablesOnly(snap)) {
            for (kr.ejg.toolbox.core.meta.ForeignKey fk : t.fks()) {
                for (kr.ejg.toolbox.core.meta.FkRule r : new kr.ejg.toolbox.core.meta.FkRule[] {fk.deleteRule(), fk.updateRule()}) {
                    if (r != null && !RULES.contains(r.label())) {
                        a.add(t.name() + " FK " + fk.name() + " 규칙 " + r);
                    }
                }
                delete.merge(fk.deleteRule() == null ? "(모름)" : fk.deleteRule().label(), 1, Integer::sum);
                update.merge(fk.updateRule() == null ? "(모름)" : fk.updateRule().label(), 1, Integer::sum);
            }
            for (kr.ejg.toolbox.core.meta.Index ix : t.indexes()) {
                for (String so : ix.sorts().stream().map(kr.ejg.toolbox.core.meta.SortOrder::label).toList()) {
                    if (!so.equals("ASC") && !so.equals("DESC") && !so.isEmpty()) {
                        a.add(t.name() + " 인덱스 " + ix.name() + " 정렬 " + so);
                    }
                    sorts.merge(so.isEmpty() ? "(모름)" : so, 1, Integer::sum);
                }
            }
            checks += t.checks().size();
        }
        boolean checksFellBack = src.warnings().stream().anyMatch(w -> w.kind().equals("checks"));
        Integer dict = checksFellBack ? null : checkCount();
        if (dict != null && dict != checks) {
            a.add("CHECK " + checks + " ≠ 딕셔너리 " + dict);
        }
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("checks", checks);
        g.put("checksFellBack", checksFellBack);
        g.put("fkDelete", delete);
        g.put("fkUpdate", update);
        g.put("indexSorts", sorts);
        g.put("sizeKnown", snap.stream().allMatch(s -> s.sizeBytes() != null));
        // 데이터를 넣은 스키마인데 용량이 0 이면 용량 SQL 이 틀렸다(PR #42 리뷰)
        for (kr.ejg.toolbox.core.meta.Schema s : snap) {
            boolean rows = s.tables().stream().anyMatch(t -> t.rowCount() != null && t.rowCount() > 0);
            if (rows && s.sizeBytes() != null && s.sizeBytes() == 0) {
                a.add(s.name() + " 용량 0 — 행이 있는데");
            }
        }
        golden.put("metaMore", g);
    }

    /** 스키마의 CHECK 수 — 수집기와 다른 길(COUNT·TABLE_CONSTRAINTS)로 잰다. Oracle 은 LONG 이라 행을 읽어 NOT NULL 자동 제약을 뺀다 */
    Integer checkCount() throws SQLException {
        String schema = names().schema();
        String sql = switch (dialect()) {
            case POSTGRES -> "SELECT COUNT(*) FROM pg_constraint con JOIN pg_class c ON c.oid = con.conrelid"
                    + " JOIN pg_namespace n ON n.oid = c.relnamespace WHERE con.contype = 'c' AND n.nspname = ? AND c.relkind IN ('r', 'p')";
            case MARIA -> "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'CHECK' AND TABLE_SCHEMA = ?";
            case MSSQL -> "SELECT COUNT(*) FROM sys.check_constraints cc JOIN sys.tables t ON t.object_id = cc.parent_object_id"
                    + " JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = ?";
            case ORACLE -> "SELECT SEARCH_CONDITION FROM ALL_CONSTRAINTS c JOIN ALL_TABLES t ON t.OWNER = c.OWNER AND t.TABLE_NAME = c.TABLE_NAME"
                    + " WHERE c.OWNER = ? AND c.CONSTRAINT_TYPE = 'C'";
        };
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (dialect() != DbCorpus.Dialect.ORACLE) {
                    rs.next();
                    return rs.getInt(1);
                }
                int n = 0;
                while (rs.next()) {
                    String cond = String.valueOf(rs.getString(1)).strip().toUpperCase(Locale.ROOT);
                    if (!(cond.endsWith(" IS NOT NULL") && cond.split("\\s+").length == 4)) {
                        n++;
                    }
                }
                return n;
            }
        }
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
        skipIfOff("commentDdlRuns");
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
        List<String> lines = new ArrayList<>(kr.ejg.toolbox.core.logical.CommentDdl.executableLines(r, ld, true));
        // 표 줄 경로 — 표본 표 이름(COMTN…·chinook)은 사전 변환이 안 돼 표 줄이 안 나온다. chinook Invoice 에 표 줄 하나를 얹어 넷 다 잰다(PR #20 리뷰)
        lines.addAll(kr.ejg.toolbox.core.logical.CommentDdl.executableLines(new kr.ejg.toolbox.core.logical.LogicalRun.Result(List.of(),
                List.of(new kr.ejg.toolbox.core.logical.LogicalRun.TableRow(names().schema(), names().invoice(), "청구서", "word", List.of())),
                List.of(), Map.of(), Set.of(), new kr.ejg.toolbox.core.logical.LogicalRun.Stats(0, 0, 0, 0, 0, 0)), ld, true));
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
        skipIfOff("snapshotDiffTwoReleases");
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
        skipIfOff("qualitySqlRuns");
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
                    a.add(k.id() + " [" + e.getSQLState() + "/" + e.getErrorCode() + "] " + firstLine(e));
                }
            }
            rows.put(k.id(), got);
        }
        golden.put("qualityRows", rows);
        golden.put("qualityManual", manual);
        CorpusFiles.conformance("db-" + goldenKey() + "-quality-a", a);
    }

    /** 오류문 첫 줄 — mariadb 드라이버가 붙이는 접속 번호 「(conn=N) 」 는 뗀다(서버의 접속 수에 따라 갈린다, V-22 MySQL) */
    static String firstLine(SQLException e) {
        return String.valueOf(e.getMessage()).lines().findFirst().orElse("").strip().replaceFirst("^\\(conn=\\d+\\) ", "");
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
        String m = firstLine(e);
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
        skipIfOff("snippets");
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
        CorpusFiles.conformance("db-" + goldenKey() + "-snippets-skipped", skipped);
        CorpusFiles.conformance("db-" + goldenKey() + "-snippets-b", b);
        // 스니펫 A 는 순수본 결함 — 고치는 곳이 portfolio 라 번들 안에서 안 고친다(설계 5). 알려진 목록으로 얼리고 새 A 가 생기면 빨강
        CorpusFiles.conformance("db-" + goldenKey() + "-snippets-a", a);
    }

    Set<String> unused() {
        return Set.of();
    }

    // ---------------------------------------------------------------- (6) DDL 생성·방언 변환 왕복(V-18)

    String ddlTarget() {
        return switch (dialect()) {
            case ORACLE -> "oracle";
            case POSTGRES -> "postgresql";
            case MARIA -> "mariadb";
            case MSSQL -> "mssql";
        };
    }

    /**
     * H2 HR 스냅샷 → DdlGen(대상 = 이 컨테이너, 이름 앞 G18_) → 문장마다 실행 → 이 방언으로 다시 스냅샷.
     * A: 실행 실패 0 · 표 7 · 표마다 컬럼 이름·순서·널 허용·PK 컬럼 · FK 수 · 코멘트 있는 컬럼 수 같음.
     * B: 자바 타입이 바뀐 컬럼 목록(type-drift). 끝에 만든 표를 지운다(FK 먼저)
     */
    @Test
    @Order(6)
    void ddlRoundTrip() throws Exception {
        skipIfOff("ddlRoundTrip");
        List<kr.ejg.toolbox.core.meta.Table> src;
        kr.ejg.toolbox.CorpusHr.Loaded hr = kr.ejg.toolbox.CorpusHr.open(false);
        try {
            src = tablesOnly(kr.ejg.toolbox.core.dialect.MetaSources.forDialect("h2", hr.conn())
                    .collect(new kr.ejg.toolbox.core.meta.Scope(List.of("PUBLIC"), null, null, null)));
        } finally {
            hr.conn().close();
        }
        kr.ejg.toolbox.core.gen.TypeMapping types = kr.ejg.toolbox.core.gen.TypeMapping.load();
        kr.ejg.toolbox.core.gen.DdlGen.Result g = kr.ejg.toolbox.core.gen.DdlGen.generate(src,
                new kr.ejg.toolbox.core.gen.DdlGen.Options("h2", ddlTarget(), null, true, true, true, "G18_"), types);
        List<String> a = new ArrayList<>();
        List<String> drift = new ArrayList<>();
        int ran = 0;
        try {
            for (String st : g.sql().split(";\n")) {
                String body = st.lines().filter(l -> !l.startsWith("--")).reduce("", (x, y) -> x + "\n" + y).trim();
                if (body.isEmpty()) {
                    continue;
                }
                try (Statement s = conn.createStatement()) {
                    s.execute(body);
                    ran++;
                } catch (SQLException e) {
                    a.add("실행 " + body.lines().findFirst().orElse("") + " — " + e.getMessage().lines().findFirst().orElse(""));
                }
            }
            Map<String, kr.ejg.toolbox.core.meta.Table> back = new LinkedHashMap<>();
            for (kr.ejg.toolbox.core.meta.Table t : tablesOnly(snapshot(names().schema()))) {
                if (t.name().toUpperCase(Locale.ROOT).startsWith("G18_")) {
                    back.put(t.name().toUpperCase(Locale.ROOT).substring(4), t);
                }
            }
            if (back.size() != src.size()) {
                a.add("표 " + back.size() + " ≠ " + src.size());
            }
            for (kr.ejg.toolbox.core.meta.Table want : src) {
                kr.ejg.toolbox.core.meta.Table t = back.get(want.name().toUpperCase(Locale.ROOT));
                if (t == null) {
                    a.add(want.name() + " 이 안 생겼다");
                    continue;
                }
                if (!shape(want).equals(shape(t))) {
                    a.add(want.name() + " 컬럼 " + shape(t) + " ≠ " + shape(want));
                }
                Set<String> pk = upper(t.pk() == null ? List.of() : t.pk().columns());
                Set<String> wantPk = upper(want.pk() == null ? List.of() : want.pk().columns());
                if (!pk.equals(wantPk)) {
                    a.add(want.name() + " PK " + pk + " ≠ " + wantPk);
                }
                if (t.fks().size() != want.fks().size()) {
                    a.add(want.name() + " FK " + t.fks().size() + " ≠ " + want.fks().size());
                }
                if (comments(t) != comments(want)) {
                    a.add(want.name() + " 코멘트 " + comments(t) + " ≠ " + comments(want));
                }
                Map<String, kr.ejg.toolbox.core.meta.Column> byName = new LinkedHashMap<>();
                t.columns().forEach(c -> byName.put(c.name().toUpperCase(Locale.ROOT), c));
                for (kr.ejg.toolbox.core.meta.Column c : want.columns()) {
                    kr.ejg.toolbox.core.meta.Column b = byName.get(c.name().toUpperCase(Locale.ROOT));
                    String j1 = types.javaType(c, "h2");
                    String j2 = b == null ? null : types.javaType(b, ddlTarget());
                    if (b != null && !java.util.Objects.equals(j1, j2)) {
                        drift.add(want.name() + "." + c.name() + " " + j1 + " → " + j2 + " (" + b.nativeType() + ")");
                    }
                }
            }
        } finally {
            dropG18();
        }
        golden.put("ddlGenStatements", ran);
        golden.put("ddlGenWarnings", g.warnings().size());
        golden.put("ddlGenTypeDrift", drift.size());
        CorpusFiles.none("DDL 생성 왕복 " + ddlTarget() + "(HR 표 " + src.size() + ")", a, src.size());
        CorpusFiles.conformance("db-" + goldenKey() + "-ddl-type-drift", drift);
    }

    /** 컬럼 이름·순서·널 허용 */
    static String shape(kr.ejg.toolbox.core.meta.Table t) {
        List<String> out = new ArrayList<>();
        t.columns().stream().sorted((x, y) -> Integer.compare(x.ordinal(), y.ordinal()))
                .forEach(c -> out.add(c.name().toUpperCase(Locale.ROOT) + (c.nullable() ? "" : "!")));
        return String.join(",", out);
    }

    static long comments(kr.ejg.toolbox.core.meta.Table t) {
        return t.columns().stream().filter(c -> c.comment() != null && !c.comment().isBlank()).count();
    }

    /** G18_ 표를 지운다 — FK 먼저 */
    void dropG18() throws SQLException {
        List<kr.ejg.toolbox.core.meta.Table> mine = tablesOnly(snapshot(names().schema())).stream()
                .filter(t -> t.name().toUpperCase(Locale.ROOT).startsWith("G18_")).toList();
        for (kr.ejg.toolbox.core.meta.Table t : mine) {
            for (kr.ejg.toolbox.core.meta.ForeignKey fk : t.fks()) {
                try (Statement s = conn.createStatement()) {
                    s.execute("ALTER TABLE " + t.name() + (dialect() == DbCorpus.Dialect.MARIA ? " DROP FOREIGN KEY " : " DROP CONSTRAINT ") + fk.name());
                } catch (SQLException e) {
                    // 이미 없음
                }
            }
        }
        for (kr.ejg.toolbox.core.meta.Table t : mine) {
            try (Statement s = conn.createStatement()) {
                s.execute("DROP TABLE " + t.name());
            }
        }
    }

    // ---------------------------------------------------------------- (7) 마스킹 UPDATE 실행(V-19)

    /**
     * 마스킹 UPDATE(7-8)를 이 방언에서 실제로 돌린다 — 표 G19_MASK 를 만들고 넷째 행까지 넣은 뒤 트랜잭션 안에서 UPDATE →
     * 다시 읽어 가린 꼴 → ROLLBACK → 원래 값 → 표를 지운다. A: 실행 실패 0 · 꼴 불일치 0
     */
    @Test
    @Order(7)
    void maskingRuns() throws Exception {
        skipIfOff("maskingRuns");
        List<kr.ejg.toolbox.core.logical.Masking.Candidate> c = kr.ejg.toolbox.core.logical.Masking.detect(List.of(
                new kr.ejg.toolbox.core.logical.Masking.Input(null, "G19_MASK", "USER_NM", "사용자명", null, "VARCHAR", 30L),
                new kr.ejg.toolbox.core.logical.Masking.Input(null, "G19_MASK", "MBTLNUM", null, null, "VARCHAR", 20L),
                new kr.ejg.toolbox.core.logical.Masking.Input(null, "G19_MASK", "EMAIL", null, null, "VARCHAR", 50L)),
                kr.ejg.toolbox.core.logical.Masking.rules()).candidates();
        String sql = kr.ejg.toolbox.core.logical.Masking.sql(c, ddlTarget(), kr.ejg.toolbox.core.logical.Masking.rules());
        String vc = dialect() == DbCorpus.Dialect.ORACLE ? "VARCHAR2" : dialect() == DbCorpus.Dialect.MSSQL ? "NVARCHAR" : "VARCHAR";
        List<String> a = new ArrayList<>();
        List<String> before = List.of("홍길동|010-1234-5678|abcd@x.kr", "김|0101|nomail", "null|null|null", "남궁민수|02-123-4567|a@b.c");
        List<String> masked = List.of("홍**|*********5678|ab**@x.kr", "*|****|no****", "null|null|null", "남***|*******4567|*@b.c");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE G19_MASK (ID INT, USER_NM " + vc + "(30), MBTLNUM " + vc + "(20), EMAIL " + vc + "(50))");
        }
        boolean auto = conn.getAutoCommit();
        try {
            try (java.sql.PreparedStatement ps = conn.prepareStatement("INSERT INTO G19_MASK VALUES (?, ?, ?, ?)")) {
                int id = 0;
                for (String row : before) {
                    String[] v = row.split("\\|", -1);
                    ps.setInt(1, ++id);
                    for (int i = 0; i < 3; i++) {
                        ps.setString(i + 2, v[i].equals("null") ? null : v[i]);
                    }
                    ps.executeUpdate();
                }
            }
            conn.setAutoCommit(false);
            for (String stmt : sql.split(";\n")) {
                String body = stmt.lines().filter(l -> !l.startsWith("--")).reduce("", (x, y) -> x + "\n" + y).trim();
                if (body.isEmpty()) {
                    continue;
                }
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(body);
                } catch (SQLException e) {
                    a.add("실행 — " + e.getMessage().lines().findFirst().orElse(""));
                }
            }
            List<String> got = readMask();
            if (!got.equals(masked)) {
                a.add("가린 꼴 " + got + " ≠ " + masked);
            }
            conn.rollback();
            conn.setAutoCommit(auto);
            List<String> back = readMask();
            if (!back.equals(before)) {
                a.add("ROLLBACK 뒤 " + back + " ≠ " + before);
            }
        } finally {
            if (!conn.getAutoCommit()) {
                conn.rollback();
                conn.setAutoCommit(auto);
            }
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TABLE G19_MASK");
            }
        }
        golden.put("maskingColumns", c.size());
        CorpusFiles.none("마스킹 UPDATE " + ddlTarget(), a, 4);
    }

    // ---------------------------------------------------------------- (8) 정의서 표준(V-24)

    /**
     * 이 방언 스냅샷으로 정의서(00·01~07·09~11)를 예시 양식에 실제로 기입한다. 08 은 코드 표 선택·접속이 있어야 해 뺀다.
     * A: 예외 0 · 양식 기입 통과(문서 수) · 03 행 수 = 컬럼 수 · Not Null 여부 Y 수 = nullable 컬럼 수 · PK\d\d 수 = PK 컬럼 수.
     * B: 문서별 행 수·추정 건수 — 골든 db-<판>.json 의 deliverable
     */
    @Test
    @Order(8)
    void deliverableBuilds() throws Exception {
        skipIfOff("deliverableBuilds");
        List<kr.ejg.toolbox.core.meta.Schema> snap = snapshot(names().schema());
        Set<String> docs = Set.of("01", "02", "03", "04", "05", "06", "07", "09", "10", "11");
        List<String> a = new ArrayList<>();
        try (kr.ejg.toolbox.core.db.Db db = kr.ejg.toolbox.core.db.Db.open(tmp.resolve("deliv"))) {
            kr.ejg.toolbox.core.dict.DictStore dict = new kr.ejg.toolbox.core.dict.DictStore(db);
            dict.importMoi();
            kr.ejg.toolbox.core.deliverable.DeliverableService.Result r = kr.ejg.toolbox.core.deliverable.DeliverableService.build(snap,
                    new kr.ejg.toolbox.core.deliverable.DeliverableService.Request(docs, kr.ejg.toolbox.core.deliverable.Definitions.Options.empty(),
                            List.of(), true, List.of()), dict, kr.ejg.toolbox.core.report.Mapping.load(Path.of("mappings/deliverable/example.yaml")),
                    Path.of("templates/deliverable/example"), tmp.resolve("deliv-out"), null, null);
            if (r.files().size() != docs.size()) {
                a.add("양식 기입 " + r.files().size() + " ≠ " + docs.size());
            }
            if (r.guide() == null || !java.nio.file.Files.exists(Path.of(r.guide()))) {
                a.add("작성안내가 없다");
            }
        }
        List<kr.ejg.toolbox.core.deliverable.Doc> defs = kr.ejg.toolbox.core.deliverable.Definitions.build(snap,
                kr.ejg.toolbox.core.deliverable.Definitions.Options.empty(), kr.ejg.toolbox.core.deliverable.DeliverableService.piiKeys(snap, null));
        kr.ejg.toolbox.core.deliverable.Doc d03 = defs.stream().filter(d -> d.no().equals("03")).findFirst().orElseThrow();
        int columns = 0;
        int nullable = 0;
        int pkCols = 0;
        for (kr.ejg.toolbox.core.meta.Table t : kr.ejg.toolbox.core.deliverable.Definitions.sorted(snap)) {
            columns += t.columns().size();
            nullable += (int) t.columns().stream().filter(kr.ejg.toolbox.core.meta.Column::nullable).count();
            pkCols += t.pk() == null ? 0 : t.pk().columns().size();
        }
        int ys = 0;
        int pks = 0;
        for (int i = 0; i < d03.rows().size(); i++) {
            ys += "Y".equals(d03.cell(i, "Not Null 여부")) ? 1 : 0;
            pks += String.valueOf(d03.cell(i, "PK정보")).matches("PK\\d\\d") ? 1 : 0;
        }
        if (d03.rows().size() != columns) {
            a.add("03 행 " + d03.rows().size() + " ≠ 컬럼 " + columns);
        }
        if (ys != nullable) {
            a.add("Not Null 여부 Y " + ys + " ≠ nullable " + nullable);
        }
        if (pks != pkCols) {
            a.add("PK정보 " + pks + " ≠ PK 컬럼 " + pkCols);
        }
        Map<String, Object> g = new TreeMap<>();
        for (kr.ejg.toolbox.core.deliverable.Doc d : defs) {
            g.put(d.no(), d.rows().size());
        }
        g.put("03.piiGuessed", d03.estimated().getOrDefault("개인정보 여부", 0));
        golden.put("deliverable", g);
        CorpusFiles.none("정의서 표준 " + dialect().meta + "(컬럼 " + columns + ")", a, columns);
    }

    List<String> readMask() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); java.sql.ResultSet rs = st.executeQuery("SELECT USER_NM, MBTLNUM, EMAIL FROM G19_MASK ORDER BY ID")) {
            while (rs.next()) {
                out.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
            }
        }
        return out;
    }
}
