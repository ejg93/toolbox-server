package kr.ejg.toolbox.core.deliverable;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.dict.Dictionaries;
import kr.ejg.toolbox.core.job.JobContext;
import kr.ejg.toolbox.core.logical.ColumnInputs;
import kr.ejg.toolbox.core.logical.DomainMatcher;
import kr.ejg.toolbox.core.logical.LogicalRun;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.report.Mapping;
import kr.ejg.toolbox.core.report.XlsxFiller;

/**
 * 산출물 작업 본문(2-5) — 스냅샷 → 값 표(2-1·2-2·2-3) → 양식 기입(2-4) → 문서마다 xlsx 하나. 진행률은 문서 단위.
 * 08 은 코드 표를 고르고 접속을 줬을 때만 만든다(코드값은 실 DB 조회).
 */
public final class DeliverableService {

    /** 요청 한 번 — docs 가 비면 만들 수 있는 전부. source 는 작성안내 「요약」 에 적을 스냅샷(없으면 빈칸) */
    public record Request(Set<String> docs, Definitions.Options options, List<String> skipTokens, boolean orgFirst,
            List<CodeAndLink.CodeTable> codeTables, Source source) {
        public Request {
            docs = docs == null ? Set.of() : Set.copyOf(docs);
            skipTokens = skipTokens == null ? List.of() : List.copyOf(skipTokens);
            codeTables = codeTables == null ? List.of() : List.copyOf(codeTables);
        }

        public Request(Set<String> docs, Definitions.Options options, List<String> skipTokens, boolean orgFirst,
                List<CodeAndLink.CodeTable> codeTables) {
            this(docs, options, skipTokens, orgFirst, codeTables, null);
        }
    }

    /** 스냅샷 출처 — 작성안내 「요약」 */
    public record Source(long snapshotId, String takenAt, String connId) {
    }

    /** 작성안내 파일 이름 — 정의서 앞에 오게 00 */
    public static final String GUIDE = "00_작성안내.xlsx";
    static final String REVERSE = "이 문서들은 DB·소스에서 거꾸로 뽑은 역설계본이다 — 등급이 수동인 칸은 사람이 채우고, 추정인 칸은 확인한다";

    /** 08 코드값을 읽을 접속 — 없으면 08 을 건너뛴다 */
    public interface CodeConnection {
        Connection open() throws java.sql.SQLException;
    }

    /** files 는 정의서, guide 는 00_작성안내.xlsx(2-13) */
    public record Result(List<String> files, List<String> skipped, String guide) {
        public Result {
            files = List.copyOf(files);
            skipped = List.copyOf(skipped);
        }
    }

    private DeliverableService() {
    }

    public static Result build(List<Schema> snapshot, Request req, DictStore dict, Mapping mapping, Path templateDir, Path outDir,
            CodeConnection codeConn, JobContext ctx) throws Exception {
        Set<String> want = req.docs().isEmpty() ? new TreeSet<>(List.of("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11"))
                : new TreeSet<>(req.docs());
        Map<String, Doc> docs = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        progress(ctx, 5, "값 표 — 정의서");
        for (Doc d : Definitions.build(snapshot, req.options())) {
            docs.put(d.no(), d);
        }
        if (want.contains("05") || want.contains("06") || want.contains("07")) {
            progress(ctx, 15, "값 표 — 표준(논리명 변환)");
            Dictionaries dicts = dict.load();
            LogicalRun.Result r = LogicalRun.run(ColumnInputs.fromSchemas(snapshot), dicts, req.skipTokens(), req.orgFirst());
            Standards.Options so = new Standards.Options(req.options().org(), req.options().dept(), req.options().dbName(), req.orgFirst());
            for (Doc d : Standards.build(r, dicts, new DomainMatcher(dict.domains()), so)) {
                docs.put(d.no(), d);
            }
        }
        if (want.contains("08")) {
            if (codeConn == null || req.codeTables().isEmpty()) {
                skipped.add("08 — 코드 표를 고르고 접속을 줘야 만든다");
            } else {
                progress(ctx, 25, "값 표 — 표준코드(코드값 조회)");
                try (Connection c = codeConn.open()) {
                    docs.put("08", CodeAndLink.codeDoc(c, req.codeTables(), new CodeAndLink.Options(req.options().org(), req.options().dept())));
                }
            }
        }
        docs.put("09", CodeAndLink.linkDoc(CodeAndLink.linkCandidates(snapshot)));

        List<String> files = new ArrayList<>();
        List<String> order = new ArrayList<>(want);
        for (int i = 0; i < order.size(); i++) {
            String no = order.get(i);
            Doc d = docs.get(no);
            if (d == null) {
                continue;
            }
            if (ctx != null) {
                ctx.checkCancelled();
            }
            progress(ctx, 30 + 70 * i / Math.max(1, order.size()), no + " " + d.name());
            Mapping.DocMapping m = mapping.of(no);
            Path out = inside(outDir, m.file());
            XlsxFiller.fill(inside(templateDir, m.file()), m, d, out);
            files.add(out.toString());
        }
        Path guide = inside(outDir, GUIDE);
        writeGuide(guide, order, docs, files, skipped, req.source(), Definitions.sorted(snapshot).size());
        progress(ctx, 100, "완료 — " + files.size() + "개 + 작성안내");
        return new Result(files, skipped, guide.toString());
    }

    /** 00_작성안내.xlsx — 「항목」(만든 문서의 열마다 등급·채우는 법·추정 건수)·「요약」. 정의서 파일엔 색·메모를 안 넣는다(2-13) */
    static void writeGuide(Path file, List<String> order, Map<String, Doc> docs, List<String> files, List<String> skipped, Source src,
            int tables) throws java.io.IOException {
        List<List<Object>> items = new ArrayList<>();
        for (String no : order) {
            Doc d = docs.get(no);
            if (d == null) {
                continue;
            }
            for (Grades.Grade g : Grades.of(no)) {
                if (d.columns().contains(g.column())) {
                    items.add(List.of(no + " " + d.name(), g.column(), g.level(), g.how(),
                            g.level().equals(Grades.GUESS) ? (Object) d.estimated().getOrDefault(g.column(), 0) : ""));
                }
            }
        }
        List<List<Object>> summary = new ArrayList<>();
        summary.add(List.of("스냅샷", src == null ? "" : "#" + src.snapshotId()));
        summary.add(List.of("찍은 시각", src == null || src.takenAt() == null ? "" : src.takenAt()));
        summary.add(List.of("접속", src == null || src.connId() == null ? "" : src.connId()));
        summary.add(List.of("표 수", tables));
        summary.add(List.of("만든 문서", String.join(", ", files.stream().map(f -> Path.of(f).getFileName().toString()).toList())));
        summary.add(List.of("건너뛴 것", String.join(" / ", skipped)));
        summary.add(List.of("안내", REVERSE));
        java.util.LinkedHashMap<String, kr.ejg.toolbox.core.sqlrun.ResultTable> sheets = new java.util.LinkedHashMap<>();
        sheets.put("항목", table(List.of("문서", "열", "등급", "채우는 법", "이번 추정 건수"), items));
        sheets.put("요약", table(List.of("항목", "값"), summary));
        kr.ejg.toolbox.core.report.XlsxWriter.write(sheets, file);
    }

    private static kr.ejg.toolbox.core.sqlrun.ResultTable table(List<String> cols, List<List<Object>> rows) {
        return new kr.ejg.toolbox.core.sqlrun.ResultTable(cols.stream().map(c -> new kr.ejg.toolbox.core.sqlrun.ResultTable.Col(c, "VARCHAR"))
                .toList(), rows, false, -1, 0);
    }

    /** 매핑의 file 은 파일 이름만 — 「../」 나 절대 경로로 폴더 밖을 가리키면 거절 */
    static Path inside(Path dir, String file) {
        Path base = dir.toAbsolutePath().normalize();
        Path p = base.resolve(file).normalize();
        if (!p.startsWith(base) || p.equals(base)) {
            throw new IllegalArgumentException("매핑의 file 이 폴더 밖을 가리킨다: " + file);
        }
        return p;
    }

    private static void progress(JobContext ctx, int pct, String msg) {
        if (ctx != null) {
            ctx.progress(pct, msg);
        }
    }
}
