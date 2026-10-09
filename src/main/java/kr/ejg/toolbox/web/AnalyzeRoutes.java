package kr.ejg.toolbox.web;

import static kr.ejg.toolbox.web.Outputs.text;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.analyze.AnalyzeRunner;
import kr.ejg.toolbox.core.analyze.AnalyzeStore;
import kr.ejg.toolbox.core.analyze.Consistency;
import kr.ejg.toolbox.core.analyze.CrudViews;
import kr.ejg.toolbox.core.analyze.Unresolved;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.Job;
import kr.ejg.toolbox.core.job.JobManager;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.profile.Profile;
import kr.ejg.toolbox.core.sqlrun.ResultTable;

/**
 * 프로그램 분석(6-4). {@code POST /api/analyze/run}(job) — 폴더 소스를 파싱해 프로그램·CRUD 를 H2 에 저장하고 결과 이벤트에 runId·수·프로그램.
 * 소스 파싱만 — 실행 파일·DB 접속 없음. 이력 조회는 프로그램·CRUD 매트릭스·미해결·영향도(6-6 — 표 → 프로그램 → JSP). 미해결 종류 글(6-22).
 * 내려받기는 xlsx 하나 — 시트 프로그램목록·CRUD목록·CRUD모듈·미해결·정합성(스냅샷을 골랐을 때만)(6-7·6-24)
 */
final class AnalyzeRoutes {

    record RunRequest(String path) {
    }

    /** Excel 열 상한 16,384 — CRUD모듈 시트의 모듈 열(앞 한 열은 표). 넘으면 POI 가 터지기 전에 400 */
    static final int MAX_MODULES = 16_000;

    /** snapshotId 가 있으면 정합성 시트를 더한다(6-24) */
    record ExportRequest(String format, Long snapshotId) {
    }

    /** GET /runs/{id}/crud — 넓은 격자 재료(tables·rows) + 6-21 보기 둘 */
    record CrudResponse(List<String> tables, List<AnalyzeStore.ProgramRow> rows, List<CrudViews.LongRow> longRows,
            CrudViews.ModuleMatrix moduleMatrix) {
    }

    private AnalyzeRoutes() {
    }

    static void register(Javalin app, JobManager jobs, Db db, LocalFiles files, Supplier<Optional<Profile>> active) {
        AnalyzeStore store = new AnalyzeStore(db);
        SnapshotStore snapshots = new SnapshotStore(db);

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

        // 6-22 미해결 종류 글 — KINDS 순서대로 {kind, name, meaning, fix}. 화면 칩·뜻 줄이 쓴다
        app.get("/api/analyze/unresolved-kinds", ctx -> {
            List<Map<String, String>> out = new ArrayList<>();
            Unresolved.KINDS.forEach((k, v) -> {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("kind", k);
                m.put("name", v.name());
                m.put("meaning", v.meaning());
                m.put("fix", v.fix());
                out.add(m);
            });
            ctx.json(out);
        });

        app.get("/api/analyze/runs/{id}/programs", ctx -> {
            Long id = runId(ctx, store);
            if (id != null) {
                ctx.json(store.programs(id));
            }
        });

        // 6-21 — tables·rows(프로그램 목록·영향도·산출물 18 이 쓴다)에 세로 목록·모듈 매트릭스를 같이 싣는다. 계산은 CrudViews 한 곳
        app.get("/api/analyze/runs/{id}/crud", ctx -> {
            Long id = runId(ctx, store);
            if (id != null) {
                AnalyzeStore.Matrix m = store.crud(id);
                ctx.json(new CrudResponse(m.tables(), m.rows(), CrudViews.longRows(m.rows()), CrudViews.moduleMatrix(m.rows())));
            }
        });

        // 6-6 영향도 — 표를 쓰는 프로그램과 그 URL 을 부르는 JSP. 그 실행에 없는 표면 빈 목록
        app.get("/api/analyze/runs/{id}/impact", ctx -> {
            String table = ctx.queryParam("table");
            if (table == null || table.isBlank()) {
                ctx.status(400).json(Map.of("message", "table 이 있어야 한다"));
                return;
            }
            Long id = runId(ctx, store);
            if (id != null) {
                ctx.json(store.impact(id, table));
            }
        });

        // 6-24 xlsx 한 파일 — 시트 프로그램목록 · CRUD목록 · CRUD모듈 · 미해결 · 정합성(snapshotId 가 있을 때만). 칸은 식별자·글자뿐(규칙 3)
        app.post("/api/analyze/runs/{id}/export", ctx -> {
            ExportRequest req = ctx.body().isBlank() ? new ExportRequest(null, null) : ctx.bodyAsClass(ExportRequest.class);
            String format = req.format() == null || req.format().isBlank() ? "xlsx" : req.format();
            if (!format.equals("xlsx")) {
                ctx.status(400).json(Map.of("message", "format 은 xlsx 만: " + format));
                return;
            }
            Long id = runId(ctx, store);
            if (id == null) {
                return;
            }
            Optional<Consistency.Report> con = Optional.empty();
            if (req.snapshotId() != null) {
                con = Consistency.of(store, snapshots, id, req.snapshotId());
                if (con.isEmpty()) {
                    ctx.status(404).json(Map.of("message", "스냅샷이 없다"));
                    return;
                }
            }
            AnalyzeStore.Matrix m = store.crud(id);
            List<List<Object>> programs = new ArrayList<>();
            for (AnalyzeStore.ProgramRow r : m.rows()) {
                // 6-18 — 화면과 같은 표시 이름(저장 값 view 는 그대로): 뷰 종류 view → jsp, 프로그램 종류 view → page
                List<String> views = r.views().stream().map(v -> ("view".equals(v.kind()) ? "jsp" : v.kind()) + ": " + v.name()).toList();
                List<String> stmts = r.statements().stream().map(s -> s.id()).toList();
                programs.add(Arrays.asList(r.className(), r.method(), r.verb(), r.url(), r.params(), "view".equals(r.kind()) ? "page" : r.kind(), String.join(", ", views),
                        String.join(", ", stmts), r.description()));
            }
            LinkedHashMap<String, ResultTable> sheets = new LinkedHashMap<>();
            sheets.put("프로그램목록", Outputs.table(List.of(text("클래스"), text("메서드"), text("verb"), text("URL"), text("params"), text("종류"),
                    text("view"), text("문장"), text("설명")), programs));
            // 6-21 — 넓은 격자 대신 세로 목록·모듈 매트릭스(열이 표 수에 안 묶인다 — 옛 MAX_TABLES 거절은 걷었다)
            sheets.put("CRUD목록", Outputs.table(List.of(text("프로그램"), text("URL"), text("모듈"), text("표"), text("CRUD")),
                    CrudViews.longRows(m.rows()).stream().map(l -> Arrays.<Object>asList(l.program(), l.url(), l.module(), l.table(), l.crud())).toList()));
            CrudViews.ModuleMatrix mm = CrudViews.moduleMatrix(m.rows());
            if (mm.modules().size() > MAX_MODULES) {
                ctx.status(400).json(Map.of("message", "모듈이 너무 많다: " + mm.modules().size()));
                return;
            }
            List<ResultTable.Col> mcols = new ArrayList<>(List.of(text("표")));
            mm.modules().forEach(x -> mcols.add(text(x)));
            List<List<Object>> mrows = new ArrayList<>();
            for (CrudViews.ModuleRow r : mm.rows()) {
                List<Object> row = new ArrayList<>();
                row.add(r.table());
                mm.modules().forEach(x -> row.add(r.cells().getOrDefault(x, "")));
                mrows.add(row);
            }
            sheets.put("CRUD모듈", Outputs.table(mcols, mrows));
            // 6-22 — 종류 코드 옆에 이름·뜻(화면 칩과 같은 글)
            List<List<Object>> un = new ArrayList<>();
            for (Unresolved u : store.unresolved(id)) {
                Unresolved.Kind k = Unresolved.KINDS.get(u.kind());
                un.add(Arrays.asList(u.kind(), k.name(), k.meaning(), u.file(), u.line(), u.detail()));
            }
            sheets.put("미해결", Outputs.table(List.of(text("종류"), text("이름"), text("뜻"), text("파일"), Outputs.num("줄"), text("식별자")), un));
            if (con.isPresent()) {
                // 6-23 — 사유 열. 넷을 한 시트에 구분 열로
                Consistency.Report r = con.get();
                List<List<Object>> rows = new ArrayList<>();
                r.missingInDb().forEach(x -> rows.add(Arrays.asList("DB 에 없는 표", x.table(), "", "", String.valueOf(x.programs()), x.reason())));
                r.unusedInCode().forEach(x -> rows.add(Arrays.asList("안 쓰는 표", x.table(), x.schema(), x.type(), "", "")));
                r.deadStatements().forEach(x -> rows.add(Arrays.asList("안 불리는 문장", x, "", "", "", "")));
                r.orphanJsps().forEach(x -> rows.add(Arrays.asList("고아 JSP", x, "", "", "", "")));
                sheets.put("정합성", Outputs.table(List.of(text("구분"), text("이름"), text("스키마"), text("종류"), text("프로그램 수"), text("사유")), rows));
            }
            Path dir = Outputs.dir(active.get().orElse(null));
            Path f = Outputs.xlsx(dir, "프로그램분석-" + id + ".xlsx", sheets);
            List<Map<String, Object>> sheetInfo = new ArrayList<>();
            sheets.forEach((n, t) -> sheetInfo.add(Map.of("name", n, "rows", t.rows().size())));
            ctx.json(Map.of("dir", dir.toString(), "files", List.of(Map.of("name", f.getFileName().toString(), "path", f.toString(), "rows", programs.size())),
                    "sheets", sheetInfo));
        });

        // 6-11 교차 정합성 — 코드 ↔ DB 스냅샷(없는 표·안 쓰는 표) + 안 불리는 문장·뷰가 안 가리키는 JSP. snapshotId 없으면 앞 둘은 빈 목록
        app.get("/api/analyze/runs/{id}/consistency", ctx -> {
            Long id = runId(ctx, store);
            if (id == null) {
                return;
            }
            String raw = ctx.queryParam("snapshotId");
            Long snap = null;
            if (raw != null && !raw.isBlank()) {
                try {
                    snap = Long.parseLong(raw.trim());
                } catch (NumberFormatException e) {
                    ctx.status(400).json(Map.of("message", "snapshotId 가 수가 아니다"));
                    return;
                }
            }
            Optional<Consistency.Report> r = Consistency.of(store, snapshots, id, snap);
            if (r.isEmpty()) {
                ctx.status(404).json(Map.of("message", "스냅샷이 없다"));
                return;
            }
            ctx.json(r.get());
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
