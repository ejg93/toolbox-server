package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;

/**
 * 5-43 — 외부 분석기 권장 수정 표. 「상」 57 유형만(사용자 2026-10-24) — 유형 이름은 sonar-findbugs 표(5-38e)의 같은 엔진 절에 있어야 하고,
 * 문구는 내장 규칙 fix 와 같은 60자 상한. 중·하로 판정해 비운 유형은 없어야 한다
 */
class ExtFixesTest {

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, String>> sonarSections() throws Exception {
        return new ObjectMapper(new YAMLFactory()).readValue(SpotBugsSeverityTest.TABLE.toFile(), Map.class);
    }

    @Test
    void tableHasOnlyCertainTypesThatExist() throws Exception {
        Map<String, String> t = ExtFixes.builtin();
        assertEquals(57, t.size(), "상 57(SpotBugs 32 · FindSecBugs 25)");
        assertEquals(32, t.keySet().stream().filter(k -> k.startsWith("spotbugs.")).count());
        Map<String, Map<String, String>> sonar = sonarSections();
        t.forEach((k, fix) -> {
            String type = k.substring(k.indexOf('.') + 1);
            String section = k.startsWith("spotbugs.") ? "findbugs" : "findsecbugs";
            assertTrue(sonar.get(section).containsKey(type), k + " — sonar 표의 " + section + " 절에 없다(오타·엔진 틀림)");
            assertTrue(!fix.isBlank() && fix.length() <= 60, k + " 문구 " + fix.length() + "자");
        });
        for (String notCertain : List.of("spotbugs.ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", "findsecbugs.SPRING_CSRF_PROTECTION_DISABLED",
                "spotbugs.IS2_INCONSISTENT_SYNC", "spotbugs.NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", "spotbugs.CT_CONSTRUCTOR_THROW",
                "findsecbugs.OBJECT_DESERIALIZATION")) {
            assertFalse(t.containsKey(notCertain), notCertain + " 은 중·하 — 비운다");
        }
        assertEquals("PreparedStatement 의 ? 바인딩으로 값을 넘긴다", t.get("findsecbugs.SQL_INJECTION_JDBC"));
    }

    /** 프로필 codecheck.rules 의 ext.* params.fixes — 더하고 덮고, 빈 문구는 지운다 */
    @Test
    void profileParamsFixesOverride() {
        Map<String, Object> over = Map.of(SpotBugsRun.SPOTBUGS, Map.of("params", Map.of("fixes",
                Map.of("NP_ALWAYS_NULL", "null 을 넘기지 않는다", "EI_EXPOSE_REP", "사업 규약대로", "MS_SHOULD_BE_FINAL", ""))));
        Profile p = new Profile("p", null, null, null, null, null, null, new Profile.CodeCheck(null, over, null), "spring", null, null, null);
        Map<String, String> m = ExtFixes.of(RuleSet.load(p, null));
        assertEquals("null 을 넘기지 않는다", m.get("spotbugs.NP_ALWAYS_NULL"));
        assertEquals("사업 규약대로", m.get("spotbugs.EI_EXPOSE_REP"));
        assertFalse(m.containsKey("spotbugs.MS_SHOULD_BE_FINAL"), "빈 문구는 지운다");
        assertEquals(ExtFixes.builtin().get("findsecbugs.PATH_TRAVERSAL_IN"), m.get("findsecbugs.PATH_TRAVERSAL_IN"), "다른 엔진은 그대로");
        assertEquals(ExtFixes.builtin(), ExtFixes.of(RuleSet.builtin()), "덮기 없으면 표 그대로");
        // 마무리 리뷰 — 글자가 아닌 값·null 키는 버린다(「false」 문구로 새지 않게)
        Map<Object, Object> odd = new java.util.HashMap<>();
        odd.put("SF_SWITCH_NO_DEFAULT", Boolean.FALSE);
        odd.put("DM_DEFAULT_ENCODING", List.of("a"));
        odd.put(null, "x");
        Profile q = new Profile("q", null, null, null, null, null, null,
                new Profile.CodeCheck(null, Map.of(SpotBugsRun.SPOTBUGS, Map.of("params", Map.of("fixes", odd))), null), "spring", null, null, null);
        Map<String, String> n = ExtFixes.of(RuleSet.load(q, null));
        assertEquals(ExtFixes.builtin().get("spotbugs.SF_SWITCH_NO_DEFAULT"), n.get("spotbugs.SF_SWITCH_NO_DEFAULT"));
        assertEquals(ExtFixes.builtin().get("spotbugs.DM_DEFAULT_ENCODING"), n.get("spotbugs.DM_DEFAULT_ENCODING"));
        assertFalse(n.containsKey("spotbugs.null"));
    }
}
