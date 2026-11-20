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

    static final String DDL = "CREATE TABLE t_a (id NUMBER(10) PRIMARY KEY, st VARCHAR2(1) CHECK (st IN ('X','Y')), nm VARCHAR2(20))";

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
        assertEquals(400, post(java.util.Map.of("ddl", DDL)).statusCode(), "방언 없음");
        assertEquals(400, post(java.util.Map.of("dialect", "oracle")).statusCode(), "테이블 원천 없음");
        assertEquals(404, post(java.util.Map.of("ddl", DDL, "dialect", "oracle", "table", "t_b")).statusCode());
        HttpResponse<String> noTable = post(java.util.Map.of("ddl", "SELECT 1 FROM DUAL", "dialect", "oracle"));
        assertEquals(400, noTable.statusCode(), "CREATE 문이 아님");
        assertEquals("CREATE TABLE 문을 못 읽었다", JSON.readTree(noTable.body()).get("message").asText());
        assertEquals(404, post(java.util.Map.of("snapshotId", 999, "table", "x", "dialect", "oracle")).statusCode());
        assertEquals(400, post(java.util.Map.of("ddl", DDL, "dialect", "oracle", "baseDate", "31/01/2026")).statusCode());
    }

    /**
     * 4-23·0-56 — 「CREATE 문에서」 탭(`dev_tools.html` 의 `#dummy_db`)이 보내는 방언 키 그대로 ddl 모드로 생성된다.
     * 미검증은 sybase 만 — ext 는 `verified` 를 받은 대로 싣는다
     */
    @Test
    void ddlModeEveryCreateTabDialect() throws Exception {
        String html = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/tools/dev_tools.html")).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        java.util.regex.Matcher sel = java.util.regex.Pattern.compile("(?s)<select id=\"dummy_db\"[^>]*>(.*?)</select>").matcher(html);
        assertTrue(sel.find(), "dev_tools.html 에 #dummy_db 가 없다");
        java.util.List<String> keys = java.util.regex.Pattern.compile("value=\"([^\"]+)\"").matcher(sel.group(1)).results()
                .map(m -> m.group(1)).toList();
        assertEquals(java.util.List.of("oracle", "mysql", "pg", "mssql", "sybase"), keys);
        for (String d : keys) {
            HttpResponse<String> r = post(java.util.Map.of("ddl", DDL, "dialect", d, "rows", 2));
            assertEquals(200, r.statusCode(), d + ": " + r.body());
            JsonNode j = JSON.readTree(r.body());
            assertTrue(j.get("sql").asText().contains("INSERT INTO"), d);
            assertEquals(!d.equals("sybase"), j.get("verified").asBoolean(), d + " verified");
        }
    }

    /** 4-23b·0-56 — ddl 모드에 접속이 없으면 FK 경고 꼬리가 DDL 글. InsertGen 꼬리 글이 바뀌면 바꿈이 빠져 빨강 */
    @Test
    void ddlModeFkTailSaysDdl() throws Exception {
        String fkDdl = DDL + ";\nCREATE TABLE t_c (id NUMBER(10) PRIMARY KEY, a_id NUMBER(10), CONSTRAINT fk_c FOREIGN KEY (a_id) REFERENCES t_a(id))";
        HttpResponse<String> fk = post(java.util.Map.of("ddl", fkDdl, "table", "t_c", "dialect", "pg"));
        assertEquals(200, fk.statusCode(), fk.body());
        String warns = JSON.readTree(fk.body()).get("warnings").toString();
        assertTrue(warns.toUpperCase(java.util.Locale.ROOT).contains("FK A_ID → T_A"), warns);
        assertTrue(warns.contains(InsertRoutes.FK_TAIL_DDL) && !warns.contains("접속을 주면"), warns);
    }

    /** 1-95b — 고른 표의 DDL 에 못 읽은 줄이 있으면 경고 첫 줄로 알린다 */
    @Test
    void unreadableIsWarned() throws Exception {
        HttpResponse<String> r = post(java.util.Map.of("ddl", "CREATE TABLE t (id INT);\nALTER TABLE t RENAME COLUMN id TO no;", "dialect", "pg"));
        assertEquals(200, r.statusCode(), r.body());
        JsonNode w = JSON.readTree(r.body()).get("warnings");
        assertTrue(w.size() > 0 && w.get(0).asText().startsWith("DDL 에서 못 읽은 줄 1개"), w.toString());
        HttpResponse<String> clean = post(java.util.Map.of("ddl", "CREATE TABLE t (id INT);", "dialect", "pg"));
        assertTrue(!clean.body().contains("못 읽은 줄"), clean.body());
    }

    /** 1-101 — 맨 MODIFY 가 NOT NULL 을 지웠으면 INSERT 경고에 「방언 모호」 한 줄 */
    @Test
    void ambiguousModifyIsWarned() throws Exception {
        HttpResponse<String> r = post(java.util.Map.of("ddl", "CREATE TABLE t (id INT PRIMARY KEY, b INT NOT NULL);\nALTER TABLE t MODIFY b BIGINT;",
                "dialect", "pg"));
        assertEquals(200, r.statusCode(), r.body());
        JsonNode w = JSON.readTree(r.body()).get("warnings");
        boolean found = false;
        for (JsonNode n : w) {
            found |= n.asText().startsWith("B: 방언 모호");
        }
        assertTrue(found, w.toString());
        HttpResponse<String> clean = post(java.util.Map.of("ddl", "CREATE TABLE t (id INT PRIMARY KEY, b INT NOT NULL);", "dialect", "pg"));
        assertTrue(!clean.body().contains("방언 모호"), clean.body());
    }

    /** 4-35 — DDL 에 표가 여럿이면 고른 표의 CHECK IN 만 쓴다 */
    @Test
    void checkInIsPerTable() throws Exception {
        HttpResponse<String> r = post(java.util.Map.of("ddl", "CREATE TABLE a (status VARCHAR(10) CHECK (status IN ('NEW','PAID')));"
                + " CREATE TABLE b (status NUMBER(1) CHECK (status IN (0, 1)));", "table", "b", "dialect", "oracle"));
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(!r.body().contains("NEW"), r.body());
    }
}
