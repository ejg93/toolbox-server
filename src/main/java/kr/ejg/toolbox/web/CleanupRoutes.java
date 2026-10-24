package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.check.Cleanup;
import kr.ejg.toolbox.core.check.CleanupRunner;
import kr.ejg.toolbox.core.check.ProjectStack;
import kr.ejg.toolbox.core.check.Rule;
import kr.ejg.toolbox.core.check.RuleSet;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.Job;
import kr.ejg.toolbox.core.job.JobManager;
import kr.ejg.toolbox.core.profile.Profile;
import kr.ejg.toolbox.core.vcs.WorkingCopy;

/**
 * 코드 정리(5-34d, 설계 30). 규칙은 코드 검사와 같은 프로필 codecheck. {@code POST /api/cleanup/preview}(job, 쓰기 없음) →
 * {@code POST /api/cleanup/apply {path, rules, files, stamp?, outDir?}}(job, 다시 읽어 hash·outHash 가 같은 파일만 결과 폴더 — 기본 `out/<프로필>/<stamp>/cleanup`, outDir 이면 그 폴더 — 에 같은 상대 경로로 쓴다. 원본은 안 고친다, 9-3b). 이력은 H2 에 안 남긴다 — 결과 글 파일 하나.
 */
final class CleanupRoutes {

    private CleanupRoutes() {
    }

    record PreviewRequest(String path, List<String> rules, Boolean changedOnly) {
    }

    record ApplyRequest(String path, List<String> rules, List<CleanupRunner.FileRef> files, String stamp, String outDir) {
    }

    static void register(Javalin app, JobManager jobs, LocalFiles files, Supplier<Optional<Profile>> active) {
        app.get("/api/cleanup/rules", ctx -> {
            Profile p = active.get().orElse(null);
            RuleSet rules = CheckRoutes.load(p, null, null);
            String le = lineEnding(p);
            List<Map<String, Object>> out = new ArrayList<>();
            for (String id : Cleanup.RULES) {
                Rule.Def d = rules.defs().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
                if (d == null) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", id);
                m.put("group", d.group());
                m.put("severity", d.severity());
                m.put("message", d.message());
                m.put("fix", d.fix());
                m.put("enabled", d.on());
                m.put("expected", "file.lineEnding".equals(id) ? le : null);
                out.add(m);
            }
            ctx.json(out);
        });

        app.post("/api/cleanup/preview", ctx -> {
            PreviewRequest req = ctx.bodyAsClass(PreviewRequest.class);
            if (!folderOk(ctx, files, req.path())) {
                return;
            }
            boolean changedOnly = Boolean.TRUE.equals(req.changedOnly());
            if (changedOnly) {
                String why = CheckRoutes.changedOnlyError(files, req.path());
                if (why != null) {
                    ctx.status(400).json(Map.of("message", why));
                    return;
                }
            }
            Profile p = active.get().orElse(null);
            ProjectStack.Stack stack = ProjectStack.detect(files.check(req.path())); // 5-39 — 빌드 결과 폴더 제외·framework 를 코드 검사와 같게
            RuleSet rules = CheckRoutes.load(p, null, null, CheckRoutes.framework(p, stack));
            List<String> wanted = wanted(req.rules());
            List<String> exclude = CheckRoutes.excludes(p == null ? null : p.codecheck(), stack);
            Job job = jobs.submit("cleanup", jc -> {
                WorkingCopy.Changes changes = changedOnly ? WorkingCopy.changed(files.check(req.path())) : null;
                CleanupRunner.Preview pv = CleanupRunner.preview(req.path(), rules, wanted, lineEnding(p), files, jc,
                        changes == null ? null : changes::contains, exclude);
                files.remember(files.check(req.path()));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("files", pv.files());
                result.put("skipped", pv.skipped());
                result.put("changed", pv.changed());
                result.put("truncated", pv.truncated());
                result.put("excluded", stack.excludes()); // 5-39 — 화면 요약 「제외 …」
                result.put("rows", pv.rows());
                jc.emit(Job.EventName.RESULT, result);
                return result;
            });
            ctx.status(202).json(Map.of("jobId", job.id()));
        });

        app.post("/api/cleanup/apply", ctx -> {
            ApplyRequest req = ctx.bodyAsClass(ApplyRequest.class);
            if (!folderOk(ctx, files, req.path())) {
                return;
            }
            if (req.files() == null || req.files().isEmpty()) {
                ctx.status(400).json(Map.of("message", "쓸 파일이 없다"));
                return;
            }
            Profile p = active.get().orElse(null);
            ProjectStack.Stack stack = ProjectStack.detect(files.check(req.path())); // 미리보기와 같은 판별 — 범위 재확인도 같은 제외
            RuleSet rules = CheckRoutes.load(p, null, null, CheckRoutes.framework(p, stack));
            List<String> wanted = wanted(req.rules());
            List<String> exclude = CheckRoutes.excludes(p == null ? null : p.codecheck(), stack);
            String stamp = FsRoutes.stamp(req.stamp());
            boolean own = req.outDir() != null && !req.outDir().isBlank();
            Path outRoot = files.resultRoot(req.outDir(), FsRoutes.outRoot(active, stamp).resolve("cleanup"), files.check(req.path()), "cleanup", stamp);
            Path reportDir = own ? outRoot : FsRoutes.outRoot(active, stamp); // 고른 폴더면 결과 글도 그 폴더에
            Job job = jobs.submit("cleanup-apply", jc -> {
                CleanupRunner.Applied ap = CleanupRunner.apply(req.path(), rules, wanted, lineEnding(p), files, jc, req.files(), exclude, outRoot, reportDir);
                files.remember(files.check(req.path()));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("written", ap.written());
                result.put("failed", ap.failed());
                result.put("stale", ap.stale());
                result.put("stamp", stamp);
                result.put("outRoot", outRoot.toString());
                result.put("report", ap.report().toString());
                result.put("rows", ap.rows());
                jc.emit(Job.EventName.RESULT, result);
                return result;
            });
            ctx.status(202).json(Map.of("jobId", job.id()));
        });
    }

    /** 폴더 검증 — 경로 거절은 LocalFiles.Refused(FsRoutes 의 예외 처리), 없으면 404 */
    private static boolean folderOk(Context ctx, LocalFiles files, String path) {
        if (path == null || path.isBlank()) {
            ctx.status(400).json(Map.of("message", "path 가 있어야 한다"));
            return false;
        }
        files.check(path);
        if (!files.exists(path).dir()) {
            ctx.status(404).json(Map.of("message", "폴더가 없다"));
            return false;
        }
        return true;
    }

    /** 고른 규칙 ∩ 아홉 — 비면 아홉 전부 */
    static List<String> wanted(List<String> asked) {
        if (asked == null || asked.isEmpty()) {
            return Cleanup.RULES;
        }
        return Cleanup.RULES.stream().filter(asked::contains).toList();
    }

    private static String lineEnding(Profile p) {
        return p == null || p.project() == null ? null : p.project().lineEnding();
    }
}
