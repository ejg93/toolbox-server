package kr.ejg.toolbox.core.check;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-34b — 코드 정리(A 일곱)를 실물 표본에 돌린다(쓰지 않음). 무리는 {@link CheckCorpusTest} 와 같다.
 * <ul>
 *   <li>등급 A: 예외 · 「구문이 깨짐」·「재검 실패」 되돌림 · 끝 지적에 구문 오류가 늘어남 · 지적이 늘어남 ·
 *       ③ 만 적용한 파일의 줄 수·줄 다중집합·토큰 다중집합이 다름(옮기기만 했나 — 시험이 따로 잰다)</li>
 *   <li>등급 B: 무리마다 {files, changed, steps: 단계 → 파일 수, skips: 사유 → 건수} 골든 {@code cleanup-<무리>.json}</li>
 * </ul>
 */
@Tag("corpus")
class CleanupCorpusTest {

    static final List<String> GLOBS = List.of("*.java", "*.jsp", "*.jspf", "*.html", "*.htm", "*.js", "*.jsx", "*.ts", "*.tsx", "*.mjs");

    @TempDir
    Path tmp;

    @Test
    void allSources() throws Exception {
        CorpusFiles.verify();
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        List<String> a = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, List<String>> g : CheckCorpusTest.GROUPS.entrySet()) {
            if (g.getKey().equals("other")) {
                continue;
            }
            Cleanup c = new Cleanup(CheckCorpusTest.rules(g.getKey().equals("egov")), Cleanup.RULES, "LF");
            int n = 0;
            int changed = 0;
            Map<String, Integer> steps = new TreeMap<>();
            Map<String, Integer> skips = new TreeMap<>();
            for (String source : g.getValue()) {
                for (Path p : CorpusFiles.files(source, GLOBS.toArray(String[]::new))) {
                    String rel = CorpusFiles.rel(p);
                    LocalFiles.Text t;
                    try {
                        t = files.read(p.toString());
                    } catch (Exception e) {
                        continue;
                    }
                    n++;
                    Source s = new Source(rel, t.text(), t.encoding(), t.lineEnding(), null);
                    Cleanup.Result r;
                    try {
                        r = c.clean(s);
                    } catch (RuntimeException e) {
                        a.add(rel + " 예외 " + e);
                        continue;
                    }
                    changed += r.changed() ? 1 : 0;
                    Set<String> stages = new TreeSet<>();
                    r.steps().forEach(x -> stages.add(x.stage()));
                    stages.forEach(x -> steps.merge(x, 1, Integer::sum));
                    for (Cleanup.Skip k : r.skips()) {
                        String why = k.stage() + " " + k.reason().replaceFirst("^줄 \\d+: ", "");
                        skips.merge(why.replaceFirst("^(\\S+ 다른 지적이 (?:생김|사라짐)): .*$", "$1"), 1, Integer::sum);
                        if (k.reason().equals("구문이 깨짐") || k.reason().equals(OrderFix.RECHECK_FAILED)) {
                            a.add(rel + " " + k.stage() + " " + k.reason());
                        }
                    }
                    if (CleanupTest.parseErrors(r.after()) > CleanupTest.parseErrors(r.before())) {
                        a.add(rel + " 구문 오류가 늘어남");
                    }
                    if (r.after().size() > r.before().size()) {
                        a.add(rel + " 지적이 늘어남 " + r.before().size() + " → " + r.after().size());
                    }
                    if (stages.equals(Set.of(Cleanup.ORDER)) || stages.equals(Set.of(Cleanup.ORDER, Cleanup.EOL))) {
                        try {
                            CleanupOrderTest.assertOnlyMoved(rel, s.text(), r.text());
                        } catch (AssertionError e) {
                            a.add(rel + " 옮기기만이 아님 " + e.getMessage().lines().findFirst().orElse(""));
                        }
                    }
                }
            }
            total += n;
            Map<String, Object> golden = new LinkedHashMap<>();
            golden.put("files", n);
            golden.put("changed", changed);
            golden.put("steps", steps);
            golden.put("skips", skips);
            GoldenFiles.assertJson("corpus/cleanup-" + g.getKey() + ".json", golden);
        }
        CorpusFiles.none("코드 정리(파일 " + total + ")", a, total);
    }
}
