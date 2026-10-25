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
        assertTrue(r.out().contains(" · 억제 "), "5-29d — fixtures/check/ignore 의 표식: " + r.out());

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
        assertEquals(1, files, "6-24 — 한 파일 시트 여럿 " + human.out());
    }

    /** 5-34d·9-3b — 미리보기는 안 쓰고, --write 는 결과 폴더에 쓴다(원본 그대로) */
    @Test
    void cleanupPreviewAndApply() throws Exception {
        CliFixture.copy(Path.of("src/test/resources/fixtures/cleanup/javaimport"), tmp.resolve("clean"));
        byte[] before = Files.readAllBytes(tmp.resolve("clean/Pos.java"));
        CliFixture.Run pv = fx.run("cleanup", "clean", "--profile", "t");
        assertEquals(0, pv.code(), pv.err());
        assertTrue(pv.out().startsWith("정리 미리보기 — 파일 1 · 바뀜 1 · 건너뜀 0"), pv.out());
        assertTrue(pv.out().contains("  Pos.java · delete · 3 → 0"), pv.out()); // 5-48 — 정적 미사용 import 하나 더
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(tmp.resolve("clean/Pos.java"))), "미리보기는 안 쓴다");
        CliFixture.Run ap = fx.run("cleanup", "clean", "--write", "--out", "res", "--profile", "t");
        assertEquals(0, ap.code(), ap.err());
        assertTrue(ap.out().contains("썼다 1/1 · 실패 0 · 낡음 0"), ap.out());
        assertTrue(ap.out().contains("결과 폴더 " + tmp.resolve("res").toAbsolutePath().normalize() + " — 원본은 그대로"), ap.out());
        String report = ap.out().lines().reduce((a, b) -> b).orElse("");
        assertTrue(report.endsWith("정리_결과.txt") && Files.exists(Path.of(report)), ap.out());
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(tmp.resolve("clean/Pos.java"))), "원본 그대로");
        assertTrue(!Files.readString(tmp.resolve("res/Pos.java")).contains("import java.util.Map;"));
        CliFixture.Run inside = fx.run("cleanup", "clean", "--write", "--out", "clean/res", "--profile", "t");
        assertEquals(1, inside.code(), inside.out() + inside.err());
        assertTrue(inside.err().contains("원본 폴더 밖"), inside.err());
    }

    @Test
    void deployListNeedsWorkingCopy() throws Exception {
        CliFixture.Run r = fx.run("deploy-list", "check", "--from", "1", "--to", "2", "--profile", "t");
        assertEquals(1, r.code(), r.out());
        assertTrue(r.err().contains("[오류] 400"), r.err());
    }
}
