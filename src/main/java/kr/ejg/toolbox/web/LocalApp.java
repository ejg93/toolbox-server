package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.db.Db;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.LocalConnector;

/**
 * 듣는 포트 없는 서버(8-1) — CLI 가 화면과 같은 라우트를 그대로 탄다. Jetty {@link LocalConnector} 하나만 달고,
 * 요청은 프로세스 안에서 HTTP 글로 주고받는다. 소켓을 안 연다(절대 규칙 1). 가드({@link LocalOnly})는 로컬 포트 0 을 이 길로 안다.
 */
public final class LocalApp implements AutoCloseable {

    /** 응답 — 본문은 바이트 그대로 */
    public record Response(int status, String contentType, byte[] body) {

        public Response {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE");
    /** 요청 줄에 들어갈 수 있는 글자만 — 공백·CR·LF·한글이 끼면 다른 요청이 섞일 수 있다(한글은 부르는 쪽이 퍼센트 인코딩) */
    private static final Pattern PATH = Pattern.compile("^/[\\x21-\\x7E]*$");
    private static final Pattern HEADER_VALUE = Pattern.compile("^[\\x20-\\x7E]*$");
    private static final long WAIT_SECONDS = 600;

    private final Javalin app;
    private final LocalConnector connector;

    private LocalApp(Javalin app, LocalConnector connector) {
        this.app = app;
        this.connector = connector;
    }

    /**
     * H2·드라이버·사전을 열고 LocalConnector 만 단 서버를 띄운다. {@code port}·{@code openBrowser} 는 무시한다.
     * 같은 data 폴더를 다른 프로세스가 쓰면 {@link Db.LockedException}.
     */
    public static LocalApp start(AppConfig config) {
        Db db = App.open(config);
        AtomicBoolean started = new AtomicBoolean();
        Javalin app;
        try {
            app = App.create(config, db, started, true);
            app.start();
        } catch (RuntimeException e) {
            db.close();
            throw e;
        }
        started.set(true); // 이 뒤로는 서버가 멈출 때 H2 를 닫는다
        LocalConnector local = null;
        for (Connector c : app.jettyServer().server().getConnectors()) {
            if (c instanceof LocalConnector l) {
                local = l;
            } else {
                app.stop();
                throw new IllegalStateException("LocalConnector 밖의 커넥터가 붙었다: " + c.getClass().getName() + " — 포트를 열지 않는다");
            }
        }
        if (local == null) {
            app.stop();
            throw new IllegalStateException("LocalConnector 가 없다");
        }
        return new LocalApp(app, local);
    }

    /** @param jsonBody 없으면 null — 있으면 {@code application/json} 으로 보낸다 */
    public Response send(String method, String path, String jsonBody) throws Exception {
        return send(method, path, jsonBody, Map.of());
    }

    /** 시험용 — 머리를 더 싣는다(가드가 사는지 잴 때) */
    Response send(String method, String path, String jsonBody, Map<String, String> headers) throws Exception {
        if (method == null || !METHODS.contains(method)) {
            throw new IllegalArgumentException("method 는 GET·POST·PUT·DELETE: " + method);
        }
        if (path == null || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("path 는 / 로 시작하는 ASCII 인쇄 글자만(공백·줄바꿈·한글은 퍼센트 인코딩): " + path);
        }
        StringBuilder head = new StringBuilder(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        head.append("Host: 127.0.0.1\r\n"); // 포트 없이 — Jetty 는 「:0」 을 400 으로 거절한다
        for (Map.Entry<String, String> h : headers.entrySet()) {
            if (!HEADER_VALUE.matcher(h.getKey()).matches() || !HEADER_VALUE.matcher(h.getValue()).matches() || h.getKey().contains(":")) {
                throw new IllegalArgumentException("머리 글자가 잘못됐다: " + h.getKey());
            }
            head.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        }
        byte[] body = jsonBody == null ? new byte[0] : jsonBody.getBytes(StandardCharsets.UTF_8);
        if (jsonBody != null) {
            head.append("Content-Type: application/json; charset=utf-8\r\n");
            head.append("Content-Length: ").append(body.length).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");
        byte[] h = head.toString().getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer req = ByteBuffer.allocate(h.length + body.length);
        req.put(h).put(body).flip();
        ByteBuffer out = connector.getResponse(req, WAIT_SECONDS, TimeUnit.SECONDS);
        if (out == null) {
            throw new IllegalStateException(WAIT_SECONDS + "초 안에 응답이 없다: " + method + " " + path);
        }
        HttpTester.Response r = HttpTester.parseResponse(out);
        if (r == null) {
            throw new IllegalStateException("응답을 못 읽었다: " + method + " " + path);
        }
        return new Response(r.getStatus(), r.get("Content-Type"), r.getContentBytes());
    }

    /** 서버를 멈춘다 — H2 도 닫힌다 */
    @Override
    public void close() {
        app.stop();
    }
}
