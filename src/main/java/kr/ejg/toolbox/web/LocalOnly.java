package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.HttpResponseException;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 루프백 바인드만으로는 못 막는 두 경로를 막는다(0-24, PLAN 5-12 보정).
 * <ol>
 *   <li><b>DNS rebinding</b> — 악성 사이트가 자기 도메인을 127.0.0.1 로 다시 풀면 브라우저는 같은 출처로 보고 요청을 보낸다.
 *       {@code Host} 가 {@code 127.0.0.1:<포트>}·{@code localhost:<포트>} 가 아니면 403.</li>
 *   <li><b>교차 출처 쓰기</b> — 다른 사이트가 {@code text/plain} POST 를 preflight 없이 보낼 수 있다.
 *       GET·HEAD·OPTIONS 밖의 메서드는 {@code Origin} 이 우리 주소일 때만, 없으면 {@code Sec-Fetch-Site} 가
 *       same-origin·none 이거나 없을 때만 통과. 본문이 있으면 JSON·multipart 만(415).</li>
 * </ol>
 * 거절 로그엔 메서드·경로·사유만(절대 규칙 3 — 본문·쿼리를 안 남긴다).
 */
final class LocalOnly {

    private static final Logger LOG = LoggerFactory.getLogger(LocalOnly.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private LocalOnly() {
    }

    static void register(Javalin app) {
        app.before(LocalOnly::check);
    }

    static void check(Context ctx) {
        // ctx.port() 는 Host 헤더에서 읽는다(서블릿 getServerPort) — 실제로 받은 소켓 포트로 잰다
        int port = ctx.req().getLocalPort();
        String host = lower(ctx.host());
        // 포트 0 = Jetty LocalConnector(CLI, 8-1). 진짜 소켓의 로컬 포트는 0 이 될 수 없다.
        // Jetty 는 Host 의 「:0」 을 400(Bad HostPort)으로 먼저 거절해서 이 길만 포트 없는 Host 로 온다
        boolean local = port == 0 && (host.equals("127.0.0.1") || host.equals("localhost"));
        if (!local && !host.equals("127.0.0.1:" + port) && !host.equals("localhost:" + port)) {
            reject(ctx, new ForbiddenResponse("허용되지 않은 Host"), "host");
        }
        String method = ctx.method().name();
        if (SAFE_METHODS.contains(method)) {
            return;
        }
        String origin = ctx.header("Origin");
        if (origin != null) {
            String o = lower(origin);
            if (!o.equals("http://127.0.0.1:" + port) && !o.equals("http://localhost:" + port)) {
                reject(ctx, new ForbiddenResponse("다른 출처의 쓰기 요청"), "origin");
            }
        } else {
            String site = ctx.header("Sec-Fetch-Site");
            if (site != null && !site.equals("same-origin") && !site.equals("none")) {
                reject(ctx, new ForbiddenResponse("다른 출처의 쓰기 요청"), "sec-fetch-site");
            }
        }
        if (ctx.contentLength() > 0) {
            String type = lower(ctx.contentType());
            if (!type.startsWith("application/json") && !type.startsWith("multipart/form-data")) {
                reject(ctx, new HttpResponseException(HttpStatus.UNSUPPORTED_MEDIA_TYPE.getCode(),
                        "본문은 application/json 이어야 한다"), "content-type");
            }
        }
    }

    private static void reject(Context ctx, HttpResponseException e, String reason) {
        LOG.warn("거절 {} {} ({})", ctx.method(), ctx.path(), reason);
        throw e;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
