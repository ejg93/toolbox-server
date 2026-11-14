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
    /** password 키와 콜론 — 따옴표 키(0-83d) 포함. 값은 {@link #valueEnd} 가 문맥으로 끝을 찾는다(0-89) */
    private static final java.util.regex.Pattern PASSWORD_KEY =
            java.util.regex.Pattern.compile("((?:\"password\"|'password'|\\bpassword)[ \\t]*:)[ \\t]*");

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
     * 0-89 — 원본과 지운 글을 문서 전부 읽어, password 값 노드를 "" 로 맞춘 트리가 다르거나 지운 글에 비지 않은 password 값이 남았으면 참.
     * 못 읽으면 참. 꼬리(`FAKE#tail` 의 `#tail`)가 남거나 다른 키로 옮겨가도 잡는다. 같은 글자가 다른 자리에 있어도(`user: tibero`) 막지 않는다
     */
    static boolean leaked(String original, String scrubbed) {
        if (PASSWORD_ALIAS.matcher(original).find()) {
            return true; // Jackson YAML 은 별칭을 안 풀고 이름을 준다 — 값이 어디 있는지 모른다
        }
        try {
            java.util.List<com.fasterxml.jackson.databind.JsonNode> after = docs(scrubbed);
            boolean kept = after.stream().anyMatch(ProfileRoutes::hasPassword);
            after.forEach(ProfileRoutes::blank);
            java.util.List<com.fasterxml.jackson.databind.JsonNode> before = docs(original);
            before.forEach(ProfileRoutes::blank);
            return kept || !before.equals(after) || strayTail(scrubbed);
        } catch (java.io.IOException | RuntimeException e) {
            return true; // 못 읽으면 남았다고 본다
        }
    }

    /** 지운 글의 password 키 뒤가 `''` 와 끝맺음(줄 끝·공백·흐름이면 `,}]`)이 아니면 참 — Jackson 은 `''#tail` 의 꼬리를 주석으로 읽어 트리로는 못 본다 */
    private static boolean strayTail(String scrubbed) {
        java.util.regex.Matcher m = PASSWORD_KEY.matcher(scrubbed);
        while (m.find()) {
            int v = m.end();
            if (!scrubbed.startsWith("''", v)) {
                return true;
            }
            int e = v + 2;
            if (e < scrubbed.length() && " \t\r\n".indexOf(scrubbed.charAt(e)) < 0
                    && !(flow(scrubbed, m.start()) && ",}]".indexOf(scrubbed.charAt(e)) >= 0)) {
                return true;
            }
        }
        return false;
    }

    private static java.util.List<com.fasterxml.jackson.databind.JsonNode> docs(String text) throws java.io.IOException {
        try (com.fasterxml.jackson.databind.MappingIterator<com.fasterxml.jackson.databind.JsonNode> it =
                YAML.readerFor(com.fasterxml.jackson.databind.JsonNode.class).readValues(text)) {
            return it.readAll();
        }
    }

    /** password 값 노드(값·null)를 "" 로 — 값이 컨테이너면 안쪽으로 */
    private static void blank(com.fasterxml.jackson.databind.JsonNode n) {
        if (n instanceof com.fasterxml.jackson.databind.node.ObjectNode o) {
            for (Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> e : o.properties()) {
                if ("password".equals(e.getKey()) && !e.getValue().isContainerNode()) {
                    e.setValue(com.fasterxml.jackson.databind.node.TextNode.valueOf(""));
                } else {
                    blank(e.getValue());
                }
            }
        } else if (n != null && n.isArray()) {
            n.forEach(ProfileRoutes::blank);
        }
    }

    /** 비지 않은 password 값이 있나 */
    private static boolean hasPassword(com.fasterxml.jackson.databind.JsonNode n) {
        if (n == null) {
            return false;
        }
        if (n.isObject()) {
            com.fasterxml.jackson.databind.JsonNode v = n.get("password");
            if (v != null && v.isValueNode() && !v.isNull() && !v.asText().isEmpty()) {
                return true;
            }
        }
        for (com.fasterxml.jackson.databind.JsonNode c : n) {
            if (hasPassword(c)) {
                return true;
            }
        }
        return false;
    }

    /** 1-58g 리뷰 — 백업은 out/ 아래라 접속 비밀번호를 빼고 쓴다(절대 규칙 2 — 비밀번호는 프로필 YAML 에만). 값만 '' 로. 값 끝은 문맥으로(0-89) */
    static String withoutPasswords(String yaml) {
        String s = withoutBlockPasswords(yaml);
        StringBuilder out = new StringBuilder(s.length());
        java.util.regex.Matcher m = PASSWORD_KEY.matcher(s);
        int at = 0;
        while (at <= s.length() && m.find(at)) {
            out.append(s, at, m.start()).append(m.group(1)).append(" ''");
            at = valueEnd(s, m.end(), flow(s, m.start()));
        }
        return out.append(s, Math.min(at, s.length()), s.length()).toString();
    }

    /** 키 앞이 들여쓰기·「- 」 뿐이면 블록 문맥(거짓), 아니면 흐름 문맥 */
    static boolean flow(String s, int keyStart) {
        int line = s.lastIndexOf('\n', keyStart - 1) + 1;
        return !s.substring(line, keyStart).replace("-", "").isBlank();
    }

    /** 값 끝 — 따옴표 값은 닫는 따옴표 다음, 평문은 문맥으로 */
    static int valueEnd(String s, int v, boolean flow) {
        if (v < s.length() && s.charAt(v) == '"') {
            return quotedEnd(s, v, '"');
        }
        if (v < s.length() && s.charAt(v) == '\'') {
            return quotedEnd(s, v, '\'');
        }
        return plainEnd(s, v, flow);
    }

    /** 큰따옴표는 `\x` 두 글자, 작은따옴표는 `''` 두 글자를 건너뛴다. 안 닫히면 글 끝 */
    private static int quotedEnd(String s, int v, char q) {
        int i = v + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (q == '"' && c == '\\') {
                i += 2;
            } else if (c == q && q == '\'' && i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                i += 2;
            } else if (c == q) {
                return i + 1;
            } else {
                i++;
            }
        }
        return s.length();
    }

    /** 줄 끝 · `공백+#` · 흐름이면 `,}]` 에서 멈추고, 멈춘 자리 앞 공백·탭을 되물린다 */
    private static int plainEnd(String s, int v, boolean flow) {
        int i = v;
        while (i < s.length()) {
            char c = s.charAt(i);
            boolean comment = c == '#' && i > v && (s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t');
            if (c == '\r' || c == '\n' || comment || flow && (c == ',' || c == '}' || c == ']')) {
                break;
            }
            i++;
        }
        while (i > v && (s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t')) {
            i--;
        }
        return i;
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
