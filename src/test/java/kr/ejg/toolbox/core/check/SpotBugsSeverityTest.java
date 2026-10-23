package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/**
 * 5-38e — 우리 등급(SpotBugs 우선순위 1·2·3 → error·warn·info)과 sonar-findbugs v4.6.0 규칙 등급(BLOCKER·CRITICAL → error, MAJOR → warn,
 * MINOR·INFO → info)을 지적마다 견줘 다른 BUG_TYPE 을 목록으로 굳힌다. 등급 규칙은 안 바꾼다 — 사용자가 고를 재료(설계 34 §4).
 * 우선순위 3 까지·제외 없이 돌린다(우리가 기본으로 숨기는 것도 보이게). 표본 쪽은 {@link SpotBugsCorpusTest#severityVsSonar}
 */
class SpotBugsSeverityTest {

    static final Path TABLE = Path.of("src/test/resources/fixtures/check/ext/sonar-findbugs-v4.6.0.yaml");

    /** findbugs.·findsecbugs. 접두를 뗀 BUG_TYPE → sonar 등급 */
    @SuppressWarnings("unchecked")
    static Map<String, String> sonar() throws Exception {
        Map<String, Map<String, String>> raw = new ObjectMapper(new YAMLFactory()).readValue(TABLE.toFile(), Map.class);
        Map<String, String> m = new TreeMap<>();
        raw.values().forEach(m::putAll);
        return m;
    }

    static String mapped(String sonar) {
        return switch (sonar) {
            case "BLOCKER", "CRITICAL" -> "error";
            case "MAJOR" -> "warn";
            default -> "info";
        };
    }

    /** 다른 것만 — 「규칙 우리→sonar(원래 등급) 건수」, 표에 없는 BUG_TYPE 은 「sonar 없음」 */
    static List<String> diff(List<Finding> findings, Map<String, String> sonar) {
        Map<String, Integer> n = new TreeMap<>();
        for (Finding f : findings) {
            String type = f.rule().substring(f.rule().indexOf('.') + 1);
            String s = sonar.get(type);
            if (s == null) {
                n.merge(f.rule() + " " + f.severity() + "→sonar 없음", 1, Integer::sum);
            } else if (!mapped(s).equals(f.severity())) {
                n.merge(f.rule() + " " + f.severity() + "→" + mapped(s) + "(" + s + ")", 1, Integer::sum);
            }
        }
        List<String> out = new ArrayList<>();
        n.forEach((k, v) -> out.add(k + " " + v));
        return out;
    }

    static List<Rule.Def> all() {
        return List.of(SpotBugsRunTest.def(SpotBugsRun.SPOTBUGS, Map.of("minPriority", 3)), SpotBugsRunTest.def(SpotBugsRun.FINDSECBUGS, Map.of("minPriority", 3)));
    }

    @Test
    void tableLoads() throws Exception {
        Map<String, String> s = sonar();
        assertEquals(497 + 129, s.size(), "v4.6.0 규칙 수");
        assertEquals("CRITICAL", s.get("IMPROPER_UNICODE"), "우리 기본 제외가 sonar 에서는 CRITICAL");
        assertEquals("MAJOR", s.get("NP_ALWAYS_NULL"));
    }

    @Test
    void fixtures() throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assumeTrue(javac != null, "JDK 컴파일러 없음");
        Path work = Path.of("target", "spotbugs-severity-test");
        Path classes = work.resolve("classes");
        if (Files.isDirectory(classes)) {
            try (Stream<Path> s = Files.walk(classes)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        Files.createDirectories(classes);
        assertEquals(0, javac.run(null, null, null, "-g", "-encoding", "UTF-8", "-d", classes.toString(),
                SpotBugsRunTest.SRC.resolve("Pos.java").toString(), SpotBugsRunTest.SRC.resolve("Neg.java").toString()));
        SpotBugsRun.Result r = SpotBugsRun.run(SpotBugsRunTest.SRC.getParent(), classes, null, Analyzers.findSecBugsJar(work.resolve("data")), all(), null);
        assertTrue(!r.findings().isEmpty());
        GoldenFiles.assertText("check/ext-severity-diff.txt", String.join("\n", diff(r.findings(), sonar())) + "\n");
    }
}
