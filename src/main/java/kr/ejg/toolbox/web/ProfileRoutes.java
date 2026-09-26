package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import kr.ejg.toolbox.core.conn.ConnectionRegistry;
import kr.ejg.toolbox.core.profile.ProfileStore;

/**
 * 프로필 — `GET /api/profiles`({names, active}) · `GET /api/profiles/{name}` · `POST /api/profiles/active {name}`.
 * 활성을 바꾸면 메모리 비밀번호를 전부 지운다 — 두 프로필에 같은 접속 id 가 있으면 앞 프로필 비밀번호가 새 DB 로 간다(1-8).
 */
final class ProfileRoutes {

    record ActiveRequest(String name) {
    }

    private ProfileRoutes() {
    }

    static void register(Javalin app, ProfileStore store, AtomicReference<String> active, ConnectionRegistry conns) {
        app.get("/api/profiles", ctx -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("names", store.list());
            m.put("active", active.get());
            ctx.json(m);
        });

        app.get("/api/profiles/{name}", ctx -> {
            String name = ctx.pathParam("name");
            if (!store.list().contains(name)) {
                ctx.status(404).json(Map.of("message", "프로필이 없다: " + name));
                return;
            }
            ctx.json(store.load(name));
        });

        app.post("/api/profiles/active", ctx -> {
            ActiveRequest req = ctx.bodyAsClass(ActiveRequest.class);
            if (req.name() == null || !store.list().contains(req.name())) {
                ctx.status(404).json(Map.of("message", "프로필이 없다: " + req.name()));
                return;
            }
            store.setActive(req.name());
            if (!req.name().equals(active.getAndSet(req.name()))) {
                conns.clearAll();
            }
            ctx.json(Map.of("active", req.name()));
        });
    }
}
