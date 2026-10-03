package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 5-10 — 자바·JS 두 구현의 교차 검증. 순수본 {@code pure/tools/code_check.html} 을 {@code file://} 로 열어 5-1 픽스처(정규식 묶음 넷)를
 * 붙여 넣은 결과가 자바 골든 {@code golden/check/fixtures.json} 의 정규식 규칙분과 파일마다 같아야 한다(등급 A).
 * node·Puppeteer({@code C:/workspace/node_modules}) 필수 — 앱은 안 띄운다.
 */
@Tag("corpus")
class PureCodeCheckTest {

    static final List<String> DIRS = List.of("common", "jsp", "tsx", "security", "a11y");

    @Test
    void pureMatchesJavaGolden() throws Exception {
        Path out = Path.of("target", "pure-code-check").toAbsolutePath();
        Path page = Path.of("pure", "tools", "code_check.html").toAbsolutePath();
        Path fixtures = Path.of("src", "test", "resources", "fixtures", "check").toAbsolutePath();
        Process p = new ProcessBuilder("node", "scripts/puppeteer/corpus-check-pure.js", page.toUri().toString(), fixtures.toString(),
                out.toString()).redirectErrorStream(true).start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "corpus-check-pure.js 실패: " + log.substring(Math.max(0, log.length() - 500)));

        JsonNode s = GoldenFiles.JSON.readTree(out.resolve("summary.json").toFile());
        assertEquals(0, s.get("pageErrors").size(), "순수본 페이지·콘솔 오류: " + s.get("pageErrors"));

        Map<String, List<String>> actual = new TreeMap<>();
        for (JsonNode f : s.get("files")) {
            List<String> hits = new ArrayList<>();
            f.get("hits").forEach(h -> hits.add(hit(h.get("line").asInt(), h.get("rule").asText())));
            hits.sort(null);
            actual.put(f.get("file").asText(), hits);
        }
        assertEquals(22, actual.size(), "픽스처 수(정규식 묶음 다섯 — 5-17 이 a11y 넷 더함) — " + actual.keySet());

        Set<String> regexIds = new HashSet<>();
        GoldenFiles.JSON.readTree(GoldenFiles.DIR.resolve("check/pure-rules.json").toFile()).forEach(r -> regexIds.add(r.get("id").asText()));
        Map<String, List<String>> expected = new TreeMap<>();
        actual.keySet().forEach(k -> expected.put(k, new ArrayList<>()));
        for (JsonNode g : GoldenFiles.JSON.readTree(GoldenFiles.DIR.resolve("check/fixtures.json").toFile())) {
            String file = g.get("file").asText();
            if (DIRS.contains(file.substring(0, file.indexOf('/'))) && regexIds.contains(g.get("rule").asText())) {
                expected.computeIfAbsent(file, k -> new ArrayList<>()).add(hit(g.get("line").asInt(), g.get("rule").asText()));
            }
        }
        expected.values().forEach(l -> l.sort(null));

        actual.forEach((file, hits) -> {
            if (file.contains("/neg.")) {
                assertTrue(hits.isEmpty(), "음성 픽스처에 걸림: " + file + " " + hits);
            }
        });
        assertEquals(expected, actual, "순수본(JS) ≠ 자바 골든 — 규칙이 바뀌었으면 pure-rules.json 을 portfolio 에 다시 붙인다");
    }

    private static String hit(int line, String rule) {
        return String.format("%04d %s", line, rule);
    }
}
