package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** 8-1 — 포트 없는 Host 는 LocalConnector(로컬 포트 0)에만 열렸다. 진짜 소켓으로는 그대로 403 */
    @Test
    void portlessHostIsForbiddenOnSocket() throws Exception {
        assertEquals(403, raw("GET", "/api/ping", "127.0.0.1", "", null));
        assertEquals(403, raw("GET", "/api/ping", "localhost", "", null));
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

    /** 0-85a — /api/ 는 GET 도 다른 사이트가 일으키면 거절(git 프로세스·recent-paths 쓰기). 화면 페이지는 그대로 */
    @Test
    void crossSiteGetIsForbidden() throws Exception {
        String ping = "/api/ping";
        assertEquals(403, raw("GET", ping, self(), "Sec-Fetch-Site: cross-site\r\n", null));
        assertEquals(403, raw("GET", ping, self(), "Sec-Fetch-Site: same-site\r\n", null));
        assertEquals(200, raw("GET", ping, self(), "Sec-Fetch-Site: same-origin\r\n", null));
        assertEquals(200, raw("GET", ping, self(), "Sec-Fetch-Site: none\r\n", null));
        assertEquals(200, raw("GET", ping, self(), "", null), "헤더 없음(CLI·옛 브라우저)");
        assertEquals(200, raw("GET", "/tools/common.js", self(), "Sec-Fetch-Site: cross-site\r\n", null), "페이지는 통과");
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

    /** G1-K14·K15 — 길이 없는(chunked) 본문은 형식·상한 판정을 비켜 가니 411 */
    @Test
    void chunkedBodyIsRefused() throws Exception {
        for (String type : new String[] {"text/plain", "application/json"}) {
            assertEquals(411, rawChunked("POST", "/api/fs/out", "Transfer-Encoding: chunked\r\nContent-Type: " + type + "\r\n"), type);
        }
    }

    /** G1-K15 — 길이가 상한(32MB)을 넘으면 본문 형식과 무관하게 413(multipart 는 Javalin 상한을 안 탄다) */
    @Test
    void oversizedLengthIs413() throws Exception {
        assertEquals(413, rawChunked("POST", "/api/dict/org/import", "Content-Type: multipart/form-data; boundary=x\r\nContent-Length: 34603008\r\n"));
    }

    /** 0-124(G-09·G-13) — int 를 넘는 Content-Length 도 413(전 -1 로 읽혀 통과) · 포트 80 이면 포트 없는 Host·Origin 도 자기 */
    @Test
    void hugeLengthAndPort80() throws Exception {
        assertEquals(413, rawChunked("POST", "/api/dict/org/import", "Content-Type: multipart/form-data; boundary=x\r\nContent-Length: 2147483648\r\n"));
        assertTrue(LocalOnly.sameHost("127.0.0.1", 80));
        assertTrue(LocalOnly.sameHost("localhost", 80));
        assertFalse(LocalOnly.sameHost("127.0.0.1", 8080), "지킴이 — 다른 포트는 포트를 적는다");
        assertTrue(LocalOnly.sameHost("127.0.0.1:8080", 8080));
        assertTrue(LocalOnly.sameOrigin("http://localhost", 80));
        assertFalse(LocalOnly.sameOrigin("http://localhost", 8080));
    }

    /** 머리만 보내고(본문 길이는 머리가 말한다) 상태 코드만 — chunked 는 본문 한 덩이를 붙인다 */
    private static int rawChunked(String method, String path, String headers) throws Exception {
        try (Socket s = new Socket("127.0.0.1", app.port())) {
            String body = headers.contains("chunked") ? "7\r\n{\"x\":1}\r\n0\r\n\r\n" : "";
            String req = method + " " + path + " HTTP/1.1\r\nHost: " + self() + "\r\n" + headers + "Connection: close\r\n\r\n" + body;
            OutputStream out = s.getOutputStream();
            out.write(req.getBytes(StandardCharsets.UTF_8));
            out.flush();
            s.shutdownOutput(); // 머리가 말한 본문은 안 보낸다 — 판정은 머리만으로 난다
            s.setSoTimeout(10_000);
            String status = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
            return Integer.parseInt(status.split(" ")[1]);
        }
    }
}
