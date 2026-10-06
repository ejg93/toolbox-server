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
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 1-43 — 접속은 프로필 password 를 쓴다(사용자 2026-10-06). 값은 목록·프로필 응답·실패 글로 안 나간다.
 * {@code ConnRoutesTest} 는 프로필 목록을 단언해 같은 앱에 프로필을 못 더한다 — 따로 띄운다
 */
class ProfilePasswordTest {

    static final String PW = "pp-비밀-4471";
    static final String WRONG = "zz-wrong-5512";
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();
    static Javalin app;
    static Connection holder;

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        String url = "jdbc:h2:mem:pptest;DB_CLOSE_DELAY=-1";
        holder = DriverManager.getConnection(url, "sa", PW);
        Path profiles = Files.createDirectories(tmp.resolve("profiles"));
        Files.writeString(profiles.resolve("p.yaml"), "name: p\nconnections:\n"
                + "  - id: h2\n    dialect: h2\n    url: " + url + "\n    user: sa\n    password: " + PW + "\n"
                + "  - id: h2none\n    dialect: h2\n    url: " + url + "\n    user: sa\n"
                + "  - id: h2bad\n    dialect: h2\n    url: " + url + "\n    user: sa\n    password: " + WRONG + "\n", StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "p", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    @AfterAll
    static void down() throws Exception {
        app.stop();
        holder.close();
    }

    static HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
                .header("Content-Type", "application/json");
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode test(String id) throws Exception {
        HttpResponse<String> r = send("POST", "/api/conn/" + id + "/test", null);
        assertFalse(r.body().contains(PW) || r.body().contains(WRONG), "실패 글에 비밀번호 없음: " + r.body());
        return JSON.readTree(r.body());
    }

    @Test
    void listSaysWhetherProfileHasPassword() throws Exception {
        String body = send("GET", "/api/conn", null).body();
        assertFalse(body.contains(PW) || body.contains(WRONG), body);
        JsonNode list = JSON.readTree(body);
        assertTrue(list.get(0).get("hasPassword").asBoolean());
        assertFalse(list.get(1).get("hasPassword").asBoolean());
        assertTrue(list.get(2).get("hasPassword").asBoolean());
    }

    @Test
    void profilePasswordConnectsWithoutInput() throws Exception {
        JsonNode r = test("h2");
        assertTrue(r.get("ok").asBoolean(), r.toString());
    }

    @Test
    void missingPasswordSaysWhereToPutIt() throws Exception {
        JsonNode r = test("h2none");
        assertFalse(r.get("ok").asBoolean());
        String m = r.get("message").asText();
        assertTrue(m.contains("password 칸") && m.contains("profiles/p.yaml") && m.contains("h2none"), m);
    }

    @Test
    void wrongPasswordFailsWithoutEcho() throws Exception {
        JsonNode r = test("h2bad");
        assertFalse(r.get("ok").asBoolean(), r.toString());
        assertFalse(r.get("message").asText().contains("password 칸"), "비밀번호가 있으면 없음 안내를 안 붙인다");
    }

    @Test
    void profileJsonHasNoPassword() throws Exception {
        HttpResponse<String> r = send("GET", "/api/profiles/p", null);
        assertEquals(200, r.statusCode(), r.body());
        assertFalse(r.body().contains(PW) || r.body().contains(WRONG), r.body());
        assertEquals("h2", JSON.readTree(r.body()).get("connections").get(0).get("id").asText());
    }

    /** CLI·시험의 메모리 덮어쓰기(/password)가 프로필보다 앞선다. 지우면 프로필 값으로 돌아간다 */
    @Test
    void memoryOverrideWinsUntilForgotten() throws Exception {
        assertEquals(200, send("POST", "/api/conn/h2/password", "{\"password\":\"x-wrong\"}").statusCode());
        try {
            assertFalse(test("h2").get("ok").asBoolean());
        } finally {
            assertEquals(200, send("DELETE", "/api/conn/h2/password", null).statusCode());
        }
        assertTrue(test("h2").get("ok").asBoolean());
    }
}
