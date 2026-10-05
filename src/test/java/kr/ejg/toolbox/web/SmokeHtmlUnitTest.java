package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.htmlunit.BrowserVersion;
import org.htmlunit.ScriptException;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.javascript.SilentJavaScriptErrorListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 서버를 띄우고 런처 + 도구를 HtmlUnit 으로 열어 스크립트 예외 0 과 모드 배지를 본다.
 * HtmlUnit 이 못 읽는 최신 문법을 쓰는 도구는 {@link #JS_OFF} 에 넣어 JS 를 끄고 로드만 본다(사유는 PROGRESS 이력).
 */
class SmokeHtmlUnitTest {

    /** HtmlUnit(Rhino) 가 문법을 못 읽는 도구 — JS 끄고 로드만 */
    static final Set<String> JS_OFF = Set.of("dev_tools");

    static Javalin app;

    @BeforeAll
    static void up() {
        app = App.start(AppTest.config(0));
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    private static WebClient client(boolean js) {
        WebClient wc = new WebClient(BrowserVersion.CHROME);
        wc.getOptions().setJavaScriptEnabled(js);
        wc.getOptions().setThrowExceptionOnScriptError(true);
        wc.getOptions().setThrowExceptionOnFailingStatusCode(true);
        wc.getOptions().setCssEnabled(false);
        wc.getOptions().setDownloadImages(false);
        wc.setJavaScriptErrorListener(new SilentJavaScriptErrorListener());
        return wc;
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "index", "db_browser", "dev_tools", "jsp_formatter", "sql_snippets", "table_builder",
        "logical_name", "deliverable_sql", "special_chars", "code_check", "program_analysis", "crud_generator"
    })
    void opensWithoutScriptErrors(String name) throws Exception {
        boolean js = !JS_OFF.contains(name);
        List<String> errors = new ArrayList<>();
        try (WebClient wc = client(js)) {
            wc.setJavaScriptErrorListener(new SilentJavaScriptErrorListener() {
                @Override
                public void scriptException(HtmlPage page, ScriptException e) {
                    errors.add(e.getMessage());
                }
            });
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/" + name + ".html");
            wc.waitForBackgroundJavaScript(3000);
            assertEquals(List.of(), errors, name + " 스크립트 예외");
            if (js) {
                DomElement badge = page.getElementById("tb-mode-badge");
                assertNotNull(badge, name + " 에 모드 배지");
                assertTrue(badge.getTextContent().startsWith("백엔드 연결"), name + " 배지: " + badge.getTextContent());
            }
        }
    }

    /** 3-8 — 논리명 화면이 CSV 붙여넣기로 변환을 불러 결과 표·랭킹을 채운다(JS 켠 채) */
    @Test
    void logicalNameRunsFromCsv() throws Exception {
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/logical_name.html");
            wc.waitForBackgroundJavaScript(5000);
            ((org.htmlunit.html.HtmlSelect) page.getElementById("snap")).setSelectedAttribute("", true);
            ((org.htmlunit.html.HtmlTextArea) page.getElementById("csv")).setText(
                    "OWNER,TABLE_NAME,COLUMN_NAME,DATA_TYPE" + (char) 10 + "S,TB_USE,USE_YN,CHAR" + (char) 10 + "S,TB_USE,QWZX_CD,VARCHAR");
            ((org.htmlunit.html.HtmlButton) page.getElementById("run")).click();
            wc.waitForBackgroundJavaScript(5000);
            String msg = page.getElementById("runMsg").getTextContent();
            assertTrue(msg.startsWith("컬럼 2 · 테이블 1"), msg);
            assertTrue(page.getElementById("tbl").getTextContent().contains("사용여부"), page.getElementById("tbl").getTextContent());
            assertTrue(page.getElementById("rank").getTextContent().contains("QWZX"), page.getElementById("rank").getTextContent());
            assertTrue(page.getElementById("moiSource").getTextContent().startsWith("공통표준단어 판 moi-"), page.getElementById("moiSource").getTextContent());
        }
    }

    /** 7-9 — 논리명 화면 마스킹: CSV → 탐지 → 후보 행 셋 → 하나 해제 → 다시 → SQL 에 그 컬럼 없음 */
    @Test
    void logicalNameMasking() throws Exception {
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/logical_name.html");
            wc.waitForBackgroundJavaScript(5000);
            ((org.htmlunit.html.HtmlSelect) page.getElementById("snap")).setSelectedAttribute("", true);
            ((org.htmlunit.html.HtmlTextArea) page.getElementById("csv")).setText("OWNER,TABLE_NAME,COLUMN_NAME,DATA_TYPE,DATA_LENGTH" + (char) 10
                    + "S,TB_MBER,MBER_NM,VARCHAR2,50" + (char) 10 + "S,TB_MBER,MBTLNUM,VARCHAR2,20" + (char) 10 + "S,TB_MBER,EMAIL,VARCHAR2,50");
            ((org.htmlunit.html.HtmlButton) page.getElementById("maskFind")).click();
            wc.waitForBackgroundJavaScript(5000);
            List<?> rows = page.querySelectorAll("#maskTbl tbody tr");
            assertEquals(3, rows.size(), page.getElementById("maskMsg").getTextContent());
            String sql = ((org.htmlunit.html.HtmlTextArea) page.getElementById("maskSql")).getText();
            assertTrue(sql.contains("EMAIL ="), sql);
            for (Object b : page.querySelectorAll("#maskTbl input[type=checkbox]")) {
                org.htmlunit.html.HtmlCheckBoxInput cb = (org.htmlunit.html.HtmlCheckBoxInput) b;
                if ("EMAIL".equals(cb.getAttribute("data-c"))) {
                    cb.click();
                }
            }
            ((org.htmlunit.html.HtmlButton) page.getElementById("maskMake")).click();
            wc.waitForBackgroundJavaScript(5000);
            sql = ((org.htmlunit.html.HtmlTextArea) page.getElementById("maskSql")).getText();
            assertFalse(sql.contains("EMAIL ="), sql);
            assertTrue(sql.contains("MBTLNUM ="), sql);
            assertTrue(page.getElementById("maskMsg").getTextContent().contains("제외 1"), page.getElementById("maskMsg").getTextContent());
        }
    }

    /** 1-9 — DB 브라우저의 DTO 카드: DDL 붙여넣기 → 소스 미리보기(JS 켠 채) */
    @Test
    void dbBrowserMakesDtoFromDdl() throws Exception {
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/db_browser.html");
            wc.waitForBackgroundJavaScript(5000);
            ((org.htmlunit.html.HtmlTextArea) page.getElementById("dtoDdl")).setText("CREATE TABLE T_ITEM (ITEM_ID INT PRIMARY KEY, ITEM_NM VARCHAR(50))");
            ((org.htmlunit.html.HtmlButton) page.getElementById("dtoFromDdl")).click();
            wc.waitForBackgroundJavaScript(5000);
            String out = ((org.htmlunit.html.HtmlTextArea) page.getElementById("dtoOut")).getText();
            assertTrue(out.contains("public record TItem("), out + " / " + page.getElementById("dtoMsg").getTextContent());
            assertTrue(out.contains("String itemNm"), out);
        }
    }

    /** 2-5 — 산출물 화면: 문서 체크 11 + SQL 가이드가 방언을 바꾸면 다시 그린다(JS 켠 채) */
    @Test
    void deliverableGuideSwitchesDialect() throws Exception {
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/deliverable_sql.html");
            wc.waitForBackgroundJavaScript(5000);
            assertEquals(12, page.querySelectorAll("#docChecks input").size(), "01~11 + 18(2-18)");
            ((org.htmlunit.html.HtmlSelect) page.getElementById("guideDoc")).setSelectedAttribute("d02", true);
            ((org.htmlunit.html.HtmlSelect) page.getElementById("dialect")).setSelectedAttribute("pg", true);
            wc.waitForBackgroundJavaScript(2000);
            String g = page.getElementById("guide").getTextContent();
            assertTrue(g.contains("ROW_NUMBER() OVER"), g.substring(0, Math.min(300, g.length())));
            assertTrue(g.contains("'__스키마_미입력__'") && !g.contains("__SCHEMAS__"), "치환");
            assertEquals(8, page.querySelectorAll("#qKind option").size(), "90 품질 진단 8종");
        }
    }

    /** 1-8 — DB 브라우저가 로드 때 API 를 불러 프로필·접속 목록을 채운다(예시 프로필 example 의 접속 dev) */
    @Test
    void dbBrowserFillsProfileAndConnections() throws Exception {
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/db_browser.html");
            wc.waitForBackgroundJavaScript(5000);
            assertEquals("example", ((org.htmlunit.html.HtmlSelect) page.getElementById("profile")).getSelectedOptions().get(0).getText());
            assertTrue(page.getElementById("conns").getTextContent().contains("dev"), page.getElementById("conns").getTextContent());
        }
    }

    /** 1-16(U-8 8a·8b) — 활성 프로필 없이 열면 select 는 「(프로필 고르기)」, 접속 칸은 프로필 만드는 안내 */
    @Test
    void dbBrowserWithoutActiveProfile(@TempDir Path tmp) throws Exception {
        Path profiles = Files.createDirectories(tmp.resolve("profiles"));
        Files.writeString(profiles.resolve("a.yaml"), "name: a\n", StandardCharsets.UTF_8);
        Javalin own = App.start(new AppConfig(0, null, tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + own.port() + "/tools/db_browser.html");
            wc.waitForBackgroundJavaScript(5000);
            org.htmlunit.html.HtmlSelect sel = (org.htmlunit.html.HtmlSelect) page.getElementById("profile");
            assertEquals("", sel.getSelectedOptions().get(0).getValueAttribute());
            assertEquals("(프로필 고르기)", sel.getSelectedOptions().get(0).getText());
            String conns = page.getElementById("conns").getTextContent();
            assertTrue(conns.contains("활성 프로필이 없다") && conns.contains("example.yaml"), conns);
            assertEquals("DB 스냅샷 · DTO 생성", page.getTitleText());
        } finally {
            own.stop();
        }
    }

    /** 4-6 — table_builder 「xlsx 저장」 → 경로 표시·파일 생김. 파일이 저장소 out/ 에 안 떨어지게 앱을 따로 띄운다 */
    @Test
    void tableBuilderSavesXlsx(@TempDir Path tmp) throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + own.port() + "/tools/table_builder.html");
            wc.waitForBackgroundJavaScript(3000);
            ((org.htmlunit.html.HtmlButton) page.getElementById("xlsxBtn")).click();
            wc.waitForBackgroundJavaScript(5000);
            String msg = page.getElementById("xlsxMsg").getTextContent();
            assertTrue(msg.startsWith("xlsx → "), msg);
            Path file = Path.of(msg.substring("xlsx → ".length()));
            assertTrue(file.startsWith(tmp.resolve("out/t")) && Files.size(file) > 0, msg);
        } finally {
            own.stop();
        }
    }

    /**
     * 4-14 — 엑셀 클립보드 text/html → 병합·정렬만 남긴 표(table_builder_ext.js). 픽스처 셋은 손으로 만든 알려진 꼴(실물은 사람 몫).
     * 병합 표는 importHtml 뒤 모델을 골든과, 병합 없는 표는 pasteGrid + 정렬 덧입힘 뒤 칸 정렬을 본다. 정리한 글에 class·font·span·mso- 가 없다
     */
    @Test
    void tableBuilderPastesExcelClipboard() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        Path fx = Path.of("src/test/resources/fixtures/table");
        String merge = json.writeValueAsString(Files.readString(fx.resolve("excel-merge.html"), StandardCharsets.UTF_8));
        String nomerge = json.writeValueAsString(Files.readString(fx.resolve("excel-nomerge.html"), StandardCharsets.UTF_8));
        String mso = json.writeValueAsString(Files.readString(fx.resolve("excel-msoignore.html"), StandardCharsets.UTF_8));
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/table_builder.html");
            wc.waitForBackgroundJavaScript(3000);
            com.fasterxml.jackson.databind.JsonNode m = json.readTree((String) page.executeJavaScript(
                    "JSON.stringify(tbClipboardTable(" + merge + "))").getJavaScriptResult());
            assertTrue(m.get("merged").asBoolean());
            assertEquals("[[\"center\",\"center\",\"center\",\"center\"],[\"center\",\"center\",\"center\",\"\"],[\"right\",\"right\",\"right\",\"\"]]",
                    m.get("aligns").toString());
            String model = (String) page.executeJavaScript("importHtml(" + json.writeValueAsString(m.get("html").asText())
                    + ") && JSON.stringify({ rows: G.rows, cols: G.cols, grid: G.grid, theadRows: G.theadRows })").getJavaScriptResult();
            kr.ejg.toolbox.GoldenFiles.assertJson("table/paste-merge.json", json.readTree(model));

            com.fasterxml.jackson.databind.JsonNode n = json.readTree((String) page.executeJavaScript(
                    "JSON.stringify(tbClipboardTable(" + nomerge + "))").getJavaScriptResult());
            assertFalse(n.get("merged").asBoolean());
            String aligns = (String) page.executeJavaScript("newTable(2, 2); sel = { r1: 0, c1: 0, r2: 0, c2: 0 };"
                    + " TB_PASTE_ALIGNS = { r: 0, c: 0, aligns: " + n.get("aligns") + " };"
                    + " pasteGrid([['이름', '점수'], ['홍길동', '90']]); tbApplyPastedAligns();"
                    + " JSON.stringify(G.grid.map(function (r) { return r.map(function (c) { return (c.align || '') + ':' + c.text; }); }))")
                    .getJavaScriptResult();
            assertEquals("[[\"center:이름\",\"right:점수\"],[\"left:홍길동\",\"right:90\"]]", aligns);

            com.fasterxml.jackson.databind.JsonNode o = json.readTree((String) page.executeJavaScript(
                    "JSON.stringify(tbClipboardTable(" + mso + "))").getJavaScriptResult());
            assertFalse(o.get("merged").asBoolean(), "mso-ignore:colspan 은 병합이 아니다");
            assertEquals("[[\"\",\"\",\"\"],[\"center\",\"center\",\"\"]]", o.get("aligns").toString());
            assertTrue(o.get("html").asText().startsWith("<table><tr><td>아주 긴 제목 글이 옆 칸으로 넘친다</td><td></td><td></td></tr>"),
                    o.get("html").asText());

            for (com.fasterxml.jackson.databind.JsonNode r : List.of(m, n, o)) {
                String h = r.get("html").asText();
                for (String bad : List.of("class=", "<font", "<span", "mso-")) {
                    assertFalse(h.contains(bad), bad + " 이 남았다: " + h);
                }
            }

            // 4-17 — 적대 픽스처: 셀 안 script·img onerror·이벤트 속성은 정리한 html 에도, 붙여 넣은 표에도 안 남고 실행되지 않는다. 꺾쇠 글자는 글자로
            String hostile = json.writeValueAsString(Files.readString(fx.resolve("excel-hostile.html"), StandardCharsets.UTF_8));
            String hh = json.readTree((String) page.executeJavaScript("JSON.stringify(tbClipboardTable(" + hostile + "))").getJavaScriptResult())
                    .get("html").asText();
            for (String bad : List.of("<script", "<img", "onerror", "onclick", "onmouseover")) {
                assertFalse(hh.toLowerCase(java.util.Locale.ROOT).contains(bad), bad + " 이 남았다: " + hh);
            }
            page.executeJavaScript("importHtml(" + json.writeValueAsString(hh) + ")");
            wc.waitForBackgroundJavaScript(1000);
            assertEquals(0, page.querySelectorAll("#grid script, #grid img").size(), "표에 script·img 0");
            assertEquals("undefined", String.valueOf(page.executeJavaScript("typeof window.TB_PWNED").getJavaScriptResult()), "실행된 것 없음");
            String cells = (String) page.executeJavaScript("JSON.stringify(G.grid.map(function (r) { return r.map(function (c) { return c.text; }); }))")
                    .getJavaScriptResult();
            assertTrue(cells.contains("&lt;b&gt;굵게&lt;/b&gt;"), "꺾쇠는 이스케이프한 글자로 남는다(셀 모델은 HTML 이스케이프 꼴): " + cells);
            assertEquals(0, page.querySelectorAll("#grid b").size(), "글자 <b> 가 태그로 안 풀린다");
            assertFalse(cells.contains("TB_PWNED"), "script 안 글이 셀 글자로 안 붙는다(4-18): " + cells);
        }
    }

    /**
     * PR #24 CodeQL·AI 리뷰 ④ — jsp_formatter 토크나이저의 태그 정규식이 병적 입력(`<a` + `=""` 반복, 닫는 `>` 없음)에서
     * 지수 백트래킹을 안 한다. 겹치던 옛 꼴은 40 회면 끝나지 않는다 — JS 시간 상한 5초가 걸리면 빨강.
     */
    @Test
    void jspFormatterTagRegexIsLinear() throws Exception {
        try (WebClient wc = client(true)) {
            wc.setJavaScriptTimeout(5000);
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/jsp_formatter.html");
            wc.waitForBackgroundJavaScript(3000);
            Object ms = page.executeJavaScript("(function () { var s = '<a'; for (var i = 0; i < 40; i++) s += '=\"\"';"
                    + " var t = Date.now(); tokenize(s); tokenize(s + ' b=\"x\">'); return Date.now() - t; })()").getJavaScriptResult();
            assertTrue(((Number) ms).doubleValue() < 2000, "tokenize 40회 반복 " + ms + "ms");
        }
    }

    /**
     * 4-4 — jsp_formatter 폴더 일괄: 폴더 검사 → 표 행 2 → 덮어쓰기 확인 거절(안 씀) → 수락 → 파일 바뀜(인코딩·줄바꿈 그대로)·백업.
     * 백업이 저장소 out/ 에 안 떨어지게 임시 프로필로 앱을 따로 띄운다.
     */
    @Test
    void jspFormatterFolderBatch(@TempDir Path tmp) throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Path web = tmp.resolve("webapp");
        Files.writeString(profiles.resolve("t.yaml"), "name: t\n"
                + "project:\n  root: '" + web + "'\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        Files.createDirectories(web.resolve("sub"));
        Charset ms949 = Charset.forName("MS949");
        byte[] aOrig = "<div>\r\n<ul>\r\n<li>하나</li>\r\n<li>둘</li>\r\n</ul>\r\n</div>\r\n".getBytes(ms949);
        Files.write(web.resolve("a.jsp"), aOrig);
        Files.writeString(web.resolve("sub/b.jsp"), "<table>\n<tr>\n<td>셀</td>\n</tr>\n</table>\n", StandardCharsets.UTF_8);
        Files.writeString(web.resolve("c.txt"), "glob 밖", StandardCharsets.UTF_8);
        Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + own.port() + "/tools/jsp_formatter.html");
            wc.waitForBackgroundJavaScript(3000);
            assertEquals(web.toString(), ((org.htmlunit.html.HtmlTextInput) page.getElementById("dir")).getValue(),
                    "폴더 칸 기본값은 프로필 프로젝트 루트(5-5)");
            ((org.htmlunit.html.HtmlElement) page.getElementById("tab-dir")).click();
            ((org.htmlunit.html.HtmlButton) page.getElementById("dirPreview")).click();
            wc.waitForBackgroundJavaScript(10000);
            String msg = page.getElementById("dirMsg").getTextContent();
            assertEquals(3, page.querySelectorAll("#dirTable tr").size(), msg + " / " + page.getElementById("dirTable").getTextContent());
            assertTrue(msg.contains("덮어쓸 대상 2개"), msg + " / " + page.getElementById("dirTable").getTextContent());
            assertEquals("", page.getElementById("cmp").getTextContent(), "폴더 검사는 붙여넣기 비교 칸을 안 쓴다(4-12)");
            // 4-19 — 머리 전체선택: 고를 수 있는 둘이 다 체크라 켜져 있다 → 끄면 0 → 다시 켜면 2
            assertTrue(((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dirAll")).isChecked(), "처음엔 둘 다 체크");
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dirAll")).click();
            assertTrue(page.getElementById("dirSum").getTextContent().endsWith("덮어쓸 대상 0"), page.getElementById("dirSum").getTextContent());
            assertTrue(((org.htmlunit.html.HtmlButton) page.getElementById("dirApply")).isDisabled(), "고른 것이 없으면 덮어쓰기 꺼짐");
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dirAll")).click();
            assertTrue(page.getElementById("dirSum").getTextContent().endsWith("덮어쓸 대상 2"), page.getElementById("dirSum").getTextContent());
            org.htmlunit.html.DomNode aRow = page.querySelectorAll("#dirTable tr").stream()
                    .filter(n -> n.getTextContent().contains("a.jsp")).findFirst().orElseThrow();
            ((org.htmlunit.html.HtmlElement) aRow).click();
            wc.waitForBackgroundJavaScript(5000);
            assertTrue(page.getElementById("dTitle").getTextContent().contains("a.jsp"), page.getElementById("dTitle").getTextContent());
            assertTrue(((org.htmlunit.html.HtmlTextArea) page.getElementById("dOrig")).getText().contains("<ul>"));
            assertTrue(((org.htmlunit.html.HtmlTextArea) page.getElementById("dOut")).getText().contains("\t<ul>"));
            assertFalse(page.getElementById("dRisk").getTextContent().isBlank());
            // 4-15 — 덮어쓰기 앞 확인(4-12). 거절하면 파일을 안 건드린다
            List<String> asked = new java.util.ArrayList<>();
            wc.setConfirmHandler((p, m) -> {
                asked.add(m);
                return false;
            });
            ((org.htmlunit.html.HtmlButton) page.getElementById("dirApply")).click();
            wc.waitForBackgroundJavaScript(5000);
            assertEquals(1, asked.size(), "덮어쓰기 앞에 확인을 묻는다");
            assertTrue(asked.get(0).contains("2개 파일을 덮어쓴다"), asked.get(0));
            assertTrue(java.util.Arrays.equals(aOrig, Files.readAllBytes(web.resolve("a.jsp"))), "확인을 거절하면 안 쓴다");
            wc.setConfirmHandler((p, m) -> true);
            ((org.htmlunit.html.HtmlButton) page.getElementById("dirApply")).click();
            wc.waitForBackgroundJavaScript(10000);
            msg = page.getElementById("dirMsg").getTextContent();
            assertTrue(msg.startsWith("덮어씀 2/2"), msg + " / " + page.getElementById("dirTable").getTextContent());
        } finally {
            own.stop();
        }
        String aText = new String(Files.readAllBytes(web.resolve("a.jsp")), ms949);
        assertTrue(aText.contains("\t<ul>\r\n"), "MS949·CRLF 그대로 들여쓰기: " + aText);
        assertTrue(Files.readString(web.resolve("sub/b.jsp"), StandardCharsets.UTF_8).contains("\t<tr>\n"));
        try (Stream<Path> s = Files.walk(tmp.resolve("out/t"))) {
            List<Path> backups = s.filter(Files::isRegularFile).toList();
            assertEquals(2, backups.size(), backups.toString());
            Path aBak = backups.stream().filter(p -> p.endsWith(Path.of("backup", "a.jsp"))).findFirst().orElseThrow();
            assertTrue(Arrays.equals(aOrig, Files.readAllBytes(aBak)), "백업은 원본 바이트");
        }
    }

    /** 7-7 — 산출물 화면 DDL 카드: H2 스냅샷 → 스냅샷 고르기 → 대상 PostgreSQL → 생성 → #ddlOut 에 CREATE TABLE. 1-17 라벨 「 · 거름」 */
    @Test
    void deliverableDdlCard(@TempDir Path tmp) throws Exception {
        try (java.sql.Connection h = java.sql.DriverManager.getConnection("jdbc:h2:mem:smoke77;DB_CLOSE_DELAY=-1", "sa", "pw");
                java.sql.Statement st = h.createStatement()) {
            st.execute("CREATE TABLE TB_DEPT (DEPT_NO INTEGER PRIMARY KEY, DEPT_NM VARCHAR(30))");
            Path profiles = tmp.resolve("profiles");
            Files.createDirectories(profiles);
            Files.writeString(profiles.resolve("t.yaml"), "name: t\n"
                    + "connections:\n  - id: h2\n    dialect: h2\n    url: jdbc:h2:mem:smoke77;DB_CLOSE_DELAY=-1\n    user: sa\n"
                    + "scope:\n  exclude:\n    prefixes: [TMP_]\n"
                    + "output:\n  dir: '" + tmp.resolve("out").toString().replace('\\', '/') + "'\n", StandardCharsets.UTF_8);
            Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
            try (WebClient wc = client(true)) {
                java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
                String base = "http://127.0.0.1:" + own.port();
                http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/conn/h2/password"))
                        .header("Content-Type", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"password\":\"pw\"}"))
                        .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/meta/snapshot"))
                        .header("Content-Type", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"connId\":\"h2\"}"))
                        .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                for (int i = 0; i < 100; i++) {
                    String l = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/meta/snapshots")).build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    if (l.contains("\"id\"")) {
                        break;
                    }
                    Thread.sleep(100);
                }
                HtmlPage page = wc.getPage(base + "/tools/deliverable_sql.html");
                wc.waitForBackgroundJavaScript(3000);
                org.htmlunit.html.HtmlSelect snap = (org.htmlunit.html.HtmlSelect) page.getElementById("snap");
                String label = snap.getOption(snap.getOptionSize() - 1).getText();
                assertTrue(label.startsWith("#") && label.contains(" · 테이블 1") && label.endsWith(" · 거름"),
                        "1-17 TB.snapLabel — exclude.prefixes 로 거른 스냅샷: " + label);
                snap.setSelectedAttribute(snap.getOption(snap.getOptionSize() - 1), true);
                ((org.htmlunit.html.HtmlSelect) page.getElementById("ddlTarget")).setSelectedAttribute("postgresql", true);
                ((org.htmlunit.html.HtmlButton) page.getElementById("ddlMake")).click();
                wc.waitForBackgroundJavaScript(5000);
                String out = ((org.htmlunit.html.HtmlTextArea) page.getElementById("ddlOut")).getText();
                assertTrue(out.contains("CREATE TABLE TB_DEPT ("), page.getElementById("ddlMsg").getTextContent() + "\n" + out);
                assertTrue(page.getElementById("ddlMsg").getTextContent().startsWith("표 "), page.getElementById("ddlMsg").getTextContent());
            } finally {
                own.stop();
            }
        }
    }

    /**
     * 7-4 — CRUD 생성기: H2 스냅샷 → 스냅샷 고르기 → 표 체크 → 생성 → 파일 열 → 행 미리보기 → 다시 생성하면 전부 .gen 옆에.
     */
    @Test
    void crudGeneratorRuns(@TempDir Path tmp) throws Exception {
        try (java.sql.Connection h = java.sql.DriverManager.getConnection("jdbc:h2:mem:smoke74;DB_CLOSE_DELAY=-1", "sa", "pw");
                java.sql.Statement st = h.createStatement()) {
            st.execute("CREATE TABLE TB_DEPT (DEPT_NO INTEGER PRIMARY KEY, DEPT_NM VARCHAR(30))");
            Path profiles = tmp.resolve("profiles");
            Files.createDirectories(profiles);
            AnalyzeRoutesTest.copy(Path.of("templates/gen"), tmp.resolve("templates/gen"));
            Path out = tmp.resolve("out");
            Files.createDirectories(out);
            Files.writeString(profiles.resolve("t.yaml"), "name: t\nlogicalName:\n  skipTokens: [TB]\n"
                    + "connections:\n  - id: h2\n    dialect: h2\n    url: jdbc:h2:mem:smoke74;DB_CLOSE_DELAY=-1\n    user: sa\n"
                    + "generator:\n  templateSet: egov5\n  basePackage: kr.go.smoke\n  outDir: '" + out.toString().replace('\\', '/') + "'\n",
                    StandardCharsets.UTF_8);
            Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
            try (WebClient wc = client(true)) {
                java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
                String base = "http://127.0.0.1:" + own.port();
                http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/conn/h2/password"))
                        .header("Content-Type", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"password\":\"pw\"}"))
                        .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/meta/snapshot"))
                        .header("Content-Type", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"connId\":\"h2\"}"))
                        .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                for (int i = 0; i < 100; i++) {
                    String l = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/meta/snapshots")).build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    if (l.contains("\"id\"")) {
                        break;
                    }
                    Thread.sleep(100);
                }
                HtmlPage page = wc.getPage(base + "/tools/crud_generator.html");
                wc.waitForBackgroundJavaScript(3000);
                assertEquals("kr.go.smoke", ((org.htmlunit.html.HtmlTextInput) page.getElementById("pkg")).getValue(), "프로필 기본값");
                org.htmlunit.html.HtmlSelect snap = (org.htmlunit.html.HtmlSelect) page.getElementById("snap");
                snap.setSelectedAttribute(snap.getOption(1), true);
                wc.waitForBackgroundJavaScript(5000);
                List<?> boxes = page.querySelectorAll("#tables tbody input[type=checkbox]");
                assertTrue(boxes.size() >= 1, page.getElementById("tables").getTextContent());
                // 7-13 — 머리 전체선택: 켜면 고를 수 있는 표 전부, 끄면 0. 찾기 줄에는 체크박스가 없다
                assertEquals(0, page.querySelectorAll(".opt input[type=checkbox]").size(), "찾기 줄의 체크박스는 머리로 옮겼다");
                ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("all")).click();
                wc.waitForBackgroundJavaScript(1000);
                int pickable = page.querySelectorAll("#tables tbody input[type=checkbox]:not([disabled])").size();
                assertTrue(page.getElementById("tCount").getTextContent().endsWith("고름 " + pickable), page.getElementById("tCount").getTextContent());
                ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("all")).click();
                wc.waitForBackgroundJavaScript(1000);
                assertTrue(page.getElementById("tCount").getTextContent().endsWith("고름 0"), page.getElementById("tCount").getTextContent());
                boxes = page.querySelectorAll("#tables tbody input[type=checkbox]");
                for (Object b : boxes) {
                    org.htmlunit.html.HtmlCheckBoxInput cb = (org.htmlunit.html.HtmlCheckBoxInput) b;
                    if ("TB_DEPT".equals(cb.getAttribute("title"))) {
                        cb.click();
                    }
                }
                ((org.htmlunit.html.HtmlButton) page.getElementById("run")).click();
                wc.waitForBackgroundJavaScript(15000);
                String m = page.getElementById("msg").getTextContent();
                List<?> rows = page.querySelectorAll("#files tbody tr");
                assertEquals(10, rows.size(), m);
                ((org.htmlunit.html.HtmlElement) rows.get(0)).click();
                wc.waitForBackgroundJavaScript(5000);
                assertTrue(page.getElementById("preview").getTextContent().contains("class "), page.getElementById("preview").getTextContent());
                ((org.htmlunit.html.HtmlButton) page.getElementById("run")).click();
                wc.waitForBackgroundJavaScript(15000);
                assertTrue(page.getElementById("files").getTextContent().contains("sidecar"), page.getElementById("msg").getTextContent());
            } finally {
                own.stop();
            }
        }
    }

    /**
     * 6-5 — 프로그램 분석: 폴더 칸 기본값(프로필 root) → 분석 → 프로그램 목록 13 → 행 상세 → CRUD 매트릭스 머리 → 미해결 → 이력 목록
     * → 영향도(6-6) → xlsx(6-7) → 정합성(6-12).
     */
    @Test
    void programAnalysisRuns(@TempDir Path tmp) throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Path proj = AnalyzeRoutesTest.project(tmp.resolve("proj"));
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nframework: egov35\nproject:\n  root: '" + proj + "'\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + own.port() + "/tools/program_analysis.html");
            wc.waitForBackgroundJavaScript(3000);
            assertEquals(proj.toString(), ((org.htmlunit.html.HtmlTextInput) page.getElementById("dir")).getValue());
            ((org.htmlunit.html.HtmlButton) page.getElementById("run")).click();
            wc.waitForBackgroundJavaScript(15000);
            String msg = page.getElementById("msg").getTextContent();
            List<?> rows = page.querySelectorAll("#programs tbody tr");
            assertEquals(13, rows.size(), msg);
            assertTrue(msg.contains("프로그램 13"), msg);
            ((org.htmlunit.html.HtmlElement) rows.get(0)).click();
            assertTrue(page.getElementById("detail").getTextContent().contains("문장"), page.getElementById("detail").getTextContent());
            ((org.htmlunit.html.HtmlElement) page.getElementById("tabCrud")).click();
            assertTrue(page.querySelectorAll("#crud thead th").size() >= 3, page.getElementById("crudCount").getTextContent());
            assertTrue(page.getElementById("crud").getTextContent().contains("COMTNBBS"), page.getElementById("crudCount").getTextContent());
            ((org.htmlunit.html.HtmlElement) page.getElementById("tabUnresolved")).click();
            assertTrue(page.querySelectorAll("#unresolved tbody tr").size() >= 1, page.getElementById("unCount").getTextContent());
            assertEquals(2, page.querySelectorAll("#runs option").size(), "빈 칸 + 이력 1");
            // 6-6 영향도 — 표 → 프로그램 → JSP
            ((org.htmlunit.html.HtmlElement) page.getElementById("tabImpact")).click();
            ((org.htmlunit.html.HtmlTextInput) page.getElementById("impTable")).setValue("COMTNBBS");
            ((org.htmlunit.html.HtmlButton) page.getElementById("impRun")).click();
            wc.waitForBackgroundJavaScript(5000);
            String im = page.getElementById("impMsg").getTextContent();
            assertTrue(page.querySelectorAll("#impact tbody tr").size() >= 1, im);
            assertTrue(page.getElementById("impJsps").getTextContent().contains("BoardList.jsp"), im);
            // 6-7 xlsx
            ((org.htmlunit.html.HtmlButton) page.getElementById("xlsx")).click();
            wc.waitForBackgroundJavaScript(5000);
            assertTrue(page.getElementById("msg").getTextContent().startsWith("xlsx"), page.getElementById("msg").getTextContent());
            // 6-12 정합성 — 스냅샷 없이: 안 불리는 문장·고아 JSP
            ((org.htmlunit.html.HtmlElement) page.getElementById("tabConsistency")).click();
            ((org.htmlunit.html.HtmlButton) page.getElementById("conRun")).click();
            wc.waitForBackgroundJavaScript(5000);
            String cm = page.getElementById("conMsg").getTextContent();
            assertTrue(page.querySelectorAll("#conDead tbody tr").size() >= 1, cm);
            assertTrue(page.querySelectorAll("#conOrphan tbody tr").size() >= 1, cm);
            assertTrue(cm.startsWith("안 불리는 문장 "), cm);
        } finally {
            own.stop();
        }
    }

    /**
     * 5-5 — 코드 검사: 폴더 칸 기본값 → 폴더 검사 → 결과 표 → 행 미리보기 → xlsx → 규칙 하나 끄고 프로필에 저장(주석 유지).
     */
    @Test
    void codeCheckFolderRun(@TempDir Path tmp) throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Path proj = tmp.resolve("proj");
        Files.createDirectories(proj.resolve("a"));
        Files.writeString(proj.resolve("a/A.java"), "/** 수정일 */\npackage a;\n\npublic class A {\n    void f() {\n        // TODO 지운다\n"
                + "        System.out.println(1);\n    }\n}\n", StandardCharsets.UTF_8);
        Path yaml = profiles.resolve("t.yaml");
        Files.writeString(yaml, "name: t\n# 사업 설명 주석은 남는다\nproject:\n  root: '" + proj + "'\n  encoding: UTF-8\n  lineEnding: LF\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n"
                + "codecheck:\n  # 묶음 주석\n  groups: { tsx: false }\n  rules: {}\n", StandardCharsets.UTF_8);
        Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + own.port() + "/tools/code_check.html");
            wc.waitForBackgroundJavaScript(3000);
            assertEquals(proj.toString(), ((org.htmlunit.html.HtmlTextInput) page.getElementById("dir")).getValue());
            assertTrue(!((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("g_tsx")).isChecked(), "프로필 묶음 끔");
            assertTrue(((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("changed")).isDisabled(), "형상 관리 폴더가 아니면 변경분만 비활성");
            assertTrue(page.getElementById("vcsInfo").getTextContent().contains(".git"), page.getElementById("vcsInfo").getTextContent());
            ((org.htmlunit.html.HtmlButton) page.getElementById("runDir")).click();
            wc.waitForBackgroundJavaScript(15000);
            String msg = page.getElementById("msg").getTextContent();
            List<?> rows = page.querySelectorAll("#result tbody tr");
            assertTrue(rows.size() >= 2, msg);
            assertTrue(page.getElementById("result").getTextContent().contains("common.sysout"), msg);
            ((org.htmlunit.html.HtmlElement) rows.get(0)).click();
            wc.waitForBackgroundJavaScript(5000);
            assertTrue(page.getElementById("preview").getTextContent().contains("a/A.java:"), page.getElementById("preview").getTextContent());
            ((org.htmlunit.html.HtmlButton) page.getElementById("xlsx")).click();
            wc.waitForBackgroundJavaScript(5000);
            assertTrue(page.getElementById("msg").getTextContent().startsWith("xlsx"), page.getElementById("msg").getTextContent());
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("r_common.todo")).setChecked(false);
            ((org.htmlunit.html.HtmlButton) page.getElementById("saveRules")).click();
            wc.waitForBackgroundJavaScript(5000);
            assertTrue(page.getElementById("ruleMsg").getTextContent().contains("저장"), page.getElementById("ruleMsg").getTextContent());
            // 5-6b — git 작업 사본이 되면 폴더 칸 change 로 「변경분만」 이 켜지고 변경 수가 보인다
            if (kr.ejg.toolbox.core.vcs.Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, proj)) {
                Process g = new ProcessBuilder("git", "init", "-q").directory(proj.toFile()).redirectErrorStream(true).start();
                g.getOutputStream().close();
                g.getInputStream().readAllBytes();
                assertEquals(0, g.waitFor());
                ((org.htmlunit.html.HtmlTextInput) page.getElementById("dir")).fireEvent("change");
                wc.waitForBackgroundJavaScript(5000);
                assertTrue(!((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("changed")).isDisabled(),
                        page.getElementById("vcsInfo").getTextContent());
                assertTrue(page.getElementById("vcsInfo").getTextContent().startsWith("git · 변경 "), page.getElementById("vcsInfo").getTextContent());
                // 5-8 — 두 커밋 뒤 배포 목록 탭: 탭 전환 → 부터·까지 → 조회 → 표 행
                for (String[] g2 : new String[][] {{"add", "-A"}, {"commit", "-q", "-m", "c1"}}) {
                    java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of("git", "-c", "user.name=t", "-c", "user.email=t@t"));
                    cmd.addAll(java.util.List.of(g2));
                    Process p = new ProcessBuilder(cmd).directory(proj.toFile()).redirectErrorStream(true).start();
                    p.getOutputStream().close();
                    p.getInputStream().readAllBytes();
                    assertEquals(0, p.waitFor(), String.join(" ", cmd));
                }
                Files.writeString(proj.resolve("a/B.java"), "package a;\nclass B {}\n", StandardCharsets.UTF_8);
                for (String[] g2 : new String[][] {{"add", "-A"}, {"commit", "-q", "-m", "c2"}}) {
                    java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of("git", "-c", "user.name=t", "-c", "user.email=t@t"));
                    cmd.addAll(java.util.List.of(g2));
                    Process p = new ProcessBuilder(cmd).directory(proj.toFile()).redirectErrorStream(true).start();
                    p.getOutputStream().close();
                    p.getInputStream().readAllBytes();
                    assertEquals(0, p.waitFor(), String.join(" ", cmd));
                }
                page.getElementById("tabDeploy").click();
                assertEquals("flex", page.getElementById("paneDeploy").getAttribute("style").replaceAll(".*display:\s*([a-z]+).*", "$1"));
                ((org.htmlunit.html.HtmlTextInput) page.getElementById("depFrom")).setValue("HEAD~1");
                ((org.htmlunit.html.HtmlButton) page.getElementById("depRun")).click();
                wc.waitForBackgroundJavaScript(10000);
                assertEquals(1, page.querySelectorAll("#depResult tbody tr").size(), page.getElementById("depMsg").getTextContent());
                assertTrue(page.getElementById("depCount").getTextContent().startsWith("추가 1"), page.getElementById("depCount").getTextContent());
            }
        } finally {
            own.stop();
        }
        String saved = Files.readString(yaml, StandardCharsets.UTF_8);
        assertTrue(saved.contains("# 사업 설명 주석은 남는다") && saved.contains("# 묶음 주석"), saved);
        assertTrue(saved.contains("\"common.todo\":false"), saved);
        try (Stream<Path> s = Files.walk(tmp.resolve("out/t"))) {
            assertTrue(s.anyMatch(p -> p.getFileName().toString().endsWith(".xlsx")));
        }
    }
}
