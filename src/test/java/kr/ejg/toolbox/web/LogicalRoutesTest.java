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
    static java.sql.Connection holder;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        holder = java.sql.DriverManager.getConnection("jdbc:h2:mem:audittest;DB_CLOSE_DELAY=-1", "sa", "pw");
        try (java.sql.Statement st = holder.createStatement()) {
            st.execute("CREATE TABLE TB_USE_HIST (USE_YN VARCHAR(10), QWZX_CD VARCHAR(5))");
            st.execute("COMMENT ON COLUMN TB_USE_HIST.USE_YN IS '사용여부'");
        }
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nlogicalName:\n  skipTokens: [TB]\n"
                + "connections:\n  - id: h2\n    dialect: h2\n    url: jdbc:h2:mem:audittest;DB_CLOSE_DELAY=-1\n    user: sa\n"
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
    void candidatesWriteFileUnderOut() throws Exception {
        HttpResponse<String> r = post("/api/logical/candidates", java.util.Map.of("csv", sampleCsv(), "kind", "terms", "dbName", "SAMPLE"));
        assertEquals(200, r.statusCode(), r.body());
        Path file = Path.of(JSON.readTree(r.body()).get("path").asText());
        String body = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(body.startsWith(String.valueOf((char) 0xFEFF) + "출처,DB명,표준용어명"), body.substring(0, 20));
        assertTrue(file.getFileName().toString().equals("표준용어후보.csv"));
        assertTrue(file.toString().replace('\\', '/').contains("/t/"), "out/<프로필>/<시각>/");
        assertEquals(400, post("/api/logical/candidates", java.util.Map.of("csv", sampleCsv(), "kind", "x")).statusCode());
    }

    @Test
    void auditFromSnapshotWritesXlsx() throws Exception {
        assertEquals(200, post("/api/conn/h2/password", java.util.Map.of("password", "pw")).statusCode());
        String jobId = JSON.readTree(post("/api/meta/snapshot", java.util.Map.of("connId", "h2")).body()).get("jobId").asText();
        com.fasterxml.jackson.databind.JsonNode job = null;
        long end = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < end) {
            job = JSON.readTree(HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/jobs/" + jobId))
                    .build(), HttpResponse.BodyHandlers.ofString()).body());
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        long id = job.get("result").get("snapshotId").asLong();

        HttpResponse<String> r = post("/api/logical/audit", java.util.Map.of("snapshotId", id));
        assertEquals(200, r.statusCode(), r.body());
        com.fasterxml.jackson.databind.JsonNode body = JSON.readTree(r.body());
        assertTrue(body.get("counts").has("NO_COMMENT") && !body.get("counts").has("COMMENT_MISMATCH"), "기본 넷: " + body.get("counts"));
        assertTrue(body.get("counts").get("DOMAIN_SPEC").asInt() >= 1, "USE_YN VARCHAR(10) — 여부C1 과 다름: " + body);
        assertTrue(body.get("counts").get("UNMATCHED_TOKEN").asInt() >= 1, "QWZX");
        Path file = Path.of(body.get("path").asText());
        assertEquals("표준미준수.xlsx", file.getFileName().toString());
        try (org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.toFile())) {
            assertEquals("규칙", wb.getSheetAt(0).getRow(0).getCell(3).getStringCellValue());
            assertEquals(body.get("findings").size(), wb.getSheetAt(0).getLastRowNum(), "머리 1 + 건수");
        }
        HttpResponse<String> on = post("/api/logical/audit", java.util.Map.of("snapshotId", id, "rules", java.util.List.of("comment_mismatch")));
        assertEquals(200, on.statusCode(), on.body());
        assertEquals(1, JSON.readTree(on.body()).get("counts").size(), "켠 규칙만");
        assertEquals(400, post("/api/logical/audit", java.util.Map.of("snapshotId", id, "rules", java.util.List.of("X"))).statusCode());
        assertEquals(400, post("/api/logical/audit", java.util.Map.of("csv", "A")).statusCode(), "CSV 는 코멘트가 없다");
        assertEquals(404, post("/api/logical/audit", java.util.Map.of("snapshotId", 999)).statusCode());
    }

    @Test
    void badInputIs400() throws Exception {
        assertEquals(400, post("/api/logical/comments", java.util.Map.of("dialect", "pg")).statusCode());
        assertEquals(400, post("/api/logical/comments", java.util.Map.of("csv", "A,B\n1,2\n")).statusCode());
        assertEquals(400, post("/api/logical/comments", java.util.Map.of("csv", "COLUMN_NAME\nX\n", "dialect", "db2")).statusCode());
        assertEquals(404, post("/api/logical/comments", java.util.Map.of("snapshotId", 999)).statusCode());
    }
}
