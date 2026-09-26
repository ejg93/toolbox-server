package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.logical.ColumnInput;
import kr.ejg.toolbox.core.logical.ColumnInputs;
import kr.ejg.toolbox.core.logical.CommentDdl;
import kr.ejg.toolbox.core.logical.Dialect;
import kr.ejg.toolbox.core.logical.LogicalRun;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * 논리명(3-5~) — `POST /api/logical/comments {snapshotId | csv, owner, skipTokens, orgFirst, dialect, includeTables}` → text/plain.
 * 입력은 스냅샷(2.2 C) 또는 컬럼목록 CSV 문자열. 무시토큰을 안 주면 프로필 `logicalName.skipTokens`.
 */
final class LogicalRoutes {

    /** 변환 요청 — 3-5·3-6·3-8 공용 */
    record LogicalRequest(Long snapshotId, String csv, String owner, List<String> skipTokens, Boolean orgFirst,
            String dialect, Boolean includeTables) {
    }

    private LogicalRoutes() {
    }

    static void register(Javalin app, DictStore dict, SnapshotStore snapshots, Supplier<Optional<Profile>> active) {
        app.post("/api/logical/comments", ctx -> {
            LogicalRequest req = ctx.bodyAsClass(LogicalRequest.class);
            Dialect d;
            try {
                d = Dialect.of(req.dialect() == null ? "oracle" : req.dialect());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return;
            }
            LogicalRun.Result r = run(ctx, req, dict, snapshots, active);
            if (r == null) {
                return;
            }
            CommentDdl.Ddl ddl = CommentDdl.generate(r, d, req.includeTables() == null || req.includeTables(), LocalDateTime.now());
            ctx.contentType("text/plain; charset=UTF-8").result(ddl.text());
        });
    }

    /** 요청 → 변환 결과. 입력이 잘못되면 400 을 쓰고 null */
    static LogicalRun.Result run(Context ctx, LogicalRequest req, DictStore dict, SnapshotStore snapshots,
            Supplier<Optional<Profile>> active) throws SQLException {
        List<ColumnInput> in;
        if (req.snapshotId() != null) {
            Optional<List<Schema>> snap = snapshots.get(req.snapshotId());
            if (snap.isEmpty()) {
                ctx.status(404).json(Map.of("message", "스냅샷이 없다: " + req.snapshotId()));
                return null;
            }
            in = ColumnInputs.fromSchemas(snap.get());
        } else if (req.csv() != null) {
            try {
                in = ColumnInputs.fromCsv(req.csv().getBytes(StandardCharsets.UTF_8), req.owner());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return null;
            }
        } else {
            ctx.status(400).json(Map.of("message", "snapshotId 또는 csv 가 있어야 한다"));
            return null;
        }
        List<String> skip = req.skipTokens() != null ? req.skipTokens()
                : active.get().map(Profile::logicalName).map(Profile.LogicalName::skipTokens).orElse(List.of());
        return LogicalRun.run(in, dict.load(), skip, req.orgFirst() == null || req.orgFirst());
    }
}
