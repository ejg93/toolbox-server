package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ejg.toolbox.core.gen.AlterGen;
import kr.ejg.toolbox.core.gen.DdlGen;
import kr.ejg.toolbox.core.gen.TypeMapping;
import kr.ejg.toolbox.core.job.Job;
import kr.ejg.toolbox.core.job.JobManager;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SnapshotDiff;
import kr.ejg.toolbox.core.meta.SnapshotService;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * 스냅샷 — `POST /api/meta/snapshot {connId, note}`(작업, 202 {jobId}) · `GET /api/meta/snapshots` ·
 * `GET /api/meta/snapshots/{id}/tables`(요약) · `GET /api/meta/snapshots/{id}/tables/{name}?schema=`(전체) ·
 * `POST /api/meta/alter`(1-60b — 반영 DDL, 생성만).
 */
final class MetaRoutes {

    record SnapshotRequest(String connId, String note) {
    }

    /** 1-60b — a(앞)를 b(뒤)로 만드는 반영 DDL. target 이 없으면 a 의 방언 */
    record AlterRequest(Long a, Long b, Boolean ignoreSchema, String target, String schema, Boolean includeIndex, Boolean includeComments,
            Boolean save) {
    }

    private MetaRoutes() {
    }

    static void register(Javalin app, JobManager jobs, SnapshotService service, SnapshotStore store,
            java.util.function.Supplier<Optional<Profile>> active) {
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

        // 1-60b 반영 DDL — 생성만(실행하는 길은 없다). 원본 방언 = b(새 표·바뀐 타입의 모습), 대상 = a 를 뜬 DB
        app.post("/api/meta/alter", ctx -> {
            AlterRequest req = ctx.bodyAsClass(AlterRequest.class);
            Optional<List<Schema>> a = store.get(req.a() == null ? -1 : req.a());
            Optional<List<Schema>> b = store.get(req.b() == null ? -1 : req.b());
            if (a.isEmpty() || b.isEmpty()) {
                ctx.status(404).json(Map.of("message", "스냅샷이 없다 — a·b 를 확인"));
                return;
            }
            String source = dialectOf(store, req.b());
            String target = req.target() == null || req.target().isBlank() ? dialectOf(store, req.a()) : req.target().trim().toLowerCase(java.util.Locale.ROOT);
            if (target == null) {
                ctx.status(400).json(Map.of("message", "대상 방언을 고른다 — 앞 스냅샷의 DB 종류를 몰라 정하지 못했다(" + String.join("·", DdlGen.targets()) + ")"));
                return;
            }
            if (!DdlGen.targets().contains(target)) {
                ctx.status(400).json(Map.of("message", "대상 방언: " + req.target() + " — " + String.join("·", DdlGen.targets())));
                return;
            }
            AlterGen.Result r = AlterGen.generate(a.get(), b.get(), new AlterGen.Options(source, target, req.schema(),
                    !Boolean.FALSE.equals(req.ignoreSchema()), !Boolean.FALSE.equals(req.includeIndex()), !Boolean.FALSE.equals(req.includeComments())),
                    TypeMapping.load());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sql", r.sql());
            out.put("warnings", r.warnings());
            out.put("statements", r.statements());
            out.put("review", r.review());
            out.put("addedTables", r.addedTables());
            out.put("removedTables", r.removedTables());
            out.put("changedTables", r.changedTables());
            out.put("source", source == null ? "" : source);
            out.put("target", target);
            if (Boolean.TRUE.equals(req.save())) {
                java.nio.file.Path dir = Outputs.dir(active.get().orElse(null));
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Path file = dir.resolve("alter-" + req.a() + "-" + req.b() + "-" + target + ".sql");
                java.nio.file.Files.writeString(file, r.sql(), java.nio.charset.StandardCharsets.UTF_8);
                out.put("path", file.toString());
            }
            ctx.json(out);
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

    /** 스냅샷 목록의 방언(제품명에서) — 모르면 null */
    private static String dialectOf(SnapshotStore store, Long id) throws java.sql.SQLException {
        if (id == null) {
            return null;
        }
        return store.list().stream().filter(x -> x.id() == id).map(SnapshotStore.Summary::dialect).filter(d -> d != null && !d.isBlank())
                .findFirst().orElse(null);
    }
}
