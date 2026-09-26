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

    /** 요청 한 번 — docs 가 비면 만들 수 있는 전부 */
    public record Request(Set<String> docs, Definitions.Options options, List<String> skipTokens, boolean orgFirst,
            List<CodeAndLink.CodeTable> codeTables) {
        public Request {
            docs = docs == null ? Set.of() : Set.copyOf(docs);
            skipTokens = skipTokens == null ? List.of() : List.copyOf(skipTokens);
            codeTables = codeTables == null ? List.of() : List.copyOf(codeTables);
        }
    }

    /** 08 코드값을 읽을 접속 — 없으면 08 을 건너뛴다 */
    public interface CodeConnection {
        Connection open() throws java.sql.SQLException;
    }

    public record Result(List<String> files, List<String> skipped) {
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
        progress(ctx, 100, "완료 — " + files.size() + "개");
        return new Result(files, skipped);
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
