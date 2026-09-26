package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.javalin.Javalin;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 0-24 — Host·Origin 검사. java.net.http 는 Host 를 못 바꿔서(제한 헤더) 소켓으로 날 요청을 보낸다.
 */
class LocalOnlyTest {

    static Javalin app;

    @BeforeAll
    static void up() {
        app = App.start(AppTest.config(0));
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    /** 상태 코드만 돌려준다 */
    static int raw(String method, String path, String host, String extraHeaders, String body) throws Exception {
        try (Socket s = new Socket("127.0.0.1", app.port())) {
            byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            String req = method + " " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + extraHeaders
                    + (b.length > 0 ? "Content-Length: " + b.length + "\r\n" : "")
                    + "Connection: close\r\n\r\n";
            OutputStream out = s.getOutputStream();
            out.write(req.getBytes(StandardCharsets.UTF_8));
            out.write(b);
            out.flush();
            String status = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
            return Integer.parseInt(status.split(" ")[1]);
        }
    }

    private static String self() {
        return "127.0.0.1:" + app.port();
    }

    @Test
    void ownHostPasses() throws Exception {
        assertEquals(200, raw("GET", "/api/ping", self(), "", null));
        assertEquals(200, raw("GET", "/api/ping", "localhost:" + app.port(), "", null));
        assertEquals(200, raw("GET", "/tools/index.html", self(), "", null));
    }

    @Test
    void foreignHostIsForbidden() throws Exception {
        assertEquals(403, raw("GET", "/api/ping", "evil.example:" + app.port(), "", null), "DNS rebinding");
        assertEquals(403, raw("GET", "/tools/index.html", "evil.example:" + app.port(), "", null), "정적 파일도");
        assertEquals(403, raw("GET", "/api/ping", "127.0.0.1:1", "", null), "포트가 다르면");
    }

    @Test
    void foreignOriginWriteIsForbidden() throws Exception {
        assertEquals(403, raw("DELETE", "/api/jobs/nope1234", self(), "Origin: http://evil.example\r\n", null));
        assertEquals(403, raw("POST", "/api/jobs/demo?steps=1&delayMs=0", self(), "Sec-Fetch-Site: cross-site\r\n", null));
    }

    @Test
    void sameOriginWritePasses() throws Exception {
        assertEquals(404, raw("DELETE", "/api/jobs/nope1234", self(), "Origin: http://" + self() + "\r\n", null),
                "검사를 지나 라우트까지 간다(없는 작업 404)");
        assertEquals(202, raw("POST", "/api/jobs/demo?steps=1&delayMs=0", self(),
                "Origin: http://" + self() + "\r\nContent-Type: application/json\r\n", "{}"));
    }

    @Test
    void textPlainBodyIsRejected() throws Exception {
        assertEquals(415, raw("POST", "/api/jobs/demo?steps=1&delayMs=0", self(),
                "Content-Type: text/plain\r\n", "{\"x\":1}"), "단순 POST 모양");
    }
}
