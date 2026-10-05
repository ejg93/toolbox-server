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

    /** 모드 배지 실시간 — /api/alive(SSE)가 붙자마자 alive 이벤트에 활성 프로필 이름을 싣는다 */
    @Test
    void aliveStreamsProfileAtOnce() throws Exception {
        Javalin app = App.start(config(0));
        try {
            // EventSource 처럼 — Javalin 은 이 머리가 없으면 SSE 로 안 답한다
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/alive"))
                    .header("Accept", "text/event-stream").GET().build();
            HttpResponse<java.util.stream.Stream<String>> res = HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofLines())
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(200, res.statusCode());
            assertTrue(res.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"), res.headers().map().toString());
            java.util.List<String> first = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),
                    () -> res.body().filter(l -> !l.isBlank()).limit(2).toList());
            assertEquals(java.util.List.of("event: alive", "data: example"), first);
            res.body().close();
        } finally {
            app.stop();
        }
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
