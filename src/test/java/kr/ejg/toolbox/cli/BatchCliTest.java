package kr.ejg.toolbox.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** 8-3 — 배치 공통 + api·snapshot·snapshots·diff. 서버(소켓) 없이 같은 라우트를 탄다 */
class BatchCliTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String SECRET = "pw-비밀-9271";

    @TempDir
    Path tmp;

    Connection holder;

    /** 명령 한 번 — 끝 코드·stdout·stderr */
    record Run(int code, String out, String err) {
    }

    @BeforeEach
    void up() throws Exception {
        String db = "jdbc:h2:mem:batch" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        holder = DriverManager.getConnection(db, "sa", "pw");
        try (Statement st = holder.createStatement()) {
            st.execute("CREATE TABLE TB_DEPT (DEPT_NO INTEGER PRIMARY KEY, DEPT_NM VARCHAR(30))");
            st.execute("ALTER USER SA SET PASSWORD '" + SECRET + "'");
        }
        Files.createDirectories(tmp.resolve("profiles"));
        Files.writeString(tmp.resolve("profiles/t.yaml"), "name: t\nconnections:\n  - id: h2\n    dialect: h2\n    url: " + db + "\n    user: sa\n"
                + "output:\n  dir: '" + tmp.resolve("out").toString().replace('\\', '/') + "'\n", StandardCharsets.UTF_8);
        Batch.passwordSource = id -> SECRET.toCharArray();
        Batch.cwdSource = () -> tmp;
    }

    @AfterEach
    void down() throws Exception {
        Batch.passwordSource = null;
        Batch.cwdSource = null;
        holder.close();
    }

    Run run(String... args) {
        List<String> a = new ArrayList<>(List.of(args));
        a.add("--data-dir");
        a.add(tmp.resolve("data").toString());
        a.add("--profiles-dir");
        a.add(tmp.resolve("profiles").toString());
        CommandLine cmd = Main.commandLine();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        int code = cmd.execute(a.toArray(String[]::new));
        return new Run(code, out.toString(), err.toString());
    }

    @Test
    void apiPingIsCleanJson() throws Exception {
        Run r = run("api", "GET", "/api/ping", "--json");
        assertEquals(0, r.code(), r.err());
        JsonNode n = JSON.readTree(r.out());
        assertTrue(n.isObject(), "stdout 은 JSON 하나 — 로그가 안 섞였다: " + r.out());
        Run notFound = run("api", "GET", "/api/nope");
        assertEquals(1, notFound.code());
        Run bad = run("api", "GET", "/api/ping x");
        assertEquals(2, bad.code(), bad.err());
    }

    @Test
    void snapshotListDiff() throws Exception {
        Run s = run("snapshot", "--conn", "h2", "--profile", "t");
        assertEquals(0, s.code(), s.err());
        assertTrue(s.out().startsWith("스냅샷 "), s.out());
        long id = Long.parseLong(s.out().split(" ")[1]);

        Run list = run("snapshots", "--json");
        assertEquals(0, list.code(), list.err());
        boolean found = false;
        for (JsonNode n : JSON.readTree(list.out())) {
            found |= n.path("id").asLong() == id && n.path("profile").asText().equals("t");
        }
        assertTrue(found, list.out());

        Run d = run("diff", "--from", String.valueOf(id), "--to", "latest", "--profile", "t");
        assertEquals(0, d.code(), d.err());
        assertTrue(d.out().contains("표 추가 0 · 삭제 0 · 변경 0"), d.out());

        for (Run r : List.of(s, list, d)) {
            assertFalse(r.out().contains(SECRET) || r.err().contains(SECRET), "비밀번호 글이 어디에도 안 찍힌다");
        }
        assertFalse(Files.exists(tmp.resolve("data/active-profile")), "배치는 활성 프로필 파일을 안 바꾼다");
    }

    @Test
    void failures() throws Exception {
        Run noProfile = run("snapshot", "--conn", "h2");
        assertEquals(2, noProfile.code(), noProfile.err());
        assertTrue(noProfile.err().contains("활성 프로필이 없다"), noProfile.err());

        Run unknownProfile = run("snapshot", "--conn", "h2", "--profile", "없음");
        assertEquals(2, unknownProfile.code(), unknownProfile.err());

        Run noConn = run("snapshot", "--conn", "nope", "--profile", "t");
        assertEquals(1, noConn.code(), noConn.err());

        Batch.passwordSource = null; // 시험 JVM 엔 콘솔이 없고 환경변수도 없다
        if (System.getenv("TOOLBOX_DB_PASSWORD") == null && System.getenv("TOOLBOX_DB_PASSWORD_H2") == null) {
            Run noPw = run("snapshot", "--conn", "h2", "--profile", "t");
            assertEquals(2, noPw.code(), noPw.err());
            assertTrue(noPw.err().contains("TOOLBOX_DB_PASSWORD"), noPw.err());
        }

        Run noSnap = run("diff", "--from", "latest", "--to", "latest", "--profile", "t");
        assertEquals(1, noSnap.code(), noSnap.err());
    }

    @Test
    void bodyFileIsResolvedFromCallerFolder() throws Exception {
        Files.writeString(tmp.resolve("body.json"), "{\"text\":\"x\"}", StandardCharsets.UTF_8);
        Run r = run("api", "POST", "api/text/logsql", "--body-file", "body.json");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("items"), r.out());
    }
}
