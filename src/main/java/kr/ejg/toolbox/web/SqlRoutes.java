package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.conn.ConnectionRegistry;
import kr.ejg.toolbox.core.profile.Profile;
import kr.ejg.toolbox.core.report.XlsxWriter;
import kr.ejg.toolbox.core.sqlrun.ResultTable;
import kr.ejg.toolbox.core.sqlrun.SqlRunner;

/**
 * SQL 실행 — `POST /api/sql/run {connId, sql, binds, maxRows}` → 결과 표 ·
 * `POST /api/sql/export {…, format: xlsx|csv}` → `out/<프로필>/<yyyyMMdd-HHmmss>/result.<ext>` 경로(12장).
 * SQL·결과는 로그에 안 남긴다(절대 규칙 3).
 */
final class SqlRoutes {

    record SqlRequest(String connId, String sql, List<Object> binds, Integer maxRows, String format) {
    }

    static final int EXPORT_MAX_ROWS = 100_000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SqlRoutes() {
    }

    static void register(Javalin app, ConnectionRegistry conns, Supplier<Optional<Profile>> active) {
        app.post("/api/sql/run", ctx -> {
            SqlRequest req = ctx.bodyAsClass(SqlRequest.class);
            String bad = check(req, conns);
            if (bad != null) {
                ctx.status(400).json(Map.of("message", bad));
                return;
            }
            int max = clamp(req.maxRows() == null ? SqlRunner.DEFAULT_MAX_ROWS : req.maxRows(), 1, EXPORT_MAX_ROWS);
            try (Connection c = conns.open(req.connId())) {
                ctx.json(SqlRunner.run(c, req.sql(), binds(req), max, SqlRunner.DEFAULT_TIMEOUT_SEC));
            } catch (java.sql.SQLException e) {
                ctx.status(400).json(Map.of("message", String.valueOf(e.getMessage())));
            }
        });

        app.post("/api/sql/export", ctx -> {
            SqlRequest req = ctx.bodyAsClass(SqlRequest.class);
            String bad = check(req, conns);
            String format = req.format() == null ? "xlsx" : req.format();
            if (bad == null && !format.equals("xlsx") && !format.equals("csv")) {
                bad = "format 은 xlsx·csv";
            }
            if (bad != null) {
                ctx.status(400).json(Map.of("message", bad));
                return;
            }
            int max = clamp(req.maxRows() == null ? EXPORT_MAX_ROWS : req.maxRows(), 1, EXPORT_MAX_ROWS);
            ResultTable t;
            try (Connection c = conns.open(req.connId())) {
                t = SqlRunner.run(c, req.sql(), binds(req), max, SqlRunner.DEFAULT_TIMEOUT_SEC);
            } catch (java.sql.SQLException e) {
                ctx.status(400).json(Map.of("message", String.valueOf(e.getMessage())));
                return;
            }
            Profile p = active.get().orElseThrow();
            String base = p.output() != null && p.output().dir() != null ? p.output().dir() : "out";
            Path file = Path.of(base, p.name(), LocalDateTime.now().format(STAMP), "result." + format);
            if (format.equals("xlsx")) {
                XlsxWriter.write(t, file);
            } else {
                XlsxWriter.writeCsv(t, file);
            }
            ctx.json(Map.of("path", file.toAbsolutePath().toString(), "rows", t.rows().size(), "truncated", t.truncated()));
        });
    }

    private static String check(SqlRequest req, ConnectionRegistry conns) {
        if (req.connId() == null || conns.find(req.connId()).isEmpty()) {
            return "접속이 없다: " + req.connId();
        }
        if (req.sql() == null || req.sql().isBlank()) {
            return "sql 이 비었다";
        }
        return null;
    }

    private static List<Object> binds(SqlRequest req) {
        return req.binds() == null ? List.of() : req.binds();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
