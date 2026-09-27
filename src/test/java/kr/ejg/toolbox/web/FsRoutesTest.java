package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 4-1 — `/api/fs/*` 목록·읽기·쓰기(백업)·최근·거절 */
class FsRoutesTest {

    static Javalin app;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    static String q(Path p) {
        return URLEncoder.encode(p.toString(), StandardCharsets.UTF_8);
    }

    static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void listReadWriteRecent() throws Exception {
        Path root = tmp.resolve("proj");
        Path jsp = root.resolve("web/a.jsp");
        Files.createDirectories(jsp.getParent());
        byte[] orig = "<%-- 가 --%>\r\n".getBytes(Charset.forName("MS949"));
        Files.write(jsp, orig);
        Files.writeString(root.resolve("web/b.txt"), "x");

        HttpResponse<String> l = get("/api/fs/list?path=" + q(root) + "&glob=*.jsp");
        assertEquals(200, l.statusCode(), l.body());
        JsonNode files = JSON.readTree(l.body()).get("files");
        assertEquals(1, files.size());
        assertEquals("web/a.jsp", files.get(0).get("rel").asText());

        JsonNode t = JSON.readTree(get("/api/fs/read?path=" + q(jsp)).body());
        assertEquals("MS949", t.get("encoding").asText());
        assertEquals("CRLF", t.get("lineEnding").asText());

        ObjectNode w = JSON.createObjectNode();
        w.put("path", jsp.toString()).put("root", root.toString()).put("text", t.get("text").asText() + "<p/>\n")
                .put("encoding", t.get("encoding").asText()).put("lineEnding", t.get("lineEnding").asText());
        HttpResponse<String> wr = post("/api/fs/write", w.toString());
        assertEquals(200, wr.statusCode(), wr.body());
        JsonNode res = JSON.readTree(wr.body());
        Path backup = Path.of(res.get("backup").asText());
        assertTrue(backup.startsWith(tmp.resolve("out/t/" + res.get("stamp").asText() + "/backup")), backup.toString());
        assertArrayEquals(orig, Files.readAllBytes(backup));
        assertArrayEquals("<%-- 가 --%>\r\n<p/>\r\n".getBytes(Charset.forName("MS949")), Files.readAllBytes(jsp));

        JsonNode recent = JSON.readTree(get("/api/fs/recent").body());
        assertEquals(root.toString(), recent.get(0).asText());
        assertTrue(JSON.readTree(get("/api/fs/exists?path=" + q(jsp)).body()).get("exists").asBoolean());
    }

    @Test
    void refusalsCarryStatus() throws Exception {
        assertEquals(400, get("/api/fs/read?path=relative/a.jsp").statusCode());
        assertEquals(400, get("/api/fs/list?path=" + q(tmp.resolve("data"))).statusCode());
        assertEquals(404, get("/api/fs/list?path=" + q(tmp.resolve("nope"))).statusCode());
        HttpResponse<String> r = get("/api/fs/read?path=");
        assertEquals(400, r.statusCode());
        assertTrue(JSON.readTree(r.body()).get("message").asText().contains("path"));
    }
}
