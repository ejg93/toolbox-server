package kr.ejg.toolbox.web;

import static kr.ejg.toolbox.web.Outputs.num;
import static kr.ejg.toolbox.web.Outputs.text;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.check.Analyzers;
import kr.ejg.toolbox.core.check.CheckRunner;
import kr.ejg.toolbox.core.check.CheckStore;
import kr.ejg.toolbox.core.check.Finding;
import kr.ejg.toolbox.core.check.Rule;
import kr.ejg.toolbox.core.check.RuleSet;
import kr.ejg.toolbox.core.check.SpotBugsRun;
import kr.ejg.toolbox.core.check.Trend;
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

    /** classesDir·libDir — 묶음 ㅅ 외부 분석기(5-38). 비면 프로필 값 */
    record RunRequest(String path, String text, String lang, Boolean changedOnly, Map<String, Boolean> groups,
            Map<String, Object> ruleOverrides, String classesDir, String libDir) {
    }

    private static final Map<String, String> STATUS = Map.of("A", "추가", "M", "수정", "D", "삭제");

    private CheckRoutes() {
    }

    /** @param dataDir 도구 data 폴더 — FindSecBugs 플러그인 파일을 꺼내 둔다(5-38) */
    static void register(Javalin app, JobManager jobs, Db db, LocalFiles files, Supplier<Optional<Profile>> active, Path dataDir) {
        CheckStore store = new CheckStore(db);

        // 5-38 — 화면 「클래스」 칸 미리 채움. 규칙 목록 응답 꼴은 그대로 두고 따로
        app.get("/api/check/defaults", ctx -> {
            Profile.CodeCheck cc = active.get().map(Profile::codecheck).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("classesDir", cc == null ? null : cc.classesDir());
            m.put("libDir", cc == null ? null : cc.libDir());
            ctx.json(m);
        });

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
                m.put("fix", d.fix()); // 5-25a — 권장 수정 · 근거
                m.put("ref", d.ref());
                m.put("params", d.params());
                m.put("enabled", d.on());
                m.put("fileLevel", d.fileLevel());
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
                String why = hasPath ? changedOnlyError(files, req.path()) : "변경분만은 폴더 검사에서만";
                if (why != null) {
                    ctx.status(400).json(Map.of("message", why));
                    return;
                }
            }
            // 5-38 묶음 ㅅ — 클래스 폴더가 있을 때만 돈다(안 줬으면 파일 규칙만). 화면이 준 폴더가 없으면 400,
            // 프로필 값이 없으면(빌드 전·다른 PC) 400 이 아니라 묶음 ㅅ 만 건너뛰고 note — 그 프로필의 폴더 검사가 다 막히지 않게(마무리 리뷰)
            Profile.CodeCheck pc = profile == null ? null : profile.codecheck();
            String classesDir = hasPath && !rules.external().isEmpty() ? folder(req.classesDir(), pc == null ? null : pc.classesDir(), profile) : null;
            String libDir = classesDir == null ? null : folder(req.libDir(), pc == null ? null : pc.libDir(), profile);
            String skip = null;
            if (classesDir != null && !files.exists(classesDir).dir()) {
                if (req.classesDir() != null && !req.classesDir().isBlank()) {
                    ctx.status(400).json(Map.of("message", "클래스 폴더가 없다 — " + classesDir));
                    return;
                }
                skip = "프로필 classesDir 가 없다(빌드 전?) — " + classesDir + " · 묶음 ㅅ 건너뜀";
                classesDir = null;
                libDir = null;
            }
            if (libDir != null && !files.exists(libDir).dir()) {
                if (req.libDir() != null && !req.libDir().isBlank()) {
                    ctx.status(400).json(Map.of("message", "lib 폴더가 없다 — " + libDir));
                    return;
                }
                skip = "프로필 libDir 가 없다 — " + libDir + " · aux jar 없이";
                libDir = null;
            }
            String extClasses = classesDir;
            String extLib = libDir;
            String extSkip = skip;
            boolean sec = rules.external().stream().anyMatch(d -> d.id().equals(SpotBugsRun.FINDSECBUGS));
            List<String> groups = rules.defs().stream().filter(Rule.Def::on).map(Rule.Def::group).distinct().toList();
            String profileName = profile == null ? null : profile.name();
            // 5-26 — 프로필 codecheck.exclude(폴더 검사만)
            List<String> exclude = pc == null ? List.of() : pc.exclude();
            Job job = jobs.submit("check", jc -> {
                Path plugin = null;
                String note = extSkip;
                if (extClasses != null && sec) {
                    try {
                        plugin = Analyzers.findSecBugsJar(dataDir);
                    } catch (RuntimeException e) { // 꺼내기 실패 — 코어만 돌고 파일 규칙 결과는 남긴다
                        note = "FindSecBugs 플러그인을 못 꺼냈다 — " + e.getMessage();
                    }
                }
                CheckRunner.External ext = extClasses == null ? null : new CheckRunner.External(extClasses, extLib, plugin);
                CheckRunner.RunResult r;
                if (!hasPath) {
                    r = CheckRunner.runText(req.text(), req.lang(), rules);
                } else if (changedOnly) {
                    WorkingCopy.Changes changes = WorkingCopy.changed(files.check(req.path()));
                    r = CheckRunner.run(req.path(), rules, files, jc, changes::contains, exclude, ext);
                } else {
                    r = CheckRunner.run(req.path(), rules, files, jc, null, exclude, ext);
                }
                long runId = store.save(profileName, hasPath ? req.path() : "(붙여넣기)", changedOnly, groups, r.findings(), r.suppressed(),
                        r.files(), r.lines(), rules.fingerprint()); // 5-36 추이의 분모·규칙 지문
                if (hasPath) {
                    files.remember(files.check(req.path()));
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("runId", runId);
                result.put("files", r.files());
                result.put("skipped", r.skipped());
                result.put("parseErrors", r.parseErrors());
                result.put("truncated", r.truncated());
                result.put("suppressed", r.suppressed());
                result.put("lines", r.lines());
                result.put("external", r.external()); // 5-38 외부 분석기 지적 수 · 못 돈 까닭
                result.put("externalNote", note == null ? r.externalNote() : r.externalNote() == null ? note : note + " · " + r.externalNote()); // 둘 다 있으면 잇는다(PR #85 리뷰)
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
            Map<String, Rule.Def> defs = new LinkedHashMap<>();
            java.util.Set<String> fileRules = new java.util.HashSet<>();
            load(p, null, null).defs().forEach(d -> {
                defs.put(d.id(), d);
                if (d.fileLevel()) {
                    fileRules.add(d.id());
                }
            });
            List<List<Object>> rows = new ArrayList<>();
            for (Finding f : store.findings(id)) {
                // 5-23 — 파일 단위 지적은 줄 칸에 「파일」(저장은 줄 1)
                Object line = fileRules.contains(f.rule()) ? "파일" : (Object) f.line();
                Rule.Def d = defs.get(f.rule());
                // 5-25a — 권장 수정·근거는 끝 열(규칙에 없으면 빈칸)
                rows.add(java.util.Arrays.asList(f.file(), line, f.group(), f.rule(), f.severity(), d == null ? null : d.message(),
                        d == null || d.fix() == null ? "" : d.fix(), d == null || d.ref() == null ? "" : d.ref()));
            }
            Path file = Outputs.xlsx(p, "코드검사-" + id + ".xlsx", List.of(text("파일"), num("줄"), text("묶음"), text("규칙"), text("등급"), text("설명"),
                    text("권장 수정"), text("근거")), rows);
            ctx.json(Map.of("path", file.toString(), "rows", rows.size()));
        });

        // 5-36 추이 — 활성 프로필·같은 폴더의 전체 검사 이력. 점수 없이 묶음×등급 건수·고쳐짐/새로 생김·폴더별
        app.get("/api/check/trend", ctx -> {
            String path = ctx.queryParam("path");
            if (path == null || path.isBlank()) {
                ctx.status(400).json(Map.of("message", "path 가 없다"));
                return;
            }
            String prof = active.get().map(Profile::name).orElse(null);
            ctx.json(Trend.of(store, prof, path, intParam(ctx.queryParam("depth"), Trend.DEFAULT_DEPTH),
                    intParam(ctx.queryParam("limit"), Trend.DEFAULT_LIMIT)));
        });

        app.post("/api/check/trend/export", ctx -> {
            TrendExport req = ctx.bodyAsClass(TrendExport.class);
            if (req.path() == null || req.path().isBlank()) {
                ctx.status(400).json(Map.of("message", "path 가 없다"));
                return;
            }
            Profile p = active.get().orElse(null);
            Trend.Report r = Trend.of(store, p == null ? null : p.name(), req.path(), req.depth() == null ? Trend.DEFAULT_DEPTH : req.depth(),
                    req.limit() == null ? Trend.DEFAULT_LIMIT : req.limit());
            java.util.Set<String> groups = new java.util.TreeSet<>();
            r.points().forEach(x -> groups.addAll(x.byGroup().keySet()));
            List<kr.ejg.toolbox.core.sqlrun.ResultTable.Col> cols = new ArrayList<>(List.of(num("실행 #"), text("시각"), num("파일"), num("줄"),
                    num("전체"), num("error"), num("warn"), num("info"), num("억제"), num("고쳐짐"), num("새로 생김"), text("규칙 지문"),
                    text("규칙 바뀜")));
            groups.forEach(g -> cols.add(num(g)));
            List<List<Object>> rows = new ArrayList<>();
            for (int i = r.points().size() - 1; i >= 0; i--) { // 화면과 같게 최근이 위
                Trend.Point x = r.points().get(i);
                List<Object> row = new ArrayList<>(java.util.Arrays.asList(x.runId(), x.at().toString().replace('T', ' '), x.files(), x.lines(),
                        x.total(), x.bySeverity().getOrDefault("error", 0), x.bySeverity().getOrDefault("warn", 0),
                        x.bySeverity().getOrDefault("info", 0), x.suppressed(), x.fixed() == null ? "" : x.fixed(),
                        x.added() == null ? "" : x.added(), x.rulesHash() == null ? "" : x.rulesHash(), x.rulesChanged() ? "다름" : ""));
                groups.forEach(g -> row.add(x.byGroup().getOrDefault(g, 0)));
                rows.add(row);
            }
            List<List<Object>> frows = new ArrayList<>();
            // 직전 실행이 없거나 규칙이 바뀐 점이면 직전·차이는 빈칸(화면과 같게)
            boolean cmp = r.points().size() > 1 && !r.points().get(r.points().size() - 1).rulesChanged();
            r.folders().forEach(f -> frows.add(java.util.Arrays.asList(f.folder(), f.now(), cmp ? f.before() : "", cmp ? f.now() - f.before() : "")));
            LinkedHashMap<String, kr.ejg.toolbox.core.sqlrun.ResultTable> sheets = new LinkedHashMap<>();
            sheets.put("추이", Outputs.table(cols, rows));
            sheets.put("폴더", Outputs.table(List.of(text("폴더"), num("최근"), num("직전"), num("차이")), frows));
            Path file = Outputs.xlsx(Outputs.dir(p), "코드검사-추이.xlsx", sheets);
            ctx.json(Map.of("path", file.toString(), "rows", rows.size(), "folders", frows.size()));
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
                // root 밖 정책은 WorkingCopy 한 곳(글자·링크 둘 다 — 5-11)
                Long size = c.status().equals("D") ? null : WorkingCopy.sizeInside(root, c.rel());
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
                    ? Outputs.xlsx(active.get().orElse(null), "배포목록.xlsx",
                            List.of(num("순번"), text("파일"), text("상태"), text("확장자"), num("크기")), sheet).toString()
                    : null);
            ctx.json(out);
        });
    }

    record TrendExport(String path, Integer depth, Integer limit) {
    }

    /** 수 쿼리 값 — 없거나 수가 아니면 기본값(범위는 Trend 가 자른다) */
    private static int intParam(String raw, int dflt) {
        try {
            return raw == null || raw.isBlank() ? dflt : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    record DeployRequest(String path, String from, String to, Boolean xlsx) {
    }

    private static Long id(Context ctx, String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("message", "id 는 수: " + raw));
            return null;
        }
    }

    /** 5-6b 변경분만 — git·svn 작업 사본이고 명령이 있으면 null, 아니면 400 에 실을 글. 코드 정리(5-34d)도 쓴다 */
    static String changedOnlyError(LocalFiles files, String path) {
        WorkingCopy.Info vcs = WorkingCopy.detect(files.check(path));
        return vcs.kind() == WorkingCopy.Kind.NONE || !vcs.available() ? vcs.reason() : null;
    }

    /** 5-38 — 화면 값, 비면 프로필 값. 상대 경로는 project.root 기준. 둘 다 없으면 null */
    static String folder(String asked, String profiled, Profile p) {
        String v = asked != null && !asked.isBlank() ? asked.strip() : profiled != null && !profiled.isBlank() ? profiled.strip() : null;
        if (v == null) {
            return null;
        }
        String root = p == null || p.project() == null ? null : p.project().root();
        return Path.of(v).isAbsolute() || root == null || root.isBlank() ? v : Path.of(root).resolve(v).normalize().toString();
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
        Profile.CodeCheck merged = new Profile.CodeCheck(g, r, cc.customRules(), cc.exclude(), cc.classesDir(), cc.libDir());
        Profile q = p == null
                ? new Profile(null, null, null, null, null, null, null, merged, "egov35", null, null, null) // 프로필 없음 = RuleSet.builtin 과 같게 eGov 규칙 켬
                : new Profile(p.name(), p.project(), p.connections(), p.defaultConnection(), p.scope(), p.deliverable(), p.naming(), merged,
                        p.framework(), p.output(), p.generator(), p.logicalName());
        return RuleSet.load(q, Path.of(""));
    }
}
