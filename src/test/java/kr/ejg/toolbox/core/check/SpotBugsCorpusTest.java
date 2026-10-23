package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 5-38b — 컴파일된 표본(설계 34 §2, MANIFEST 밖 — 없으면 건너뛴다)에 묶음 ㅅ 기본 규칙(우선순위 2 이상)을 돌린다.
 * A 불변식: 예외 없음 · 지적의 소스 파일이 표본에 있다 ≥ 95% · 줄이 있다 ≥ 95%. B: BUG_TYPE·등급 건수 골든 + 히트 파일 목록(conformance).
 * jspwiki-bin 은 소스 표본 jspwiki 와 같은 판(2.12.5) — 매핑률을 잰다. ruoyi-bin 은 MyBatis 앱에서 보안 패턴이 실제로 걸리는가
 */
@Tag("corpus")
class SpotBugsCorpusTest {

    static final double MAPPED = 0.95;

    @Test
    void jspwiki() throws Exception {
        Map<String, Integer> byType = run("jspwiki-bin", "jspwiki", "spotbugs-jspwiki");
        assertTrue(!byType.isEmpty(), "jspwiki 지적 0");
    }

    @Test
    void ruoyi() throws Exception {
        Map<String, Integer> byType = run("ruoyi-bin", "ruoyi", "spotbugs-ruoyi");
        int sec = byType.entrySet().stream().filter(e -> e.getKey().startsWith("findsecbugs.SQL_") || e.getKey().startsWith("findsecbugs.PATH_TRAVERSAL"))
                .mapToInt(Map.Entry::getValue).sum();
        assertTrue(sec >= 1, "MyBatis 앱에서 FindSecBugs SQL·경로 패턴 0 — " + byType);
    }

    /** @return 규칙별 건수 */
    static Map<String, Integer> run(String bin, String source, String golden) throws Exception {
        Path root = CorpusFiles.root();
        Path classes = root.resolve(bin).resolve("classes");
        assumeTrue(Files.isDirectory(classes), bin + " 없음 — bash scripts/corpus-fetch.sh " + bin);
        Path src = root.resolve(source);
        Path plugin = Analyzers.findSecBugsJar(Path.of("target", "spotbugs-corpus-test"));
        long t0 = System.nanoTime();
        SpotBugsRun.Result r = SpotBugsRun.run(src, classes, root.resolve(bin).resolve("lib"), plugin, RuleSet.builtin().external(), null);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(r.note() == null, r.note());
        List<Finding> fs = r.findings();
        List<String> unmapped = new ArrayList<>();
        TreeSet<String> hits = new TreeSet<>();
        Map<String, Integer> byType = new TreeMap<>();
        Map<String, Integer> bySeverity = new TreeMap<>();
        for (Finding f : fs) {
            if (Files.isRegularFile(src.resolve(f.file()))) {
                hits.add(f.file());
            } else {
                unmapped.add(f.file() + " " + f.rule());
            }
            byType.merge(f.rule(), 1, Integer::sum);
            bySeverity.merge(f.severity(), 1, Integer::sum);
        }
        int n = fs.size();
        System.out.println(bin + ": 클래스 " + r.classes() + " · 지적 " + n + " · 못 찾은 파일 " + unmapped.size() + " · 줄 없음 " + r.noLine()
                + " · 못 본 참조 " + r.missingClasses() + " · " + ms + "ms");
        assertTrue(n == 0 || unmapped.size() <= n * (1 - MAPPED), bin + ": 소스 파일 매핑 " + (n - unmapped.size()) + "/" + n + " < 95% — "
                + unmapped.subList(0, Math.min(CorpusFiles.REPORT, unmapped.size())));
        assertTrue(n == 0 || r.noLine() <= n * (1 - MAPPED), bin + ": 줄 있는 지적 " + (n - r.noLine()) + "/" + n + " < 95%");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("classes", r.classes());
        summary.put("findings", n);
        summary.put("severity", bySeverity);
        summary.put("rules", byType);
        GoldenFiles.assertJson("corpus/" + golden + ".json", summary);
        CorpusFiles.conformance(golden + "-hits", new ArrayList<>(hits));
        return byType;
    }
}
