package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kr.ejg.toolbox.CorpusFiles;

/**
 * 실물 표본 JS 기능(V-2·V-3·V-7) — 앱을 띄우고 {@code scripts/puppeteer/<script>} 를 불러 {@code target/<out>/summary.json} 을 읽는다.
 * 순수본 JS 가 IIFE·전역 함수라 자바로 옮기지 않고 브라우저에서 돈다. node·Puppeteer({@code C:/workspace/node_modules}) 필수.
 */
final class CorpusNode {

    private CorpusNode() {
    }

    record Result(Path out, List<JsonNode> files) {
    }

    static Result run(String script, String outName, Path tmp) throws Exception {
        CorpusFiles.verify();
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\n", StandardCharsets.UTF_8);
        Javalin app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        Path out = Path.of("target", outName).toAbsolutePath();
        try {
            Process p = new ProcessBuilder("node", "scripts/puppeteer/" + script, "http://127.0.0.1:" + app.port(),
                    CorpusFiles.root().toString(), out.toString()).redirectErrorStream(true).start();
            String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, p.waitFor(), script + " 실패: " + log.substring(Math.max(0, log.length() - 500)));
        } finally {
            app.stop();
        }
        JsonNode s = new ObjectMapper().readTree(out.resolve("summary.json").toFile());
        assertEquals(0, s.get("pageErrors").size(), "페이지 오류: " + s.get("pageErrors"));
        List<JsonNode> rows = new ArrayList<>();
        s.get("files").forEach(rows::add);
        return new Result(out, rows);
    }
}
