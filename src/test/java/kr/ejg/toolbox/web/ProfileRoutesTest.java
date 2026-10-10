package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
