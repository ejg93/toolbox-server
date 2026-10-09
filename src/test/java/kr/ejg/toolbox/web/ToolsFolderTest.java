package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /** 1-34 — 산출물 화면 카드는 한 줄에 하나(한 열). HtmlUnit 은 CSS 를 안 재서 규칙 글로 */
    @Test
    void deliverableCardsAreOneColumn() throws IOException {
        String html = Files.readString(DIR.resolve("deliverable_sql.html"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\\.grid \\{[^}]*\\}").matcher(html);
        assertTrue(m.find(), ".grid 규칙");
        assertTrue(m.group().contains("grid-template-columns: 1fr;") && !m.group().contains("280px"), m.group());
    }

    /** 1-36 — 산출물 화면은 SQL 을 실행하지 않는다(DB 툴에서 돌린다). 실행 버튼·실행 함수·실행 API 글이 없다 */
    @Test
    void deliverableHasNoSqlRun() throws IOException {
        String html = Files.readString(DIR.resolve("deliverable_sql.html"), StandardCharsets.UTF_8);
        for (String bad : List.of("/api/sql/run", "고른 접속에서 실행", "runSql")) {
            assertTrue(!html.contains(bad), "deliverable_sql.html 에 " + bad);
        }
    }

    /**
     * 1-35 — 복사는 TB.copy 한 곳으로. 성공 글은 「복사됨」 하나 — 「복사했다」·「복사함」 없음.
     * execCommand·clipboard.writeText 는 common.js 와 순수본 핀(sql_snippets, 4-11)에만
     */
    @Test
    void copyGoesThroughTbCopy() throws IOException {
        Map<String, Integer> min = Map.of("db_browser.html", 1, "deliverable_sql.html", 3, "logical_name.html", 1,
                "code_check_ext.js", 1, "dev_tools.html", 2, "dev_tools_ext.js", 2, "jsp_formatter.html", 1,
                "table_builder.html", 1, "special_chars.html", 1);
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, Integer> e : min.entrySet()) {
            String body = Files.readString(DIR.resolve(e.getKey()), StandardCharsets.UTF_8);
            int n = body.split("TB\\.copy\\(", -1).length - 1;
            if (n < e.getValue()) {
                bad.add(e.getKey() + " TB.copy( " + n + " < " + e.getValue());
            }
        }
        for (Path f : files()) {
            String name = f.getFileName().toString();
            String body = Files.readString(f, StandardCharsets.UTF_8);
            Matcher w = COPY_WORDS.matcher(body);
            while (w.find()) {
                bad.add(name + ":" + body.substring(0, w.start()).split("\n", -1).length + " " + w.group());
            }
            if (name.equals("common.js") || name.equals("sql_snippets.html")) {
                continue;
            }
            Matcher m = RAW_COPY.matcher(body);
            while (m.find()) {
                bad.add(name + ":" + body.substring(0, m.start()).split("\n", -1).length + " " + m.group());
            }
        }
        assertEquals(List.of(), bad, "복사는 TB.copy 로(1-35)");
    }

    static final Pattern COPY_WORDS = Pattern.compile("복사했다|복사함");
    static final Pattern RAW_COPY = Pattern.compile("execCommand\\(|clipboard\\.writeText");

    /**
     * 1-37 — 저장 알림은 {@code TB.savedText}(0-44: 하나면 파일 전체 경로, 여럿이면 「n개 — 폴더」)를 거친다.
     * 화면마다 그 자리 수가 줄면 빨강, 「'저장 ' + 경로」 직접 이어붙이기는 {@code common.js} 밖에서 금지
     */
    @Test
    void saveNoticesGoThroughSavedText() throws IOException {
        java.util.Map<String, Integer> sites = new java.util.LinkedHashMap<>();
        sites.put("db_browser.html", 2);
        sites.put("deliverable_sql.html", 2);
        sites.put("program_analysis_ext.js", 1);
        sites.put("code_check_ext.js", 2);
        sites.put("logical_name.html", 4);
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
        // 글 조각이 「저장 」 으로 끝나고 + 로 경로를 잇는 꼴 — '저장 ' + p · ' · 저장 ' + p · ' — 저장 ' + p(PR #47 리뷰: 앞에 글이 붙은 꼴을 놓쳤다)
        Pattern direct = Pattern.compile("저장 ['\"]\\s*\\+");
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

    /**
     * 1-29 — INSERT 탭 스냅샷 고르기의 글은 {@code TB.snapLabel}(1-17·1-25 「#id 접속 시각 · 테이블 n · 거름」)로 채운다.
     * Puppeteer(집 검증)만 보던 것을 verify·CI 로 — HtmlUnit 은 dev_tools 를 못 읽는다(JS_OFF)
     */
    @Test
    void insertSnapshotOptionsUseSnapLabel() throws IOException {
        String ext = Files.readString(DIR.resolve("dev_tools_ext.js"), StandardCharsets.UTF_8);
        int from = ext.indexOf("function insLoad()");
        assertTrue(from >= 0, "insLoad 가 있다");
        int to = ext.indexOf("\n  function ", from + 1);
        String body = ext.substring(from, to < 0 ? ext.length() : to);
        assertTrue(body.contains("TB.snapLabel("), "스냅샷 option 글은 TB.snapLabel — " + body);
    }

    /** 3-11 — 이름은 「표준 사전 · 논리명」. 화면 어디에도 옛 이름 「논리명 변환기」 가 없다 */
    @Test
    void logicalNameRenamed() throws IOException {
        String html = Files.readString(DIR.resolve("logical_name.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("<title>표준 사전 · 논리명</title>") && html.contains("<h1>표준 사전 · 논리명</h1>"), "제목");
        List<String> old = new ArrayList<>();
        for (Path f : files()) {
            if (Files.readString(f, StandardCharsets.UTF_8).contains("논리명 변환기")) {
                old.add(f.getFileName().toString());
            }
        }
        assertEquals(List.of(), old, "옛 이름이 남은 화면");
    }

    /** 3-10 — 표준 사전 화면의 후보 버튼 번호는 산출물 번호와 같다(05 표준단어·06 표준도메인·07 표준용어) */
    @Test
    void logicalCandidateNumbersMatchDeliverables() throws IOException {
        String html = Files.readString(DIR.resolve("logical_name.html"), StandardCharsets.UTF_8);
        for (String[] k : new String[][] {{"words", "05 표준단어"}, {"domains", "06 표준도메인"}, {"terms", "07 표준용어"}}) {
            assertTrue(html.contains("data-kind=\"" + k[0] + "\">" + k[1]), k[0] + " 버튼 글이 「" + k[1] + "」 로 시작");
        }
    }

    /** 3-13 — 「1. 입력」 카드 하나에 ① 공통표준단어(선택) ② 기관표준단어(선택) ③ 컬럼 목록(필수 — 스냅샷·CSV 파일 중 하나). 붙여넣기 칸은 없다 */
    @Test
    void logicalNameInputIsOneCardWithThreeSteps() throws IOException {
        String html = Files.readString(DIR.resolve("logical_name.html"), StandardCharsets.UTF_8);
        int from = html.indexOf("<h2>1. 입력</h2>");
        int to = html.indexOf("<h2>2. 설정</h2>");
        assertTrue(from > 0 && to > from, "1. 입력 → 2. 설정 순서");
        String input = html.substring(from, to);
        for (String step : List.of("class=\"k\">① 공통표준단어", "class=\"k\">② 기관표준단어", "class=\"k\">③ 컬럼 목록", "id=\"srcSnap\"", "id=\"srcCsv\"", "id=\"csvFile\"")) {
            assertTrue(input.contains(step), step);
        }
        assertEquals(1, input.split("badge req", -1).length - 1, "필수는 ③ 하나");
        assertEquals(2, input.split("badge opt", -1).length - 1, "선택은 ①② 둘");
        assertFalse(html.contains("CSV 붙여넣기"), "붙여넣기 칸 글이 남음");
        assertFalse(html.contains("classList.toggle("), "HtmlUnit 이 둘째 인자를 버린다 — onOff 를 쓴다");
    }

    /** 3-14 — 화면에 DB 로 COMMENT 를 실행하는 버튼·호출이 없고, 실행 클래스도 없다 */
    @Test
    void noCommentApply() throws IOException {
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path f : files.toList()) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                assertFalse(text.contains("comments/apply"), f.getFileName() + " 이 COMMENT 실행 API 를 부른다");
                assertFalse(text.contains("COMMENT 실행"), f.getFileName() + " 에 COMMENT 실행 글");
            }
        }
        assertFalse(Files.exists(Path.of("src/main/java/kr/ejg/toolbox/core/logical/CommentApply.java")), "CommentApply 가 남음");
    }

    /** 1-54 — 고칠 수 있는 화면 전부(sql_snippets 는 글자 고정). 런처 포함 */
    private static List<Path> themedPages() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(f -> f.toString().endsWith(".html") && !f.getFileName().toString().equals("sql_snippets.html")).sorted().toList();
        }
    }

    private static String styles(String html) {
        StringBuilder out = new StringBuilder();
        Matcher m = Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL).matcher(html);
        while (m.find()) {
            out.append(m.group(1)).append('\n');
        }
        return out.toString();
    }

    /** 고정 색으로 남겨도 되는 것 — 흰·검정, 캔버스 둘레·그림자, DB 구분색, JWT 머리, 바이트 넘침. 나머지 색은 common.css 토큰 */
    static final java.util.Set<String> FIXED_COLORS = java.util.Set.of("#fff", "#000", "#0002", "#333",
            "#f0a500", "#00758f", "#00bcd4", "#336791", "#5b9bd5", "#c586c0", "#ce9178", "#fb8c00", "#f48771");

    /** 1-54 — 토큰·테마·버튼 꼴은 common.css 에만. 화면 <style> 은 그 화면에만 있는 것(설계 18 D2·D4) */
    @Test
    void toolsHaveNoLocalTheme() throws IOException {
        List<String> bad = new ArrayList<>();
        Pattern hex = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");
        for (Path f : themedPages()) {
            String css = styles(Files.readString(f, StandardCharsets.UTF_8));
            String n = f.getFileName().toString();
            if (css.contains(":root")) bad.add(n + " :root");
            if (css.contains("prefers-color-scheme")) bad.add(n + " prefers-color-scheme");
            if (Pattern.compile("(?m)^\\s*button\\s*[{:]").matcher(css).find()) bad.add(n + " button 규칙");
            if (Pattern.compile("\\.btn-(p|g|green|red|del)\\b[^{}]*\\{").matcher(css).find()) bad.add(n + " .btn-* 규칙");
            if (Pattern.compile("\\.dl\\b[^{}]*\\{").matcher(css).find()) bad.add(n + " .dl 규칙");
            Matcher m = hex.matcher(css);
            while (m.find()) {
                if (!FIXED_COLORS.contains(m.group().toLowerCase(java.util.Locale.ROOT))) bad.add(n + " 고정 색 " + m.group());
            }
        }
        assertEquals(List.of(), bad, "화면 <style> 에 공용이 맡는 것이 남았다");
    }

    /** 1-54 — 공용 CSS link 는 첫 <style> 앞(같은 특이도면 화면 규칙이 이기게) */
    @Test
    void toolsLinkCommonCss() throws IOException {
        String link = "<link rel=\"stylesheet\" href=\"/tools/common.css\">";
        for (Path f : themedPages()) {
            String html = Files.readString(f, StandardCharsets.UTF_8);
            int l = html.indexOf(link), st = html.indexOf("<style>");
            assertTrue(l > 0, f.getFileName() + " 에 common.css link");
            assertTrue(st < 0 || l < st, f.getFileName() + " 의 link 가 첫 <style> 앞");
            assertTrue(l < html.indexOf("</head>"), f.getFileName() + " 의 link 가 </head> 앞");
        }
    }

    /** 1-54 — 공용 CSS 가 버튼 유형·테마 스위치를 다 담고, 밖으로 나가는 주소가 없다 */
    @Test
    void commonCssDefinesButtonScheme() throws IOException {
        String css = Files.readString(DIR.resolve("common.css"), StandardCharsets.UTF_8);
        for (String need : List.of(".btn-p {", ".btn-green {", ".btn-red {", ".dl {", ".ico-xlsx {", ":root[data-theme=\"light\"]",
                ":root:not([data-theme=\"dark\"])", "prefers-color-scheme: light", "prefers-reduced-motion")) {
            assertTrue(css.contains(need), need);
        }
        assertFalse(css.contains("url(http") || css.contains("@import"), "밖으로 나가는 주소");
    }

    /** 3-12 — 산출물 → 표준 사전 링크는 만들기 시작 때 스냅샷 id 를 쓴다. 끝날 때 고르기 값을 읽으면 만드는 동안 바꾼 스냅샷을 가리킨다 */
    @Test
    void buildLinkUsesSnapshotFromBuildStart() throws IOException {
        String html = Files.readString(DIR.resolve("deliverable_sql.html"), StandardCharsets.UTF_8);
        int from = html.indexOf("function buildLink(");
        String fn = html.substring(from, html.indexOf("\n\t}", from));
        assertFalse(fn.contains("$('snap')"), "buildLink 가 고르기 값을 읽는다: " + fn);
        assertTrue(html.contains("poll(r.jobId, id)") && html.contains("buildLink(c, snap)"), "시작 때 id 를 poll → buildLink 로 넘긴다");
    }

    /** 0-50 — 숨은 탭은 배지 연결(/api/alive SSE)을 닫는다. 브라우저 연결 6개를 숨은 탭이 쥐면 보이는 탭의 중지가 줄을 선다 */
    @Test
    void hiddenTabsReleaseAliveConnection() throws IOException {
        String js = Files.readString(DIR.resolve("common.js"), StandardCharsets.UTF_8);
        for (String need : List.of("addEventListener('visibilitychange'", "live.close()", "document.visibilityState === 'hidden'")) {
            assertTrue(js.contains(need), "common.js 에 " + need);
        }
    }

    /** 1-38 — 모드 배지는 오른쪽 아래(0-46, 사용자 정정). 배지 CSS 에 right 가 있고 left 가 없다 */
    @Test
    void modeBadgeSitsBottomRight() throws IOException {
        String js = Files.readString(DIR.resolve("common.js"), StandardCharsets.UTF_8);
        int from = js.indexOf("BADGE_ID + '{");
        assertTrue(from >= 0, "배지 CSS 자리");
        String css = js.substring(from, js.indexOf('}', from));
        assertTrue(css.contains("bottom:") && css.contains("right:"), css);
        assertTrue(!css.contains("left:"), "배지는 오른쪽 아래 — " + css);
    }

    /** 패턴이 빈 초록이 아닌지 — 잡아야 할 모양을 실제로 잡는다 */
    @Test
    void patternCatchesKnownShapes() {
        assertTrue(COPY_WORDS.matcher("msg('ddlMsg', '복사했다', 'ok')").find() && COPY_WORDS.matcher("'전부 복사함'").find());
        assertTrue(RAW_COPY.matcher("try { document.execCommand('copy'); }").find() && RAW_COPY.matcher("navigator.clipboard.writeText(t)").find());
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
