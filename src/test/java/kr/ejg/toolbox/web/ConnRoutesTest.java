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

/** 1-4 라우트 — 비밀번호가 응답 본문에 안 나온다 */
class ConnRoutesTest {

    static final String PW = "route-Pw-5521";
    static Javalin app;
    static Connection holder;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        holder = DriverManager.getConnection("jdbc:h2:mem:routetest;DB_CLOSE_DELAY=-1", "sa", PW);
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nconnections:\n  - id: h2\n    dialect: h2\n"
                + "    url: jdbc:h2:mem:routetest;DB_CLOSE_DELAY=-1\n    user: sa\n", StandardCharsets.UTF_8);
        Files.writeString(profiles.resolve("u.yaml"), "name: u\nconnections:\n  - id: h2\n    dialect: h2\n"
                + "    url: jdbc:h2:mem:other;DB_CLOSE_DELAY=-1\n    user: sa\n", StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
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

    @Test
    void passwordFlow() throws Exception {
        JsonNode list = JSON.readTree(send("GET", "/api/conn", null).body());
        assertEquals("h2", list.get(0).get("id").asText());
        assertFalse(list.get(0).get("hasPassword").asBoolean());

        HttpResponse<String> wrong = send("POST", "/api/conn/h2/password", "{\"password\":\"nope-" + PW + "\"}");
        assertEquals(200, wrong.statusCode());
        HttpResponse<String> t1 = send("POST", "/api/conn/h2/test", null);
        assertFalse(JSON.readTree(t1.body()).get("ok").asBoolean());
        assertFalse(t1.body().contains(PW), "응답에 비밀번호 없음");

        send("POST", "/api/conn/h2/password", "{\"password\":\"" + PW + "\"}");
        HttpResponse<String> t2 = send("POST", "/api/conn/h2/test", null);
        assertTrue(JSON.readTree(t2.body()).get("ok").asBoolean(), t2.body());
        assertFalse(t2.body().contains(PW));

        String after = send("GET", "/api/conn", null).body();
        assertTrue(JSON.readTree(after).get(0).get("hasPassword").asBoolean());
        assertFalse(after.contains(PW), "목록에 비밀번호 없음");
    }

    /** 1-8 — 프로필 목록·전환. 전환하면 ping 이 따르고 메모리 비밀번호가 지워진다(같은 접속 id 가 다른 DB 로 가지 않게) */
    @Test
    void profileSwitchClearsPasswords() throws Exception {
        JsonNode p = JSON.readTree(send("GET", "/api/profiles", null).body());
        assertEquals("[\"t\",\"u\"]", p.get("names").toString());
        assertEquals("t", p.get("active").asText());

        send("POST", "/api/conn/h2/password", "{\"password\":\"" + PW + "\"}");
        assertTrue(JSON.readTree(send("GET", "/api/conn", null).body()).get(0).get("hasPassword").asBoolean());

        assertEquals(200, send("POST", "/api/profiles/active", "{\"name\":\"u\"}").statusCode());
        assertEquals("u", JSON.readTree(send("GET", "/api/ping", null).body()).get("profile").asText());
        JsonNode conns = JSON.readTree(send("GET", "/api/conn", null).body());
        assertTrue(conns.get(0).get("url").asText().contains("other"));
        assertFalse(conns.get(0).get("hasPassword").asBoolean(), "전환하면 비밀번호를 지운다");

        assertEquals(404, send("POST", "/api/profiles/active", "{\"name\":\"nope\"}").statusCode());
        assertEquals(200, send("POST", "/api/profiles/active", "{\"name\":\"t\"}").statusCode());
        assertEquals(404, send("GET", "/api/profiles/nope", null).statusCode());
    }

    @Test
    void unknownConnectionIs404() throws Exception {
        assertEquals(404, send("POST", "/api/conn/nope/test", null).statusCode());
        assertEquals(404, send("POST", "/api/conn/nope/password", "{\"password\":\"x\"}").statusCode());
    }
}
