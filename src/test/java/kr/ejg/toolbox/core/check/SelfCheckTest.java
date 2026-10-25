package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.profile.Profile;
import kr.ejg.toolbox.core.profile.ProfileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 0-64c — 이 도구가 남에게 거는 기준을 자기 코드에(설계 37). 지적은 기준선에 고정해 새로 생겨도·없어져도 빨강(래칫 — 줄면 갱신),
 * 정리는 바꿀 것이 없어야 하고, 시험은 단언이 있어야 한다. 의도된 지적은 원문의 {@code tb-ignore <규칙> <사유>} 로.
 * 묶음 ext(SpotBugs)는 끈다 — 빌드의 {@code spotbugs:check} 가 같은 엔진을 클래스·메서드 단위 제외({@code config/spotbugs/exclude.xml})와
 * 함께 verify 마다 돌린다(기준 둘을 두지 않는다). {@code sql_snippets.html} 은 순수본 그대로여야 해서 뺀다.
 */
class SelfCheckTest {

    static final Path MAIN = Path.of("src/main").toAbsolutePath();
    static final Path TESTS = Path.of("src/test/java").toAbsolutePath();
    /** 단언을 대신하는 시험 도우미 — 표본 기준선({@code CorpusFiles})과 JPA 표본 묶음 */
    static final List<String> HELPERS = List.of("none", "baseline", "conformance", "jpaCorpus");

    static Profile profile(Path dir, String codecheck) throws Exception {
        Path f = dir.resolve("self.yaml");
        Files.writeString(f, "name: self\nframework: spring\ncodecheck:\n" + codecheck, StandardCharsets.UTF_8);
        return ProfileStore.load(f);
    }

    static Profile mainProfile(Path dir) throws Exception {
        return profile(dir, "  groups: {ext: false}\n  exclude: ['**/sql_snippets.html']\n");
    }

    @Test
    void mainMatchesBaseline(@TempDir Path tmp) throws Exception {
        Profile p = mainProfile(tmp);
        CheckRunner.RunResult r = CheckRunner.run(MAIN.toString(), RuleSet.load(p, null), new LocalFiles(tmp.resolve("data")), null, null,
                p.codecheck().exclude());
        assertEquals(0, r.parseErrors(), "자기 코드가 다 읽혀야 한다");
        CorpusFiles.baseline(GoldenFiles.DIR.resolve("check/self-check.txt"), "self-check", Baseline.keys(r.findings()), Integer.MAX_VALUE,
                GoldenFiles.updating());
    }

    @Test
    void cleanupChangesNothing(@TempDir Path tmp) throws Exception {
        Profile p = mainProfile(tmp);
        CleanupRunner.Preview pv = CleanupRunner.preview(MAIN.toString(), RuleSet.load(p, null), Cleanup.RULES, "LF",
                new LocalFiles(tmp.resolve("data")), null, null, p.codecheck().exclude());
        List<String> skips = new ArrayList<>();
        pv.rows().forEach(row -> row.skips().forEach(s -> skips.add(row.rel() + "|" + s.stage() + "|" + s.reason().replaceFirst("^줄 \\d+: ", ""))));
        CorpusFiles.baseline(GoldenFiles.DIR.resolve("check/self-cleanup.txt"), "self-cleanup", skips, Integer.MAX_VALUE,
                GoldenFiles.updating());
        assertEquals(0, pv.changed(), "코드 정리가 고칠 것이 남았다 — " + pv.rows());
    }

    @Test
    void everyTestAsserts(@TempDir Path tmp) throws Exception {
        Profile p = profile(tmp, "  groups: {common: false, security: false, metric: false, file: false, jsp: false, tsx: false, a: false,"
                + " mybatis: false, ext: false}\n  rules:\n    java.testNoAssert: {params: {helpers: " + HELPERS + "}}\n");
        CheckRunner.RunResult r = CheckRunner.run(TESTS.toString(), RuleSet.load(p, null), new LocalFiles(tmp.resolve("data")), null);
        List<String> hits = r.findings().stream().filter(f -> f.rule().equals("java.testNoAssert")).map(f -> f.file() + ":" + f.line() + " " + f.excerpt())
                .toList();
        assertEquals(List.of(), hits, "단언 없는 시험 — 단언을 넣거나, 단언하는 도우미면 HELPERS 에");
    }
}
