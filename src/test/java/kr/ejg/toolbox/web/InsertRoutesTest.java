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
        HttpResponse<String> noTable = post(java.util.Map.of("ddl", "SELECT 1 FROM DUAL", "dialect", "oracle"));
        assertEquals(400, noTable.statusCode(), "CREATE 문이 아님");
        assertEquals("CREATE TABLE 문을 못 읽었다", JSON.readTree(noTable.body()).get("message").asText());
        assertEquals(404, post(java.util.Map.of("snapshotId", 999, "table", "x", "dialect", "oracle")).statusCode());
        assertEquals(400, post(java.util.Map.of("ddl", DDL, "dialect", "oracle", "baseDate", "31/01/2026")).statusCode());
    }

    /** 4-23 — 「CREATE 문에서」 탭이 보내는 방언 다섯(`DM_DIALECT`)이 전부 ddl 모드로 생성된다 */
    @Test
    void ddlModeEveryCreateTabDialect() throws Exception {
        for (String d : java.util.List.of("oracle", "mariadb", "postgresql", "mssql", "sybase")) {
            HttpResponse<String> r = post(java.util.Map.of("ddl", DDL, "dialect", d, "rows", 2));
            assertEquals(200, r.statusCode(), d + ": " + r.body());
            assertTrue(JSON.readTree(r.body()).get("sql").asText().contains("INSERT INTO"), d);
        }
        // 4-23b — dev_tools_ext.js insFromCreate 가 이 꼬리 글을 그대로 찾아 바꾼다. 글을 고치면 ext 도 같이
        HttpResponse<String> fk = post(java.util.Map.of("ddl",
                "CREATE TABLE t_c (id NUMBER(10) PRIMARY KEY, a_id NUMBER(10), CONSTRAINT fk_c FOREIGN KEY (a_id) REFERENCES t_a(id))",
                "dialect", "oracle"));
        assertEquals(200, fk.statusCode(), fk.body());
        assertTrue(fk.body().contains(" — 접속을 주면 실존 값을 쓴다"), fk.body());
    }
}
