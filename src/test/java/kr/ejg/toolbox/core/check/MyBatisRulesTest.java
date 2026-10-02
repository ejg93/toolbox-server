package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-3 — MyBatis XML 묶음 ㄷ 을 {@code fixtures/check/mybatis} 양성(Pos*)·음성(Neg*)에 돌린다. 음성에는 주석 안 {@code ${}}·
 * 페이징 래퍼·iBATIS sqlMap·FQCN namespace 가 있다. namespace 는 같은 폴더 {@code src/} 의 Java 와 견준다.
 */
class MyBatisRulesTest {

    static final Map<String, Boolean> ONLY_MYBATIS = Map.of("common", false, "jsp", false, "tsx", false, "file", false,
            "security", false, "java", false, "mybatis", true);

    static List<Finding> run(RuleSet rules, Path data) throws Exception {
        RuleSet.Run run = rules.run();
        List<Finding> out = new ArrayList<>();
        for (Source s : CheckRulesTest.fixtureSources(CheckRulesTest.FIXTURES.resolve("mybatis"), data)) {
            out.addAll(run.apply(s));
        }
        out.addAll(run.finish());
        return out;
    }

    static RuleSet rules() {
        return RuleSet.load(new Profile("t", null, null, null, null, null, null, new Profile.CodeCheck(ONLY_MYBATIS, null, null),
                "egov35", null, null, null), null);
    }

    @Test
    void fixturesGolden(@TempDir Path data) throws Exception {
        RuleSet rules = rules();
        List<Finding> found = run(rules, data);
        List<Map<String, Object>> golden = new ArrayList<>();
        for (Finding f : found) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("file", f.file());
            row.put("line", f.line());
            row.put("rule", f.rule());
            golden.add(row);
        }
        assertEquals(List.of(), found.stream().filter(f -> f.file().replaceAll(".*/", "").startsWith("Neg")).map(Finding::toString)
                .toList(), "음성 히트");
        Set<String> missing = new TreeSet<>();
        rules.defs().stream().filter(d -> d.on() && d.group().equals("mybatis")).forEach(d -> missing.add(d.id()));
        missing.removeAll(found.stream().map(Finding::rule).collect(Collectors.toSet()));
        assertEquals(Set.of(), missing, "양성에서 한 번도 안 걸린 규칙");
        GoldenFiles.assertJson("check/mybatis.json", golden);
    }

    /** Java 를 하나도 못 본 실행(붙여넣기)은 namespace 를 안 잰다 */
    @Test
    void namespaceNeedsJavaSources() {
        RuleSet.Run run = rules().run();
        run.apply(Source.pasted("<mapper namespace=\"X\"><select id=\"a\">SELECT 1</select></mapper>", "xml"));
        assertEquals(List.of(), run.finish());
    }
}
