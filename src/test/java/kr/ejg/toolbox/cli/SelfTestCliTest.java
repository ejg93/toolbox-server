package kr.ejg.toolbox.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 8-7 — selftest: 실패 없음 · 화면 수 = 런처 카드 수 · DB 연결 · 틀린 비밀번호는 실패 */
class SelfTestCliTest {

    @TempDir
    Path tmp;

    CliFixture fx;

    @BeforeEach
    void up() throws Exception {
        fx = new CliFixture(tmp).withTemplates();
    }

    @AfterEach
    void down() throws Exception {
        fx.close();
    }

    static int cards() throws Exception {
        try (InputStream in = SelfTestCliTest.class.getResourceAsStream("/tools/index.html")) {
            Matcher m = Pattern.compile("data-file=\"").matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            int n = 0;
            while (m.find()) {
                n++;
            }
            return n;
        }
    }

    @Test
    void passesWithoutFailures() throws Exception {
        CliFixture.Run r = fx.run("selftest", "--profile", "t", "--conn", "h2");
        assertEquals(0, r.code(), r.out() + r.err());
        assertFalse(r.out().contains("[실패]"), r.out());
        assertTrue(r.out().contains("[통과] 화면 파일 — " + cards() + "개 카드 + common.js"), r.out());
        assertTrue(r.out().contains("[통과] DB 연결 h2"), r.out());
        assertTrue(r.out().contains("[통과] 생성기 템플릿 세트"), r.out());
        assertTrue(r.out().lines().reduce((a, b) -> b).orElse("").matches("통과 \\d+ · 경고 \\d+ · 실패 0"), r.out());
        assertFalse(r.out().contains(CliFixture.SECRET), "비밀번호가 안 찍힌다");
    }

    @Test
    void wrongPasswordFails() throws Exception {
        Batch.passwordHook(id -> "틀림".toCharArray());
        CliFixture.Run r = fx.run("selftest", "--profile", "t", "--conn", "h2");
        assertEquals(1, r.code(), r.out());
        assertTrue(r.out().contains("[실패] DB 연결 h2"), r.out());
    }

    /** PR #37 리뷰 — 서버가 쥔 data 폴더면 1 + 문구 */
    @Test
    void lockedDataFolderFails() throws Exception {
        try (AutoCloseable held = fx.holdData()) {
            CliFixture.Run r = fx.run("selftest");
            assertEquals(1, r.code(), r.out() + r.err());
            assertTrue(r.err().contains("서버가 켜져 있다"), r.err());
        }
    }

    @Test
    void worksWithoutProfile() throws Exception {
        CliFixture.Run r = fx.run("selftest");
        assertEquals(0, r.code(), r.out() + r.err());
        assertTrue(r.out().contains("[경고] 프로필"), r.out());
    }
}
