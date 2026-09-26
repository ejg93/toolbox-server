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

    /** 변환 요청 — 3-5·3-6·3-8 공용 */
    record LogicalRequest(Long snapshotId, String csv, String owner, List<String> skipTokens, Boolean orgFirst,
            String dialect, Boolean includeTables) {
    }

    private LogicalRoutes() {
    }

    static void register(Javalin app, DictStore dict, SnapshotStore snapshots, Supplier<Optional<Profile>> active) {
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
            ctx.contentType("text/plain; charset=UTF-8").result(ddl.text());
        });
        registerCandidates(app, dict, snapshots, active);
        registerAudit(app, dict, snapshots, active);
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
                default -> kr.ejg.toolbox.core.logical.Candidates.wordUse(moiRows(), 1, r);
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
        java.nio.file.Files.createDirectories(file.getParent());
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

    private static List<List<String>> moiRows() throws java.io.IOException {
        try (java.io.InputStream in = DictStore.class.getResourceAsStream(DictStore.MOI_WORDS)) {
            if (in == null) {
                throw new IllegalStateException("동봉 사전이 없다");
            }
            return kr.ejg.toolbox.core.text.Csv.parse(kr.ejg.toolbox.core.text.Csv.decode(in.readAllBytes()));
        }
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
            in = ColumnInputs.fromSchemas(snap.get());
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
