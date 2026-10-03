package kr.ejg.toolbox.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.Console;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.profile.ProfileStore;
import kr.ejg.toolbox.web.AppConfig;
import kr.ejg.toolbox.web.LocalApp;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * 배치 명령 공통(8-3) — 소켓 없는 서버({@link LocalApp})를 열어 화면과 같은 라우트를 탄다.
 * stdout 은 결과만(사람이 읽는 줄 또는 {@code --json} 이면 응답 JSON), 진행·오류는 stderr.
 * 끝 코드 0 성공 · 1 실패 · 2 사용법·프로필·비밀번호 없음 · 3 {@code check --fail-on}.
 * DB 비밀번호는 환경변수(접속별 {@code TOOLBOX_DB_PASSWORD_<ID>} → {@code TOOLBOX_DB_PASSWORD}) 또는 콘솔 프롬프트로만 받는다 —
 * 명령줄 옵션은 셸 이력 파일에 남아 만들지 않는다(절대 규칙 2, 사용자 2026-10-03).
 */
public final class Batch {

    static final ObjectMapper JSON = new ObjectMapper();

    /** 끝 코드로 끝낸다 — 사유는 이미 stderr 에 찍었다 */
    static final class Exit extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int code;

        Exit(int code) {
            super(null, null, false, false);
            this.code = code;
        }
    }

    /** 시험이 갈아 끼우는 자리 — 환경변수는 JVM 안에서 못 바꾼다. null 이면 실제 길(환경변수·콘솔·TOOLBOX_CWD) */
    private static volatile Function<String, char[]> passwordSource;
    private static volatile Supplier<Path> cwdSource;

    /** 시험용 — 둘 다 null 이면 원래대로 */
    static void hooks(Function<String, char[]> password, Supplier<Path> cwd) {
        passwordSource = password;
        cwdSource = cwd;
    }

    /** 시험용 — 비밀번호 공급자만 바꾼다(cwd 는 그대로) */
    static void passwordHook(Function<String, char[]> password) {
        passwordSource = password;
    }

    @Option(names = "--profile", description = "이 실행에 쓸 프로필. 없으면 활성 프로필(data/active-profile). 배치는 활성 프로필 파일을 안 바꾼다")
    String profile;

    @Option(names = "--data-dir", description = "data 폴더. 기본 ${DEFAULT-VALUE}")
    Path dataDir = Path.of("data");

    @Option(names = "--profiles-dir", description = "프로필 YAML 폴더. 기본 ${DEFAULT-VALUE}")
    Path profilesDir = Path.of("profiles");

    @Option(names = "--json", description = "결과를 응답 JSON 그대로")
    boolean json;

    @Spec(Spec.Target.MIXEE)
    CommandSpec spec;

    private LocalApp app;
    private String profileName;

    PrintWriter out() {
        return spec.commandLine().getOut();
    }

    PrintWriter err() {
        return spec.commandLine().getErr();
    }

    String profileName() {
        return profileName;
    }

    boolean json() {
        return json;
    }

    /** 사유를 찍고 끝 코드로 끝낸다 */
    Exit fail(int code, String message) {
        err().println("[오류] " + message);
        err().flush();
        return new Exit(code);
    }

    /** 서버를 연다. needProfile 이면 프로필이 없을 때 끝 코드 2 */
    void open(boolean needProfile) {
        ProfileStore store = new ProfileStore(profilesDir, dataDir);
        String name = profile != null && !profile.isBlank() ? profile.trim() : store.active().orElse(null);
        if (name != null && !store.list().contains(name)) {
            throw fail(2, "프로필이 없다: " + name + " — " + profilesDir.toAbsolutePath().normalize() + " 의 YAML 이름(" + String.join("·", store.list()) + ")");
        }
        if (name == null && needProfile) {
            throw fail(2, "활성 프로필이 없다 — --profile <이름>");
        }
        profileName = name;
        try {
            app = LocalApp.start(new AppConfig(0, name, dataDir, profilesDir, Path.of("drivers"), false));
        } catch (Db.LockedException e) {
            throw fail(1, "서버가 켜져 있다(같은 data 폴더 " + e.file() + ") — 서버를 끄고 다시 돌리거나 --data-dir 을 따로 준다");
        }
    }

    void close() {
        if (app != null) {
            app.close();
            app = null;
        }
    }

    /** 요청 하나. 400 이상이면 사유를 찍고 끝 코드 1. 본문이 JSON 이 아니면 글 노드 */
    JsonNode call(String method, String path, Object body) throws Exception {
        LocalApp.Response r = app.send(method, path, body == null ? null : body instanceof String s ? s : JSON.writeValueAsString(body));
        JsonNode node = parse(r);
        if (r.status() >= 400) {
            String msg = node.isObject() && node.hasNonNull("message") ? node.get("message").asText() : r.text();
            throw fail(1, r.status() + " " + msg);
        }
        return node;
    }

    /** 응답 그대로(상태와 함께) — api 명령 */
    LocalApp.Response raw(String method, String path, String body) throws Exception {
        return app.send(method, path, body);
    }

    static JsonNode parse(LocalApp.Response r) {
        String ct = r.contentType() == null ? "" : r.contentType().toLowerCase(Locale.ROOT);
        if (ct.startsWith("application/json")) {
            try {
                return JSON.readTree(r.body());
            } catch (java.io.IOException e) {
                return TextNode.valueOf(r.text());
            }
        }
        return TextNode.valueOf(r.text());
    }

    /** 202 {jobId} → 끝날 때까지 기다려 result. 실패·취소는 사유를 찍고 끝 코드 1 */
    JsonNode job(JsonNode accepted) throws Exception {
        String id = accepted.path("jobId").asText(null);
        if (id == null) {
            throw fail(1, "작업 ID 가 없다: " + accepted);
        }
        String last = null;
        while (true) {
            JsonNode s = call("GET", "/api/jobs/" + enc(id), null);
            String status = s.path("status").asText();
            String line = s.path("progress").asInt() + "% " + s.path("message").asText("");
            if (!line.equals(last)) {
                err().println("[진행] " + line);
                err().flush();
                last = line;
            }
            switch (status) {
                case "DONE":
                    return s.path("result");
                case "FAILED":
                    throw fail(1, "작업 실패 — " + s.path("error").asText("사유 없음"));
                case "CANCELLED":
                    throw fail(1, "작업이 취소됐다");
                default:
                    Thread.sleep(200);
            }
        }
    }

    /** 접속 비밀번호를 서버 메모리에 넣는다. 값은 어디에도 안 찍고 쓴 뒤 0 으로 지운다 */
    void password(String connId) throws Exception {
        char[] pw = readPassword(connId);
        try {
            call("POST", "/api/conn/" + enc(connId) + "/password", Map.of("password", pw));
        } finally {
            Arrays.fill(pw, '\0');
        }
    }

    private char[] readPassword(String connId) {
        if (passwordSource != null) {
            char[] p = passwordSource.apply(connId);
            if (p != null) {
                return p;
            }
        }
        String own = System.getenv("TOOLBOX_DB_PASSWORD_" + envKey(connId));
        if (own != null) {
            return own.toCharArray();
        }
        String any = System.getenv("TOOLBOX_DB_PASSWORD");
        if (any != null) {
            return any.toCharArray();
        }
        Console c = System.console();
        if (c != null) {
            char[] p = c.readPassword("비밀번호(%s): ", connId);
            if (p != null) {
                return p;
            }
        }
        throw fail(2, "비밀번호를 받을 길이 없다(" + connId + ") — 콘솔에서 돌리거나 환경변수 TOOLBOX_DB_PASSWORD_" + envKey(connId)
                + " 또는 TOOLBOX_DB_PASSWORD");
    }

    /** 접속 id → 환경변수 꼬리: 대문자, 영숫자 밖은 _ */
    static String envKey(String connId) {
        return connId.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }

    /** 사용자가 준 경로 — 상대면 부른 폴더(TOOLBOX_CWD, bat 이 jar 폴더로 cd 하기 전) 기준 */
    Path userPath(String p) {
        Path path = Path.of(p);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path base;
        if (cwdSource != null) {
            base = cwdSource.get();
        } else {
            String env = System.getenv("TOOLBOX_CWD");
            base = env == null || env.isBlank() ? Path.of("").toAbsolutePath() : Path.of(env);
        }
        return base.resolve(path).toAbsolutePath().normalize();
    }

    /** 수 그대로, latest 면 이 실행 프로필의 가장 큰 스냅샷 id */
    long snapshotId(String s) throws Exception {
        if (s == null || s.isBlank()) {
            throw fail(2, "--snapshot <id 또는 latest>");
        }
        if (!s.equalsIgnoreCase("latest")) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                throw fail(2, "스냅샷 id 는 수 또는 latest: " + s);
            }
        }
        long best = -1;
        for (JsonNode n : call("GET", "/api/meta/snapshots", null)) {
            if (Objects.equals(n.path("profile").asText(null), profileName) && n.path("id").asLong() > best) {
                best = n.path("id").asLong();
            }
        }
        if (best < 0) {
            throw fail(1, "이 프로필(" + profileName + ")의 스냅샷이 없다 — snapshot --conn <id> 먼저");
        }
        return best;
    }

    static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** --json 이면 node 그대로, 아니면 사람이 읽는 줄 */
    void print(JsonNode node, String human) {
        out().println(json ? node.toString() : human);
        out().flush();
    }
}
