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
    /** 접속 password 값 — 블록 꼴 `password: x`·흐름 꼴 `{…, password: x}`, 따옴표 값 · 따옴표 키(0-83d) 포함 */
    private static final java.util.regex.Pattern PASSWORD = java.util.regex.Pattern.compile(
            "(?m)((?:\"password\"|'password'|\\bpassword)[ \\t]*:)[ \\t]*(\"(?:[^\"\\\\]|\\\\.)*+\"|'(?:[^']|'')*+'|[^,}\\r\\n#]*?(?=[ \\t]*(?:[#,}\\r\\n]|$)))");

    /** password 값이 별칭(`*이름`) — 값은 다른 자리(앵커)에 있다 */
    private static final java.util.regex.Pattern PASSWORD_ALIAS = java.util.regex.Pattern.compile("(?m)\\bpassword[\"']?[ \\t]*:[ \\t]*\\*");
    private static final com.fasterxml.jackson.databind.ObjectMapper YAML =
            new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

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
            // 백업은 접속 비밀번호를 비운 판(절대 규칙 2) — 되돌릴 때 password 줄은 손으로(PR #57 리뷰). 거절이면 폴더도 안 만든다
            String text = backupText(Files.readString(store.file(name), StandardCharsets.UTF_8));
            Path dir = FsRoutes.backupRoot(activeProfile, LocalDateTime.now().format(STAMP)).resolve("profiles");
            Files.createDirectories(dir);
            Path backup = dir.resolve(name + ".yaml");
            for (int n = 2; Files.exists(backup); n++) {
                backup = dir.resolve(name + "-" + n + ".yaml"); // 같은 초에 두 번 — 앞 백업(원본 판)을 안 덮는다
            }
            Files.writeString(backup, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
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

    /** 0-83d — 지운 판에 원본 password 값이 남았으면(별칭 등 정규식이 못 보는 꼴) 백업을 안 쓰고 저장을 거절한다 */
    static String backupText(String original) {
        String out = withoutPasswords(original);
        if (leaked(original, out)) {
            throw new io.javalin.http.BadRequestResponse("백업에서 접속 비밀번호를 못 지웠다 — 별칭(*이름)이나 여러 줄 값이 아닌 한 줄 `password: 값` 꼴로 적는다");
        }
        return out;
    }

    /**
     * 원본을 읽어 비지 않은 password 값마다, 지운 글에서 그 값이 password 자리 수만큼 줄지 않았으면 참(별칭은 안 준다). 못 읽으면 참.
     * 있나가 아니라 줄었나로 잰다 — `user: tibero`·`password: tibero` 처럼 같은 글자가 다른 자리에 있어도 저장을 막지 않는다(마무리 리뷰)
     */
    static boolean leaked(String original, String scrubbed) {
        if (PASSWORD_ALIAS.matcher(original).find()) {
            return true; // Jackson YAML 은 별칭을 안 풀고 이름을 준다 — 값이 어디 있는지 모른다
        }
        com.fasterxml.jackson.databind.JsonNode root;
        try {
            root = YAML.readTree(original);
        } catch (java.io.IOException e) {
            return true;
        }
        java.util.List<String> secrets = new java.util.ArrayList<>();
        collectPasswords(root, secrets);
        Map<String, Long> need = secrets.stream().collect(java.util.stream.Collectors.groupingBy(v -> v, java.util.stream.Collectors.counting()));
        return need.entrySet().stream().anyMatch(e -> count(original, e.getKey()) - count(scrubbed, e.getKey()) < e.getValue());
    }

    private static long count(String text, String v) {
        long n = 0;
        for (int i = text.indexOf(v); i >= 0; i = text.indexOf(v, i + v.length())) {
            n++;
        }
        return n;
    }

    private static void collectPasswords(com.fasterxml.jackson.databind.JsonNode n, java.util.List<String> out) {
        if (n == null) {
            return;
        }
        if (n.isObject()) {
            for (Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> e : n.properties()) {
                com.fasterxml.jackson.databind.JsonNode v = e.getValue();
                if ("password".equals(e.getKey()) && v.isValueNode() && !v.isNull() && !v.asText().isEmpty()) {
                    out.add(v.asText());
                }
            }
        }
        for (com.fasterxml.jackson.databind.JsonNode c : n) {
            collectPasswords(c, out);
        }
    }

    /** 1-58g 리뷰 — 백업은 out/ 아래라 접속 비밀번호를 빼고 쓴다(절대 규칙 2 — 비밀번호는 프로필 YAML 에만). 값만 '' 로 */
    static String withoutPasswords(String yaml) {
        return PASSWORD.matcher(withoutBlockPasswords(yaml)).replaceAll("$1 ''");
    }

    /** 블록 꼴 `password: |`·`>-`·빈 값 뒤 다음 줄 값(0-83d) — 키 줄을 `password: ''` 로, 그 아래 키보다 깊이 들여쓴 줄(값)과 빈 줄을 뺀다(PR #57 리뷰) */
    static String withoutBlockPasswords(String yaml) {
        java.util.regex.Pattern block = java.util.regex.Pattern.compile("^(\\s*(?:-\\s+)?)((?:\"password\"|'password'|password)[ \\t]*:)[ \\t]*[|>][-+0-9]*[ \\t]*(#.*)?$");
        // 빈 값(다음 줄에 값) — 블록 꼴과 한 식으로 묶으면 빈 선택 묶음 양옆 공백이 겹쳐 SpotBugs REDOS
        java.util.regex.Pattern empty = java.util.regex.Pattern.compile("^(\\s*(?:-\\s+)?)((?:\"password\"|'password'|password)[ \\t]*:)[ \\t]*(#.*)?$");
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
            if (!m.matches()) {
                m = empty.matcher(bare);
            }
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
