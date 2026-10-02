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
import kr.ejg.toolbox.core.vcs.WorkingCopy;

/**
 * 코드 검사(5-4). {@code POST /api/check/run}(job) — 끝에 이력을 저장하고 결과 이벤트·작업 결과에 runId + 결과 표(발췌 포함, 메모리만).
 * 이력 조회는 발췌 없이. 규칙 목록은 활성 프로필 덮어쓰기를 적용한 상태. {@code changedOnly} 는 git·svn 작업 사본의 변경분만(5-6b) —
 * VCS 상태는 {@code GET /api/check/vcs}.
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
            boolean changedOnly = Boolean.TRUE.equals(req.changedOnly());
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
            // 5-6b 변경분만 — 폴더 검사이고 git·svn 작업 사본이며 명령이 있을 때
            if (changedOnly) {
                WorkingCopy.Info vcs = hasPath ? WorkingCopy.detect(files.check(req.path())) : null;
                if (vcs == null || vcs.kind() == WorkingCopy.Kind.NONE || !vcs.available()) {
                    ctx.status(400).json(Map.of("message", vcs == null ? "변경분만은 폴더 검사에서만" : vcs.reason()));
                    return;
                }
            }
            List<String> groups = rules.defs().stream().filter(Rule.Def::on).map(Rule.Def::group).distinct().toList();
            String profileName = profile == null ? null : profile.name();
            Job job = jobs.submit("check", jc -> {
                CheckRunner.RunResult r;
                if (!hasPath) {
                    r = CheckRunner.runText(req.text(), req.lang(), rules);
                } else if (changedOnly) {
                    WorkingCopy.Changes changes = WorkingCopy.changed(files.check(req.path()));
                    r = CheckRunner.run(req.path(), rules, files, jc, changes::contains);
                } else {
                    r = CheckRunner.run(req.path(), rules, files, jc);
                }
                long runId = store.save(profileName, hasPath ? req.path() : "(붙여넣기)", changedOnly, groups, r.findings());
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

        // 5-6b 폴더의 형상 관리 상태 — 화면 「변경분만」 체크박스가 켤지 정한다. 변경 수만(경로는 안 싣는다)
        app.get("/api/check/vcs", ctx -> {
            String p = ctx.queryParam("path");
            if (p == null || p.isBlank()) {
                ctx.status(400).json(Map.of("message", "path 가 있어야 한다"));
                return;
            }
            java.nio.file.Path root = files.check(p);
            if (!files.exists(p).dir()) {
                ctx.status(404).json(Map.of("message", "폴더가 없다"));
                return;
            }
            WorkingCopy.Info info = WorkingCopy.detect(root);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("kind", info.kind().name().toLowerCase(java.util.Locale.ROOT));
            out.put("available", info.available());
            out.put("reason", info.reason());
            Integer changed = null;
            if (info.available()) {
                try {
                    changed = WorkingCopy.changed(root).size();
                } catch (IllegalStateException e) {
                    out.put("available", false);
                    out.put("reason", e.getMessage());
                }
            }
            out.put("changed", changed);
            // 5-8 배포 목록 칸의 고를 거리 — git 만(svn 은 서버를 자동으로 안 탄다). 응답에만 싣고 저장·로그 안 함
            List<WorkingCopy.Rev> recent = List.of();
            if (info.available()) {
                try {
                    recent = WorkingCopy.recent(root, 20);
                } catch (IllegalStateException e) {
                    recent = List.of(); // 커밋이 아직 없는 저장소
                }
            }
            out.put("recent", recent);
            ctx.json(out);
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
            Path file = xlsx(p, "코드검사-" + id + ".xlsx", new String[] {"파일", "줄", "묶음", "규칙", "등급", "설명"}, "줄", rows);
            ctx.json(Map.of("path", file.toString(), "rows", rows.size()));
        });

        // 5-8 배포 파일 목록 — git 커밋·svn 리비전 구간의 바뀐 파일. 동기(Cli 30초 상한). svn 은 저장소 서버에 묻는다(규칙 1 예외)
        app.post("/api/check/deploy-list", ctx -> {
            DeployRequest req = ctx.bodyAsClass(DeployRequest.class);
            if (req.path() == null || req.path().isBlank()) {
                ctx.status(400).json(Map.of("message", "path 가 있어야 한다"));
                return;
            }
            Path root = files.check(req.path());
            if (!files.exists(req.path()).dir()) {
                ctx.status(404).json(Map.of("message", "폴더가 없다"));
                return;
            }
            WorkingCopy.Info info = WorkingCopy.detect(root);
            if (info.kind() == WorkingCopy.Kind.NONE || !info.available()) {
                ctx.status(400).json(Map.of("message", info.reason()));
                return;
            }
            List<WorkingCopy.Change> changes;
            try {
                changes = WorkingCopy.diff(root, req.from(), req.to());
            } catch (IllegalArgumentException | IllegalStateException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return;
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("A", 0);
            counts.put("M", 0);
            counts.put("D", 0);
            List<Map<String, Object>> rows = new ArrayList<>();
            List<List<Object>> sheet = new ArrayList<>();
            for (WorkingCopy.Change c : changes) {
                counts.merge(c.status(), 1, Integer::sum);
                Path f = root.resolve(c.rel());
                Long size = !c.status().equals("D") && java.nio.file.Files.isRegularFile(f) ? java.nio.file.Files.size(f) : null;
                String name = c.rel().substring(c.rel().lastIndexOf('/') + 1);
                String ext = name.lastIndexOf('.') > 0 ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT) : "";
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("file", c.rel());
                row.put("status", c.status());
                row.put("ext", ext);
                row.put("size", size);
                rows.add(row);
                sheet.add(java.util.Arrays.asList(rows.size(), c.rel(), STATUS.get(c.status()), ext, size));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("kind", info.kind().name().toLowerCase(java.util.Locale.ROOT));
            out.put("from", req.from());
            out.put("to", req.to());
            out.put("counts", counts);
            out.put("rows", rows);
            out.put("xlsxPath", Boolean.TRUE.equals(req.xlsx())
                    ? xlsx(active.get().orElse(null), "배포목록.xlsx", new String[] {"순번", "파일", "상태", "확장자", "크기"}, "순번·크기", sheet).toString()
                    : null);
            ctx.json(out);
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

    record DeployRequest(String path, String from, String to, Boolean xlsx) {
    }

    private static final Map<String, String> STATUS = Map.of("A", "추가", "M", "수정", "D", "삭제");

    /** {@code out/<프로필>/<시각>/<이름>} 에 값 표 xlsx(XlsxWriter — 행 상한 없음). {@code numeric} 에 든 열 이름은 수 칸 */
    private static Path xlsx(Profile p, String name, String[] headers, String numeric, List<List<Object>> rows) throws java.io.IOException {
        String base = p != null && p.output() != null && p.output().dir() != null ? p.output().dir() : "out";
        Path file = Path.of(base, p == null ? "default" : p.name(),
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")), name).toAbsolutePath();
        List<kr.ejg.toolbox.core.sqlrun.ResultTable.Col> cols = new ArrayList<>();
        for (String h : headers) {
            cols.add(new kr.ejg.toolbox.core.sqlrun.ResultTable.Col(h, numeric.contains(h) ? "INTEGER" : "VARCHAR"));
        }
        kr.ejg.toolbox.core.report.XlsxWriter.write(new kr.ejg.toolbox.core.sqlrun.ResultTable(cols, rows, false, -1, 0), file);
        return file;
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
