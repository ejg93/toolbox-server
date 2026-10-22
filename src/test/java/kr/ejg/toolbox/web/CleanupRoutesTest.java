package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 5-34d — 코드 정리 라우트. 규칙 일곱 · 미리보기 → 적용(같은 stamp 의 백업·결과 글) · 낡은 hash·outHash · 거절 */
class CleanupRoutesTest {

    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tmp;

    Path project;
    Javalin app;

    @BeforeEach
    void up() throws Exception {
        Path profiles = Files.createDirectories(tmp.resolve("profiles"));
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nproject:\n  encoding: UTF-8\n  lineEnding: LF\nframework: egov35\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        project = tmp.resolve("proj");
        Path src = Path.of("src/test/resources/fixtures/cleanup");
        try (Stream<Path> s = Files.walk(src.resolve("javaimport"))) {
            Files.createDirectories(project);
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Files.copy(p, project.resolve(p.getFileName().toString()));
            }
        }
        Files.copy(src.resolve("debugger/pos.jsp"), project.resolve("pos.jsp"));
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    @AfterEach
    void down() {
        app.stop();
    }

    HttpResponse<String> post(String path, Object body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    JsonNode get(String path) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), path + " " + r.body());
        return JSON.readTree(r.body());
    }

    JsonNode waitJob(HttpResponse<String> r) throws Exception {
        assertEquals(202, r.statusCode(), r.body());
        String id = JSON.readTree(r.body()).get("jobId").asText();
        JsonNode job = null;
        long end = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < end) {
            job = get("/api/jobs/" + id);
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        return job.get("result");
    }

    static List<Map<String, Object>> refs(JsonNode preview) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode row : preview.path("rows")) {
            if (row.path("changed").asBoolean()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rel", row.path("rel").asText());
                m.put("hash", row.path("hash").asText());
                m.put("outHash", row.path("outHash").asText());
                out.add(m);
            }
        }
        return out;
    }

    @Test
    void rulesListsSeven() throws Exception {
        JsonNode r = get("/api/cleanup/rules");
        assertEquals(7, r.size());
        for (JsonNode x : r) {
            assertTrue(x.path("enabled").asBoolean(), x.toString());
            if (x.path("id").asText().equals("file.lineEnding")) {
                assertEquals("LF", x.path("expected").asText());
            }
        }
    }

    @Test
    void previewThenApply() throws Exception {
        byte[] before = Files.readAllBytes(project.resolve("Pos.java"));
        JsonNode pv = waitJob(post("/api/cleanup/preview", Map.of("path", project.toString())));
        assertArrayEquals(before, Files.readAllBytes(project.resolve("Pos.java")), "미리보기는 안 쓴다");
        assertEquals(2, pv.path("changed").asInt(), pv.toString());
        List<Map<String, Object>> refs = refs(pv);
        JsonNode ap = waitJob(post("/api/cleanup/apply", Map.of("path", project.toString(), "files", refs)));
        assertEquals(2, ap.path("written").asInt(), ap.toString());
        Path backup = Path.of(ap.path("backup").asText());
        assertTrue(backup.toString().contains(ap.path("stamp").asText()), "백업은 이 stamp 폴더");
        assertArrayEquals(before, Files.readAllBytes(backup.resolve("Pos.java")), "원본이 백업에");
        assertFalse(Files.readString(project.resolve("Pos.java")).contains("import java.util.Map;"));
        Path report = Path.of(ap.path("report").asText());
        assertTrue(Files.exists(report) && report.getParent().equals(backup.getParent()), report.toString());
    }

    @Test
    void staleRefused() throws Exception {
        JsonNode pv = waitJob(post("/api/cleanup/preview", Map.of("path", project.toString())));
        List<Map<String, Object>> refs = refs(pv);
        Files.writeString(project.resolve("Pos.java"), Files.readString(project.resolve("Pos.java")) + "// 고침\n");
        byte[] edited = Files.readAllBytes(project.resolve("Pos.java"));
        refs.stream().filter(m -> m.get("rel").equals("pos.jsp")).forEach(m -> m.put("outHash", "0"));
        JsonNode ap = waitJob(post("/api/cleanup/apply", Map.of("path", project.toString(), "files", refs)));
        assertEquals(2, ap.path("stale").asInt(), ap.toString());
        assertEquals(0, ap.path("written").asInt());
        assertArrayEquals(edited, Files.readAllBytes(project.resolve("Pos.java")));
    }

    @Test
    void refusals() throws Exception {
        assertEquals(404, post("/api/cleanup/preview", Map.of("path", tmp.resolve("없음").toString())).statusCode());
        assertEquals(400, post("/api/cleanup/preview", Map.of()).statusCode());
        assertEquals(400, post("/api/cleanup/apply", Map.of("path", project.toString(), "files", List.of())).statusCode());
        HttpResponse<String> vcs = post("/api/cleanup/preview", Map.of("path", project.toString(), "changedOnly", true));
        assertEquals(400, vcs.statusCode(), vcs.body());
    }
}
