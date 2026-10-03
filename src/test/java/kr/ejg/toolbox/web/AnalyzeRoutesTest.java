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
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 6-4 — 분석 실행(job) → 이력 조회. 6-2·6-3 픽스처를 한 폴더에 복사해 프로그램·CRUD·미해결 값을 본다 */
class AnalyzeRoutesTest {

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
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nframework: egov35\n", StandardCharsets.UTF_8);
        project = tmp.resolve("proj");
        copy(Path.of("src/test/resources/fixtures/analyze/java"), project.resolve("src/main/java"));
        copy(Path.of("src/test/resources/fixtures/analyze/mapper"), project.resolve("src/main/resources/mapper"));
        // 픽스처 Java 의 문장(Board.*)과 맞는 매퍼 하나
        Files.writeString(project.resolve("src/main/resources/mapper/Board_SQL.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="Board">
                    <select id="selectList">SELECT * FROM COMTNBBS a JOIN COMVNUSERMASTER b ON a.ID = b.ID</select>
                    <insert id="insert">INSERT INTO COMTNBBS (ID) VALUES (#{id})</insert>
                    <select id="selectDetail">SELECT * FROM COMTNBBS</select>
                </mapper>
                """, StandardCharsets.UTF_8);
        Files.writeString(project.resolve("src/main/resources/mapper/Login_SQL.xml"), """
                <mapper namespace="Login">
                    <update id="updateIncorrectUSR">UPDATE COMTNUSER SET X = 1</update>
                    <update id="updateIncorrectGNR">UPDATE COMTNGNR SET X = 1</update>
                </mapper>
                """, StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    static void copy(Path from, Path to) throws Exception {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Path d = to.resolve(from.relativize(p).toString());
                Files.createDirectories(d.getParent());
                Files.copy(p, d);
            }
        }
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

    static HttpResponse<String> raw(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode get(String path) throws Exception {
        HttpResponse<String> r = raw(path);
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
    void runAndHistory() throws Exception {
        JsonNode res = waitJob(post("/api/analyze/run", Map.of("path", project.toString())));
        long runId = res.get("runId").asLong();
        assertEquals(12, res.get("programs").size(), res.toString());

        JsonNode crud = get("/api/analyze/runs/" + runId + "/crud");
        Map<String, String> byUrl = new java.util.TreeMap<>();
        for (JsonNode row : crud.get("rows")) {
            byUrl.put(row.get("verb").asText() + " " + row.get("url").asText() + " " + row.get("params").asText() + " " + row.get("method").asText(),
                    row.get("crud").toString());
        }
        assertEquals("{\"COMTNBBS\":\"R\",\"COMVNUSERMASTER\":\"R\"}", byUrl.get("ANY /bbs/list.do  list"));
        assertEquals("{\"COMTNBBS\":\"C\",\"COMTNGNR\":\"U\",\"COMTNUSER\":\"U\"}", byUrl.get("POST /bbs/add.do cmd=Regist add"),
                "접두 Login.updateIncorrect → 문장 둘");
        assertTrue(crud.get("tables").toString().contains("COMTNUSER"), crud.toString());

        JsonNode un = get("/api/analyze/runs/" + runId + "/unresolved");
        String kinds = un.toString();
        for (String k : new String[] {"\"parse\"", "\"prefix\"", "\"statement\"", "\"ambiguous\"", "\"missing\"", "\"viewDynamic\""}) {
            assertTrue(kinds.contains(k), k + " — " + kinds);
        }
        assertTrue(kinds.contains("Board.selectVar"), "색인에 없는 ns.id 는 missing — " + kinds);

        JsonNode programs = get("/api/analyze/runs/" + runId + "/programs");
        assertEquals(12, programs.size());
        assertEquals(1, get("/api/analyze/runs").size());
        assertEquals(404, raw("/api/analyze/runs/999/crud").statusCode());
        assertEquals(404, post("/api/analyze/run", Map.of("path", tmp.resolve("none").toString())).statusCode());
        assertEquals(400, post("/api/analyze/run", Map.of()).statusCode());
    }
}
