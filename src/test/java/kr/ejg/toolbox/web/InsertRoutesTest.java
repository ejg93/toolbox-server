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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 4-7 — `/api/insert/generate` DDL·CSV 모드와 거절 */
class InsertRoutesTest {

    static Javalin app;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\n", StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    static HttpResponse<String> post(Object body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/insert/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static final String DDL = "CREATE TABLE t_a (id NUMBER(10) PRIMARY KEY, st VARCHAR2(1) CHECK (st IN ('X','Y')), nm VARCHAR2(20))";

    @Test
    void ddlAndCsvModes() throws Exception {
        HttpResponse<String> r = post(java.util.Map.of("ddl", DDL, "dialect", "oracle", "rows", 2, "baseDate", "2026-01-31"));
        assertEquals(200, r.statusCode(), r.body());
        JsonNode j = JSON.readTree(r.body());
        assertTrue(j.get("sql").asText().contains("'X'") && j.get("sql").asText().endsWith("COMMIT;"), j.get("sql").asText());

        r = post(java.util.Map.of("ddl", DDL, "dialect", "oracle", "csv", "ID,NM\n1,가\n"));
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(JSON.readTree(r.body()).get("sql").asText().contains("'가'"));

        assertEquals(400, post(java.util.Map.of("ddl", DDL, "dialect", "oracle", "csv", "ID,NOPE\n1,2\n")).statusCode(), "없는 열");
        assertEquals(400, post(java.util.Map.of("ddl", DDL, "dialect", "db2")).statusCode(), "모르는 방언");
        assertEquals(400, post(java.util.Map.of("dialect", "oracle")).statusCode(), "테이블 원천 없음");
        assertEquals(404, post(java.util.Map.of("ddl", DDL, "dialect", "oracle", "table", "t_b")).statusCode());
        assertEquals(404, post(java.util.Map.of("snapshotId", 999, "table", "x", "dialect", "oracle")).statusCode());
        assertEquals(400, post(java.util.Map.of("ddl", DDL, "dialect", "oracle", "baseDate", "31/01/2026")).statusCode());
    }
}
