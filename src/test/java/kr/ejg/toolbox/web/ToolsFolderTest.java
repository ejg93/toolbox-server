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

    /**
     * 4-11 — sql_snippets 는 복사 전용: common.js 줄을 뺀 글이 순수본과 같고(CR 제거 뒤) 백엔드 호출({@code /api/})이 없다.
     * 접속·실행을 되살리면 빨강(2.5 를 사용자가 뒤집음, 2026-10-04)
     */
    @Test
    void sqlSnippetsIsPurePlusCommonJs() throws IOException {
        String common = "<script src=\"/tools/common.js\" defer></script>\n";
        String mine = Files.readString(DIR.resolve("sql_snippets.html"), StandardCharsets.UTF_8).replace("\r", "");
        String pure = Files.readString(Path.of("pure/tools/sql_snippets.html"), StandardCharsets.UTF_8).replace("\r", "");
        assertTrue(mine.contains(common), "common.js 한 줄(배지)");
        assertEquals(pure, mine.replace(common, ""));
        assertTrue(!mine.contains("/api/"), "백엔드 호출 없음");
    }

    /**
     * 4-16 — INSERT 탭 결과 칸이 둘로 갈렸다(4-13): 서버 생성 {@code insRun} 은 {@code #ins_out} 에만 쓰고 순수본 칸 {@code #dummy_out} 은 안 건드린다.
     * Puppeteer(집 검증)만 보던 것을 verify·CI 로 내렸다
     */
    @Test
    void insertResultBoxesStaySeparate() throws IOException {
        String ext = Files.readString(DIR.resolve("dev_tools_ext.js"), StandardCharsets.UTF_8);
        int from = ext.indexOf("function insRun()");
        assertTrue(from >= 0, "insRun 이 있다");
        int to = ext.indexOf("\n  function ", from + 1);
        int cmt = ext.indexOf("\n  /*", from + 1);
        String body = ext.substring(from, Math.min(to < 0 ? ext.length() : to, cmt < 0 ? ext.length() : cmt));
        assertTrue(body.contains("ins_out"), "insRun 은 #ins_out 에 쓴다");
        assertTrue(!body.contains("dummy_out"), "insRun 은 #dummy_out 을 안 건드린다");
        String html = Files.readString(DIR.resolve("dev_tools.html"), StandardCharsets.UTF_8);
        assertEquals(1, html.split("id=\"ins_out\"", -1).length - 1, "#ins_out 하나");
        assertEquals(1, html.split("id=\"dummy_out\"", -1).length - 1, "#dummy_out 하나");
    }

    /**
     * 0-48 — 배지 SSE {@code /api/alive}(0-45)가 연결 하나를 늘 열어 두어 {@code networkidle0} 은 오지 않는다(0-47).
     * Puppeteer 는 집 검증이라 verify·CI 가 안 돌리므로 글로 막는다
     */
    @Test
    void puppeteerDoesNotWaitForNetworkIdle0() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> s = Files.list(Path.of("scripts/puppeteer"))) {
            for (Path p : s.filter(f -> f.toString().endsWith(".js")).sorted().toList()) {
                if (Files.readString(p, StandardCharsets.UTF_8).contains("networkidle0")) {
                    hits.add(p.getFileName().toString());
                }
            }
        }
        assertEquals(List.of(), hits, "networkidle0 금지 — 배지 SSE 가 열려 있어 30초 제한에 걸린다. networkidle2");
    }

    /**
     * 1-37 — 저장 알림은 {@code TB.savedText}(0-44: 하나면 파일 전체 경로, 여럿이면 「n개 — 폴더」)를 거친다.
     * 화면마다 그 자리 수가 줄면 빨강, 「'저장 ' + 경로」 직접 이어붙이기는 {@code common.js} 밖에서 금지
     */
    @Test
    void saveNoticesGoThroughSavedText() throws IOException {
        java.util.Map<String, Integer> sites = new java.util.LinkedHashMap<>();
        sites.put("db_browser.html", 1);
        sites.put("deliverable_sql.html", 2);
        sites.put("program_analysis_ext.js", 1);
        sites.put("code_check_ext.js", 2);
        sites.put("logical_name.html", 2);
        List<String> bad = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : sites.entrySet()) {
            String body = Files.readString(DIR.resolve(e.getKey()), StandardCharsets.UTF_8);
            int n = body.split("TB\\.savedText\\(", -1).length - 1;
            if (n < e.getValue()) {
                bad.add(e.getKey() + " savedText " + n + " < " + e.getValue());
            }
        }
        String dev = Files.readString(DIR.resolve("dev_tools_ext.js"), StandardCharsets.UTF_8);
        if (!dev.contains("SB.backupRoot")) {
            bad.add("dev_tools_ext.js 폴더 적용 알림이 백업 폴더를 안 보인다");
        }
        Pattern direct = Pattern.compile("['\"]저장 ['\"]\\s*\\+");
        for (Path p : files()) {
            if (p.getFileName().toString().equals("common.js")) {
                continue;
            }
            Matcher m = direct.matcher(Files.readString(p, StandardCharsets.UTF_8));
            if (m.find()) {
                bad.add(p.getFileName() + " 「'저장 ' +」 직접 이어붙이기");
            }
        }
        assertEquals(List.of(), bad, "저장 알림은 TB.savedText 로(0-44)");
    }

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
