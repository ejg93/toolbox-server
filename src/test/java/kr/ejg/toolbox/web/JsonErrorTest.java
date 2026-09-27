package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

/** 0-35 — 본문 JSON 을 못 읽으면 400. 메시지에 필드 이름만, 값은 없다(규칙 3) */
class JsonErrorTest {

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

    static HttpResponse<String> put(String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/dict/user/JSONERR"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void unknownFieldIs400WithNameOnly() throws Exception {
        HttpResponse<String> r = put("{\"ko\":\"값\",\"secretField\":\"비밀값-4821\"}");
        assertEquals(400, r.statusCode(), r.body());
        assertEquals("모르는 필드: secretField", JSON.readTree(r.body()).get("message").asText());
        assertFalse(r.body().contains("4821"), "값이 응답에 실렸다");
    }

    @Test
    void brokenJsonIs400WithoutBody() throws Exception {
        HttpResponse<String> r = put("{\"ko\":\"비밀값-7719\",");
        assertEquals(400, r.statusCode(), r.body());
        assertEquals("본문을 못 읽었다", JSON.readTree(r.body()).get("message").asText());
        assertFalse(r.body().contains("7719"), "값이 응답에 실렸다");
    }
}
