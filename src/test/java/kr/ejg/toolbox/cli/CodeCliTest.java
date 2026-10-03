package kr.ejg.toolbox.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import kr.ejg.toolbox.core.analyze.AnalyzeRunner;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 8-6 — check·analyze·deploy-list. 폴더는 임시 폴더로 복사한 픽스처(서버 data 폴더 거절 목록을 피한다) */
class CodeCliTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tmp;

    CliFixture fx;

    @BeforeEach
    void up() throws Exception {
        fx = new CliFixture(tmp);
        CliFixture.copy(Path.of("src/test/resources/fixtures/check"), tmp.resolve("check"));
        CliFixture.copy(Path.of("src/test/resources/fixtures/analyze/java"), tmp.resolve("proj/src/main/java"));
        CliFixture.copy(Path.of("src/test/resources/fixtures/analyze/mapper"), tmp.resolve("proj/src/main/resources/mapper"));
    }

    @AfterEach
    void down() throws Exception {
        fx.close();
    }

    @Test
    void checkFailOnAndXlsx() throws Exception {
        CliFixture.Run r = fx.run("check", "check", "--profile", "t");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().startsWith("검사 "), r.out());
        assertTrue(!r.out().contains("error 0 "), "픽스처 양성에 error 등급이 있다: " + r.out());

        CliFixture.Run fail = fx.run("check", "check", "--fail-on", "error", "--xlsx", "--profile", "t");
        assertEquals(3, fail.code(), fail.err());
        String xlsx = fail.out().lines().reduce((a, b) -> b).orElse("");
        assertTrue(xlsx.endsWith(".xlsx") && Files.exists(Path.of(xlsx)), fail.out());

        assertEquals(2, fx.run("check", "check", "--fail-on", "fatal", "--profile", "t").code());
        assertEquals(1, fx.run("check", "없는폴더", "--profile", "t").code());
    }

    @Test
    void analyzeMatchesEngine() throws Exception {
        CliFixture.Run r = fx.run("analyze", "proj", "--xlsx", "--profile", "t", "--json");
        assertEquals(0, r.code(), r.err());
        JsonNode n = JSON.readTree(r.out());
        AnalyzeRunner.Result direct = AnalyzeRunner.run(tmp.resolve("proj").toString(), new LocalFiles(tmp.resolve("data2")), null, null);
        assertEquals(direct.rows().size(), n.path("programs").size(), "CLI 와 엔진이 같은 수");
        assertTrue(n.path("programs").size() > 0, r.out());
        assertEquals(direct.tables().size(), n.path("tables").size());

        CliFixture.Run human = fx.run("analyze", "proj", "--xlsx", "--profile", "t");
        assertEquals(0, human.code(), human.err());
        long files = human.out().lines().filter(l -> l.endsWith(".xlsx")).filter(l -> Files.exists(Path.of(l))).count();
        assertEquals(2, files, human.out());
    }

    @Test
    void deployListNeedsWorkingCopy() throws Exception {
        CliFixture.Run r = fx.run("deploy-list", "check", "--from", "1", "--to", "2", "--profile", "t");
        assertEquals(1, r.code(), r.out());
        assertTrue(r.err().contains("[오류] 400"), r.err());
    }
}
