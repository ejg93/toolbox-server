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
}
