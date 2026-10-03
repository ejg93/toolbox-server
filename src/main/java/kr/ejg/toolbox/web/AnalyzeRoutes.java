package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.analyze.AnalyzeRunner;
import kr.ejg.toolbox.core.analyze.AnalyzeStore;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.Job;
import kr.ejg.toolbox.core.job.JobManager;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * 프로그램 분석(6-4). {@code POST /api/analyze/run}(job) — 폴더 소스를 파싱해 프로그램·CRUD 를 H2 에 저장하고 결과 이벤트에 runId·수·프로그램.
 * 소스 파싱만 — 실행 파일·DB 접속 없음. 이력 조회는 프로그램·CRUD 매트릭스·미해결. 내보내기·영향도는 번들 14
 */
final class AnalyzeRoutes {

    record RunRequest(String path) {
    }

    private AnalyzeRoutes() {
    }

    static void register(Javalin app, JobManager jobs, Db db, LocalFiles files, Supplier<Optional<Profile>> active) {
        AnalyzeStore store = new AnalyzeStore(db);

        app.post("/api/analyze/run", ctx -> {
            RunRequest req = ctx.bodyAsClass(RunRequest.class);
            if (req.path() == null || req.path().isBlank()) {
                ctx.status(400).json(Map.of("message", "path 가 있어야 한다"));
                return;
            }
            files.check(req.path());
            if (!files.exists(req.path()).dir()) {
                ctx.status(404).json(Map.of("message", "폴더가 없다"));
                return;
            }
            Profile profile = active.get().orElse(null);
            Profile.Naming naming = profile == null ? null : profile.naming();
            String profileName = profile == null ? null : profile.name();
            Job job = jobs.submit("analyze", jc -> {
                AnalyzeRunner.Result r = AnalyzeRunner.run(req.path(), files, naming, jc);
                long runId = store.save(profileName, req.path(), r);
                files.remember(files.check(req.path()));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("runId", runId);
                result.put("files", r.files());
                result.put("skipped", r.skipped());
                result.put("truncated", r.truncated());
                result.put("statements", r.statements());
                result.put("tables", r.tables());
                result.put("programs", store.programs(runId));
                result.put("unresolved", r.unresolved());
                jc.emit(Job.EventName.RESULT, result);
                return result;
            });
            ctx.status(202).json(Map.of("jobId", job.id()));
        });

        app.get("/api/analyze/runs", ctx -> ctx.json(store.runs()));

        app.get("/api/analyze/runs/{id}/programs", ctx -> {
            Long id = runId(ctx, store);
            if (id != null) {
                ctx.json(store.programs(id));
            }
        });

        app.get("/api/analyze/runs/{id}/crud", ctx -> {
            Long id = runId(ctx, store);
            if (id != null) {
                ctx.json(store.crud(id));
            }
        });

        app.get("/api/analyze/runs/{id}/unresolved", ctx -> {
            Long id = runId(ctx, store);
            if (id != null) {
                ctx.json(store.unresolved(id));
            }
        });
    }

    /** 없는 실행이면 404 를 쓰고 null */
    private static Long runId(Context ctx, AnalyzeStore store) throws Exception {
        long id;
        try {
            id = Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("message", "id 가 수가 아니다"));
            return null;
        }
        if (store.run(id).isEmpty()) {
            ctx.status(404).json(Map.of("message", "실행이 없다"));
            return null;
        }
        return id;
    }
}
