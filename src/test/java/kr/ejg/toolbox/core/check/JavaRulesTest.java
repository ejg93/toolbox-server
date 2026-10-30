package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
            if (s.rel().startsWith("link/")) {
                continue; // 설계 43 — 메서드 사이 픽스처는 TaintTest.linkThroughRuleSet 이 잰다
            }
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
        rules.defs().stream().filter(d -> d.on() && Set.of("java", "metric").contains(d.group()) && d.kind().equals("java"))
                .forEach(d -> missing.add(d.id()));
        missing.removeAll(found.stream().map(Finding::rule).collect(Collectors.toSet()));
        missing.remove(JavaRules.CLASS_LENGTH); // 1,000줄 픽스처 대신 classLengthOverride 가 잰다
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
        assertEquals(List.of("NegController.java", "NegParams.java", "PosController.java"), naming);
    }

    /** 5-32b — params.ignoreModifiers: 접근자 순서를 안 보고, 생성자·메서드 전의 「인스턴스 뒤 static」 도 허용. 같은 픽스처를 덮어쓰기 프로필로 */
    @Test
    void declarationOrderIgnoreModifiers(@TempDir Path data) throws Exception {
        Profile p = new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null, null,
                new Profile.CodeCheck(ONLY_JAVA, Map.of(JavaRules.ORDER, Map.of("params", Map.of("ignoreModifiers", true))), null),
                "egov35", null, null, null);
        List<Map<String, Object>> golden = new ArrayList<>();
        for (Finding f : run(RuleSet.load(p, null), data)) {
            if (f.rule().equals(JavaRules.ORDER)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("file", f.file());
                row.put("line", f.line());
                golden.add(row);
            }
        }
        GoldenFiles.assertJson("check/java-order-ignore.json", golden);
    }

    /** 5-35a — classLength 는 기준을 20 으로 덮어써 PosMetricClass(35줄)에 걸고, 발췌에 측정값·기준·클래스 이름 */
    @Test
    void classLengthOverride(@TempDir Path data) throws Exception {
        Profile p = new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null, null,
                new Profile.CodeCheck(ONLY_JAVA, Map.of(JavaRules.CLASS_LENGTH, Map.of("params", Map.of("max", 20))), null),
                "egov35", null, null, null);
        List<String> hits = run(RuleSet.load(p, null), data).stream().filter(f -> f.rule().equals(JavaRules.CLASS_LENGTH))
                .map(f -> f.file() + ":" + f.line() + " " + f.excerpt()).toList();
        assertTrue(hits.contains("PosMetricClass.java:4 길이 126 > 20 · PosMetricClass"), hits.toString()); // 4~129줄
    }

    /** 5-32b — 우리 코드(0-61e 가 Checkstyle 로 0 을 보증)는 이 규칙으로도 0 — 음성 303 파일 */
    @Test
    void ownSourcesAreInOrder() throws Exception {
        // 마무리 리뷰 — parseError 도 켠다. 못 읽은 파일이 지적 0 으로 조용히 통과하지 않게
        List<Rule.Def> defs = RuleSet.builtin().defs().stream()
                .filter(d -> d.id().equals(JavaRules.ORDER) || d.id().equals(JavaRules.PARSE_ERROR)).toList();
        List<String> bad = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"))) {
            try (Stream<Path> s = Files.walk(root)) {
                for (Path p : s.filter(x -> x.toString().endsWith(".java")).sorted().toList()) {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    for (Finding f : new JavaRules(defs, null).apply(new Source(rel, Files.readString(p, StandardCharsets.UTF_8), "UTF-8", "LF", "java"))) {
                        bad.add(rel + ":" + f.line() + " " + f.rule());
                    }
                }
            }
        }
        assertEquals(List.of(), bad, "0-61e 와 같은 판정이어야 한다");
    }
}
