package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 1-58g — 코드 검사 켬·끔을 프로필 YAML 에 쓰기 전에 앞 판을 백업한다(R11) */
class ProfileRoutesTest {

    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    /** 리뷰 — 백업은 out/ 아래라 접속 비밀번호 값을 뺀다(절대 규칙 2). 블록·흐름·따옴표 꼴 */
    @Test
    void backupDropsPasswords() {
        String y = "connections:\n  - id: a\n    password: s3cret # 주석\n  - {id: b, password: 'p,w', user: u}\n"
                + "  - id: c\n    password: \"q\\\"x\"\n";
        String out = ProfileRoutes.withoutPasswords(y);
        for (String secret : new String[] {"s3cret", "p,w", "q\\\"x"}) {
            assertTrue(!out.contains(secret), secret + " 가 남았다: " + out);
        }
        assertTrue(out.contains("password: '' # 주석") && out.contains("user: u}") && out.contains("id: c"), out);
        // PR #57 리뷰 — 블록 꼴은 값 줄까지 뺀다. 그 뒤 같은 깊이 키는 남는다
        String block = ProfileRoutes.withoutPasswords("connections:\n  - id: d\n    password: |-\n      blk\n      sec\n    user: v\n");
        assertTrue(!block.contains("blk") && !block.contains("sec") && block.contains("    password: ''\n    user: v"), block);
        // 0-83d — 따옴표 키 · 다음 줄 값(둘 다 Jackson 이 읽는 꼴)
        String quoted = ProfileRoutes.withoutPasswords("connections:\n  - id: e\n    \"password\": qk1\n  - {id: f, 'password': qk2}\n");
        assertTrue(!quoted.contains("qk1") && !quoted.contains("qk2") && quoted.contains("id: f"), quoted);
        String next = ProfileRoutes.withoutPasswords("connections:\n  - id: g\n    password:\n      nx1\n    user: w\n  - password:\n      nx2\n    id: h\n");
        assertTrue(!next.contains("nx1") && !next.contains("nx2") && next.contains("    user: w") && next.contains("    id: h"), next);
    }

    /** 0-85g — 긴 따옴표 비밀번호가 교대 반복 재귀로 스택을 넘기지 않는다(소유 수량자) */
    @Test
    void longQuotedPasswordDoesNotOverflow() {
        String longValue = "a".repeat(200_000);
        String single = ProfileRoutes.withoutPasswords("connections:\n  - id: a\n    password: '" + longValue + "'\n");
        assertTrue(single.contains("password: ''") && !single.contains(longValue), "작은따옴표");
        String dbl = ProfileRoutes.withoutPasswords("connections:\n  - id: a\n    password: \"" + longValue + "\"\n");
        assertTrue(dbl.contains("password: ''") && !dbl.contains(longValue), "큰따옴표");
    }

    /** 0-85g(⑭a) — 여러 줄 평문 password 는 둘째 줄이 남는 대신 저장을 거절한다(leaked 그물). 코드 불변 — 단언으로 굳힌다 */
    @Test
    void multiLinePlainPasswordIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(io.javalin.http.BadRequestResponse.class,
                () -> ProfileRoutes.backupText("connections:\n  - id: a\n    password: abc\n      def\n"));
    }

    /** 0-83d — 정규식이 못 지우는 꼴(별칭)은 원본 password 값이 백업 글에 남았나로 잡는다 — 백업을 안 쓰고 저장을 거절 */
    @Test
    void aliasPasswordIsCaught() {
        String y = "keep: &pw al1as\nconnections:\n  - id: a\n    password: *pw\n";
        assertTrue(ProfileRoutes.leaked(y, ProfileRoutes.withoutPasswords(y)));
        String plain = "connections:\n  - id: a\n    \"password\": qk1\n  - id: b\n    password:\n      nx1\n  - id: c\n    password: ''\n";
        assertFalse(ProfileRoutes.leaked(plain, ProfileRoutes.withoutPasswords(plain)));
        assertTrue(ProfileRoutes.leaked(plain, plain));
        assertTrue(ProfileRoutes.leaked("connections: [", "x"), "못 읽으면 남았다고 본다");
        // 마무리 리뷰 — 같은 글자가 다른 자리에 있어도(user=password · 짧은 값 · 빈 password 와 null 글자) 저장을 막지 않는다
        String same = "connections:\n  - id: t\n    user: tibero\n    password: tibero\n    dialect: tibero\n  - id: n\n    password:\n    note: null\n";
        assertFalse(ProfileRoutes.leaked(same, ProfileRoutes.withoutPasswords(same)));
    }

    /** 0-89(설계 69 §3-2) — 블록 평문은 `공백+#` 앞·줄 끝까지, 흐름은 `,}]` 앞까지가 값이다. `#`·`,`·`}` 꼬리가 백업에 남지 않는다 */
    @Test
    void backupDropsPasswordTails() {
        for (String v : new String[] {"FAKE#tail", "FAKE,tail", "FAKE}tail"}) {
            String out = ProfileRoutes.withoutPasswords("a:\n  password: " + v + "\n  user: u\n");
            assertEquals("a:\n  password: ''\n  user: u\n", out, v);
        }
        String flow = ProfileRoutes.withoutPasswords("connections:\n  - {id: b, password: FAKE#tail}\n");
        assertEquals("connections:\n  - {id: b, password: ''}\n", flow);
        String flowNext = ProfileRoutes.withoutPasswords("connections:\n  - {id: c, password: FAKE#tail, user: u}\n");
        assertEquals("connections:\n  - {id: c, password: '', user: u}\n", flowNext);
    }

    /** 0-89 — 그물은 「password 값을 맞춘 트리가 같은가」. 꼬리가 남거나 꼬리가 다른 키로 옮겨가도 참 */
    @Test
    void fragmentLeftBehindIsRefused() {
        String original = "a:\n  password: FAKE#tail\n";
        assertTrue(ProfileRoutes.leaked(original, "a:\n  password: ''#tail\n"));
        assertTrue(ProfileRoutes.leaked(original, "a:\n  password: ''\n  x: tail\n"));
        assertFalse(ProfileRoutes.leaked(original, "a:\n  password: ''\n"));
        assertFalse(ProfileRoutes.leaked(original, ProfileRoutes.withoutPasswords(original)));
    }

    /** 0-89(G1-K17) — 값 안 긴 공백에 게으른 수량자 + 전방 탐색이 제곱 시간이던 자리 */
    @Test
    void longSpacesInValueAreLinear() {
        String y = "password: a" + " ".repeat(200_000) + "b\n";
        String out = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                () -> ProfileRoutes.withoutPasswords(y));
        assertEquals("password: ''\n", out);
    }

    /** PR #57 리뷰 — 저장이 409 로 실패하면 백업도 안 남는다(R11 덮어쓰기 때만) */
    @Test
    void conflictLeavesNoBackup(@TempDir Path tmp) throws Exception {
        Path profiles = Files.createDirectories(tmp.resolve("profiles"));
        // codecheck 바로 아래 맨 앞 주석이 있으면 줄 갈아 끼우기가 블록을 비었다고 보고 새 키를 앞에 넣는다 —
        // 옛 groups 가 뒤에 남아 이겨서 「다른 값도 바뀐다」 409
        Files.writeString(profiles.resolve("t.yaml"), "name: t\noutput:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/')
                + "\ncodecheck:\n# 맨 앞 주석\n  groups:\n    common: true\n", StandardCharsets.UTF_8);
        Javalin app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/profiles/t/codecheck"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"groups\":{\"common\":false}}", StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(409, r.statusCode(), "맨 앞 주석 꼴이 409 를 안 낸다 — 시험 전제가 깨졌다: " + r.body()); // PR #58 리뷰 — 건너뛰지 않는다
            try (java.util.stream.Stream<Path> s = Files.exists(tmp.resolve("out")) ? Files.walk(tmp.resolve("out")) : java.util.stream.Stream.empty()) {
                assertEquals(java.util.List.of(), s.filter(Files::isRegularFile).toList(), "409 뒤 백업 파일");
            }
        } finally {
            app.stop();
        }
    }

    @Test
    void codecheckSaveBacksUpPreviousYaml(@TempDir Path tmp) throws Exception {
        Path profiles = Files.createDirectories(tmp.resolve("profiles"));
        String before = "name: t\n# 주석은 남는다\noutput:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n"
                + "codecheck:\n  groups:\n    common: true\n";
        Files.writeString(profiles.resolve("t.yaml"), before, StandardCharsets.UTF_8);
        Javalin app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/profiles/t/codecheck"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"groups\":{\"common\":false}}", StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r.statusCode(), r.body());
            JsonNode j = JSON.readTree(r.body());
            Path backup = Path.of(j.get("backup").asText());
            assertTrue(backup.isAbsolute(), backup.toString());
            assertTrue(backup.startsWith(tmp.resolve("out").resolve("t").toAbsolutePath()), backup.toString());
            assertTrue(backup.endsWith(Path.of("backup", "profiles", "t.yaml")), backup.toString());
            assertEquals(before, Files.readString(backup, StandardCharsets.UTF_8), "백업은 앞 판 그대로");
            // 리뷰 — 같은 초에 다시 저장해도 앞 백업(원본 판)을 안 덮는다
            HttpResponse<String> r2 = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/profiles/t/codecheck"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"groups\":{\"common\":true}}", StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(before, Files.readString(backup, StandardCharsets.UTF_8), "둘째 저장 뒤에도 첫 백업은 원본");
            assertTrue(Files.exists(Path.of(JSON.readTree(r2.body()).get("backup").asText())), r2.body());
            assertTrue(Path.of(j.get("path").asText()).isAbsolute(), j.get("path").asText());
            String after = Files.readString(profiles.resolve("t.yaml"), StandardCharsets.UTF_8);
            assertTrue(after.contains("# 주석은 남는다"), after);
        } finally {
            app.stop();
        }
    }
}
