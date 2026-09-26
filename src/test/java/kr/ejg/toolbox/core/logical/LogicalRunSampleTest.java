package kr.ejg.toolbox.core.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.dict.Dictionaries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 3-2 강제 지점 — 순수본 JS 를 Puppeteer 로 실제로 돌려 뜬 결과(scripts/puppeteer/dump-logicalname.js)와 행 단위로 같다.
 * 다르면 자바를 고친다. 골든을 고치지 않는다(골든은 순수본이 바뀔 때만 다시 뜬다).
 */
class LogicalRunSampleTest {

    static final Path SAMPLE = Path.of("src/test/resources/sample/logical");
    static final Path GOLDEN = Path.of("src/test/resources/golden/logical");
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    static Dictionaries dicts;
    static List<DictStore.Domain> domains;
    static LogicalRun.Result result;

    /** 샘플 셋 결과 — 3-5·3-6 테스트도 쓴다 */
    static LogicalRun.Result sampleResult() throws Exception {
        if (result == null) {
            up();
        }
        return result;
    }

    static Dictionaries sampleDicts() throws Exception {
        if (dicts == null) {
            up();
        }
        return dicts;
    }

    @BeforeAll
    static void up() throws Exception {
        if (result != null) {
            return;
        }
        Path dir = tmp != null ? tmp : Files.createTempDirectory("logical");
        try (Db db = Db.open(dir)) {
            DictStore store = new DictStore(db);
            store.importMoi();
            store.importOrg(Files.readAllBytes(SAMPLE.resolve("org-words.csv")));
            dicts = store.load();
            domains = store.domains();
        }
        List<ColumnInput> in = ColumnInputs.fromCsv(Files.readAllBytes(SAMPLE.resolve("columns-1000.csv")), null);
        result = LogicalRun.run(in, dicts, List.of("TB"), true);
    }

    static Map<String, Object> row(String owner, String table, String col, String name, String src, List<String> missing,
            String dtype, String dlen, String dscale, String pk, String nnull, String ord, boolean isTable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("owner", owner);
        m.put("table", table);
        m.put("col", col);
        m.put("name", name);
        m.put("src", src);
        m.put("missing", missing);
        m.put("dtype", dtype);
        m.put("dlen", dlen);
        m.put("dscale", dscale);
        m.put("pk", pk);
        m.put("nnull", nnull);
        m.put("ord", ord);
        m.put("isTable", isTable);
        return m;
    }

    @Test
    void statsMatchReadme() throws Exception {
        LogicalRun.Stats s = sampleResult().stats();
        assertEquals(new LogicalRun.Stats(944, 104, 589, 71, 368, 20), s);
        assertEquals(List.of(new LogicalRun.Rank("UPD", 208), new LogicalRun.Rank("ORD", 38), new LogicalRun.Rank("TEL", 26),
                new LogicalRun.Rank("EMAIL", 26)), sampleResult().rank().subList(0, 4));
    }

    @Test
    void rowsMatchJsRowByRow() throws Exception {
        JsonNode g = JSON.readTree(GOLDEN.resolve("sample-rows.json").toFile());
        List<Map<String, Object>> jsRows = JSON.convertValue(g.get("rows"), new TypeReference<>() {
        });
        List<Map<String, Object>> jsTables = JSON.convertValue(g.get("tableRows"), new TypeReference<>() {
        });
        // 순수본 TROWS 는 col:'' 이고 dtype 등이 없다(undefined → JSON 에서 빠짐) — 자바 쪽을 같은 모양으로
        jsTables.forEach(m -> {
            m.remove("col");
            m.keySet().retainAll(List.of("owner", "table", "name", "src", "missing", "isTable"));
        });

        List<Map<String, Object>> rows = new ArrayList<>();
        for (LogicalRun.Row r : sampleResult().rows()) {
            rows.add(row(r.owner(), r.table(), r.col(), r.name(), r.src(), r.missing(), r.dtype(), r.dlen(), r.dscale(), r.pk(),
                    r.nnull(), r.ord(), false));
        }
        List<Map<String, Object>> tables = new ArrayList<>();
        for (LogicalRun.TableRow t : sampleResult().tableRows()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("owner", t.owner());
            m.put("table", t.table());
            m.put("name", t.name());
            m.put("src", t.src());
            m.put("missing", t.missing());
            m.put("isTable", true);
            tables.add(m);
        }
        assertEquals(jsRows.size(), rows.size());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(jsRows.get(i), rows.get(i), "컬럼 " + i);
        }
        assertEquals(jsTables, tables);
    }

    @Test
    void rankAndUsedTokensMatchJs() throws Exception {
        JsonNode g = JSON.readTree(GOLDEN.resolve("sample-rank.json").toFile());
        List<List<Object>> jsRank = JSON.convertValue(g.get("rank"), new TypeReference<>() {
        });
        List<List<Object>> rank = new ArrayList<>();
        sampleResult().rank().forEach(r -> rank.add(List.of(r.token(), r.count())));
        assertEquals(jsRank, rank, "랭킹 전체 — 순서까지");

        Map<String, Map<String, String>> jsUsed = JSON.convertValue(g.get("usedTokens"), new TypeReference<>() {
        });
        Map<String, Map<String, String>> used = new TreeMap<>();
        sampleResult().usedTokens().forEach((k, v) -> used.put(k, Map.of("kor", v.kor(), "src", v.src())));
        assertEquals(new TreeMap<>(jsUsed), used);

        List<String> jsWords = JSON.convertValue(g.get("usedWords"), new TypeReference<>() {
        });
        assertEquals(jsWords, sampleResult().usedWords().stream().sorted().toList());
    }
}
