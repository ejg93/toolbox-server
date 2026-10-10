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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 1-58g — 코드 검사 켬·끔을 프로필 YAML 에 쓰기 전에 앞 판을 백업한다(R11) */
class ProfileRoutesTest {

    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void codecheckSaveBacksUpPreviousYaml(@TempDir Path tmp) throws Exception {
        Path profiles = Files.createDirectories(tmp.resolve("profiles"));
        String before = "name: t\n# 주석은 남는다\noutput:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n"
                + "codecheck:\n  groups:\n    common: true\n";
        Files.writeString(profiles.resolve("t.yaml"), before, StandardCharsets.UTF_8);
        Javalin app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/profiles/t/codecheck"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"groups\":{\"common\":false}}", StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r.statusCode(), r.body());
            JsonNode j = JSON.readTree(r.body());
            Path backup = Path.of(j.get("backup").asText());
            assertTrue(backup.isAbsolute(), backup.toString());
            assertTrue(backup.startsWith(tmp.resolve("out").resolve("t").toAbsolutePath()), backup.toString());
            assertTrue(backup.endsWith(Path.of("backup", "profiles", "t.yaml")), backup.toString());
            assertEquals(before, Files.readString(backup, StandardCharsets.UTF_8), "백업은 앞 판 그대로");
            assertTrue(Path.of(j.get("path").asText()).isAbsolute(), j.get("path").asText());
            String after = Files.readString(profiles.resolve("t.yaml"), StandardCharsets.UTF_8);
            assertTrue(after.contains("\"common\":false") && after.contains("# 주석은 남는다"), after);
        } finally {
            app.stop();
        }
    }
}
