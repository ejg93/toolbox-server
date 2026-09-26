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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 1-9 — DTO 라우트. DDL 붙여넣기·스냅샷 두 입력, 저장, 잘못된 입력 */
class GenRoutesTest {

    static Javalin app;
    static Connection holder;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        holder = DriverManager.getConnection("jdbc:h2:mem:gentest;DB_CLOSE_DELAY=-1", "sa", "pw");
        try (Statement st = holder.createStatement()) {
            st.execute("CREATE TABLE TB_CUST_MST (CUST_ID BIGINT PRIMARY KEY, USE_YN CHAR(1) NOT NULL, AMT DECIMAL(12,2))");
            st.execute("COMMENT ON COLUMN TB_CUST_MST.AMT IS '금액'");
        }
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nlogicalName:\n  skipTokens: [TB]\n"
                + "connections:\n  - id: h2\n    dialect: h2\n    url: jdbc:h2:mem:gentest;DB_CLOSE_DELAY=-1\n    user: sa\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    @AfterAll
    static void down() throws Exception {
        app.stop();
        holder.close();
    }

    static HttpResponse<String> post(String path, Object body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void fromDdlWithUnreadableAndSave() throws Exception {
        HttpResponse<String> r = post("/api/gen/dto?save=true", Map.of("ddl", "CREATE TABLE TB_ORDER (ORDER_NO VARCHAR(20) NOT NULL, ?? x);",
                "packageName", "a.b", "style", "egovVo"));
        assertEquals(200, r.statusCode(), r.body());
        JsonNode b = JSON.readTree(r.body());
        assertEquals("OrderVO.java", b.get("files").get(0).get("name").asText(), "무시토큰 TB 를 뗀다(프로필)");
        assertTrue(b.get("files").get(0).get("source").asText().contains("implements Serializable"));
        assertEquals(1, b.get("unreadable").size());
        Path saved = Path.of(b.get("path").asText()).resolve("OrderVO.java");
        assertTrue(Files.readString(saved, StandardCharsets.UTF_8).contains("package a.b;"), saved.toString());
    }

    @Test
    void fromSnapshotUsesCommentThenLogicalName() throws Exception {
        assertEquals(200, post("/api/conn/h2/password", Map.of("password", "pw")).statusCode());
        String jobId = JSON.readTree(post("/api/meta/snapshot", Map.of("connId", "h2")).body()).get("jobId").asText();
        JsonNode job = null;
        long end = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < end) {
            job = JSON.readTree(HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/jobs/" + jobId)).build(),
                    HttpResponse.BodyHandlers.ofString()).body());
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        long id = job.get("result").get("snapshotId").asLong();
        HttpResponse<String> r = post("/api/gen/dto", Map.of("snapshotId", id, "tables", List.of(Map.of("name", "TB_CUST_MST"))));
        assertEquals(200, r.statusCode(), r.body());
        String src = JSON.readTree(r.body()).get("files").get(0).get("source").asText();
        assertTrue(src.contains("public record CustMst("), src);
        assertTrue(src.contains("@param amt 금액"), "코멘트가 먼저: " + src);
        assertTrue(src.contains("@param useYn 사용여부"), "코멘트가 없으면 조립 한글: " + src);
        assertTrue(src.contains("BigDecimal amt"), src);
    }

    @Test
    void badInput() throws Exception {
        assertEquals(400, post("/api/gen/dto", Map.of("packageName", "x")).statusCode());
        assertEquals(400, post("/api/gen/dto", Map.of("ddl", "SELECT 1")).statusCode(), "CREATE TABLE 이 없다");
        assertEquals(400, post("/api/gen/dto", Map.of("ddl", "CREATE TABLE T (A INT)", "style", "pojo")).statusCode());
        assertEquals(404, post("/api/gen/dto", Map.of("snapshotId", 999)).statusCode());
        assertEquals(400, post("/api/gen/dto", Map.of("ddl", "CREATE TABLE T (A INT)", "packageName", "a.b; class X {}")).statusCode(),
                "package 문에 코드가 끼면 안 된다");
        assertEquals(200, post("/api/gen/dto", Map.of("ddl", "CREATE TABLE T (A INT)", "packageName", "egovframework.minwon.service")).statusCode());
    }
}
