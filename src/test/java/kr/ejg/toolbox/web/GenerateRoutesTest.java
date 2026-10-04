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

/** 7-3 — 저장소 템플릿 세트를 임시 templates/gen 으로 복사 → H2 스냅샷 → job → 파일 열 · 세트 목록 · 400·404 */
class GenerateRoutesTest {

    static Javalin app;
    static Connection holder;
    static Path out;
    static long snapshotId;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        holder = DriverManager.getConnection("jdbc:h2:mem:generate73;DB_CLOSE_DELAY=-1", "sa", "pw");
        try (Statement st = holder.createStatement()) {
            st.execute("CREATE TABLE TB_EMP (EMP_NO INTEGER PRIMARY KEY, EMP_NM VARCHAR(30) NOT NULL, HIRE_DT DATE)");
            st.execute("COMMENT ON TABLE TB_EMP IS '사원'");
            st.execute("CREATE VIEW EMP_V AS SELECT EMP_NO, EMP_NM FROM TB_EMP");
        }
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        AnalyzeRoutesTest.copy(Path.of("templates/gen"), tmp.resolve("templates/gen"));
        out = tmp.resolve("out");
        Files.createDirectories(out);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nlogicalName:\n  skipTokens: [TB]\n"
                + "connections:\n  - id: h2\n    dialect: h2\n    url: jdbc:h2:mem:generate73;DB_CLOSE_DELAY=-1\n    user: sa\n"
                + "generator:\n  templateSet: egov35\n  basePackage: kr.go.test\n  outDir: '" + out.toString().replace('\\', '/') + "'\n",
                StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        assertEquals(200, post("/api/conn/h2/password", Map.of("password", "pw")).statusCode());
        JsonNode job = waitJob(JSON.readTree(post("/api/meta/snapshot", Map.of("connId", "h2")).body()).get("jobId").asText());
        snapshotId = job.get("result").get("snapshotId").asLong();
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

    static JsonNode get(String path) throws Exception {
        return JSON.readTree(HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString()).body());
    }

    static JsonNode waitJob(String jobId) throws Exception {
        JsonNode job = null;
        long end = System.nanoTime() + 20_000_000_000L;
        while (System.nanoTime() < end) {
            job = get("/api/jobs/" + jobId);
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        return job;
    }

    @Test
    void templatesListsBothSets() throws Exception {
        JsonNode t = get("/api/generate/templates");
        assertEquals(3, t.size(), t.toString());
        assertEquals("egov35", t.get(0).get("name").asText());
        assertEquals("egov4", t.get(1).get("name").asText()); // 7-12 — org.egovframe.rte + javax
        assertEquals("org.egovframe.rte", t.get(1).get("vars").get("rte").asText());
        assertEquals("javax", t.get(1).get("vars").get("ee").asText());
        assertEquals("egov5", t.get(2).get("name").asText());
        assertEquals("jakarta", t.get(2).get("vars").get("ee").asText());
        assertEquals(10, t.get(0).get("files").size());
    }

    @Test
    void generatesWithProfileDefaults() throws Exception {
        HttpResponse<String> r = post("/api/generate", Map.of("snapshotId", snapshotId, "tables", List.of(Map.of("name", "TB_EMP"),
                Map.of("name", "EMP_V"))));
        assertEquals(202, r.statusCode(), r.body());
        JsonNode res = waitJob(JSON.readTree(r.body()).get("jobId").asText()).get("result");
        assertEquals(10, res.get("created").asInt(), res.toString());
        assertTrue(res.get("warnings").toString().contains("EMP_V"), "뷰는 건너뜀 — " + res);
        assertTrue(Files.exists(out.resolve("src/main/java/kr/go/test/emp/web/EmpController.java")), "프로필 기본 패키지·세트");
    }

    @Test
    void refusals() throws Exception {
        assertEquals(400, post("/api/generate", Map.of("snapshotId", snapshotId, "tables", List.of(), "templateSet", "egov35")).statusCode());
        assertEquals(400, post("/api/generate", Map.of("snapshotId", snapshotId, "tables", List.of(Map.of("name", "TB_EMP")),
                "templateSet", "nope")).statusCode());
        assertEquals(400, post("/api/generate", Map.of("snapshotId", snapshotId, "tables", List.of(Map.of("name", "TB_EMP")),
                "basePackage", "Kr.Go")).statusCode());
        assertEquals(400, post("/api/generate", Map.of("snapshotId", snapshotId, "tables", List.of(Map.of("name", "TB_EMP")),
                "module", "a..b")).statusCode());
        assertEquals(400, post("/api/generate", Map.of("snapshotId", snapshotId, "tables", List.of(Map.of("name", "TB_EMP")),
                "outDir", tmp.resolve("nowhere").toString())).statusCode());
        assertEquals(400, post("/api/generate", Map.of("snapshotId", snapshotId, "tables", List.of(Map.of("name", "TB_EMP")),
                "dialect", "db2")).statusCode(), "모르는 방언(7-10)");
        assertEquals(404, post("/api/generate", Map.of("snapshotId", 999, "tables", List.of(Map.of("name", "TB_EMP")))).statusCode());
        assertEquals(404, post("/api/generate", Map.of("snapshotId", snapshotId, "tables", List.of(Map.of("name", "NOPE")))).statusCode());
    }
}
