package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.conn.ConnectionRegistry;
import kr.ejg.toolbox.core.gen.DdlReader;
import kr.ejg.toolbox.core.gen.InsertGen;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.sqlrun.ResultTable;
import kr.ejg.toolbox.core.sqlrun.SqlRunner;

/**
 * 4-7 — `POST /api/insert/generate` → `{sql, warnings}`.
 * 테이블은 `snapshotId + schema? + table` 또는 `ddl`(+ table 로 고르기). `csv` 가 있으면 CSV 모드(머리 = 컬럼명).
 * `connId` 를 주면 FK 컬럼에 부모 테이블의 실존 값을 돌려 쓴다. 생성문·CSV 값은 로그에 안 남긴다(절대 규칙 3).
 * 0-56 — 방언은 「CREATE 문에서」 탭 값(`mysql`·`pg`)도 받는다. 응답 `verified` 는 컨테이너로 실행해 본 방언인가(sybase 만 아니다).
 * ddl 모드에 접속이 없으면 FK 경고 꼬리를 DDL 글로 바꾼다 — 그 탭엔 접속 고르기가 없다.
 */
final class InsertRoutes {

    /** FK 조회 SQL 에 이어 붙일 식별자 — 이 모양이 아니면 조회하지 않는다(메타·DDL 에서 온 이름이지만 DDL 은 사용자 글이다) */
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_$#]{0,127}");

    /** 「CREATE 문에서」 탭(`dummy_db`)이 보내는 순수본 방언 키 → InsertGen 방언 */
    static final Map<String, String> ALIASES = Map.of("mysql", "mariadb", "pg", "postgresql");
    /** 컨테이너가 없어 생성문을 실행해 보지 않은 방언(V-10) */
    static final Set<String> UNVERIFIED = Set.of("sybase");
    /** InsertGen 이 접속 없이 FK 를 만났을 때 붙이는 꼬리 — InsertRoutesTest 가 바꿈을 잰다 */
    static final String FK_TAIL_CONN = " — 접속을 주면 실존 값을 쓴다";
    static final String FK_TAIL_DDL = " — DDL 만으로는 알 수 없다(「스냅샷에서」 탭은 접속으로 실존 값을 쓴다)";

    private InsertRoutes() {
    }

    record GenerateRequest(Long snapshotId, String schema, String table, String ddl, String csv, String connId,
            String dialect, Integer rows, Boolean upsert, Boolean commit, String baseDate) {
    }

    static void register(Javalin app, SnapshotStore snapshots, ConnectionRegistry conns) {
        app.post("/api/insert/generate", ctx -> {
            GenerateRequest req = ctx.bodyAsClass(GenerateRequest.class);
            String dialect = req.dialect() == null ? null : req.dialect().toLowerCase(Locale.ROOT);
            dialect = ALIASES.getOrDefault(dialect, dialect);
            if (dialect == null || !InsertGen.DIALECTS.contains(dialect)) {
                ctx.status(400).json(Map.of("message", "dialect 는 " + String.join("·", InsertGen.DIALECTS.stream().sorted().toList())));
                return;
            }
            LocalDate base;
            try {
                base = req.baseDate() == null || req.baseDate().isBlank() ? null : LocalDate.parse(req.baseDate());
            } catch (DateTimeParseException e) {
                ctx.status(400).json(Map.of("message", "baseDate 는 YYYY-MM-DD"));
                return;
            }
            Map<String, List<String>> allowed = Map.of();
            Optional<Table> table;
            boolean ddlMode = req.ddl() != null && !req.ddl().isBlank();
            if (ddlMode) {
                List<Table> parsed = DdlReader.read(req.ddl()).tables();
                if (parsed.isEmpty()) {
                    ctx.status(400).json(Map.of("message", "CREATE TABLE 문을 못 읽었다"));
                    return;
                }
                table = pick(parsed, req.table());
                allowed = InsertGen.checkIns(req.ddl());
            } else if (req.snapshotId() != null) {
                Optional<List<Schema>> snap = snapshots.get(req.snapshotId());
                if (snap.isEmpty()) {
                    ctx.status(404).json(Map.of("message", "스냅샷이 없다: " + req.snapshotId()));
                    return;
                }
                List<Table> ts = new ArrayList<>();
                snap.get().stream().filter(s -> req.schema() == null || req.schema().isBlank() || s.name().equalsIgnoreCase(req.schema()))
                        .forEach(s -> ts.addAll(s.tables()));
                table = req.table() == null ? Optional.empty() : pick(ts, req.table());
            } else {
                ctx.status(400).json(Map.of("message", "snapshotId+table 또는 ddl 이 있어야 한다"));
                return;
            }
            if (table.isEmpty()) {
                ctx.status(404).json(Map.of("message", "테이블을 못 찾았다: " + req.table()));
                return;
            }
            InsertGen.Options o = new InsertGen.Options(dialect, req.rows() == null ? 5 : req.rows(),
                    Boolean.TRUE.equals(req.upsert()), req.commit() == null || req.commit(), base);
            boolean verified = !UNVERIFIED.contains(dialect);
            try {
                if (req.csv() != null && !req.csv().isBlank()) {
                    ctx.json(body(InsertGen.fromCsv(table.get(), req.csv(), o), verified, false));
                } else if (req.connId() != null && !req.connId().isBlank()) {
                    generateWithConn(ctx, table.get(), allowed, o, conns, req.connId(), verified);
                } else {
                    ctx.json(body(InsertGen.generate(table.get(), allowed, o, null), verified, ddlMode));
                }
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
            }
        });
    }

    private static void generateWithConn(Context ctx, Table t, Map<String, List<String>> allowed, InsertGen.Options o,
            ConnectionRegistry conns, String connId, boolean verified) throws java.sql.SQLException {
        if (conns.find(connId).isEmpty()) {
            ctx.status(400).json(Map.of("message", "접속이 없다: " + connId));
            return;
        }
        try (Connection c = conns.open(connId)) {
            ctx.json(body(InsertGen.generate(t, allowed, o, (fk, max) -> fkValues(c, fk, max)), verified, false));
        }
    }

    /** {@code {sql, warnings, verified}} — ddl 모드(접속 없음)면 FK 꼬리를 DDL 글로 */
    private static Map<String, Object> body(InsertGen.Result r, boolean verified, boolean ddlNoConn) {
        List<String> warns = ddlNoConn ? r.warnings().stream().map(w -> w.replace(FK_TAIL_CONN, FK_TAIL_DDL)).toList() : r.warnings();
        return Map.of("sql", r.sql(), "warnings", warns, "verified", verified);
    }

    /** {@code SELECT DISTINCT refCols FROM [refSchema.]refTable ORDER BY 1..n} — 값 조회라 SqlRunner 상한·시간 제한을 쓴다 */
    static List<List<Object>> fkValues(Connection c, ForeignKey fk, int max) throws java.sql.SQLException {
        List<String> cols = fk.refColumns().isEmpty() ? fk.columns() : fk.refColumns();
        List<String> names = new ArrayList<>(cols);
        names.add(fk.refTable());
        if (fk.refSchema() != null) {
            names.add(fk.refSchema());
        }
        if (!names.stream().allMatch(n -> n != null && IDENT.matcher(n).matches())) {
            throw new IllegalStateException("식별자 모양이 아니라 조회하지 않는다");
        }
        StringBuilder order = new StringBuilder();
        for (int i = 1; i <= cols.size(); i++) {
            order.append(i > 1 ? ", " : "").append(i);
        }
        String sql = "SELECT DISTINCT " + String.join(", ", cols) + " FROM "
                + (fk.refSchema() == null ? "" : fk.refSchema() + ".") + fk.refTable() + " ORDER BY " + order;
        ResultTable r = SqlRunner.run(c, sql, List.of(), max, 30);
        return r.rows();
    }

    private static Optional<Table> pick(List<Table> tables, String name) {
        if (name == null || name.isBlank()) {
            return tables.stream().findFirst();
        }
        return tables.stream().filter(t -> t.name().equalsIgnoreCase(name.trim())).findFirst();
    }
}
