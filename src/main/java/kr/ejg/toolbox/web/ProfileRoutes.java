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

    record CodeCheckRequest(Map<String, Boolean> groups, Map<String, Object> rules) {
    }

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

        // 5-5 코드 검사 체크 상태 — 프로필이 원본. codecheck 의 두 키만 갈아 끼운다(YAML 주석 유지)
        app.put("/api/profiles/{name}/codecheck", ctx -> {
            String name = ctx.pathParam("name");
            if (!store.list().contains(name)) {
                ctx.status(404).json(Map.of("message", "프로필이 없다: " + name));
                return;
            }
            CodeCheckRequest req = ctx.bodyAsClass(CodeCheckRequest.class);
            try {
                Map<String, Object> res = new java.util.LinkedHashMap<>();
                res.put("codecheck", store.saveCodeCheck(name, req.groups(), req.rules()).codecheck());
                res.put("path", store.file(name).toString());
                ctx.json(res);
            } catch (IllegalStateException e) {
                ctx.status(409).json(Map.of("message", e.getMessage()));
            }
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
