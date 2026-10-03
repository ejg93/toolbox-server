package kr.ejg.toolbox.core.logical;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.gen.DdlReader;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.text.Csv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * V-19 — 개인정보 마스킹 탐지(7-8)를 eGov 공통컴포넌트 Oracle DDL + 코멘트 실물에 돌린다.
 * <ul>
 *   <li>등급 A: 손 대조 — COMTNEMPLYRINFO 의 주민번호·이동전화·이메일·사용자명·주택주소가 그 종류로 잡히고, 부서·기관·조직명은 안 잡힌다</li>
 *   <li>등급 B: 후보 목록(표.컬럼 종류 근거) — 바뀌면 빨강, 갱신은 diff 를 이력에</li>
 * </ul>
 */
@Tag("corpus")
class MaskingCorpusTest {

    @Test
    void egov() throws Exception {
        CorpusFiles.verify();
        List<Path> ddl = new ArrayList<>();
        List<Path> comment = new ArrayList<>();
        for (Path p : CorpusFiles.files("egov", "*.sql")) {
            String rel = CorpusFiles.rel(p);
            if (rel.contains("/ddl/oracle/")) {
                ddl.add(p);
            } else if (rel.contains("/comment/oracle/")) {
                comment.add(p);
            }
        }
        StringBuilder text = new StringBuilder();
        for (Path p : ddl) {
            text.append(Csv.decode(Files.readAllBytes(p))).append("\n;\n");
        }
        for (Path p : comment) { // 코멘트는 표 뒤에 — DdlReader 가 같은 읽기 안의 표에 붙인다
            text.append(Csv.decode(Files.readAllBytes(p))).append("\n;\n");
        }
        List<Table> tables = DdlReader.read(text.toString()).tables();
        Map<String, String> comments = new HashMap<>();
        int columns = 0;
        for (Table t : tables) {
            for (Column c : t.columns()) {
                columns++;
                if (c.comment() != null && !c.comment().isBlank()) {
                    comments.put(("" + "." + t.name() + "." + c.name()).toUpperCase(Locale.ROOT), c.comment());
                }
            }
        }
        List<Table> owned = tables.stream().map(t -> new Table("", t.name(), t.type(), t.comment(), t.columns(), t.pk(), t.fks(), t.uniques(),
                t.indexes(), t.rowCount(), t.createdAt(), t.lastDdlAt())).toList();
        LogicalRun.Result r = LogicalRun.run(ColumnInputs.fromSchemas(List.of(new Schema("EGOV", null, owned))),
                LogicalRunSampleTest.sampleDicts(), List.of(), true);
        Masking.Detection d = Masking.detect(Masking.inputs(r, comments), Masking.rules());

        Map<String, Masking.Candidate> byCol = new LinkedHashMap<>();
        Map<String, Integer> byKind = new TreeMap<>();
        Map<String, Integer> byReason = new TreeMap<>();
        List<String> listed = new ArrayList<>();
        int notText = 0;
        for (Masking.Candidate c : d.candidates()) {
            byCol.put(c.table().toUpperCase(Locale.ROOT) + "." + c.col().toUpperCase(Locale.ROOT), c);
            byKind.merge(c.kind(), 1, Integer::sum);
            byReason.merge(c.reason(), 1, Integer::sum);
            notText += c.text() ? 0 : 1;
            listed.add(c.table() + "." + c.col() + " " + c.kind() + " " + c.reason() + (c.text() ? "" : " 문자열아님"));
        }
        List<String> a = new ArrayList<>();
        Map<String, String> want = new LinkedHashMap<>();
        want.put("COMTNEMPLYRINFO.IHIDNUM", "rrn");
        want.put("COMTNEMPLYRINFO.MBTLNUM", "phone");
        want.put("COMTNEMPLYRINFO.EMAIL_ADRES", "email");
        want.put("COMTNEMPLYRINFO.USER_NM", "name");
        want.put("COMTNEMPLYRINFO.HOUSE_ADRES", "address");
        want.forEach((k, v) -> {
            Masking.Candidate c = byCol.get(k);
            if (c == null || !c.kind().equals(v)) {
                a.add(k + " 가 " + v + " 로 안 잡혔다: " + (c == null ? "후보 아님" : c.kind()));
            }
        });
        for (Masking.Candidate c : d.candidates()) {
            String what = (c.comment() == null ? "" : c.comment()) + "|" + (c.logicalName() == null ? "" : c.logicalName());
            if (what.matches(".*(부서명|기관명|조직명)(\\|.*)?$")) {
                a.add(c.table() + "." + c.col() + " 는 부서·기관·조직명인데 " + c.kind() + " 로 잡혔다");
            }
        }
        Map<String, Object> golden = new LinkedHashMap<>();
        golden.put("tables", tables.size());
        golden.put("columns", columns);
        golden.put("commentedColumns", comments.size());
        golden.put("candidates", d.candidates().size());
        golden.put("byKind", byKind);
        golden.put("byReason", byReason);
        golden.put("notText", notText);
        GoldenFiles.assertJson("corpus/masking-egov.json", golden);
        CorpusFiles.none("마스킹 탐지 eGov(컬럼 " + columns + ")", a, columns);
        CorpusFiles.conformance("masking-egov-candidates", listed);
    }
}
