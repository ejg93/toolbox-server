package kr.ejg.toolbox.core.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.gen.DdlReader;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.text.Csv;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * V-5 — 논리명 변환(3-1 자바 이식)을 실물 컬럼 전부에 돌려 순수본 JS 와 행 단위로 대조한다(3-2 강제 지점을 실물로).
 * 표본 = eGov 공통컴포넌트 Oracle DDL(공통 + 모듈별) · Oracle 샘플 스키마 · chinook Oracle — 사전은 3-2 와 같은 샘플.
 * <ul>
 *   <li>등급 A: 자바 결과 ≠ 순수본 결과(다르면 자바를 고친다 — 순수본이 정답)</li>
 *   <li>감사(R 규칙) 위반 수는 규칙별 골든 — 실물에서의 규칙 분포(등급 B 성격의 수)</li>
 * </ul>
 */
@Tag("corpus")
class LogicalCorpusTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final Path OUT = Path.of("target", "corpus-logical").toAbsolutePath();
    static List<Schema> schemas;
    static LogicalRun.Result jv;
    static JsonNode js;

    @BeforeAll
    static void run() throws Exception {
        CorpusFiles.verify();
        Map<String, Table> tables = new LinkedHashMap<>();
        List<Path> ddls = new ArrayList<>();
        for (Path p : CorpusFiles.files("egov", "*.sql")) {
            if (CorpusFiles.rel(p).contains("/ddl/oracle/")) {
                ddls.add(p);
            }
        }
        ddls.addAll(CorpusFiles.files("db-samples", "*.sql"));
        ddls.addAll(CorpusFiles.files("chinook", "Chinook_Oracle.sql"));
        for (Path p : ddls) {
            for (Table t : DdlReader.read(Csv.decode(Files.readAllBytes(p))).tables()) {
                tables.putIfAbsent(t.name().toUpperCase(java.util.Locale.ROOT), t); // 모듈별 DDL 이 공통 테이블을 되풀이한다
            }
        }
        schemas = List.of(new Schema("CORPUS", null, List.copyOf(tables.values())));
        StringBuilder csv = new StringBuilder("OWNER,TABLE_NAME,COLUMN_ID,COLUMN_NAME,DATA_TYPE,DATA_LENGTH,DATA_SCALE,NULLABLE,DATA_DEFAULT,COMMENTS\n");
        for (Table t : tables.values()) {
            for (Column c : t.columns()) {
                csv.append("CORPUS,").append(t.name()).append(',').append(c.ordinal()).append(',').append(c.name()).append(',')
                        .append(c.nativeType()).append(',').append(c.length() == null ? "" : c.length()).append(',')
                        .append(c.scale() == null ? "" : c.scale()).append(',').append(c.nullable() ? "Y" : "N").append(",,\n");
            }
        }
        Files.createDirectories(OUT);
        Path colFile = OUT.resolve("columns.csv");
        Files.writeString(colFile, csv.toString(), StandardCharsets.UTF_8);

        Process p = new ProcessBuilder("node", "scripts/puppeteer/dump-logicalname.js", colFile.toString(), OUT.toString())
                .redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "dump-logicalname.js 실패: " + log.substring(Math.max(0, log.length() - 500)));
        js = JSON.readTree(OUT.resolve("rows.json").toFile());

        jv = LogicalRun.run(ColumnInputs.fromCsv(Files.readAllBytes(colFile), null), LogicalRunSampleTest.sampleDicts(),
                List.of("TB"), true);
    }

    @Test
    void javaEqualsPureJsOnEveryColumn() {
        List<Map<String, Object>> jsRows = JSON.convertValue(js.get("rows"), new TypeReference<>() {
        });
        List<String> bad = new ArrayList<>();
        List<LogicalRun.Row> rows = jv.rows();
        if (rows.size() != jsRows.size()) {
            bad.add("행 수 자바 " + rows.size() + " ≠ JS " + jsRows.size());
        }
        for (int i = 0; i < Math.min(rows.size(), jsRows.size()); i++) {
            LogicalRun.Row r = rows.get(i);
            Map<String, Object> m = LogicalRunSampleTest.row(r.owner(), r.table(), r.col(), r.name(), r.src(), r.missing(), r.dtype(),
                    r.dlen(), r.dscale(), r.pk(), r.nnull(), r.ord(), false);
            if (!m.equals(jsRows.get(i))) {
                bad.add(r.table() + "." + r.col() + " 자바 " + m.get("name") + "/" + m.get("src") + " ≠ JS " + jsRows.get(i).get("name")
                        + "/" + jsRows.get(i).get("src"));
            }
        }
        CorpusFiles.none("논리명 자바 ≠ 순수본(컬럼 " + rows.size() + ")", bad, rows.size());
    }

    @Test
    void statsAndAuditGolden() throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stats", jv.stats());
        out.put("jsStats", JSON.convertValue(JSON.readTree(OUT.resolve("rank.json").toFile()).get("stats"), Map.class));
        Map<String, Integer> byRule = new TreeMap<>();
        for (Audit.Finding f : Audit.run(schemas, LogicalRunSampleTest.sampleDicts(), LogicalRunSampleTest.domains, List.of("TB"), true,
                EnumSet.allOf(Audit.Rule.class))) {
            byRule.merge(f.rule().name(), 1, Integer::sum);
        }
        out.put("auditByRule", byRule);
        GoldenFiles.assertJson("corpus/logical-summary.json", out);
    }
}
