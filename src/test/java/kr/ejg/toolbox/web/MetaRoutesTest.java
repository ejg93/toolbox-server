package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 1-5 끝에서 끝 — 접속 비밀번호 → 스냅샷 작업 → 목록·테이블 조회. H2 in-memory 접속(dialect h2 → JDBC 뼈대) */
class MetaRoutesTest {

    static Javalin app;
    static Connection holder;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        holder = DriverManager.getConnection("jdbc:h2:mem:snaptest;DB_CLOSE_DELAY=-1", "sa", "pw");
        try (Statement st = holder.createStatement()) {
            st.execute("CREATE TABLE ITEMS (ID INT PRIMARY KEY, NAME VARCHAR(20) NOT NULL)");
            st.execute("COMMENT ON TABLE ITEMS IS '항목'");
            st.execute("CREATE TABLE TMP_X (ID INT)");
        }
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nconnections:\n  - id: h2\n    dialect: h2\n"
                + "    url: jdbc:h2:mem:snaptest;DB_CLOSE_DELAY=-1\n    user: sa\n"
                + "scope:\n  exclude:\n    prefixes: [TMP_]\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    @AfterAll
    static void down() throws Exception {
        app.stop();
        holder.close();
    }

    static JsonNode call(String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
                .header("Content-Type", "application/json");
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(res.statusCode() < 300, method + " " + path + " → " + res.statusCode() + " " + res.body());
        return res.body().isEmpty() ? null : JSON.readTree(res.body());
    }

    static long takeSnapshot(String note) throws Exception {
        call("POST", "/api/conn/h2/password", "{\"password\":\"pw\"}");
        String jobId = call("POST", "/api/meta/snapshot", "{\"connId\":\"h2\",\"note\":\"" + note + "\"}").get("jobId").asText();
        long end = System.nanoTime() + 10_000_000_000L;
        JsonNode job = null;
        while (System.nanoTime() < end) {
            job = call("GET", "/api/jobs/" + jobId, null);
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        return job.get("result").get("snapshotId").asLong();
    }

    static HttpResponse<String> raw(String path, String json) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 1-60b — 반영 DDL: 컬럼 하나 더한 뒤 스냅샷 둘 → ADD COLUMN · 대상 방언 없으면(H2 라 모름) 400 · 모르는 방언 400 · 없는 스냅샷 404 · 저장 */
    @Test
    void alterDdl() throws Exception {
        long a = takeSnapshot("반영 앞");
        try (Statement st = holder.createStatement()) {
            st.execute("ALTER TABLE ITEMS ADD COLUMN QTY INT");
        }
        try {
            long b = takeSnapshot("반영 뒤");
            JsonNode r = call("POST", "/api/meta/alter", "{\"a\":" + a + ",\"b\":" + b + ",\"target\":\"postgresql\"}");
            assertTrue(r.get("sql").asText().contains("ADD COLUMN QTY"), r.get("sql").asText());
            assertEquals(1, r.get("statements").asInt(), r.get("sql").asText());
            assertEquals(1, r.get("changedTables").asInt());
            assertEquals("postgresql", r.get("target").asText());
            assertEquals(400, raw("/api/meta/alter", "{\"a\":" + a + ",\"b\":" + b + "}").statusCode(), "H2 스냅샷은 방언을 몰라 대상을 골라야 한다");
            assertEquals(400, raw("/api/meta/alter", "{\"a\":" + a + ",\"b\":" + b + ",\"target\":\"sybase\"}").statusCode());
            assertEquals(404, raw("/api/meta/alter", "{\"a\":999999,\"b\":" + b + ",\"target\":\"oracle\"}").statusCode());
            JsonNode saved = call("POST", "/api/meta/alter", "{\"a\":" + a + ",\"b\":" + b + ",\"target\":\"oracle\",\"save\":true}");
            Path file = Path.of(saved.get("path").asText());
            assertTrue(Files.exists(file) && file.getFileName().toString().equals("alter-" + a + "-" + b + "-oracle.sql"), file.toString());
            assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("ADD (QTY"), Files.readString(file, StandardCharsets.UTF_8));
        } finally {
            try (Statement st = holder.createStatement()) {
                st.execute("ALTER TABLE ITEMS DROP COLUMN QTY"); // snapshotFlow 가 컬럼 둘을 본다
            }
        }
    }

    @Test
    void snapshotFlow() throws Exception {
        call("POST", "/api/conn/h2/password", "{\"password\":\"pw\"}");
        String jobId = call("POST", "/api/meta/snapshot", "{\"connId\":\"h2\",\"note\":\"첫 스냅샷\"}").get("jobId").asText();

        JsonNode job = null;
        long end = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < end) {
            job = call("GET", "/api/jobs/" + jobId, null);
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        long id = job.get("result").get("snapshotId").asLong();
        assertEquals(1, job.get("result").get("tables").asInt(), "scope 접두 제외로 TMP_X 는 빠진다");
        assertTrue(job.get("result").get("elapsedMs").asLong() >= 0, job.toString());
        assertTrue(job.get("result").get("store").asText().endsWith("toolbox.mv.db"), "스냅샷은 H2 파일의 행(1-11): " + job);

        JsonNode list = call("GET", "/api/meta/snapshots", null);
        assertEquals(id, list.get(0).get("id").asLong());
        assertEquals("첫 스냅샷", list.get(0).get("note").asText());
        assertTrue(list.get(0).get("takenAt").isTextual(), "시각은 ISO 문자열: " + list.get(0).get("takenAt"));
        assertTrue(list.get(0).get("filtered").asBoolean(), "테스트 프로필이 exclude.prefixes 로 거른다(1-14): " + list.get(0));
        assertEquals("TMP_", list.get(0).get("scope").get("exclude").get("prefixes").get(0).asText());
        assertEquals(0, list.get(0).get("warningCount").asInt());

        JsonNode tables = call("GET", "/api/meta/snapshots/" + id + "/tables", null);
        assertEquals("ITEMS", tables.get(0).get("name").asText());
        assertEquals("항목", tables.get(0).get("comment").asText());

        JsonNode items = call("GET", "/api/meta/snapshots/" + id + "/tables/ITEMS", null);
        assertEquals(2, items.get("columns").size());
        assertEquals("ID", items.get("pk").get("columns").get(0).asText());

        // 번들 2 리뷰 지적 확인 — 파라미터가 없어도 500 이 아니라 404(Long.parseLong(null) 은 NumberFormatException)
        HttpResponse<String> noParams = HTTP.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + app.port() + "/api/meta/diff")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, noParams.statusCode());

        JsonNode diff = call("GET", "/api/meta/diff?a=" + id + "&b=" + id, null);
        assertEquals(0, diff.get("changedTables").size(), "같은 스냅샷끼리는 차이 없음(1-6)");
        assertTrue(diff.get("empty").asBoolean());

        // 1-7 — 실행·내보내기
        JsonNode run = call("POST", "/api/sql/run", "{\"connId\":\"h2\",\"sql\":\"SELECT ID FROM ITEMS WHERE ID > ?\",\"binds\":[0]}");
        assertEquals("ID", run.get("columns").get(0).get("name").asText());
        JsonNode exp = call("POST", "/api/sql/export", "{\"connId\":\"h2\",\"sql\":\"SELECT 1 AS A\",\"format\":\"csv\"}");
        Path file = Path.of(exp.get("path").asText());
        assertTrue(Files.isRegularFile(file) && file.toString().replace('\\', '/').contains("/t/"), "out/<프로필>/<시각>/: " + file);
    }
}
