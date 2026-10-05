package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 모드 배지 살아 있음 — `GET /api/alive`(SSE). 붙자마자, 그 뒤 {@link #PERIOD_SEC}초마다 `alive` 이벤트(데이터 = 활성 프로필 이름).
 * 서버가 죽으면 브라우저 EventSource 가 바로 error 를 내 배지가 빨개지고, 서버가 다시 뜨면 EventSource 가 스스로 붙어 돌아온다 —
 * 화면이 ping 을 되풀이하지 않는다. 주기 전송은 다른 탭에서 바꾼 프로필을 알리고, Jetty 유휴 제한에 연결이 끊기지 않게 하는 몫도 한다.
 */
final class AliveRoutes {

    static final int PERIOD_SEC = 10;

    private AliveRoutes() {
    }

    /** 주기 전송 스레드(데몬). 서버가 멈출 때 부른 쪽이 {@code shutdownNow} */
    static ScheduledExecutorService executor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "alive-sse");
            t.setDaemon(true);
            return t;
        });
    }

    static void register(Javalin app, ScheduledExecutorService beat, Supplier<String> activeName) {
        Set<SseClient> clients = ConcurrentHashMap.newKeySet();
        beat.scheduleAtFixedRate(() -> clients.forEach(c -> send(c, activeName, clients)), PERIOD_SEC, PERIOD_SEC, TimeUnit.SECONDS);
        app.sse("/api/alive", client -> {
            client.keepAlive();
            clients.add(client);
            client.onClose(() -> clients.remove(client));
            send(client, activeName, clients);
        });
    }

    /** 끊긴 클라이언트는 빼고 넘어간다 — 예외가 새면 주기 작업이 멈춘다 */
    private static void send(SseClient c, Supplier<String> activeName, Set<SseClient> clients) {
        if (c.terminated()) {
            clients.remove(c);
            return;
        }
        try {
            String name = activeName.get();
            c.sendEvent("alive", name == null ? "" : name);
        } catch (RuntimeException e) {
            clients.remove(c);
        }
    }
}
