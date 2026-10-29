package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-36a(설계 41) — OWASP Benchmark Java 1.2 정답 CSV 로 보안 정규식 규칙을 범주별로 채점한다(파일 단위 — 정답에 줄이 없다).
 * 정답 real=true 인 파일에 그 범주에 매핑된 규칙 지적이 하나라도 있으면 TP, 없으면 FN · real=false 인데 있으면 FP, 없으면 TN.
 * 매핑이 빈 범주는 미탐 범주 목록이다. 표본은 GPL-2.0 — 골든에는 시험 이름·건수만.
 */
@Tag("corpus")
class SecurityBenchmarkCorpusTest {

    /** Benchmark 범주 → 우리 규칙(설계 41 §3-4 초기값, R3 가 고친다). 비어 있으면 미탐 범주 */
    static final Map<String, List<String>> MAP = new LinkedHashMap<>();

    static {
        MAP.put("cmdi", List.of("security.osCommand"));
        MAP.put("sqli", List.of("security.sqlConcat"));
        MAP.put("pathtraver", List.of("security.pathTraversal"));
        MAP.put("crypto", List.of("security.weakCipher"));
        MAP.put("hash", List.of("security.weakHash"));
        MAP.put("weakrand", List.of("security.weakRandom"));
        for (String c : List.of("xss", "ldapi", "xpathi", "trustbound", "securecookie")) {
            MAP.put(c, List.of());
        }
    }

    static final String TESTCODE = "src/main/java/org/owasp/benchmark/testcode";

    @TempDir
    Path tmp;

    @Test
    void score() throws Exception {
        CorpusFiles.verify();
        Path root = CorpusFiles.root().resolve("benchmark");
        CheckRunner.RunResult r = CheckRunner.run(root.resolve(TESTCODE).toString(), CheckCorpusTest.rules(false),
                new LocalFiles(tmp.resolve("data")), null);
        List<String> a = new ArrayList<>();
        Map<String, Set<String>> rulesByTest = new HashMap<>();
        for (Finding f : r.findings()) {
            String test = Path.of(f.file()).getFileName().toString().replaceFirst("\\.java$", "");
            if (f.rule().endsWith(".parseError")) {
                a.add(test + ":" + f.line() + " " + f.rule());
            } else if (f.rule().startsWith("security.")) {
                rulesByTest.computeIfAbsent(test, k -> new TreeSet<>()).add(f.rule());
            }
        }
        Map<String, int[]> score = new LinkedHashMap<>(); // tp fp fn tn other
        MAP.keySet().forEach(c -> score.put(c, new int[5]));
        List<String> fp = new ArrayList<>();
        List<String> fn = new ArrayList<>();
        int rows = 0;
        for (String line : Files.readAllLines(root.resolve("expectedresults-1.2.csv"), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            rows++;
            String[] c = line.split(",");
            String test = c[0].trim();
            String cat = c[1].trim();
            boolean real = Boolean.parseBoolean(c[2].trim());
            int[] s = score.computeIfAbsent(cat, k -> new int[5]);
            List<String> mapped = MAP.getOrDefault(cat, List.of());
            Set<String> got = rulesByTest.getOrDefault(test, Set.of());
            boolean hit = got.stream().anyMatch(mapped::contains);
            s[4] += (int) got.stream().filter(x -> !mapped.contains(x)).count();
            if (real && hit) {
                s[0]++;
            } else if (!real && hit) {
                s[1]++;
                fp.add(cat + " " + test);
            } else if (real) {
                s[2]++;
                fn.add(cat + " " + test);
            } else {
                s[3]++;
            }
        }
        Map<String, Object> golden = new LinkedHashMap<>();
        int[] all = new int[5];
        score.forEach((cat, s) -> {
            golden.put(cat, row(MAP.getOrDefault(cat, List.of()), s));
            for (int i = 0; i < 5; i++) {
                all[i] += s[i];
            }
        });
        golden.put("_total", row(List.of(), all));
        GoldenFiles.assertJson("corpus/security-benchmark.json", golden);
        CorpusFiles.conformance("security-benchmark-fp", fp.stream().sorted().toList());
        CorpusFiles.conformance("security-benchmark-fn", fn.stream().sorted().toList());
        CorpusFiles.none("Benchmark 코드 검사(파일 " + r.files() + ")", a, r.files());
        assertEquals(2740, rows, "정답 CSV 행 수");
        assertEquals(rows, r.files(), "검사한 파일 수 = 정답 행 수(못 읽은 파일은 지적 없음으로 TN·FN 에 섞인다)");
        assertEquals(0, r.skipped(), "건너뛴 파일");
    }

    /** 범주 한 줄 — 비율은 백분율 정수, 분모가 0 이면 -1 */
    static Map<String, Object> row(List<String> rules, int... s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rules", rules);
        m.put("tests", s[0] + s[1] + s[2] + s[3]);
        m.put("tp", s[0]);
        m.put("fp", s[1]);
        m.put("fn", s[2]);
        m.put("tn", s[3]);
        m.put("precisionPct", s[0] + s[1] == 0 ? -1 : s[0] * 100 / (s[0] + s[1]));
        m.put("recallPct", s[0] + s[2] == 0 ? -1 : s[0] * 100 / (s[0] + s[2]));
        m.put("otherRules", s[4]);
        return m;
    }
}
