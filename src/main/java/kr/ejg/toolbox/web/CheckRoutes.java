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
import kr.ejg.toolbox.core.check.CheckRunner;
import kr.ejg.toolbox.core.check.CheckStore;
import kr.ejg.toolbox.core.check.Finding;
import kr.ejg.toolbox.core.check.Rule;
import kr.ejg.toolbox.core.check.RuleSet;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.Job;
import kr.ejg.toolbox.core.job.JobManager;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * 코드 검사(5-4). {@code POST /api/check/run}(job) — 끝에 이력을 저장하고 결과 이벤트·작업 결과에 runId + 결과 표(발췌 포함, 메모리만).
 * 이력 조회는 발췌 없이. 규칙 목록은 활성 프로필 덮어쓰기를 적용한 상태. {@code changedOnly} 는 번들 12(5-6) — 지금은 400.
 */
final class CheckRoutes {

    record RunRequest(String path, String text, String lang, Boolean changedOnly, Map<String, Boolean> groups,
            Map<String, Object> ruleOverrides) {
    }

    private CheckRoutes() {
    }

    static void register(Javalin app, JobManager jobs, Db db, LocalFiles files, Supplier<Optional<Profile>> active) {
        CheckStore store = new CheckStore(db);

        app.get("/api/check/rules", ctx -> {
            Profile prof = active.get().orElse(null);
            RuleSet rules = load(prof, null, null);
            // 묶음을 다 켠 상태 — 규칙 하나의 켬(묶음과 따로)을 화면 체크박스에 보이려고
            Map<String, Boolean> allOn = new LinkedHashMap<>();
            rules.defs().forEach(d -> allOn.put(d.group(), true));
            Map<String, Boolean> ruleOn = new LinkedHashMap<>();
            load(prof, allOn, null).defs().forEach(d -> ruleOn.put(d.id(), d.on()));
            Map<String, Boolean> groupOn = prof == null || prof.codecheck() == null ? Map.of() : prof.codecheck().groups();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Rule.Def d : rules.defs()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", d.id());
                m.put("group", d.group());
                m.put("severity", d.severity());
                m.put("kind", d.kind());
                m.put("globs", d.globs());
                m.put("regex", d.regex());
                m.put("flags", d.flags());
                m.put("message", d.message());
                m.put("params", d.params());
                m.put("enabled", d.on());
                m.put("groupEnabled", !Boolean.FALSE.equals(groupOn.get(d.group())));
                m.put("ruleEnabled", ruleOn.get(d.id()));
                out.add(m);
            }
            ctx.json(out);
        });

        app.post("/api/check/run", ctx -> {
            RunRequest req = ctx.bodyAsClass(RunRequest.class);
            if (Boolean.TRUE.equals(req.changedOnly())) {
                ctx.status(400).json(Map.of("message", "변경분만은 번들 12(svn·git) 에서 연다"));
                return;
            }
            boolean hasText = req.text() != null && !req.text().isBlank();
            boolean hasPath = req.path() != null && !req.path().isBlank();
            if (hasText == hasPath) {
                ctx.status(400).json(Map.of("message", "path 또는 text 하나만"));
                return;
            }
            Profile profile = active.get().orElse(null);
            RuleSet rules;
            try {
                rules = load(profile, req.groups(), req.ruleOverrides());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return;
            }
            if (hasPath) {
                files.check(req.path());
                if (!files.exists(req.path()).dir()) {
                    ctx.status(404).json(Map.of("message", "폴더가 없다"));
                    return;
                }
            }
            List<String> groups = rules.defs().stream().filter(Rule.Def::on).map(Rule.Def::group).distinct().toList();
            String profileName = profile == null ? null : profile.name();
            Job job = jobs.submit("check", jc -> {
                CheckRunner.RunResult r = hasPath ? CheckRunner.run(req.path(), rules, files, jc) : CheckRunner.runText(req.text(), req.lang(), rules);
                long runId = store.save(profileName, hasPath ? req.path() : "(붙여넣기)", false, groups, r.findings());
                if (hasPath) {
                    files.remember(files.check(req.path()));
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("runId", runId);
                result.put("files", r.files());
                result.put("skipped", r.skipped());
                result.put("parseErrors", r.parseErrors());
                result.put("truncated", r.truncated());
                result.put("findings", r.findings());
                jc.emit(Job.EventName.RESULT, result);
                return result;
            });
            ctx.status(202).json(Map.of("jobId", job.id()));
        });

        app.get("/api/check/runs", ctx -> ctx.json(store.runs()));

        app.get("/api/check/runs/{id}", ctx -> {
            Long id = id(ctx, ctx.pathParam("id"));
            if (id == null) {
                return;
            }
            Optional<CheckStore.RunInfo> run = store.run(id);
            if (run.isEmpty()) {
                ctx.status(404).json(Map.of("message", "검사 이력이 없다: " + id));
                return;
            }
            ctx.json(Map.of("run", run.get(), "findings", store.findings(id)));
        });

        // 5-5 결과 표 xlsx — 이력에서 만든다(원문 칸 없음). 행 수 상한이 있는 TableXlsx 대신 XlsxWriter
        app.post("/api/check/runs/{id}/export", ctx -> {
            Long id = id(ctx, ctx.pathParam("id"));
            if (id == null) {
                return;
            }
            if (store.run(id).isEmpty()) {
                ctx.status(404).json(Map.of("message", "검사 이력이 없다: " + id));
                return;
            }
            Profile p = active.get().orElse(null);
            Map<String, String> messages = new LinkedHashMap<>();
            load(p, null, null).defs().forEach(d -> messages.put(d.id(), d.message()));
            List<List<Object>> rows = new ArrayList<>();
            for (Finding f : store.findings(id)) {
                rows.add(java.util.Arrays.asList(f.file(), f.line(), f.group(), f.rule(), f.severity(), messages.get(f.rule())));
            }
            String base = p != null && p.output() != null && p.output().dir() != null ? p.output().dir() : "out";
            Path file = Path.of(base, p == null ? "default" : p.name(),
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")),
                    "코드검사-" + id + ".xlsx").toAbsolutePath();
            java.nio.file.Files.createDirectories(file.getParent());
            List<kr.ejg.toolbox.core.sqlrun.ResultTable.Col> cols = new ArrayList<>();
            for (String c : new String[] {"파일", "줄", "묶음", "규칙", "등급", "설명"}) {
                cols.add(new kr.ejg.toolbox.core.sqlrun.ResultTable.Col(c, c.equals("줄") ? "INTEGER" : "VARCHAR"));
            }
            kr.ejg.toolbox.core.report.XlsxWriter.write(new kr.ejg.toolbox.core.sqlrun.ResultTable(cols, rows, false, -1, 0), file);
            ctx.json(Map.of("path", file.toString(), "rows", rows.size()));
        });

        app.get("/api/check/runs/{id}/compare", ctx -> {
            Long id = id(ctx, ctx.pathParam("id"));
            if (id == null) {
                return;
            }
            if (store.run(id).isEmpty()) {
                ctx.status(404).json(Map.of("message", "검사 이력이 없다: " + id));
                return;
            }
            String p = ctx.queryParam("prev");
            Long prev = p == null || p.isBlank() ? store.previous(id).orElse(null) : id(ctx, p);
            if (prev == null) {
                if (p == null || p.isBlank()) {
                    ctx.status(404).json(Map.of("message", "같은 경로의 앞 실행이 없다"));
                }
                return;
            }
            if (store.run(prev).isEmpty()) {
                ctx.status(404).json(Map.of("message", "검사 이력이 없다: " + prev));
                return;
            }
            ctx.json(store.compare(id, prev));
        });
    }

    private static Long id(Context ctx, String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("message", "id 는 수: " + raw));
            return null;
        }
    }

    /** 프로필 codecheck 위에 요청의 묶음·규칙 덮어쓰기를 얹는다(저장은 화면이 PUT /profiles 로) */
    static RuleSet load(Profile p, Map<String, Boolean> groups, Map<String, Object> overrides) {
        Profile.CodeCheck cc = p == null || p.codecheck() == null ? new Profile.CodeCheck(null, null, null) : p.codecheck();
        Map<String, Boolean> g = new LinkedHashMap<>(cc.groups());
        if (groups != null) {
            g.putAll(groups);
        }
        Map<String, Object> r = new LinkedHashMap<>(cc.rules());
        if (overrides != null) {
            r.putAll(overrides);
        }
        Profile.CodeCheck merged = new Profile.CodeCheck(g, r, cc.customRules());
        Profile q = p == null
                ? new Profile(null, null, null, null, null, null, null, merged, "egov35", null, null, null) // 프로필 없음 = RuleSet.builtin 과 같게 eGov 규칙 켬
                : new Profile(p.name(), p.project(), p.connections(), p.defaultConnection(), p.scope(), p.deliverable(), p.naming(), merged,
                        p.framework(), p.output(), p.generator(), p.logicalName());
        return RuleSet.load(q, Path.of(""));
    }
}
