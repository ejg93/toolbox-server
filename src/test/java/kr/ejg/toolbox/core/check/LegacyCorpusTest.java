package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
import kr.ejg.toolbox.core.analyze.AnalyzeRunner;
import kr.ejg.toolbox.core.analyze.Unresolved;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-35(설계 41) — 레거시 표본 넷(jeecg·jeesite·zheng·ruoyi-classic)에 코드 검사·프로그램 분석을 돌려 숫자를 얼린다.
 * {@code .parseError}·예외는 등급 A — baseline 없이 0(설계 42 V-38a, R1 의 {@code legacy-a} 목록을 승격).
 * 결과의 발췌는 싣지 않는다 — 경로·줄·규칙만.
 */
@Tag("corpus")
class LegacyCorpusTest {

    static final List<String> SOURCES = List.of("jeecg", "jeesite", "zheng", "ruoyi-classic");

    @TempDir
    Path tmp;

    @Test
    void checkAndAnalyze() throws Exception {
        CorpusFiles.verify();
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        RuleSet rules = CheckCorpusTest.rules(false);
        List<String> aCandidates = new ArrayList<>();
        int total = 0;
        for (String s : SOURCES) {
            Path dir = CorpusFiles.root().resolve(s);
            try {
                total += check(s, dir, rules, files, aCandidates);
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException | IndexOutOfBoundsException e) {
                aCandidates.add(s + " 검사 예외 " + e.getClass().getSimpleName());
            }
            try {
                analyze(s, dir, new LocalFiles(tmp.resolve("data-" + s)));
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException | IndexOutOfBoundsException e) {
                aCandidates.add(s + " 분석 예외 " + e.getClass().getSimpleName());
            }
        }
        CorpusFiles.none("레거시 코드 검사·분석(파일 " + total + ")", aCandidates, total); // 설계 42 §3-10 — 등급 A 게이트
        assertTrue(total > 1500, "레거시 표본 파일이 너무 적다: " + total);
    }

    /** CheckCorpusTest 와 같은 집계 — 규칙별 건수·파일 수 골든 + 히트 파일 목록. parseError 는 A 후보 */
    private static int check(String s, Path dir, RuleSet rules, LocalFiles files, List<String> aCandidates) throws Exception {
        CheckRunner.RunResult r = CheckRunner.run(dir.toString(), rules, files, null);
        Map<String, int[]> counts = new TreeMap<>();
        Map<String, Set<String>> filesPerRule = new TreeMap<>();
        Set<String> hits = new TreeSet<>();
        for (Finding f : r.findings()) {
            String where = s + "/" + f.file();
            if (f.rule().endsWith(".parseError")) {
                aCandidates.add(where + ":" + f.line() + " " + f.rule());
                continue;
            }
            counts.computeIfAbsent(f.rule(), k -> new int[1])[0]++;
            filesPerRule.computeIfAbsent(f.rule(), k -> new TreeSet<>()).add(where);
            hits.add(where);
        }
        Map<String, Object> golden = new TreeMap<>();
        counts.forEach((rule, c) -> golden.put(rule, Map.of("count", c[0], "files", filesPerRule.get(rule).size())));
        golden.put("_files", r.files());
        golden.put("_truncated", r.truncated());
        GoldenFiles.assertJson("corpus/check-" + s + ".json", golden);
        CorpusFiles.conformance("check-" + s + "-hits", new ArrayList<>(hits));
        return r.files();
    }

    /** AnalyzeCorpusTest.ruoyiRisks 와 같은 요약 키 + 미해결 종류별 수 */
    private static void analyze(String s, Path dir, LocalFiles files) throws Exception {
        AnalyzeRunner.Result a = AnalyzeRunner.run(dir.toString(), files, null, null);
        Map<String, Integer> unresolved = new TreeMap<>();
        for (Unresolved u : a.unresolved()) {
            unresolved.merge(u.kind(), 1, Integer::sum);
        }
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("files", a.files());
        sum.put("programs", a.rows().size());
        sum.put("withCrud", a.rows().stream().filter(x -> !x.crud().isEmpty()).count());
        sum.put("statements", a.statements());
        sum.put("tables", a.tables().size());
        sum.put("risks", a.risks().size());
        sum.put("reachedRisks", a.risks().stream().filter(k -> !k.programs().isEmpty()).count());
        sum.put("unresolved", unresolved);
        GoldenFiles.assertJson("corpus/analyze-" + s + ".json", sum);
    }
}
