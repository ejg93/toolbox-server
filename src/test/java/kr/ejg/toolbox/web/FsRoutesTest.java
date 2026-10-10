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
                + "project:\n  root: '" + tmp.resolve("proj") + "'\n"
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
        assertEquals(tmp.resolve("out/t/" + res.get("stamp").asText() + "/backup").toAbsolutePath().toString(), res.get("backupRoot").asText(),
                "백업 폴더 전체 경로(0-44)");
        assertTrue(backup.startsWith(tmp.resolve("out/t/" + res.get("stamp").asText() + "/backup")), backup.toString());
        assertArrayEquals(orig, Files.readAllBytes(backup));
        assertArrayEquals("<%-- 가 --%>\r\n<p/>\r\n".getBytes(Charset.forName("MS949")), Files.readAllBytes(jsp));

        JsonNode recent = JSON.readTree(get("/api/fs/recent").body());
        assertEquals(root.toString(), recent.get(0).asText());
        assertTrue(JSON.readTree(get("/api/fs/exists?path=" + q(jsp)).body()).get("exists").asBoolean());
        JsonNode d = JSON.readTree(get("/api/fs/defaults").body());
        assertEquals(root.toString(), d.get("projectRoot").asText(), "프로필 프로젝트 루트(5-5)");
        assertEquals(root.toString(), d.get("recent").get(0).asText());
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

    /** 1-58f — 파일 하나 정리 결과: out/<프로필>/<stamp>/정리_<이름>(UTF-8), 같은 stamp 면 한 폴더, 경로·「..」 이름 거절 */
    @Test
    void outWritesCleanedFileUnderProfileOut() throws Exception {
        HttpResponse<String> r = post("/api/fs/out", "{\"name\":\"a.jsp\",\"text\":\"<div>가</div>\"}");
        assertEquals(200, r.statusCode(), r.body());
        JsonNode j = JSON.readTree(r.body());
        Path file = Path.of(j.get("path").asText());
        assertTrue(file.isAbsolute() && file.startsWith(tmp.resolve("out").resolve("t").toAbsolutePath()), file.toString());
        assertEquals("정리_a.jsp", file.getFileName().toString());
        assertEquals("<div>가</div>", Files.readString(file, StandardCharsets.UTF_8));
        String stamp = j.get("stamp").asText();
        HttpResponse<String> r2 = post("/api/fs/out", "{\"name\":\"b.jsp\",\"text\":\"x\",\"stamp\":\"" + stamp + "\"}");
        assertEquals(file.getParent(), Path.of(JSON.readTree(r2.body()).get("path").asText()).getParent(), "같은 stamp 는 한 폴더");
        for (String bad : new String[] {"../x.jsp", "a/b.jsp", "a\\\\b.jsp", "C:x.jsp", "", ".."}) {
            HttpResponse<String> no = post("/api/fs/out", "{\"name\":\"" + bad + "\",\"text\":\"x\"}");
            assertEquals(400, no.statusCode(), bad + " " + no.body());
        }
    }
}
