package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 3-5~ 논리명 라우트 — CSV 입력으로 끝에서 끝 */
class LogicalRoutesTest {

    static Javalin app;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nlogicalName:\n  skipTokens: [TB]\n", StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    static HttpResponse<String> post(String path, Object body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    static String sampleCsv() throws Exception {
        return Files.readString(Path.of("src/test/resources/sample/logical/columns-1000.csv"), StandardCharsets.UTF_8);
    }

    @Test
    void commentsFromCsvUsesProfileSkipTokens() throws Exception {
        HttpResponse<String> r = post("/api/logical/comments", java.util.Map.of("csv", sampleCsv(), "dialect", "pg"));
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().startsWith("-- 생성 "), r.body().substring(0, 40));
        assertTrue(r.body().contains("[PostgreSQL] · 총 1048줄"), "테이블 104 + 컬럼 944");
        assertTrue(r.body().contains("COMMENT ON COLUMN SHOP.TB_CUST_MST.CUST_ID IS"), "프로필 무시토큰 TB 가 테이블에만");
    }

    @Test
    void badInputIs400() throws Exception {
        assertEquals(400, post("/api/logical/comments", java.util.Map.of("dialect", "pg")).statusCode());
        assertEquals(400, post("/api/logical/comments", java.util.Map.of("csv", "A,B\n1,2\n")).statusCode());
        assertEquals(400, post("/api/logical/comments", java.util.Map.of("csv", "COLUMN_NAME\nX\n", "dialect", "db2")).statusCode());
        assertEquals(404, post("/api/logical/comments", java.util.Map.of("snapshotId", 999)).statusCode());
    }
}
