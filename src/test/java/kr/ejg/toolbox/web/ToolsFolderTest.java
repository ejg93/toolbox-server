package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * `resources/tools/` 폴더 규칙을 폴더 전체로 잰다(0-26, 2026-09-27 AI 리뷰 — 일곱 이름만 보던 BackendToolsTest 의 빈틈).
 * CLAUDE.md 구역 표: 파일명 영문, CDN 금지. 절대 규칙 1: 외부 통신 0.
 */
class ToolsFolderTest {

    private static final Path DIR = Path.of("src/main/resources/tools");

    /** 외부에서 불러오는 모양 — src/href 의 절대·프로토콜 상대 URL, @import url(http…) */
    private static final Pattern EXTERNAL_LOAD = Pattern.compile(
            "(?i)(?:\\b(?:src|href)\\s*=\\s*[\"']?(?:https?:)?//)|(?:@import\\s+(?:url\\()?[\"']?(?:https?:)?//)");

    private static List<Path> files() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(Files::isRegularFile).sorted().toList();
        }
    }

    @Test
    void fileNamesAreAsciiLowerSnake() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path p : files()) {
            String n = p.getFileName().toString();
            if (!n.matches("[a-z0-9_]+\\.(html|js|css)")) {
                bad.add(n);
            }
        }
        assertEquals(List.of(), bad, "파일명은 영문 소문자·숫자·밑줄 + html·js·css");
    }

    @Test
    void noExternalLoads() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path p : files()) {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            Matcher m = EXTERNAL_LOAD.matcher(body);
            while (m.find()) {
                int line = body.substring(0, m.start()).split("\n", -1).length;
                hits.add(p.getFileName() + ":" + line + " " + m.group());
            }
        }
        assertEquals(List.of(), hits, "외부 로드 금지(CDN·원격 스크립트·스타일)");
    }

    /** 패턴이 빈 초록이 아닌지 — 잡아야 할 모양을 실제로 잡는다 */
    @Test
    void patternCatchesKnownShapes() {
        assertTrue(EXTERNAL_LOAD.matcher("<script src=\"https://cdn.example/x.js\">").find());
        assertTrue(EXTERNAL_LOAD.matcher("<link rel=stylesheet href=//cdn.example/x.css>").find());
        assertTrue(EXTERNAL_LOAD.matcher("@import url('http://x/y.css');").find());
        assertTrue(!EXTERNAL_LOAD.matcher("<script src=\"/tools/common.js\">").find());
        assertTrue(!EXTERNAL_LOAD.matcher("/@import\\b|@charset\\b/").find());
    }
}
