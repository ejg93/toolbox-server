package kr.ejg.toolbox.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kr.ejg.toolbox.core.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class MainTest {

    @TempDir
    Path tmp;

    @Test
    void versionPrintsVersion() {
        CommandLine cmd = Main.commandLine();
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        int code = cmd.execute("version");
        assertEquals(0, code);
        assertEquals("toolbox-server " + Version.get(), out.toString().trim());
    }

    @Test
    void serveStartsAndStops() throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("site.yaml"), "name: site\n", StandardCharsets.UTF_8);
        Path data = tmp.resolve("data");

        Serve serve = new Serve();
        CommandLine cmd = new CommandLine(serve);
        CompletableFuture<Integer> run = CompletableFuture.supplyAsync(() -> cmd.execute(
                "--no-browser", "--port", "0",
                "--profile", "site",
                "--data-dir", data.toString(),
                "--profiles-dir", profiles.toString()));

        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (serve.app() == null) {
            assertTrue(System.nanoTime() < end, "10초 안에 떠야 한다");
            Thread.sleep(20);
        }
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + serve.app().port() + "/api/ping")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"profile\":\"site\""), res.body());
        assertEquals("site", Files.readString(data.resolve("active-profile")).trim());

        serve.stop();
        assertEquals(0, run.get(10, TimeUnit.SECONDS));
    }

    /** 0-27 — run.bat 을 거치지 않고 serve 를 불러도 쓰기 검사가 돈다. Windows 에선 읽기 전용 폴더 대신 data 를 파일로 만들어 재현(0-5) */
    @Test
    void serveRefusesUnwritableDataDir() throws Exception {
        Path notDir = tmp.resolve("data-is-a-file");
        Files.writeString(notDir, "x", StandardCharsets.UTF_8);
        java.io.PrintStream err = System.err;
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int code;
        try {
            System.setErr(new java.io.PrintStream(buf, true, StandardCharsets.UTF_8));
            code = new CommandLine(new Serve()).execute("--no-browser", "--port", "0", "--data-dir", notDir.toString(),
                    "--profiles-dir", tmp.resolve("profiles").toString());
        } finally {
            System.setErr(err);
        }
        assertEquals(1, code);
        String text = buf.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("쓸 수 없다") && text.contains("사용자 폴더"), text);
        assertEquals(null, Serve.unwritable(tmp.resolve("ok-dir")), "만들 수 있는 폴더는 통과");
    }

    @Test
    void subcommandsAreRegistered() {
        CommandLine cmd = Main.commandLine();
        assertNotNull(cmd.getSubcommands().get("serve"));
        assertNotNull(cmd.getSubcommands().get("version"));
    }

    private static String get(int port, String path, int status) throws Exception {
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, res.statusCode(), path + " " + res.body());
        return res.body();
    }

    /** serve 를 띄우고 뜰 때까지 기다린다 */
    private static CompletableFuture<Integer> start(Serve serve, String... args) throws Exception {
        CommandLine cmd = new CommandLine(serve);
        CompletableFuture<Integer> run = CompletableFuture.supplyAsync(() -> cmd.execute(args));
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (serve.app() == null) {
            assertTrue(!run.isDone(), "기동 전에 끝났다: " + (run.isDone() ? run.join() : null));
            assertTrue(System.nanoTime() < end, "10초 안에 떠야 한다");
            Thread.sleep(20);
        }
        return run;
    }

    /** 1-15 — 없는 프로필 이름이면 띄우지 않고 2 + 사유. active-profile 에 안 적는다 */
    @Test
    void serveRefusesMissingProfile() throws Exception {
        Path profiles = Files.createDirectories(tmp.resolve("profiles"));
        Path data = tmp.resolve("data");
        java.io.PrintStream err = System.err;
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int code;
        try {
            System.setErr(new java.io.PrintStream(buf, true, StandardCharsets.UTF_8));
            code = new CommandLine(new Serve()).execute("--no-browser", "--port", "0", "--profile", "nosuch",
                    "--data-dir", data.toString(), "--profiles-dir", profiles.toString());
        } finally {
            System.setErr(err);
        }
        assertEquals(2, code);
        String text = buf.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("프로필 파일이 없다") && text.contains("nosuch.yaml") && text.contains("example.yaml"), text);
        assertTrue(!Files.exists(data.resolve("active-profile")), "기동 거절 전에 active-profile 을 안 쓴다");
    }

    /** 1-15 — active-profile 이 없는 파일을 가리키면 활성 없음으로 뜨고 접속 목록은 200 [] */
    @Test
    void serveStartsWithoutActiveWhenSavedProfileIsGone() throws Exception {
        Path profiles = Files.createDirectories(tmp.resolve("profiles"));
        Path data = Files.createDirectories(tmp.resolve("data"));
        Files.writeString(data.resolve("active-profile"), "gone\n", StandardCharsets.UTF_8);

        Serve serve = new Serve();
        CompletableFuture<Integer> run = start(serve, "--no-browser", "--port", "0",
                "--data-dir", data.toString(), "--profiles-dir", profiles.toString());
        try {
            int port = serve.app().port();
            assertEquals("[]", get(port, "/api/conn", 200));
            assertTrue(get(port, "/api/ping", 200).contains("\"profile\":null"));
        } finally {
            serve.stop();
        }
        assertEquals(0, run.get(10, TimeUnit.SECONDS));
    }

    /** 1-15 — 켠 뒤 프로필 파일이 사라지면 접속 목록은 500 이 아니라 400 + 사유 */
    @Test
    void deletedProfileFileGives400() throws Exception {
        Path profiles = Files.createDirectories(tmp.resolve("profiles"));
        Files.writeString(profiles.resolve("site.yaml"), "name: site\n", StandardCharsets.UTF_8);
        Path data = tmp.resolve("data");

        Serve serve = new Serve();
        CompletableFuture<Integer> run = start(serve, "--no-browser", "--port", "0", "--profile", "site",
                "--data-dir", data.toString(), "--profiles-dir", profiles.toString());
        try {
            int port = serve.app().port();
            Files.delete(profiles.resolve("site.yaml"));
            String body = get(port, "/api/conn", 400);
            assertTrue(body.contains("프로필을 못 읽었다: site"), body);
        } finally {
            serve.stop();
        }
        assertEquals(0, run.get(10, TimeUnit.SECONDS));
    }
}
