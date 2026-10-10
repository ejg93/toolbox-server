package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.conn.ConnectionRegistry;
import kr.ejg.toolbox.core.profile.Profile;
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

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ProfileRoutes() {
    }

    static void register(Javalin app, ProfileStore store, AtomicReference<String> active, ConnectionRegistry conns,
            Supplier<Optional<Profile>> activeProfile) {
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
            try {
                ctx.json(store.load(name).withoutPasswords()); // 1-42 — 접속 비밀번호는 응답에 안 싣는다
            } catch (java.io.UncheckedIOException e) {
                ctx.status(400).json(Map.of("message", e.getMessage())); // 1-41 — 원문 없는 글
            }
        });

        // 5-5 코드 검사 체크 상태 — 프로필이 원본. codecheck 의 두 키만 갈아 끼운다(YAML 주석 유지).
        // 1-58g — 쓰기 전에 앞 판을 out/<활성 프로필>/<시각>/backup/profiles/<이름>.yaml 로(R11), 응답 backup
        app.put("/api/profiles/{name}/codecheck", ctx -> {
            String name = ctx.pathParam("name");
            if (!store.list().contains(name)) {
                ctx.status(404).json(Map.of("message", "프로필이 없다: " + name));
                return;
            }
            CodeCheckRequest req = ctx.bodyAsClass(CodeCheckRequest.class);
            try {
                Path backup = FsRoutes.backupRoot(activeProfile, LocalDateTime.now().format(STAMP))
                        .resolve("profiles").resolve(name + ".yaml");
                Files.createDirectories(backup.getParent());
                Files.copy(store.file(name), backup, StandardCopyOption.REPLACE_EXISTING);
                Map<String, Object> res = new java.util.LinkedHashMap<>();
                res.put("codecheck", store.saveCodeCheck(name, req.groups(), req.rules()).codecheck());
                res.put("path", store.file(name).toAbsolutePath().toString());
                res.put("backup", backup.toString());
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
