package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import kr.ejg.toolbox.core.Version;
import org.junit.jupiter.api.Test;

class AppTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    static HttpResponse<String> get(Javalin app, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    static AppConfig config(int port) {
        return new AppConfig(port, "example", Path.of("target/test-data"), Path.of("profiles"), Path.of("target/test-drivers"), false);
    }

    @Test
    void hostIsLoopbackConstant() {
        assertEquals("127.0.0.1", App.HOST);
    }

    @Test
    void pingAnswersBackendMode() throws Exception {
        Javalin app = App.start(config(0));
        try {
            HttpResponse<String> res = get(app, "/api/ping");
            assertEquals(200, res.statusCode());
            JsonNode body = new ObjectMapper().readTree(res.body());
            assertEquals("backend", body.get("mode").asText());
            assertEquals("example", body.get("profile").asText());
            assertEquals(Version.get(), body.get("version").asText());
        } finally {
            app.stop();
        }
    }

    @Test
    void busyPortMovesToNext() throws Exception {
        Javalin first = App.start(config(0));
        try {
            int busy = first.port();
            Javalin second = App.start(config(busy));
            try {
                assertNotEquals(busy, second.port());
                assertTrue(second.port() > busy && second.port() < busy + App.PORT_TRIES,
                        "다음 포트 범위에 뜬다: " + second.port());
                assertEquals(200, get(second, "/api/ping").statusCode());
            } finally {
                second.stop();
            }
        } finally {
            first.stop();
        }
    }
}
