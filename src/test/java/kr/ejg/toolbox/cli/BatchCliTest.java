package kr.ejg.toolbox.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 8-3 — 배치 공통 + api·snapshot·snapshots·diff. 서버(소켓) 없이 같은 라우트를 탄다 */
class BatchCliTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tmp;

    CliFixture fx;

    @BeforeEach
    void up() throws Exception {
        fx = new CliFixture(tmp, "CREATE TABLE TB_DEPT (DEPT_NO INTEGER PRIMARY KEY, DEPT_NM VARCHAR(30))");
    }

    @AfterEach
    void down() throws Exception {
        fx.close();
    }

    @Test
    void apiPingIsCleanJson() throws Exception {
        CliFixture.Run r = fx.run("api", "GET", "/api/ping", "--json");
        assertEquals(0, r.code(), r.err());
        JsonNode n = JSON.readTree(r.out());
        assertTrue(n.isObject(), "stdout 은 JSON 하나 — 로그가 안 섞였다: " + r.out());
        assertEquals(1, fx.run("api", "GET", "/api/nope").code());
        CliFixture.Run bad = fx.run("api", "GET", "/api/ping x");
        assertEquals(2, bad.code(), bad.err());
    }

    @Test
    void snapshotListDiff() throws Exception {
        CliFixture.Run s = fx.run("snapshot", "--conn", "h2", "--profile", "t");
        assertEquals(0, s.code(), s.err());
        assertTrue(s.out().startsWith("스냅샷 "), s.out());
        long id = Long.parseLong(s.out().split(" ")[1]);

        CliFixture.Run list = fx.run("snapshots", "--json");
        assertEquals(0, list.code(), list.err());
        boolean found = false;
        for (JsonNode n : JSON.readTree(list.out())) {
            found |= n.path("id").asLong() == id && n.path("profile").asText().equals("t");
        }
        assertTrue(found, list.out());

        CliFixture.Run d = fx.run("diff", "--from", String.valueOf(id), "--to", "latest", "--profile", "t");
        assertEquals(0, d.code(), d.err());
        assertTrue(d.out().contains("표 추가 0 · 삭제 0 · 변경 0"), d.out());

        // 1-60b — 반영 DDL: 같은 스냅샷끼리면 차이 없음, 파일이 생긴다(H2 라 대상 방언을 준다)
        CliFixture.Run al = fx.run("alter", "--from", String.valueOf(id), "--to", "latest", "--target", "postgresql", "--profile", "t");
        assertEquals(0, al.code(), al.err());
        assertTrue(al.out().contains("반영 DDL ") && al.out().contains("문장 0"), al.out());
        Path alterFile = Path.of(al.out().strip().substring(al.out().strip().lastIndexOf('\n') + 1));
        assertTrue(Files.readString(alterFile, StandardCharsets.UTF_8).contains("-- 차이 없음"), alterFile.toString());

        for (CliFixture.Run r : List.of(s, list, d, al)) {
            assertFalse(r.out().contains(CliFixture.SECRET) || r.err().contains(CliFixture.SECRET), "비밀번호 글이 어디에도 안 찍힌다");
        }
        assertFalse(Files.exists(tmp.resolve("data/active-profile")), "배치는 활성 프로필 파일을 안 바꾼다");
    }

    @Test
    void failures() throws Exception {
        CliFixture.Run noProfile = fx.run("snapshot", "--conn", "h2");
        assertEquals(2, noProfile.code(), noProfile.err());
        assertTrue(noProfile.err().contains("활성 프로필이 없다"), noProfile.err());

        assertEquals(2, fx.run("snapshot", "--conn", "h2", "--profile", "없음").code());
        assertEquals(1, fx.run("snapshot", "--conn", "nope", "--profile", "t").code());

        Batch.passwordHook(null); // 시험 JVM 엔 콘솔이 없다
        if (System.getenv("TOOLBOX_DB_PASSWORD") == null && System.getenv("TOOLBOX_DB_PASSWORD_H2") == null) {
            CliFixture.Run noPw = fx.run("snapshot", "--conn", "h2", "--profile", "t");
            assertEquals(2, noPw.code(), noPw.err());
            assertTrue(noPw.err().contains("TOOLBOX_DB_PASSWORD"), noPw.err());
        }

        assertEquals(1, fx.run("diff", "--from", "latest", "--to", "latest", "--profile", "t").code());
    }

    /** 1-44 — 프로필 접속에 password 가 있으면 환경변수·프롬프트를 안 묻는다(시험 JVM 엔 콘솔이 없어 물으면 끝 코드 2) */
    @Test
    void profilePasswordNeedsNoPrompt() throws Exception {
        String t = Files.readString(fx.dir.resolve("profiles/t.yaml"), StandardCharsets.UTF_8);
        Files.writeString(fx.dir.resolve("profiles/t2.yaml"),
                t.replace("name: t\n", "name: t2\n").replace("    user: sa\n", "    user: sa\n    password: " + CliFixture.SECRET + "\n"),
                StandardCharsets.UTF_8);
        Batch.passwordHook(null);
        CliFixture.Run s = fx.run("snapshot", "--conn", "h2", "--profile", "t2");
        assertEquals(0, s.code(), s.err());
        assertFalse(s.out().contains(CliFixture.SECRET) || s.err().contains(CliFixture.SECRET));
    }

    /** PR #37 리뷰 — 비밀번호 라우트는 api 로 못 부른다 · latest 는 프로필이 있어야 · 서버가 쥔 data 폴더는 1 + 문구 */
    @Test
    void reviewFollowUps() throws Exception {
        for (String p : new String[] {"/api/conn/h2/password", "api/conn/h2/password", "/API/CONN/h2/PASSWORD?x=1"}) {
            CliFixture.Run r = fx.run("api", "POST", p, "--body", "{\"password\":\"x\"}", "--profile", "t");
            assertEquals(2, r.code(), p + " " + r.err());
            assertTrue(r.err().contains("비밀번호는 api 로 못 보낸다"), r.err());
        }
        assertEquals(0, fx.run("api", "GET", "/api/conn", "--profile", "t").code(), "다른 conn 라우트는 그대로");

        fx.snapshot();
        CliFixture.Run noProfile = fx.run("diff", "--from", "latest", "--to", "latest");
        assertEquals(2, noProfile.code(), noProfile.err());
        assertTrue(noProfile.err().contains("latest 는 프로필이 있어야"), noProfile.err());
        assertEquals(0, fx.run("snapshots").code(), "목록은 전 프로필이라 프로필 없이 연다");

        try (AutoCloseable held = fx.holdData()) {
            CliFixture.Run locked = fx.run("snapshots");
            assertEquals(1, locked.code(), locked.err());
            assertTrue(locked.err().contains("서버가 켜져 있다"), locked.err());
        }
    }

    @Test
    void bodyFileIsResolvedFromCallerFolder() throws Exception {
        Files.writeString(tmp.resolve("body.json"), "{\"text\":\"x\"}", StandardCharsets.UTF_8);
        CliFixture.Run r = fx.run("api", "POST", "api/text/logsql", "--body-file", "body.json");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("items"), r.out());
    }
}
