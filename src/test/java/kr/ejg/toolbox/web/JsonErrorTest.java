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

    /** 0-83e — 모르는 예외는 500 + 고정 글. 예외 글(SQLException 의 값·SQL 조각)은 응답에도 로그에도 없다(규칙 3) */
    @Test
    void unexpectedExceptionLeavesNoMessage() throws Exception {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs = new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        root.addAppender(logs);
        Javalin own = Javalin.create();
        App.jsonErrors(own);
        own.get("/boom", ctx -> {
            throw new IllegalStateException("ORA-00001 rrn='800101-1234567'");
        });
        own.get("/bad", ctx -> {
            throw new io.javalin.http.BadRequestResponse("고친 사유");
        });
        own.start(java.net.InetAddress.getLoopbackAddress().getHostAddress(), 0);
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + own.port() + "/boom")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(500, r.statusCode());
            assertEquals("서버 오류", JSON.readTree(r.body()).get("message").asText());
            for (ch.qos.logback.classic.spi.ILoggingEvent e : logs.list) {
                // PR #125 claude[bot] ④ — 글만이 아니라 스택·원인 사슬 전체
                String all = e.getFormattedMessage()
                        + (e.getThrowableProxy() == null ? "" : ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(e.getThrowableProxy()));
                assertFalse(all.contains("1234567"), "예외 글이 로그에 실렸다: " + all);
            }
            HttpResponse<String> bad = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + own.port() + "/bad")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, bad.statusCode(), "Javalin 응답 예외는 제 상태 그대로");
        } finally {
            own.stop();
            root.detachAppender(logs);
        }
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
