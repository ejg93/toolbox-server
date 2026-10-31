package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * V-43a(설계 47 §3-19) — sqlfluff 4.4.0 규칙 사례({@code std_rule_cases/*.yml})를 매퍼 SQL 판정({@link SqlLint})에 맞댄다.
 * CV05 → NULL_COMPARE · AM08 → CROSS_JOIN · AM09 → PAGING_NO_ORDER. fail_str 은 걸려야, pass_str 은 안 걸려야 일치.
 * 방언 설정(configs)·고친 글(fix_str)은 안 본다. 어긋남은 목록으로 얼리고(새로 깨짐·고쳐짐 빨강), 예외는 등급 A.
 */
@Tag("corpus")
class SqlRuleCasesCorpusTest {

    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    static final Map<String, SqlLint.Kind> RULES = Map.of("CV05", SqlLint.Kind.NULL_COMPARE, "AM08", SqlLint.Kind.CROSS_JOIN,
            "AM09", SqlLint.Kind.PAGING_NO_ORDER);
    static final String FAIL = "fail_str";
    static final String PASS = "pass_str";

    @Test
    void casesAgree() throws Exception {
        CorpusFiles.verify();
        Map<String, Object> golden = new TreeMap<>();
        List<String> diff = new ArrayList<>();
        List<String> a = new ArrayList<>();
        int total = 0;
        for (Path yml : CorpusFiles.files("sqlfluff-dialects", "test/fixtures/rules/std_rule_cases/{CV05,AM08,AM09}.yml")) {
            String rule = yml.getFileName().toString().replace(".yml", "");
            SqlLint.Kind kind = RULES.get(rule);
            int cases = 0;
            int agree = 0;
            Iterator<Map.Entry<String, JsonNode>> it = YAML.readTree(yml.toFile()).fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode c = e.getValue();
                boolean fail = c.has(FAIL);
                if (!fail && !c.has(PASS)) {
                    continue; // rule: 머리 줄
                }
                cases++;
                String sql = c.get(fail ? FAIL : PASS).asText();
                try {
                    boolean hit = SqlLint.check(RegexRule.stripComments(sql, "sql"), false).stream().anyMatch(h -> h.kind() == kind);
                    if (hit == fail) {
                        agree++;
                    } else {
                        diff.add(rule + "/" + e.getKey() + " 기대=" + (fail ? "hit" : "pass") + " 우리=" + (hit ? "hit" : "pass"));
                    }
                } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException ex) {
                    a.add(rule + "/" + e.getKey() + " " + ex.getClass().getSimpleName());
                }
            }
            Map<String, Integer> row = new LinkedHashMap<>();
            row.put("cases", cases);
            row.put("agree", agree);
            golden.put(rule, row);
            total += cases;
        }
        assertTrue(total > 40, "규칙 사례가 너무 적다: " + total);
        GoldenFiles.assertJson("corpus/sqlrules.json", golden);
        CorpusFiles.conformance("sqlrules-diff", diff);
        CorpusFiles.none("sqlfluff 규칙 사례(사례 " + total + ")", a, total);
    }
}
