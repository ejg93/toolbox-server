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

/** 5-34d — 코드 정리 라우트. 규칙 아홉(5-42) · 미리보기 → 적용(같은 stamp 의 백업·결과 글) · 낡은 hash·outHash · 거절 */
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
    void rulesListsFourteen() throws Exception {
        JsonNode r = get("/api/cleanup/rules");
        assertEquals(14, r.size()); // 5-42 console 둘 · 설계 50 값·자원 다섯
        List<String> opt = new ArrayList<>();
        for (JsonNode x : r) {
            assertTrue(x.path("enabled").asBoolean(), x.toString());
            if (x.path("opt").asBoolean()) {
                opt.add(x.path("id").asText());
            }
            if (x.path("id").asText().equals("file.lineEnding")) {
                assertEquals("LF", x.path("expected").asText());
            }
        }
        assertEquals(List.of("java.bigDecimalDouble", "java.tryWithResources"), opt, "B 둘만 기본 끔(설계 50)");
    }

    /** 5-39b — 판별이 찾은 빌드 결과 폴더(package.json 옆 dist)는 미리보기에서 빠지고, 쓰기의 범위 재확인도 같은 제외로 거절한다 */
    @Test
    void buildOutputExcluded() throws Exception {
        Path fe = Files.createDirectories(project.resolve("frontend/src"));
        Files.writeString(project.resolve("frontend/package.json"), "{\"dependencies\":{\"react\":\"19\"},\"devDependencies\":{\"vite\":\"7\"}}");
        Files.writeString(fe.resolve("a.js"), "function f() {\n  debugger;\n}\n");
        Files.createDirectories(project.resolve("frontend/dist"));
        Files.writeString(project.resolve("frontend/dist/app.js"), "function g() {\n  debugger;\n}\n");
        JsonNode pv = waitJob(post("/api/cleanup/preview", Map.of("path", project.toString())));
        List<String> rels = new ArrayList<>();
        pv.path("rows").forEach(r -> rels.add(r.path("rel").asText()));
        assertTrue(rels.contains("frontend/src/a.js"), rels.toString());
        assertFalse(rels.contains("frontend/dist/app.js"), rels.toString());
        assertEquals("[\"frontend/dist/**\"]", pv.path("excluded").toString());
        Map<String, Object> forged = new LinkedHashMap<>();
        forged.put("rel", "frontend/dist/app.js");
        forged.put("hash", "x");
        forged.put("outHash", "y");
        JsonNode ap = waitJob(post("/api/cleanup/apply", Map.of("path", project.toString(), "files", List.of(forged))));
        assertEquals(1, ap.path("stale").asInt(), ap.toString());
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
        Path outRoot = Path.of(ap.path("outRoot").asText());
        assertEquals(tmp.resolve("out/t/" + ap.path("stamp").asText() + "/cleanup").toAbsolutePath(), outRoot, "결과 폴더는 이 stamp 의 cleanup");
        assertArrayEquals(before, Files.readAllBytes(project.resolve("Pos.java")), "원본 그대로(9-3b)");
        assertFalse(Files.readString(outRoot.resolve("Pos.java")).contains("import java.util.Map;"));
        Path report = Path.of(ap.path("report").asText());
        assertTrue(Files.exists(report) && report.getParent().equals(outRoot.getParent()), report.toString());
    }

    /** 9-3b — 고른 결과 폴더: 빈 폴더면 거기에 결과·표식·결과 글, 원본 안이면 400 */
    @Test
    void userOutDir() throws Exception {
        JsonNode pv = waitJob(post("/api/cleanup/preview", Map.of("path", project.toString())));
        Path mine = Files.createDirectories(tmp.resolve("mine"));
        JsonNode ap = waitJob(post("/api/cleanup/apply", Map.of("path", project.toString(), "files", refs(pv), "outDir", mine.toString())));
        assertEquals(mine.toAbsolutePath().toString(), ap.path("outRoot").asText());
        assertTrue(Files.exists(mine.resolve("Pos.java")) && Files.exists(mine.resolve(kr.ejg.toolbox.core.fs.LocalFiles.MARK)), ap.toString());
        assertTrue(Files.exists(mine.resolve(kr.ejg.toolbox.core.check.CleanupRunner.REPORT)), "결과 글도 고른 폴더에");
        HttpResponse<String> inside = post("/api/cleanup/apply", Map.of("path", project.toString(), "files", refs(pv),
                "outDir", project.resolve("res").toString()));
        assertEquals(400, inside.statusCode(), inside.body());
        assertFalse(Files.exists(project.resolve("res")), "원본 안에 폴더를 안 만든다");
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
