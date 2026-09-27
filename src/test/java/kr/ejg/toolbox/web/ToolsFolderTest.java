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

    /**
     * iframe 은 전부 sandbox(스크립트 허용 없이). srcdoc iframe 은 부모와 같은 출처라 붙여 넣은 HTML 이
     * 127.0.0.1 권한으로 돈다(0-31, table_builder 미리보기).
     */
    @Test
    void iframesAreSandboxedWithoutScripts() throws IOException {
        Pattern iframe = Pattern.compile("(?is)<iframe\\b[^>]*>");
        List<String> bad = new ArrayList<>();
        for (Path p : files()) {
            Matcher m = iframe.matcher(Files.readString(p, StandardCharsets.UTF_8));
            while (m.find()) {
                String tag = m.group();
                if (!tag.matches("(?is).*\\bsandbox\\s*=.*") || tag.matches("(?is).*allow-scripts.*")) {
                    bad.add(p.getFileName() + " " + tag);
                }
            }
        }
        assertEquals(List.of(), bad);
    }

    /**
     * 백엔드본이 새로 쓴 JS 파일(common.js·dev_tools_ext.js 등 tools/*.js)은 innerHTML 에 비우기('')만 넣는다 —
     * 서버·파일에서 온 값은 textContent(0-31). html 안의 순수본 복사 JS 는 이 검사 밖이다(원형 유지).
     */
    @Test
    void extractedScriptsOnlyClearInnerHtml() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path p : files()) {
            if (!p.getFileName().toString().endsWith(".js")) {
                continue;
            }
            Matcher m = INNER_HTML_SET.matcher(Files.readString(p, StandardCharsets.UTF_8));
            while (m.find()) {
                if (!m.group(1).trim().matches("(''|\"\")\\s*;?")) {
                    bad.add(p.getFileName() + ": " + m.group().trim());
                }
            }
        }
        assertEquals(List.of(), bad, "tools/*.js 는 innerHTML 에 '' 만 — 값은 textContent");
    }

    /** {@code x.innerHTML = <식>} · {@code +=} — 줄 끝까지를 식으로 본다 */
    static final Pattern INNER_HTML_SET = Pattern.compile("\\.innerHTML\\s*\\+?=\\s*([^\\n]*)");

    /** 패턴이 빈 초록이 아닌지 — 잡아야 할 모양을 실제로 잡는다 */
    @Test
    void patternCatchesKnownShapes() {
        Matcher ih = INNER_HTML_SET.matcher("box.innerHTML = '<b>' + name;");
        assertTrue(ih.find() && ih.group(1).startsWith("'<b>'"));
        Matcher ih2 = INNER_HTML_SET.matcher("el.innerHTML += x;");
        assertTrue(ih2.find() && ih2.group(1).equals("x;"));
        Matcher clear = INNER_HTML_SET.matcher("el.innerHTML = '';");
        assertTrue(clear.find() && clear.group(1).matches("(''|\"\")\\s*;?"));
        assertTrue(EXTERNAL_LOAD.matcher("<script src=\"https://cdn.example/x.js\">").find());
        assertTrue(EXTERNAL_LOAD.matcher("<link rel=stylesheet href=//cdn.example/x.css>").find());
        assertTrue(EXTERNAL_LOAD.matcher("@import url('http://x/y.css');").find());
        assertTrue(!EXTERNAL_LOAD.matcher("<script src=\"/tools/common.js\">").find());
        assertTrue(!EXTERNAL_LOAD.matcher("/@import\\b|@charset\\b/").find());
    }
}
