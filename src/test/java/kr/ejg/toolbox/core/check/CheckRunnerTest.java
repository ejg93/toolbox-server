package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.JobContext;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 0-85f — 규칙 사이 취소 · 파일 하나의 스택 넘침은 그 파일만 「못 읽음」 에 센다(검사 전체가 FAILED 가 되지 않게) */
class CheckRunnerTest {

    private static final String RULES_YAML = """
            rules:
              - id: t.deep
                group: common
                severity: warn
                globs: ['*.txt']
                regex: '(?:a|b)*c'
                message: deep
              - id: t.hit
                group: common
                severity: warn
                globs: ['*.txt']
                regex: 'HIT'
                message: hit
            """;

    private static RuleSet rules(Path base) throws Exception {
        Files.writeString(base.resolve("rules.yaml"), RULES_YAML, StandardCharsets.UTF_8);
        return RuleSet.load(CheckRulesTest.profile(new Profile.CodeCheck(Map.of("java", false, "mybatis", false), null, "rules.yaml")), base);
    }

    @Test
    void stackOverflowFileIsSkipped(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("deep.txt"), "ab".repeat(200_000) + "\n", StandardCharsets.UTF_8);
        Files.writeString(src.resolve("ok.txt"), "HIT\n", StandardCharsets.UTF_8);
        CheckRunner.RunResult r = CheckRunner.run(src.toString(), rules(tmp), new LocalFiles(tmp.resolve("data")), null);
        assertEquals(1, r.skipped(), "스택 넘친 파일 하나");
        assertEquals(List.of("ok.txt t.hit"),
                r.findings().stream().filter(f -> f.rule().startsWith("t.")).map(f -> f.file() + " " + f.rule()).toList());
    }

    @Test
    void cancelBetweenRules(@TempDir Path tmp) throws Exception {
        RuleSet.Run run = rules(tmp).run().onRule(() -> {
            throw new JobContext.JobCancelledException();
        });
        Source s = new Source("a.txt", "HIT\n", null, null, null);
        assertThrows(JobContext.JobCancelledException.class, () -> run.apply(s));
    }
}
