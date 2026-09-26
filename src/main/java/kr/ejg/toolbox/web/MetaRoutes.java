package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ejg.toolbox.core.job.Job;
import kr.ejg.toolbox.core.job.JobManager;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SnapshotDiff;
import kr.ejg.toolbox.core.meta.SnapshotService;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.meta.Table;

/**
 * 스냅샷 — `POST /api/meta/snapshot {connId, note}`(작업, 202 {jobId}) · `GET /api/meta/snapshots` ·
 * `GET /api/meta/snapshots/{id}/tables`(요약) · `GET /api/meta/snapshots/{id}/tables/{name}?schema=`(전체).
 */
final class MetaRoutes {

    record SnapshotRequest(String connId, String note) {
    }

    private MetaRoutes() {
    }

    static void register(Javalin app, JobManager jobs, SnapshotService service, SnapshotStore store) {
        app.post("/api/meta/snapshot", ctx -> {
            SnapshotRequest req = ctx.bodyAsClass(SnapshotRequest.class);
            if (req.connId() == null || req.connId().isBlank()) {
                ctx.status(400).json(Map.of("message", "connId 가 없다"));
                return;
            }
            Job job = jobs.submit("snapshot", service.take(req.connId(), req.note()));
            ctx.status(202).json(Map.of("jobId", job.id()));
        });

        app.get("/api/meta/snapshots", ctx -> ctx.json(store.list()));

        // 1-6 — 접속이 달라도 된다(개발 vs 운영). ignoreSchema 기본 true
        app.get("/api/meta/diff", ctx -> {
            Optional<List<Schema>> a = store.get(id(ctx.queryParam("a")));
            Optional<List<Schema>> b = store.get(id(ctx.queryParam("b")));
            if (a.isEmpty() || b.isEmpty()) {
                ctx.status(404).json(Map.of("message", "스냅샷이 없다 — a·b 를 확인"));
                return;
            }
            boolean ignoreSchema = !"false".equalsIgnoreCase(ctx.queryParam("ignoreSchema"));
            ctx.json(SnapshotDiff.compare(a.get(), b.get(), ignoreSchema));
        });

        app.get("/api/meta/snapshots/{id}/tables", ctx -> {
            Optional<List<Schema>> snap = store.get(id(ctx.pathParam("id")));
            if (snap.isEmpty()) {
                ctx.status(404).json(Map.of("message", "스냅샷이 없다"));
                return;
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Schema s : snap.get()) {
                for (Table t : s.tables()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("schema", t.schema());
                    m.put("name", t.name());
                    m.put("type", t.type());
                    m.put("comment", t.comment());
                    m.put("rowCount", t.rowCount());
                    m.put("columnCount", t.columns().size());
                    out.add(m);
                }
            }
            ctx.json(out);
        });

        app.get("/api/meta/snapshots/{id}/tables/{name}", ctx -> {
            Optional<Table> t = store.getTable(id(ctx.pathParam("id")), ctx.queryParam("schema"), ctx.pathParam("name"));
            if (t.isEmpty()) {
                ctx.status(404).json(Map.of("message", "테이블이 없다"));
                return;
            }
            ctx.json(t.get());
        });
    }

    private static long id(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
