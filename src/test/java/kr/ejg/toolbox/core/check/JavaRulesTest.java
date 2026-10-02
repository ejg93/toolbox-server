package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
 * 5-2 — Java 구조 묶음 ㄴ 을 {@code fixtures/check/java} 양성(Pos*)·음성(Neg*)에 돌린다. 파일 사이 규칙(dupMapping)까지
 * {@link RuleSet.Run#finish()} 로. 음성 히트 0, 켜진 규칙 전부 양성 히트, 파일·줄·규칙 골든.
 */
class JavaRulesTest {

    static final Map<String, Boolean> ONLY_JAVA = Map.of("common", false, "jsp", false, "tsx", false, "file", false,
            "security", false, "mybatis", false, "java", true);

    static Profile profile(String framework, Profile.Naming naming) {
        return new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null, naming,
                new Profile.CodeCheck(ONLY_JAVA, null, null), framework, null, null, null);
    }

    static List<Finding> run(RuleSet rules, Path data) throws Exception {
        RuleSet.Run run = rules.run();
        List<Finding> out = new ArrayList<>();
        for (Source s : CheckRulesTest.fixtureSources(CheckRulesTest.FIXTURES.resolve("java"), data)) {
            out.addAll(run.apply(s));
        }
        out.addAll(run.finish());
        return out;
    }

    @Test
    void fixturesGolden(@TempDir Path data) throws Exception {
        RuleSet rules = RuleSet.load(profile("egov35", null), null);
        List<Finding> found = run(rules, data);
        List<Map<String, Object>> golden = new ArrayList<>();
        for (Finding f : found) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("file", f.file());
            row.put("line", f.line());
            row.put("rule", f.rule());
            golden.add(row);
        }
        assertEquals(List.of(), found.stream().filter(f -> f.file().startsWith("Neg")).map(Finding::toString).toList(), "음성 히트");
        Set<String> missing = new TreeSet<>();
        rules.defs().stream().filter(d -> d.on() && d.group().equals("java")).forEach(d -> missing.add(d.id()));
        missing.removeAll(found.stream().map(Finding::rule).collect(Collectors.toSet()));
        assertEquals(Set.of(), missing, "양성에서 한 번도 안 걸린 규칙");
        GoldenFiles.assertJson("check/java.json", golden);
    }

    /** spring 이면 eGov 전용 둘(상속·@Service 이름)이 꺼진다 */
    @Test
    void springTurnsOffEgovRules(@TempDir Path data) throws Exception {
        List<Finding> found = run(RuleSet.load(profile("spring", null), null), data);
        assertFalse(found.stream().anyMatch(f -> f.rule().equals("java.egovBase") || f.rule().equals("java.serviceName")));
        assertFalse(found.isEmpty());
    }

    /** 프로필 naming 이 명명 규칙을 바꾼다 */
    @Test
    void profileNaming(@TempDir Path data) throws Exception {
        Profile.Naming ctrl = new Profile.Naming("^[A-Z]\\w*Ctrl$", null, null, null, null, null);
        List<String> naming = run(RuleSet.load(profile("egov35", ctrl), null), data).stream()
                .filter(f -> f.rule().equals("java.naming")).map(Finding::file).toList();
        assertEquals(List.of("NegController.java", "PosController.java"), naming);
    }
}
