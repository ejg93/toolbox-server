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

        for (CliFixture.Run r : List.of(s, list, d)) {
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

        Batch.passwordSource = null; // 시험 JVM 엔 콘솔이 없다
        if (System.getenv("TOOLBOX_DB_PASSWORD") == null && System.getenv("TOOLBOX_DB_PASSWORD_H2") == null) {
            CliFixture.Run noPw = fx.run("snapshot", "--conn", "h2", "--profile", "t");
            assertEquals(2, noPw.code(), noPw.err());
            assertTrue(noPw.err().contains("TOOLBOX_DB_PASSWORD"), noPw.err());
        }

        assertEquals(1, fx.run("diff", "--from", "latest", "--to", "latest", "--profile", "t").code());
    }

    @Test
    void bodyFileIsResolvedFromCallerFolder() throws Exception {
        Files.writeString(tmp.resolve("body.json"), "{\"text\":\"x\"}", StandardCharsets.UTF_8);
        CliFixture.Run r = fx.run("api", "POST", "api/text/logsql", "--body-file", "body.json");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("items"), r.out());
    }
}
