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
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-11 — 코드 검사(5-1~5-4)를 실물 표본 출처 폴더마다 묶음 전부로 돌린다(PLAN 4장·10장 M5). eGov 출처는 egov35, 나머지는 spring.
 * <ul>
 *   <li>등급 A: 규칙 예외(실행이 멈춤) · {@code java.parseError} · {@code mybatis.parseError} — 표본은 전부 컴파일·배포된 코드라
 *       못 읽으면 검사기 결함. JavaParser 자체 한계로 판정한 파일은 {@code check-parse-limit} 목록(사유는 이력)</li>
 *   <li>등급 B: 규칙별 건수·파일 수 골든 {@code check-<무리>.json} + 히트 파일 목록 {@code check-<무리>-hits}. 시간은 안 잰다</li>
 * </ul>
 * 결과의 발췌는 공개 표본이라도 보고에 싣지 않는다 — 경로·줄·규칙만.
 */
@Tag("corpus")
class CheckCorpusTest {

    /** 무리 → 출처들. 골든은 무리마다 하나(행: eGov·commons·jspwiki·roller). struts·egov-react 는 A 만 */
    static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("egov", List.of("egov", "egov-portal", "egov-enterprise", "egov-homepage"));
        GROUPS.put("commons", List.of("commons-lang", "commons-io", "commons-collections"));
        GROUPS.put("jspwiki", List.of("jspwiki"));
        GROUPS.put("roller", List.of("roller"));
        GROUPS.put("other", List.of("struts", "egov-react"));
    }

    @TempDir
    Path tmp;

    static RuleSet rules(boolean egov) {
        Profile p = new Profile("corpus", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null,
                new Profile.Naming(null, null, null, null, null, egov), new Profile.CodeCheck(null, null, null), egov ? "egov35" : "spring",
                null, null, null);
        return RuleSet.load(p, null);
    }

    @Test
    void allSources() throws Exception {
        CorpusFiles.verify();
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        List<String> a = new ArrayList<>();
        Set<String> parseLimit = new TreeSet<>();
        int total = 0;
        for (Map.Entry<String, List<String>> g : GROUPS.entrySet()) {
            boolean egov = g.getKey().equals("egov");
            RuleSet rules = rules(egov);
            Map<String, int[]> counts = new TreeMap<>();
            Map<String, Set<String>> filesPerRule = new TreeMap<>();
            Set<String> hits = new TreeSet<>();
            for (String source : g.getValue()) {
                Path dir = CorpusFiles.root().resolve(source);
                CheckRunner.RunResult r;
                try {
                    r = CheckRunner.run(dir.toString(), rules, files, null);
                } catch (RuntimeException e) {
                    a.add(source + " 예외 " + e);
                    continue;
                }
                total += r.files();
                for (Finding f : r.findings()) {
                    String where = source + "/" + f.file();
                    if (f.rule().endsWith(".parseError")) {
                        if (JAVAPARSER_LIMIT.contains(where)) {
                            parseLimit.add(where);
                        } else {
                            a.add(where + ":" + f.line() + " " + f.rule());
                        }
                        continue;
                    }
                    counts.computeIfAbsent(f.rule(), k -> new int[1])[0]++;
                    filesPerRule.computeIfAbsent(f.rule(), k -> new TreeSet<>()).add(where);
                    hits.add(where);
                }
            }
            if (g.getKey().equals("other")) {
                continue;
            }
            Map<String, Map<String, Integer>> golden = new TreeMap<>();
            counts.forEach((rule, c) -> golden.put(rule, Map.of("count", c[0], "files", filesPerRule.get(rule).size())));
            GoldenFiles.assertJson("corpus/check-" + g.getKey() + ".json", golden);
            CorpusFiles.conformance("check-" + g.getKey() + "-hits", new ArrayList<>(hits));
        }
        CorpusFiles.conformance("check-parse-limit", new ArrayList<>(parseLimit));
        CorpusFiles.none("코드 검사(파일 " + total + ")", a, total);
    }

    /** JavaParser 가 못 읽는 실물(라이브러리 한계 — 이력에 사유). 비어 있으면 한계가 없다 */
    static final Set<String> JAVAPARSER_LIMIT = Set.of();
}
