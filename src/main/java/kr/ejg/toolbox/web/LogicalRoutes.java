package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.logical.Audit;
import kr.ejg.toolbox.core.logical.ColumnInput;
import kr.ejg.toolbox.core.logical.ColumnInputs;
import kr.ejg.toolbox.core.logical.CommentDdl;
import kr.ejg.toolbox.core.logical.Dialect;
import kr.ejg.toolbox.core.logical.LogicalRun;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.profile.Profile;
import kr.ejg.toolbox.core.report.XlsxWriter;
import kr.ejg.toolbox.core.sqlrun.ResultTable;

/**
 * 논리명(3-5~) — `POST /api/logical/comments {snapshotId | csv, owner, skipTokens, orgFirst, dialect, includeTables}` → text/plain.
 * 입력은 스냅샷(2.2 C) 또는 컬럼목록 CSV 문자열. 무시토큰을 안 주면 프로필 `logicalName.skipTokens`.
 */
final class LogicalRoutes {

    /** 변환 요청 — 3-5·3-6·3-8 공용. deliverableFilter 면 스냅샷을 프로필 deliverable.filter 로 거른다(3-12 — 산출물과 같은 표) */
    record LogicalRequest(Long snapshotId, String csv, String owner, List<String> skipTokens, Boolean orgFirst,
            String dialect, Boolean includeTables, Boolean deliverableFilter) {

        LogicalRequest(Long snapshotId, String csv, String owner, List<String> skipTokens, Boolean orgFirst, String dialect,
                Boolean includeTables) {
            this(snapshotId, csv, owner, skipTokens, orgFirst, dialect, includeTables, null);
        }
    }

    /** 7-9 — LogicalRequest 칸 + 제외할 컬럼 + 파일 저장 */
    record MaskingRequest(Long snapshotId, String csv, String owner, List<String> skipTokens, Boolean orgFirst, String dialect,
            List<MaskCol> exclude, Boolean save) {
    }

    record MaskCol(String table, String col) {
    }

    private LogicalRoutes() {
    }

    static void register(Javalin app, DictStore dict, SnapshotStore snapshots, Supplier<Optional<Profile>> active,
            kr.ejg.toolbox.core.job.JobManager jobs, kr.ejg.toolbox.core.conn.ConnectionRegistry conns) {
        app.post("/api/logical/comments", ctx -> {
            LogicalRequest req = ctx.bodyAsClass(LogicalRequest.class);
            Dialect d;
            try {
                d = Dialect.of(req.dialect() == null ? "oracle" : req.dialect());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return;
            }
            LogicalRun.Result r = run(ctx, req, dict, snapshots, active);
            if (r == null) {
                return;
            }
            CommentDdl.Ddl ddl = CommentDdl.generate(r, d, req.includeTables() == null || req.includeTables(), LocalDateTime.now());
            if ("true".equals(ctx.queryParam("save"))) { // 3-8 「파일로 저장」
                java.nio.file.Path file = outFile(active, "comments-" + d.name().toLowerCase(java.util.Locale.ROOT) + ".sql");
                java.nio.file.Files.writeString(file, ddl.text(), StandardCharsets.UTF_8);
                ctx.json(Map.of("path", file.toString(), "lines", ddl.lines(), "reviewCount", ddl.reviewCount()));
                return;
            }
            ctx.contentType("text/plain; charset=UTF-8").result(ddl.text());
        });

        // 7-9 개인정보 마스킹 — 탐지 + UPDATE 글. 실행 길은 없다(되돌릴 수 없는 데이터 변경). 값은 안 읽는다
        app.post("/api/logical/masking", ctx -> {
            MaskingRequest mreq = ctx.bodyAsClass(MaskingRequest.class);
            LogicalRequest req = new LogicalRequest(mreq.snapshotId(), mreq.csv(), mreq.owner(), mreq.skipTokens(), mreq.orgFirst(),
                    mreq.dialect(), null);
            LogicalRun.Result r = run(ctx, req, dict, snapshots, active);
            if (r == null) {
                return;
            }
            Map<String, String> comments = new java.util.HashMap<>();
            String dialect = mreq.dialect();
            if (mreq.snapshotId() != null) {
                for (Schema s : snapshots.get(mreq.snapshotId()).orElseThrow()) {
                    if (dialect == null || dialect.isBlank()) {
                        dialect = kr.ejg.toolbox.core.gen.TypeMapping.dialectOf(s.dbVersion());
                    }
                    for (kr.ejg.toolbox.core.meta.Table t : s.tables()) {
                        for (kr.ejg.toolbox.core.meta.Column c : t.columns()) {
                            if (c.comment() != null && !c.comment().isBlank()) {
                                comments.put((nz(t.schema()) + "." + t.name() + "." + c.name()).toUpperCase(java.util.Locale.ROOT), c.comment());
                            }
                        }
                    }
                }
            }
            if (dialect == null || dialect.isBlank()) {
                dialect = "oracle";
            }
            kr.ejg.toolbox.core.logical.Masking.Rules rules = kr.ejg.toolbox.core.logical.Masking.rules();
            kr.ejg.toolbox.core.logical.Masking.Detection det = kr.ejg.toolbox.core.logical.Masking.detect(
                    kr.ejg.toolbox.core.logical.Masking.inputs(r, comments), rules);
            java.util.Set<String> ex = new java.util.HashSet<>();
            if (mreq.exclude() != null) {
                mreq.exclude().forEach(x -> ex.add((nz(x.table()) + "." + nz(x.col())).toUpperCase(java.util.Locale.ROOT)));
            }
            List<kr.ejg.toolbox.core.logical.Masking.Candidate> use = new java.util.ArrayList<>();
            List<Map<String, Object>> rows = new java.util.ArrayList<>();
            for (kr.ejg.toolbox.core.logical.Masking.Candidate c : det.candidates()) {
                boolean excluded = ex.contains((nz(c.table()) + "." + nz(c.col())).toUpperCase(java.util.Locale.ROOT));
                if (!excluded) {
                    use.add(c);
                }
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("owner", nz(c.owner()));
                m.put("table", c.table());
                m.put("col", c.col());
                m.put("kind", c.kind());
                m.put("label", rules.kind(c.kind()).label());
                m.put("reason", c.reason());
                m.put("logicalName", nz(c.logicalName()));
                m.put("comment", nz(c.comment()));
                m.put("dtype", nz(c.dtype()));
                m.put("text", c.text());
                m.put("excluded", excluded);
                rows.add(m);
            }
            String sql;
            try {
                sql = kr.ejg.toolbox.core.logical.Masking.sql(use, dialect, rules);
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return;
            }
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("candidates", rows);
            out.put("sql", sql);
            out.put("warnings", det.warnings());
            out.put("dialect", dialect);
            if (Boolean.TRUE.equals(mreq.save())) {
                java.nio.file.Path file = outFile(active, "masking-" + dialect.toLowerCase(java.util.Locale.ROOT) + ".sql");
                java.nio.file.Files.writeString(file, sql, StandardCharsets.UTF_8);
                out.put("path", file.toString());
            }
            ctx.json(out);
        });

        // 3-8 — 화면이 부르는 변환 결과. 랭킹에 충돌(3-4)을 라우트에서 붙인다 — core 는 그대로
        app.post("/api/logical/run", ctx -> {
            LogicalRequest req = ctx.bodyAsClass(LogicalRequest.class);
            LogicalRun.Result r = run(ctx, req, dict, snapshots, active);
            if (r == null) {
                return;
            }
            kr.ejg.toolbox.core.dict.Dictionaries dicts = dict.load();
            boolean orgFirst = req.orgFirst() == null || req.orgFirst();
            List<Map<String, Object>> rank = new java.util.ArrayList<>();
            for (LogicalRun.Rank k : r.rank()) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("token", k.token());
                m.put("count", k.count());
                m.put("user", dicts.user().get(k.token()));
                m.put("conflict", LogicalRun.conflict(k.token(), dicts, orgFirst));
                rank.add(m);
            }
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("rows", r.rows());
            out.put("tableRows", r.tableRows());
            out.put("rank", rank);
            out.put("stats", r.stats());
            ctx.json(out);
        });
        registerCandidates(app, dict, snapshots, active);
        registerAudit(app, dict, snapshots, active);
        registerApply(app, dict, snapshots, active, jobs, conns);
    }

    static final java.util.regex.Pattern IDENT = java.util.regex.Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_$#@]*");

    private static void checkIdent(List<String> odd, String owner, String table, String col) {
        for (String s : new String[] {owner, table, col}) {
            if (s != null && !s.isEmpty() && !IDENT.matcher(s).matches() && !odd.contains(s)) {
                odd.add(s);
            }
        }
    }

    /** 3-9 — 변환 요청 + 실행할 접속 */
    record ApplyRequest(Long snapshotId, String csv, String owner, List<String> skipTokens, Boolean orgFirst, String dialect,
            Boolean includeTables, String connId) {
        LogicalRequest asRun() {
            return new LogicalRequest(snapshotId, csv, owner, skipTokens, orgFirst, dialect, includeTables);
        }
    }

    static void registerApply(Javalin app, DictStore dict, SnapshotStore snapshots, Supplier<Optional<Profile>> active,
            kr.ejg.toolbox.core.job.JobManager jobs, kr.ejg.toolbox.core.conn.ConnectionRegistry conns) {
        app.post("/api/logical/comments/apply", ctx -> {
            ApplyRequest req = ctx.bodyAsClass(ApplyRequest.class);
            if (req.connId() == null || conns.find(req.connId()).isEmpty()) {
                ctx.status(400).json(Map.of("message", "접속이 없다: " + req.connId()));
                return;
            }
            Dialect d;
            try {
                d = Dialect.of(req.dialect() == null ? "oracle" : req.dialect());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return;
            }
            if (d.noExec()) {
                ctx.status(400).json(Map.of("message", d.label() + " 는 실행할 COMMENT 문이 없다 — 대조표만 내려받는다"));
                return;
            }
            LogicalRun.Result r = run(ctx, req.asRun(), dict, snapshots, active);
            if (r == null) {
                return;
            }
            // DDL 글은 순수본과 글자 일치라 이름에 따옴표를 안 씌운다 — 따옴표가 필요한 이름(공백·기호)이 있으면 직접 실행을
            // 거절하고 내려받아 고쳐 쓰게 한다. CSV 로 들어온 이름이 SQL 조각이 되는 길도 여기서 막힌다(번들 4 리뷰)
            List<String> odd = new java.util.ArrayList<>();
            r.tableRows().forEach(t -> checkIdent(odd, t.owner(), t.table(), null));
            r.rows().forEach(c -> checkIdent(odd, c.owner(), c.table(), c.col()));
            if (!odd.isEmpty()) {
                ctx.status(400).json(Map.of("message", "따옴표가 필요한 이름이 있어 직접 실행하지 않는다 — DDL 을 내려받아 고친 뒤 실행: "
                        + String.join(", ", odd.subList(0, Math.min(5, odd.size())))));
                return;
            }
            boolean tables = req.includeTables() == null || req.includeTables();
            List<String> lines = CommentDdl.executableLines(r, d, tables);
            int review = CommentDdl.generate(r, d, tables, LocalDateTime.now()).reviewCount();
            String connId = req.connId();
            kr.ejg.toolbox.core.job.Job job = jobs.submit("comments-apply", jc -> {
                try (java.sql.Connection conn = conns.open(connId)) {
                    return kr.ejg.toolbox.core.logical.CommentApply.apply(conn, lines, review, jc);
                }
            });
            ctx.status(202).json(Map.of("jobId", job.id(), "lines", lines.size()));
        });
    }

    /** 3-6 — 후보 CSV 한 종류를 out/<프로필>/<시각>/ 에 쓴다 */
    record CandidatesRequest(Long snapshotId, String csv, String owner, List<String> skipTokens, Boolean orgFirst,
            String kind, String dbName, Boolean excludeReview) {
        LogicalRequest asRun() {
            return new LogicalRequest(snapshotId, csv, owner, skipTokens, orgFirst, null, null);
        }
    }

    private static final java.time.format.DateTimeFormatter STAMP = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Map<String, String> FILE = Map.of("terms", "표준용어후보.csv", "words", "표준단어사전.csv",
            "domains", "표준도메인후보.csv", "wordUse", "공통표준단어_사용여부.csv");

    static void registerCandidates(Javalin app, DictStore dict, SnapshotStore snapshots, Supplier<Optional<Profile>> active) {
        app.post("/api/logical/candidates", ctx -> {
            CandidatesRequest req = ctx.bodyAsClass(CandidatesRequest.class);
            String kind = req.kind() == null ? "" : req.kind();
            if (!FILE.containsKey(kind)) {
                ctx.status(400).json(Map.of("message", "kind 는 terms·words·domains·wordUse"));
                return;
            }
            LogicalRun.Result r = run(ctx, req.asRun(), dict, snapshots, active);
            if (r == null) {
                return;
            }
            boolean orgFirst = req.orgFirst() == null || req.orgFirst();
            String db = kr.ejg.toolbox.core.logical.Candidates.dbName(req.dbName(), r);
            String body = switch (kind) {
                case "terms" -> kr.ejg.toolbox.core.logical.Candidates.terms(r, dict.load(), orgFirst, db,
                        Boolean.TRUE.equals(req.excludeReview()));
                case "words" -> kr.ejg.toolbox.core.logical.Candidates.stdWords(r, dict.load(), db);
                case "domains" -> kr.ejg.toolbox.core.logical.Candidates.domains(r,
                        new kr.ejg.toolbox.core.logical.DomainMatcher(dict.domains()), db);
                default -> wordUse(dict, r);
            };
            java.nio.file.Path file = outFile(active, FILE.get(kind));
            java.nio.file.Files.writeString(file, body, StandardCharsets.UTF_8);
            ctx.json(Map.of("path", file.toAbsolutePath().toString(), "lines", body.split("\r\n", -1).length - 1));
        });
    }

    /** out/<프로필>/<시각>/<이름> — 폴더까지 만든다(12장) */
    static java.nio.file.Path outFile(Supplier<Optional<Profile>> active, String name) throws java.io.IOException {
        Profile p = active.get().orElseThrow();
        String base = p.output() != null && p.output().dir() != null ? p.output().dir() : "out";
        java.nio.file.Path file = java.nio.file.Path.of(base, p.name(), LocalDateTime.now().format(STAMP), name).toAbsolutePath();
        java.nio.file.Path dir = file.getParent();
        if (dir != null) {
            java.nio.file.Files.createDirectories(dir);
        }
        return file;
    }

    /** 3-7 — 스냅샷만(코멘트가 입력이다). rules 를 안 주면 COMMENT_MISMATCH 뺀 넷 */
    record AuditRequest(Long snapshotId, String csv, List<String> skipTokens, Boolean orgFirst, List<String> rules) {
    }

    static void registerAudit(Javalin app, DictStore dict, SnapshotStore snapshots, Supplier<Optional<Profile>> active) {
        app.post("/api/logical/audit", ctx -> {
            AuditRequest req = ctx.bodyAsClass(AuditRequest.class);
            if (req.snapshotId() == null) {
                ctx.status(400).json(Map.of("message", "snapshotId 가 있어야 한다 — 코멘트는 스냅샷에만 있다"));
                return;
            }
            java.util.Set<Audit.Rule> rules;
            try {
                rules = req.rules() == null || req.rules().isEmpty() ? Audit.DEFAULT_RULES
                        : java.util.Set.copyOf(req.rules().stream()
                                .map(x -> Audit.Rule.valueOf(x.trim().toUpperCase(java.util.Locale.ROOT))).toList());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", "모르는 규칙: " + req.rules()));
                return;
            }
            Optional<List<Schema>> snap = snapshots.get(req.snapshotId());
            if (snap.isEmpty()) {
                ctx.status(404).json(Map.of("message", "스냅샷이 없다: " + req.snapshotId()));
                return;
            }
            List<String> skip = req.skipTokens() != null ? req.skipTokens()
                    : active.get().map(Profile::logicalName).map(Profile.LogicalName::skipTokens).orElse(List.of());
            List<Audit.Finding> found = Audit.run(snap.get(), dict.load(), dict.domains(), skip,
                    req.orgFirst() == null || req.orgFirst(), rules);
            Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            for (Audit.Rule r : Audit.Rule.values()) {
                if (rules.contains(r)) {
                    counts.put(r.name(), 0);
                }
            }
            List<List<Object>> rows = new java.util.ArrayList<>();
            for (Audit.Finding f : found) {
                counts.merge(f.rule().name(), 1, Integer::sum);
                rows.add(java.util.Arrays.asList(f.schema(), f.table(), f.column(), f.rule().name(), f.detail()));
            }
            java.nio.file.Path file = outFile(active, "표준미준수.xlsx");
            XlsxWriter.write(new ResultTable(List.of(new ResultTable.Col("스키마", "VARCHAR"), new ResultTable.Col("테이블", "VARCHAR"),
                    new ResultTable.Col("컬럼", "VARCHAR"), new ResultTable.Col("규칙", "VARCHAR"), new ResultTable.Col("내용", "VARCHAR")),
                    rows, false, -1, 0), file);
            ctx.json(Map.of("findings", found, "counts", counts, "path", file.toString()));
        });
    }

    /** 지금 쓰는 공통표준단어 원본(올린 판이 있으면 그것, 0-32) + 사용여부. 약어 열은 머리 글자로 찾는다 */
    private static String wordUse(DictStore dict, LogicalRun.Result r) {
        List<List<String>> rows = kr.ejg.toolbox.core.text.Csv.parse(kr.ejg.toolbox.core.text.Csv.decode(dict.moiCsv()));
        int abbr = rows.isEmpty() ? -1 : rows.get(0).indexOf("공통표준단어영문약어명");
        return kr.ejg.toolbox.core.logical.Candidates.wordUse(rows, abbr < 0 ? 1 : abbr, r);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 요청 → 변환 결과. 입력이 잘못되면 400 을 쓰고 null */
    static LogicalRun.Result run(Context ctx, LogicalRequest req, DictStore dict, SnapshotStore snapshots,
            Supplier<Optional<Profile>> active) throws SQLException {
        List<ColumnInput> in;
        if (req.snapshotId() != null) {
            Optional<List<Schema>> snap = snapshots.get(req.snapshotId());
            if (snap.isEmpty()) {
                ctx.status(404).json(Map.of("message", "스냅샷이 없다: " + req.snapshotId()));
                return null;
            }
            List<Schema> schemas = snap.get();
            if (Boolean.TRUE.equals(req.deliverableFilter())) {
                schemas = kr.ejg.toolbox.core.deliverable.Deliverables.filter(schemas, active.get().map(Profile::deliverable)
                        .map(Profile.Deliverable::filter).orElse(null));
            }
            in = ColumnInputs.fromSchemas(schemas);
        } else if (req.csv() != null) {
            try {
                in = ColumnInputs.fromCsv(req.csv().getBytes(StandardCharsets.UTF_8), req.owner());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return null;
            }
        } else {
            ctx.status(400).json(Map.of("message", "snapshotId 또는 csv 가 있어야 한다"));
            return null;
        }
        List<String> skip = req.skipTokens() != null ? req.skipTokens()
                : active.get().map(Profile::logicalName).map(Profile.LogicalName::skipTokens).orElse(List.of());
        return LogicalRun.run(in, dict.load(), skip, req.orgFirst() == null || req.orgFirst());
    }
}
