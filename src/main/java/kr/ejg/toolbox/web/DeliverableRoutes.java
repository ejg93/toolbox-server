package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ejg.toolbox.core.conn.ConnectionRegistry;
import kr.ejg.toolbox.core.deliverable.CodeAndLink;
import kr.ejg.toolbox.core.deliverable.Doc;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.sqlrun.SqlRunner;

/**
 * 산출물(2-3~) — 08 공통코드 후보·코드값, 09 연계 후보. 코드값은 응답으로만 간다 — H2·로그 없음(규칙 3).
 */
final class DeliverableRoutes {

    record SnapshotRequest(Long snapshotId, String connId) {
    }

    record CodeRowsRequest(String connId, List<CodeAndLink.CodeTable> tables, String org, String dept) {
    }

    /** 2-5 — 옵션은 프로필 deliverable(author·org) 이 기본, 요청으로 덮는다 */
    record BuildRequest(Long snapshotId, List<String> docs, String author, String org, String dept, String bizArea, String dbDesc,
            String dbName, String logicalDbName, String os, List<String> skipTokens, Boolean orgFirst, String codeConnId,
            List<CodeAndLink.CodeTable> codeTables, Long analyzeRunId) {
    }

    /** 7-7 — 표 비면 스냅샷 전부. include* 는 비면 true */
    record DdlRequest(Long snapshotId, List<DdlTable> tables, String target, String schema, Boolean includeFk, Boolean includeIndex,
            Boolean includeComments, Boolean save) {
    }

    record DdlTable(String schema, String name) {
    }

    private DeliverableRoutes() {
    }

    static void registerBuild(Javalin app, SnapshotStore snapshots, ConnectionRegistry conns, kr.ejg.toolbox.core.dict.DictStore dict,
            kr.ejg.toolbox.core.job.JobManager jobs, java.util.function.Supplier<Optional<kr.ejg.toolbox.core.profile.Profile>> active,
            kr.ejg.toolbox.core.analyze.AnalyzeStore analyses) {
        // 7-7 DDL 생성·방언 변환 — 글만 만든다(실행 안 함). save 면 내려받기 폴더에 ddl-<target>.sql
        app.post("/api/deliverable/ddl", ctx -> {
            DdlRequest req = ctx.bodyAsClass(DdlRequest.class);
            String target = req.target() == null ? "" : req.target().trim().toLowerCase(java.util.Locale.ROOT);
            if (!kr.ejg.toolbox.core.gen.DdlGen.targets().contains(target)) {
                ctx.status(400).json(Map.of("message", "대상 방언: " + String.join("·", kr.ejg.toolbox.core.gen.DdlGen.targets())));
                return;
            }
            Optional<List<Schema>> snap = filtered(ctx, req.snapshotId(), snapshots, active);
            if (snap.isEmpty()) {
                return;
            }
            String source = null;
            List<kr.ejg.toolbox.core.meta.Table> tables = new ArrayList<>();
            for (Schema s : snap.get()) {
                if (source == null) {
                    source = kr.ejg.toolbox.core.gen.TypeMapping.dialectOf(s.dbVersion());
                }
                for (kr.ejg.toolbox.core.meta.Table t : s.tables()) {
                    if (req.tables() == null || req.tables().isEmpty() || req.tables().stream().anyMatch(r -> r.name() != null
                            && r.name().equalsIgnoreCase(t.name()) && (r.schema() == null || r.schema().isBlank() || r.schema().equalsIgnoreCase(t.schema())))) {
                        tables.add(t);
                    }
                }
            }
            if (tables.isEmpty()) {
                ctx.status(400).json(Map.of("message", "만들 표가 없다"));
                return;
            }
            kr.ejg.toolbox.core.gen.DdlGen.Result r = kr.ejg.toolbox.core.gen.DdlGen.generate(tables, new kr.ejg.toolbox.core.gen.DdlGen.Options(
                    source, target, req.schema() == null || req.schema().isBlank() ? null : req.schema().trim(), !Boolean.FALSE.equals(req.includeFk()),
                    !Boolean.FALSE.equals(req.includeIndex()), !Boolean.FALSE.equals(req.includeComments()), null),
                    kr.ejg.toolbox.core.gen.TypeMapping.load());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sql", r.sql());
            out.put("warnings", r.warnings());
            out.put("tables", r.tables());
            out.put("source", source == null ? "" : source);
            if (Boolean.TRUE.equals(req.save())) {
                java.nio.file.Path dir = Outputs.dir(active.get().orElse(null));
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Path file = dir.resolve("ddl-" + target + ".sql");
                java.nio.file.Files.writeString(file, r.sql(), java.nio.charset.StandardCharsets.UTF_8);
                out.put("path", file.toString());
            }
            ctx.json(out);
        });
        app.post("/api/deliverable/build", ctx -> {
            BuildRequest req = ctx.bodyAsClass(BuildRequest.class);
            Optional<List<Schema>> whole = snapshot(ctx, req.snapshotId(), snapshots);
            if (whole.isEmpty()) {
                return;
            }
            Optional<List<Schema>> snap = filtered(ctx, req.snapshotId(), snapshots, active);
            if (snap.isEmpty()) {
                return;
            }
            int tables = kr.ejg.toolbox.core.deliverable.Deliverables.tableCount(snap.get());
            kr.ejg.toolbox.core.analyze.AnalyzeStore.Matrix crud = null;
            if (req.analyzeRunId() != null) {
                if (analyses.runs().stream().noneMatch(x -> x.id() == req.analyzeRunId())) {
                    ctx.status(404).json(Map.of("message", "프로그램 분석 실행이 없다: " + req.analyzeRunId()));
                    return;
                }
                crud = filterCrud(analyses.crud(req.analyzeRunId()), active);
                if (crud.tables().size() > AnalyzeRoutes.MAX_TABLES) {
                    ctx.status(400).json(Map.of("message", "표가 너무 많다: " + crud.tables().size()));
                    return;
                }
            }
            int snapshotTables = kr.ejg.toolbox.core.deliverable.Deliverables.tableCount(whole.get());
            if (req.codeConnId() != null && conns.find(req.codeConnId()).isEmpty()) {
                ctx.status(400).json(Map.of("message", "접속이 없다: " + req.codeConnId()));
                return;
            }
            kr.ejg.toolbox.core.profile.Profile p = active.get().orElseThrow();
            kr.ejg.toolbox.core.profile.Profile.Deliverable pd = p.deliverable();
            String templateDir = pd != null && pd.templateDir() != null ? pd.templateDir() : "templates/deliverable/example";
            String mappingFile = pd != null && pd.mapping() != null ? pd.mapping() : "mappings/deliverable/example.yaml";
            kr.ejg.toolbox.core.report.Mapping mapping;
            try {
                mapping = kr.ejg.toolbox.core.report.Mapping.load(java.nio.file.Path.of(mappingFile));
            } catch (java.io.IOException | IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", "매핑을 못 읽었다: " + mappingFile + " — " + e.getMessage()));
                return;
            }
            kr.ejg.toolbox.core.deliverable.Definitions.Options o = new kr.ejg.toolbox.core.deliverable.Definitions.Options(
                    or(req.author(), pd == null ? null : pd.author()), or(req.org(), pd == null ? null : pd.org()), req.dept(), req.bizArea(),
                    req.dbDesc(), req.dbName(), req.logicalDbName(), req.os());
            List<String> skip = req.skipTokens() != null ? req.skipTokens()
                    : p.logicalName() == null || p.logicalName().skipTokens() == null ? List.of() : p.logicalName().skipTokens();
            kr.ejg.toolbox.core.deliverable.DeliverableService.Request r = new kr.ejg.toolbox.core.deliverable.DeliverableService.Request(
                    req.docs() == null ? null : new java.util.TreeSet<>(req.docs()), o, skip, req.orgFirst() == null || req.orgFirst(),
                    req.codeTables(), source(snapshots, req.snapshotId(), tables == snapshotTables ? "없음 — 스냅샷의 표 전부"
                            : "표 " + tables + " / 스냅샷 " + snapshotTables + " (deliverable.filter)"), crud);
            java.nio.file.Path out = LogicalRoutes.outFile(active, "산출물");
            String codeConn = req.codeConnId();
            List<Schema> schemas = snap.get();
            kr.ejg.toolbox.core.job.Job job = jobs.submit("deliverable", jc -> kr.ejg.toolbox.core.deliverable.DeliverableService.build(schemas, r,
                    dict, mapping, java.nio.file.Path.of(templateDir), out, codeConn == null ? null : () -> conns.open(codeConn), jc));
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("jobId", job.id());
            res.put("dir", out.toString());
            res.put("tables", tables);
            res.put("snapshotTables", snapshotTables);
            ctx.status(202).json(res);
        });
    }

    /** 작성안내 「요약」 의 스냅샷 출처(2-13) */
    static kr.ejg.toolbox.core.deliverable.DeliverableService.Source source(SnapshotStore snapshots, long id, String filter)
            throws java.sql.SQLException {
        return snapshots.list().stream().filter(x -> x.id() == id).findFirst()
                .map(x -> new kr.ejg.toolbox.core.deliverable.DeliverableService.Source(x.id(),
                        x.takenAt() == null ? null : x.takenAt().withNano(0).toString().replace('T', ' '), x.connId(), filter))
                .orElse(null);
    }

    /** 18 의 표 열도 deliverable.filter 로 거른다(2-18) — 이름 규칙만 본다 */
    static kr.ejg.toolbox.core.analyze.AnalyzeStore.Matrix filterCrud(kr.ejg.toolbox.core.analyze.AnalyzeStore.Matrix m,
            java.util.function.Supplier<Optional<kr.ejg.toolbox.core.profile.Profile>> active) {
        kr.ejg.toolbox.core.meta.Scope f = active.get().map(kr.ejg.toolbox.core.profile.Profile::deliverable)
                .map(kr.ejg.toolbox.core.profile.Profile.Deliverable::filter).orElse(null);
        if (f == null) {
            return m;
        }
        return new kr.ejg.toolbox.core.analyze.AnalyzeStore.Matrix(m.tables().stream()
                .filter(t -> f.accepts(kr.ejg.toolbox.core.meta.Table.of(null, t, "TABLE", null))).toList(), m.rows());
    }

    /** 스냅샷을 프로필 deliverable.filter 로 거른다(2-16). 걸렀는데 표가 하나도 없으면 400 */
    static Optional<List<Schema>> filtered(Context ctx, Long id, SnapshotStore snapshots,
            java.util.function.Supplier<Optional<kr.ejg.toolbox.core.profile.Profile>> active) throws java.sql.SQLException {
        Optional<List<Schema>> snap = snapshot(ctx, id, snapshots);
        if (snap.isEmpty()) {
            return snap;
        }
        kr.ejg.toolbox.core.meta.Scope f = active.get().map(kr.ejg.toolbox.core.profile.Profile::deliverable)
                .map(kr.ejg.toolbox.core.profile.Profile.Deliverable::filter).orElse(null);
        List<Schema> out = kr.ejg.toolbox.core.deliverable.Deliverables.filter(snap.get(), f);
        if (f != null && kr.ejg.toolbox.core.deliverable.Deliverables.tableCount(out) == 0) {
            ctx.status(400).json(Map.of("message", "deliverable.filter 에 맞는 표가 없다 — 프로필 YAML 의 deliverable.filter 를 본다"));
            return Optional.empty();
        }
        return Optional.of(out);
    }

    private static String or(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    /** 2-6 — 품질 진단 SQL 을 채워 준다. 실행은 화면이 1-7 실행기로(결과 저장 없음, 규칙 3) */
    record QualityRequest(String kind, String dialect, String schema, String table, String column, List<String> keys,
            List<String> schemas) {
    }

    static void registerQuality(Javalin app, SnapshotStore snapshots) {
        app.get("/api/quality/kinds", ctx -> {
            Map<String, Object> out = new LinkedHashMap<>();
            List<Map<String, String>> kinds = new ArrayList<>();
            kr.ejg.toolbox.core.quality.QualitySql.kinds().forEach(k -> kinds.add(Map.of("id", k.id(), "title", k.title())));
            out.put("kinds", kinds);
            out.put("notes", kr.ejg.toolbox.core.quality.QualitySql.notes());
            ctx.json(out);
        });
        app.post("/api/quality/sql", ctx -> {
            QualityRequest q = ctx.bodyAsClass(QualityRequest.class);
            try {
                ctx.json(kr.ejg.toolbox.core.quality.QualitySql.render(q.kind(), q.dialect(), q.schema(), q.table(), q.column(), q.keys(),
                        q.schemas()));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
            }
        });
        // PK 없는 표는 SQL 없이 스냅샷으로도 안다
        app.post("/api/quality/nopk", ctx -> {
            Optional<List<Schema>> snap = snapshot(ctx, ctx.bodyAsClass(SnapshotRequest.class).snapshotId(), snapshots);
            if (snap.isEmpty()) {
                return;
            }
            List<Map<String, String>> out = new ArrayList<>();
            for (kr.ejg.toolbox.core.meta.Table t : kr.ejg.toolbox.core.deliverable.Definitions.sorted(snap.get())) {
                boolean view = t.type() != null && t.type().toUpperCase(java.util.Locale.ROOT).contains("VIEW");
                if (!view && (t.pk() == null || t.pk().columns().isEmpty())) {
                    out.add(Map.of("schema", t.schema() == null ? "" : t.schema(), "table", t.name()));
                }
            }
            ctx.json(out);
        });
    }

    static void register(Javalin app, SnapshotStore snapshots, ConnectionRegistry conns,
            java.util.function.Supplier<Optional<kr.ejg.toolbox.core.profile.Profile>> active) {
        registerQuality(app, snapshots);
        app.post("/api/deliverable/codes/candidates", ctx -> {
            Optional<List<Schema>> snap = filtered(ctx, ctx.bodyAsClass(SnapshotRequest.class).snapshotId(), snapshots, active);
            if (snap.isPresent()) {
                ctx.json(CodeAndLink.codeCandidates(snap.get()));
            }
        });

        app.post("/api/deliverable/codes/rows", ctx -> {
            CodeRowsRequest req = ctx.bodyAsClass(CodeRowsRequest.class);
            if (req.connId() == null || conns.find(req.connId()).isEmpty()) {
                ctx.status(400).json(Map.of("message", "접속이 없다: " + req.connId()));
                return;
            }
            if (req.tables() == null || req.tables().isEmpty()) {
                ctx.status(400).json(Map.of("message", "고른 코드 표가 없다"));
                return;
            }
            Doc doc;
            try (Connection c = conns.open(req.connId())) {
                doc = CodeAndLink.codeDoc(c, req.tables(), new CodeAndLink.Options(req.org(), req.dept()));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return;
            }
            ctx.json(doc);
        });

        // 스냅샷으로 표·뷰 후보, 접속을 주면 DB링크도(방언별 딕셔너리 조회)
        app.post("/api/deliverable/links/candidates", ctx -> {
            SnapshotRequest req = ctx.bodyAsClass(SnapshotRequest.class);
            Optional<List<Schema>> snap = filtered(ctx, req.snapshotId(), snapshots, active);
            if (snap.isEmpty()) {
                return;
            }
            List<CodeAndLink.LinkCandidate> cands = new ArrayList<>(CodeAndLink.linkCandidates(snap.get()));
            String note = null;
            if (req.connId() != null) {
                Optional<kr.ejg.toolbox.core.profile.Profile.Connection> conn = conns.find(req.connId());
                String sql = conn.map(x -> CodeAndLink.dbLinkSql(x.dialect())).orElse(null);
                if (conn.isEmpty()) {
                    ctx.status(400).json(Map.of("message", "접속이 없다: " + req.connId()));
                    return;
                } else if (sql == null) {
                    note = conn.get().dialect() + " 는 DB링크 조회가 없다 — 표·뷰 단서만";
                } else {
                    try (Connection c = conns.open(req.connId())) {
                        cands.addAll(CodeAndLink.dbLinks(SqlRunner.run(c, sql, List.of(), SqlRunner.DEFAULT_MAX_ROWS,
                                SqlRunner.DEFAULT_TIMEOUT_SEC)));
                    } catch (java.sql.SQLException e) {
                        note = "DB링크 조회 실패(권한일 수 있다): " + e.getMessage();
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("candidates", cands);
            out.put("doc", CodeAndLink.linkDoc(cands));
            out.put("note", note);
            ctx.json(out);
        });
    }

    static Optional<List<Schema>> snapshot(Context ctx, Long id, SnapshotStore snapshots) throws java.sql.SQLException {
        if (id == null) {
            ctx.status(400).json(Map.of("message", "snapshotId 가 있어야 한다"));
            return Optional.empty();
        }
        Optional<List<Schema>> snap = snapshots.get(id);
        if (snap.isEmpty()) {
            ctx.status(404).json(Map.of("message", "스냅샷이 없다: " + id));
        }
        return snap;
    }
}
