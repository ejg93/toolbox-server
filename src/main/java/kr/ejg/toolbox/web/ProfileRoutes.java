package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    /** 접속 password 값 — 블록 꼴 `password: x`·흐름 꼴 `{…, password: x}`, 따옴표 값 포함 */
    private static final java.util.regex.Pattern PASSWORD = java.util.regex.Pattern.compile(
            "(?m)(\\bpassword[ \\t]*:)[ \\t]*(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^']|'')*'|[^,}\\r\\n#]*?(?=[ \\t]*(?:[#,}\\r\\n]|$)))");

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
            Path dir = FsRoutes.backupRoot(activeProfile, LocalDateTime.now().format(STAMP)).resolve("profiles");
            Files.createDirectories(dir);
            Path backup = dir.resolve(name + ".yaml");
            for (int n = 2; Files.exists(backup); n++) {
                backup = dir.resolve(name + "-" + n + ".yaml"); // 같은 초에 두 번 — 앞 백업(원본 판)을 안 덮는다
            }
            // 백업은 접속 비밀번호를 비운 판(절대 규칙 2) — 되돌릴 때 password 줄은 손으로(PR #57 리뷰)
            Files.writeString(backup, withoutPasswords(Files.readString(store.file(name), StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            try {
                Map<String, Object> res = new java.util.LinkedHashMap<>();
                res.put("codecheck", store.saveCodeCheck(name, req.groups(), req.rules()).codecheck());
                res.put("path", store.file(name).toAbsolutePath().toString());
                res.put("backup", backup.toString());
                ctx.json(res);
            } catch (IllegalStateException e) {
                Files.deleteIfExists(backup); // 안 덮었으니 백업도 없다(R11 — 덮어쓰기 때만, PR #57 리뷰)
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

    /** 1-58g 리뷰 — 백업은 out/ 아래라 접속 비밀번호를 빼고 쓴다(절대 규칙 2 — 비밀번호는 프로필 YAML 에만). 값만 '' 로 */
    static String withoutPasswords(String yaml) {
        return PASSWORD.matcher(withoutBlockPasswords(yaml)).replaceAll("$1 ''");
    }

    /** 블록 꼴 `password: |`·`>-` — 키 줄을 `password: ''` 로, 그 아래 키보다 깊이 들여쓴 줄(값)과 빈 줄을 뺀다(PR #57 리뷰) */
    static String withoutBlockPasswords(String yaml) {
        java.util.regex.Pattern block = java.util.regex.Pattern.compile("^(\\s*(?:-\\s+)?)(password[ \\t]*:)[ \\t]*[|>][-+0-9]*[ \\t]*(#.*)?$");
        String[] lines = yaml.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int skipDeeper = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean cr = line.endsWith("\r");
            String bare = cr ? line.substring(0, line.length() - 1) : line;
            if (skipDeeper >= 0) {
                int indent = bare.length() - bare.stripLeading().length();
                if (bare.isBlank() || indent > skipDeeper) {
                    continue;
                }
                skipDeeper = -1;
            }
            java.util.regex.Matcher m = block.matcher(bare);
            if (m.matches()) {
                skipDeeper = m.group(1).length();
                line = m.group(1) + m.group(2) + " ''" + (cr ? "\r" : "");
            }
            out.append(line);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }
}
