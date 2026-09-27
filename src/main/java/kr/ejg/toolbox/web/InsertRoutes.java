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
 */
final class InsertRoutes {

    /** FK 조회 SQL 에 이어 붙일 식별자 — 이 모양이 아니면 조회하지 않는다(메타·DDL 에서 온 이름이지만 DDL 은 사용자 글이다) */
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_$#]{0,127}");

    private InsertRoutes() {
    }

    record GenerateRequest(Long snapshotId, String schema, String table, String ddl, String csv, String connId,
            String dialect, Integer rows, Boolean upsert, Boolean commit, String baseDate) {
    }

    static void register(Javalin app, SnapshotStore snapshots, ConnectionRegistry conns) {
        app.post("/api/insert/generate", ctx -> {
            GenerateRequest req = ctx.bodyAsClass(GenerateRequest.class);
            if (req.dialect() == null || !InsertGen.DIALECTS.contains(req.dialect().toLowerCase(Locale.ROOT))) {
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
            if (req.ddl() != null && !req.ddl().isBlank()) {
                table = pick(DdlReader.read(req.ddl()).tables(), req.table());
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
            InsertGen.Options o = new InsertGen.Options(req.dialect(), req.rows() == null ? 5 : req.rows(),
                    Boolean.TRUE.equals(req.upsert()), req.commit() == null || req.commit(), base);
            try {
                if (req.csv() != null && !req.csv().isBlank()) {
                    ctx.json(InsertGen.fromCsv(table.get(), req.csv(), o));
                } else if (req.connId() != null && !req.connId().isBlank()) {
                    generateWithConn(ctx, table.get(), allowed, o, conns, req.connId());
                } else {
                    ctx.json(InsertGen.generate(table.get(), allowed, o, null));
                }
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
            }
        });
    }

    private static void generateWithConn(Context ctx, Table t, Map<String, List<String>> allowed, InsertGen.Options o,
            ConnectionRegistry conns, String connId) throws java.sql.SQLException {
        if (conns.find(connId).isEmpty()) {
            ctx.status(400).json(Map.of("message", "접속이 없다: " + connId));
            return;
        }
        try (Connection c = conns.open(connId)) {
            ctx.json(InsertGen.generate(t, allowed, o, (fk, max) -> fkValues(c, fk, max)));
        }
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
