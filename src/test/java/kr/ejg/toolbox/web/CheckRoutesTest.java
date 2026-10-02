package kr.ejg.toolbox.web;

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
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 5-4 — 픽스처를 복사한 폴더를 검사 → 결과·이력·비교, 붙여넣기, 거절 */
class CheckRoutesTest {

    static Javalin app;
    static Path project;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nproject:\n  encoding: UTF-8\n  lineEnding: LF\nframework: egov35\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n"
                + "codecheck:\n  groups: { tsx: false }\n  rules: { common.todo: false }\n", StandardCharsets.UTF_8);
        project = tmp.resolve("proj");
        Path src = Path.of("src/test/resources/fixtures/check/java");
        Files.createDirectories(project);
        for (String n : new String[] {"PosController.java", "PosBadName.java", "NegController.java"}) {
            Files.copy(src.resolve(n), project.resolve(n));
        }
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

    static JsonNode get(String path) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), path + " " + r.body());
        return JSON.readTree(r.body());
    }

    static JsonNode waitJob(HttpResponse<String> r) throws Exception {
        assertEquals(202, r.statusCode(), r.body());
        String jobId = JSON.readTree(r.body()).get("jobId").asText();
        JsonNode job = null;
        long end = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < end) {
            job = get("/api/jobs/" + jobId);
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        return job.get("result");
    }

    @Test
    void folderRunHistoryCompare() throws Exception {
        JsonNode first = waitJob(post("/api/check/run", Map.of("path", project.toString())));
        assertEquals(3, first.get("files").asInt());
        JsonNode rows = first.get("findings");
        assertTrue(rows.size() > 0);
        boolean dup = false;
        for (JsonNode f : rows) {
            dup |= f.get("rule").asText().equals("java.dupMapping");
            assertTrue(f.hasNonNull("excerpt"), "실행 결과에는 발췌가 있다");
        }
        assertTrue(dup, rows.toString());
        long a = first.get("runId").asLong();

        // 한 파일을 고치고 다시 — 비교
        Path pc = project.resolve("PosController.java");
        Files.writeString(pc, Files.readString(pc).replace("import java.util.List;\n", ""), StandardCharsets.UTF_8);
        JsonNode second = waitJob(post("/api/check/run", Map.of("path", project.toString())));
        long b = second.get("runId").asLong();

        JsonNode runs = get("/api/check/runs");
        assertTrue(runs.size() >= 2);
        JsonNode one = get("/api/check/runs/" + b);
        assertEquals(project.toString(), one.get("run").get("path").asText());
        for (JsonNode f : one.get("findings")) {
            assertTrue(f.get("excerpt").isNull(), "이력에는 발췌가 없다");
        }
        JsonNode cmp = get("/api/check/runs/" + b + "/compare");
        assertEquals(a, cmp.get("prevId").asLong());
        assertTrue(cmp.get("removed").toString().contains("java.unusedImport"), cmp.toString());
        assertEquals(cmp.toString(), get("/api/check/runs/" + b + "/compare?prev=" + a).toString());
        HttpResponse<String> x = post("/api/check/runs/" + b + "/export", Map.of());
        assertEquals(200, x.statusCode(), x.body());
        assertTrue(Files.size(Path.of(JSON.readTree(x.body()).get("path").asText())) > 0);
    }

    @Test
    void textRunAndRules() throws Exception {
        JsonNode r = waitJob(post("/api/check/run", Map.of("text", "class A { void f() { System.out.println(1); } }", "lang", "java",
                "groups", Map.of("file", false))));
        assertEquals("[\"common.sysout\"]", JSON.writeValueAsString(r.get("findings").findValuesAsText("rule")));
        JsonNode rules = get("/api/check/rules");
        boolean todoOff = false;
        boolean tsxOff = false;
        for (JsonNode d : rules) {
            todoOff |= d.get("id").asText().equals("common.todo") && !d.get("enabled").asBoolean();
            tsxOff |= d.get("id").asText().equals("tsx.console") && !d.get("enabled").asBoolean();
        }
        assertTrue(todoOff && tsxOff, "프로필 덮어쓰기 적용");
    }

    @Test
    void refusals() throws Exception {
        assertEquals(400, post("/api/check/run", Map.of("path", project.toString(), "changedOnly", true)).statusCode());
        assertEquals(400, post("/api/check/run", Map.of()).statusCode());
        assertEquals(400, post("/api/check/run", Map.of("text", "x", "ruleOverrides", Map.of("common.sysout", Map.of("regex", "(")))).statusCode());
        assertEquals(400, post("/api/check/run", Map.of("path", "relative/dir")).statusCode());
        assertEquals(404, post("/api/check/run", Map.of("path", tmp.resolve("none").toString())).statusCode());
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/check/runs/999999")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, r.statusCode());
        assertFalse(r.body().isEmpty());
    }
}
