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
            List<CodeAndLink.CodeTable> codeTables) {
    }

    private DeliverableRoutes() {
    }

    static void registerBuild(Javalin app, SnapshotStore snapshots, ConnectionRegistry conns, kr.ejg.toolbox.core.dict.DictStore dict,
            kr.ejg.toolbox.core.job.JobManager jobs, java.util.function.Supplier<Optional<kr.ejg.toolbox.core.profile.Profile>> active) {
        app.post("/api/deliverable/build", ctx -> {
            BuildRequest req = ctx.bodyAsClass(BuildRequest.class);
            Optional<List<Schema>> snap = snapshot(ctx, req.snapshotId(), snapshots);
            if (snap.isEmpty()) {
                return;
            }
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
                    req.codeTables());
            java.nio.file.Path out = LogicalRoutes.outFile(active, "산출물");
            String codeConn = req.codeConnId();
            List<Schema> schemas = snap.get();
            kr.ejg.toolbox.core.job.Job job = jobs.submit("deliverable", jc -> kr.ejg.toolbox.core.deliverable.DeliverableService.build(schemas, r,
                    dict, mapping, java.nio.file.Path.of(templateDir), out, codeConn == null ? null : () -> conns.open(codeConn), jc));
            ctx.status(202).json(Map.of("jobId", job.id(), "dir", out.toString()));
        });
    }

    private static String or(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    static void register(Javalin app, SnapshotStore snapshots, ConnectionRegistry conns) {
        app.post("/api/deliverable/codes/candidates", ctx -> {
            Optional<List<Schema>> snap = snapshot(ctx, ctx.bodyAsClass(SnapshotRequest.class).snapshotId(), snapshots);
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
            Optional<List<Schema>> snap = snapshot(ctx, req.snapshotId(), snapshots);
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
