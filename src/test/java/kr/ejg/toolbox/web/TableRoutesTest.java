package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** 4-6 — `/api/table/export` 경로·거절 */
class TableRoutesTest {

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

    static HttpResponse<String> post(String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/table/export"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void writesUnderProfileOut() throws Exception {
        String model = "{\"rows\":1,\"cols\":2,\"theadRows\":0,\"caption\":\"\",\"colWidths\":[],"
                + "\"grid\":[[{\"t\":\"td\",\"cs\":1,\"rs\":1,\"text\":\"가\"},{\"t\":\"td\",\"cs\":1,\"rs\":1,\"text\":\"나\"}]]}";
        HttpResponse<String> r = post("{\"model\":" + model + ",\"format\":\"xlsx\"}");
        assertEquals(200, r.statusCode(), r.body());
        Path file = Path.of(JSON.readTree(r.body()).get("path").asText());
        assertTrue(file.startsWith(tmp.resolve("out/t")) && file.endsWith("table.xlsx"), file.toString());
        assertTrue(Files.size(file) > 0);

        assertEquals(400, post("{\"model\":" + model + ",\"format\":\"hwp\"}").statusCode());
        assertEquals(400, post("{\"format\":\"xlsx\"}").statusCode());
        String overlap = "{\"rows\":1,\"cols\":2,\"theadRows\":0,\"grid\":[[{\"t\":\"td\",\"cs\":2,\"rs\":1,\"text\":\"a\"},"
                + "{\"t\":\"td\",\"cs\":1,\"rs\":1,\"text\":\"b\"}]]}";
        assertEquals(200, post("{\"model\":" + overlap + "}").statusCode(), "덮인 칸에 글이 있어도 병합이 먼저 — 겹침은 아니다");
        String tooFew = "{\"rows\":3,\"cols\":2,\"theadRows\":0,\"grid\":[]}";
        assertEquals(400, post("{\"model\":" + tooFew + "}").statusCode());
    }
}
