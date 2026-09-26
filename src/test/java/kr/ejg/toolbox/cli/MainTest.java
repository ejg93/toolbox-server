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

    @Test
    void subcommandsAreRegistered() {
        CommandLine cmd = Main.commandLine();
        assertNotNull(cmd.getSubcommands().get("serve"));
        assertNotNull(cmd.getSubcommands().get("version"));
    }
}
