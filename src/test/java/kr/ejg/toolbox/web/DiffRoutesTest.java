package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.net.URI;
import java.net.URLEncoder;
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

/** 4-2 — `/api/diff/folders` · `/api/diff/file` */
class DiffRoutesTest {

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

    @Test
    void foldersThenFile() throws Exception {
        Path a = tmp.resolve("a");
        Path b = tmp.resolve("b");
        Files.createDirectories(a);
        Files.createDirectories(b);
        Files.writeString(a.resolve("x.jsp"), "1\n2\n");
        Files.writeString(b.resolve("x.jsp"), "1\n3\n");
        String body = JSON.createObjectNode().put("a", a.toString()).put("b", b.toString()).put("glob", "*.jsp").toString();
        HttpResponse<String> f = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/diff/folders"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, f.statusCode(), f.body());
        JsonNode item = JSON.readTree(f.body()).get("items").get(0);
        assertEquals("DIFF", item.get("status").asText());

        String q = "?a=" + URLEncoder.encode(a.resolve("x.jsp").toString(), StandardCharsets.UTF_8)
                + "&b=" + URLEncoder.encode(b.resolve("x.jsp").toString(), StandardCharsets.UTF_8);
        HttpResponse<String> d = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/diff/file" + q))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, d.statusCode(), d.body());
        assertEquals("[{\"op\":\"=\",\"line\":\"1\"},{\"op\":\"-\",\"line\":\"2\"},{\"op\":\"+\",\"line\":\"3\"}]",
                JSON.readTree(d.body()).get("ops").toString());
    }
}
