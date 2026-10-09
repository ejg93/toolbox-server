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

    /** 1-35 — HtmlUnit 에는 navigator.clipboard 가 없다. 심어서 TB.copy 가 넣은 글을 window.__copied 로 받는다 */
    private static void stubClipboard(HtmlPage page) {
        page.executeJavaScript("navigator.clipboard = { writeText: function (t) { window.__copied = t; return Promise.resolve(); } };");
    }

    private static String js(HtmlPage page, String expr) {
        return String.valueOf(page.executeJavaScript(expr).getJavaScriptResult());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "index", "db_browser", "dev_tools", "jsp_formatter", "sql_snippets", "table_builder",
        "logical_name", "deliverable_sql", "special_chars", "code_check", "program_analysis", "spring_source_generator"
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

    /**
     * 3-13 — 컬럼 목록은 스냅샷·CSV 파일 둘 중 하나. CSV 카드를 고르면 스냅샷 칸이 꺼지고 DB 유형 칸이 CSV 카드로 온다.
     * 파일을 고르면 숨은 #csv 에 담겨 변환된다
     */
    @Test
    void logicalNameRunsFromCsvFile(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("cols.csv");
        Files.writeString(csv, "OWNER,TABLE_NAME,COLUMN_NAME,DATA_TYPE" + (char) 10 + "S,TB_USE,USE_YN,CHAR" + (char) 10 + "S,TB_USE,QWZX_CD,VARCHAR" + (char) 10, StandardCharsets.UTF_8);
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/logical_name.html");
            wc.waitForBackgroundJavaScript(5000);
            ((org.htmlunit.html.HtmlRadioButtonInput) page.getElementById("srcCsv")).click();
            wc.waitForBackgroundJavaScript(500);
            assertTrue(page.getElementById("optCsv").getAttribute("class").contains("on"), page.getElementById("optCsv").getAttribute("class"));
            assertFalse(page.getElementById("optSnap").getAttribute("class").contains("on"), page.getElementById("optSnap").getAttribute("class"));
            assertTrue(((org.htmlunit.html.HtmlSelect) page.getElementById("snap")).isDisabled());
            assertEquals("optCsv", js(page, "document.getElementById('dialectRow').parentNode.id"));
            assertEquals("false", js(page, "document.getElementById('dialectRow').hidden"));
            org.htmlunit.html.HtmlFileInput file = (org.htmlunit.html.HtmlFileInput) page.getElementById("csvFile");
            file.setFiles(csv.toFile());
            file.fireEvent("change");
            wc.waitForBackgroundJavaScript(3000);
            String read = page.getElementById("csvMsg").getTextContent();
            assertTrue(read.startsWith("cols.csv · ") && read.contains("2행"), read);
            ((org.htmlunit.html.HtmlButton) page.getElementById("run")).click();
            wc.waitForBackgroundJavaScript(5000);
            String msg = page.getElementById("runMsg").getTextContent();
            assertTrue(msg.startsWith("컬럼 2 · 테이블 1"), msg);
            assertTrue(page.getElementById("rank").getTextContent().contains("QWZX"), page.getElementById("rank").getTextContent());
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
            // 1-35 — 복사: 칸이 선택되고 「복사됨」
            stubClipboard(page);
            ((org.htmlunit.html.HtmlButton) page.getElementById("maskCopy")).click();
            wc.waitForBackgroundJavaScript(2000);
            assertEquals(sql, js(page, "window.__copied"));
            assertEquals("maskSql", js(page, "document.activeElement.id"));
            assertEquals("복사됨", page.getElementById("maskMsg").getTextContent());
            // 3-13 — 머리 체크: 하나가 풀린 채 누르면 전부 켬, 다시 누르면 전부 끔. 머리 체크는 제외 수에 안 든다
            org.htmlunit.html.HtmlCheckBoxInput all = (org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("maskAll");
            all.click();
            for (Object b : page.querySelectorAll("#maskTbl tbody input[type=checkbox]")) {
                assertTrue(((org.htmlunit.html.HtmlCheckBoxInput) b).isChecked(), "전체 선택");
            }
            all.click();
            for (Object b : page.querySelectorAll("#maskTbl tbody input[type=checkbox]")) {
                assertFalse(((org.htmlunit.html.HtmlCheckBoxInput) b).isChecked(), "전체 해제");
            }
            ((org.htmlunit.html.HtmlButton) page.getElementById("maskMake")).click();
            wc.waitForBackgroundJavaScript(5000);
            assertTrue(page.getElementById("maskMsg").getTextContent().contains("제외 3"), page.getElementById("maskMsg").getTextContent());
        }
    }

    /** 1-9 — DB 브라우저의 DTO 카드: DDL 붙여넣기 → 소스 미리보기(JS 켠 채) */
    @Test
    void dbBrowserMakesDtoFromDdl() throws Exception {
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/db_browser.html");
            wc.waitForBackgroundJavaScript(5000);
            ((org.htmlunit.html.HtmlElement) page.getElementById("dtoTabDdl")).click();
            ((org.htmlunit.html.HtmlTextArea) page.getElementById("dtoDdl")).setText("CREATE TABLE T_ITEM (ITEM_ID INT PRIMARY KEY, ITEM_NM VARCHAR(50))");
            ((org.htmlunit.html.HtmlButton) page.getElementById("dtoFromDdl")).click();
            wc.waitForBackgroundJavaScript(5000);
            String out = ((org.htmlunit.html.HtmlTextArea) page.getElementById("dtoOut")).getText();
            assertTrue(out.contains("public record TItem("), out + " / " + page.getElementById("dtoMsg").getTextContent());
            assertTrue(out.contains("String itemNm"), out);
            // 1-35 — 복사: 결과 칸 전체가 선택되고 「복사됨」
            stubClipboard(page);
            ((org.htmlunit.html.HtmlButton) page.getElementById("dtoCopy")).click();
            wc.waitForBackgroundJavaScript(2000);
            assertEquals(out, js(page, "window.__copied"));
            assertEquals("0," + js(page, "document.getElementById('dtoOut').value.length"),
                    js(page, "var o = document.getElementById('dtoOut'); o.selectionStart + ',' + o.selectionEnd"));
            assertEquals("복사됨", page.getElementById("dtoMsg").getTextContent());
            // 1-31c — 검증 어노테이션은 기본 켬, 끄고 다시 만들면 없다
            assertTrue(out.contains("@Size(max = 50)"), out);
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dtoValid")).setChecked(false);
            ((org.htmlunit.html.HtmlButton) page.getElementById("dtoFromDdl")).click();
            wc.waitForBackgroundJavaScript(5000);
            String off = ((org.htmlunit.html.HtmlTextArea) page.getElementById("dtoOut")).getText();
            assertTrue(off.contains("public record TItem(") && !off.contains("@Size"), off);
        }
    }

    /**
     * 1-32 — DTO 카드 탭 둘은 결과 칸이 따로라 서로 안 덮는다. 방언 설명은 「CREATE 문에서」 탭에만.
     * 스모크는 CSS 끔이라 isDisplayed() 가 늘 참 — 숨김은 style 속성으로 잰다
     */
    @Test
    void dbBrowserDtoTabsKeepOwnResult() throws Exception {
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/db_browser.html");
            wc.waitForBackgroundJavaScript(5000);
            ((org.htmlunit.html.HtmlElement) page.getElementById("dtoTabDdl")).click();
            ((org.htmlunit.html.HtmlTextArea) page.getElementById("dtoDdl")).setText("CREATE TABLE T_ITEM (ITEM_ID INT PRIMARY KEY, ITEM_NM VARCHAR(50))");
            ((org.htmlunit.html.HtmlButton) page.getElementById("dtoFromDdl")).click();
            wc.waitForBackgroundJavaScript(5000);
            assertTrue(((org.htmlunit.html.HtmlTextArea) page.getElementById("dtoOut")).getText().contains("public record TItem("));

            ((org.htmlunit.html.HtmlElement) page.getElementById("dtoTabSnap")).click();
            assertEquals("", ((org.htmlunit.html.HtmlTextArea) page.getElementById("dtoSnapOut")).getText(), "다른 탭 결과가 안 옮겨 온다");
            assertTrue(page.getElementById("dtoPaneDdl").getAttribute("style").contains("none"));
            assertFalse(page.getElementById("dtoPaneSnap").getAttribute("style").contains("none"));

            ((org.htmlunit.html.HtmlElement) page.getElementById("dtoTabDdl")).click();
            assertTrue(((org.htmlunit.html.HtmlTextArea) page.getElementById("dtoOut")).getText().contains("public record TItem("), "탭을 오가도 그대로");
            assertTrue(page.getElementById("dtoPaneDdl").getTextContent().contains("LocalDateTime"));
            assertFalse(page.getElementById("dtoPaneSnap").getTextContent().contains("LocalDateTime"), "방언 설명은 CREATE 탭에만");
        }
    }

    /** 1-32 — 저장은 보이는 결과를 만든 요청 그대로. 만든 뒤 붙여넣기를 바꿔도 저장물은 화면 결과. 파일이 저장소 out/ 에 안 떨어지게 앱을 따로 */
    @Test
    void dbBrowserDtoSaveUsesShownResult(@TempDir Path tmp) throws Exception {
        Path profiles = Files.createDirectories(tmp.resolve("profiles"));
        Files.writeString(profiles.resolve("t.yaml"), "name: t\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + own.port() + "/tools/db_browser.html");
            wc.waitForBackgroundJavaScript(5000);
            ((org.htmlunit.html.HtmlElement) page.getElementById("dtoTabDdl")).click();
            org.htmlunit.html.HtmlTextArea ddl = (org.htmlunit.html.HtmlTextArea) page.getElementById("dtoDdl");
            ddl.setText("CREATE TABLE T_ITEM (ITEM_ID INT)");
            ((org.htmlunit.html.HtmlButton) page.getElementById("dtoFromDdl")).click();
            wc.waitForBackgroundJavaScript(5000);
            ddl.setText("CREATE TABLE T_OTHER (X INT)");
            ((org.htmlunit.html.HtmlButton) page.getElementById("dtoSave")).click();
            wc.waitForBackgroundJavaScript(5000);
            String msg = page.getElementById("dtoMsg").getTextContent();
            assertTrue(msg.contains("TItem.java") && !msg.contains("TOther"), msg);
            Path file = Path.of(msg.substring(msg.indexOf("저장 ") + 3));
            assertTrue(file.startsWith(tmp.resolve("out/t")) && Files.exists(file), msg);
        } finally {
            own.stop();
        }
    }

    /** 1-40 — 직접 동작하는 버튼 넷만 btn-p(찍기·테이블로 만들기·CREATE 문으로 만들기·비교) */
    @Test
    void dbBrowserActionButtonsArePrimary() throws Exception {
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/db_browser.html");
            wc.waitForBackgroundJavaScript(5000);
            for (String id : List.of("snapTake", "dtoTable", "dtoFromDdl", "diffRun")) {
                assertTrue(page.getElementById(id).getAttribute("class").contains("btn-p"), id);
            }
            for (String id : List.of("connTest", "snapStop", "dtoSave", "dtoCopy")) {
                assertFalse(page.getElementById(id).getAttribute("class").contains("btn-p"), id);
            }
        }
    }

    /** 2-5 — 산출물 화면: 문서 체크 11 + SQL 가이드가 방언을 바꾸면 다시 그린다(JS 켠 채) */
    @Test
    void deliverableGuideSwitchesDialect() throws Exception {
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/deliverable_sql.html");
            wc.waitForBackgroundJavaScript(5000);
            assertEquals(12, page.querySelectorAll("#docChecks input").size(), "01~11 + 18(2-18)");
            // 1-34 — 문서마다 판정 기호와 근거(title)
            List<?> marks = page.querySelectorAll("#docChecks .mark");
            assertEquals(12, marks.size());
            for (Object o : marks) {
                org.htmlunit.html.HtmlElement m = (org.htmlunit.html.HtmlElement) o;
                String no = ((org.htmlunit.html.HtmlElement) ((org.htmlunit.html.HtmlElement) m.getParentNode()).querySelector("input")).getAttribute("data-no");
                assertTrue(m.getTextContent().matches("[●◐○]") && !m.getAttribute("title").isBlank(), no + " " + m.asXml());
                assertTrue(no.equals("18") || m.getAttribute("title").contains("자동"), no + " " + m.getAttribute("title"));
                if (no.equals("02")) {
                    assertEquals("●", m.getTextContent());
                }
                if (no.equals("05")) {
                    assertEquals("◐", m.getTextContent());
                }
            }
            ((org.htmlunit.html.HtmlSelect) page.getElementById("guideDoc")).setSelectedAttribute("d02", true);
            ((org.htmlunit.html.HtmlSelect) page.getElementById("dialect")).setSelectedAttribute("pg", true);
            wc.waitForBackgroundJavaScript(2000);
            String g = page.getElementById("guide").getTextContent();
            assertTrue(g.contains("ROW_NUMBER() OVER"), g.substring(0, Math.min(300, g.length())));
            assertTrue(g.contains("'__스키마_미입력__'") && !g.contains("__SCHEMAS__"), "치환");
            // 1-36 — SQL 은 복사만. 실행 버튼이 없다
            List<String> buttons = new java.util.ArrayList<>();
            for (Object o : page.querySelectorAll("#guide button")) {
                buttons.add(((org.htmlunit.html.HtmlElement) o).getTextContent());
            }
            assertTrue(!buttons.isEmpty() && buttons.stream().allMatch("복사"::equals), buttons.toString());
            // 1-35 — 가이드 복사: 그 SQL(pre)이 선택되고 아래 칸에 「복사됨」
            stubClipboard(page);
            ((org.htmlunit.html.HtmlElement) page.querySelectorAll("#guide button").get(0)).click();
            wc.waitForBackgroundJavaScript(2000);
            String firstSql = ((org.htmlunit.html.HtmlElement) page.querySelectorAll("#guide pre.sql").get(0)).getTextContent();
            assertEquals(firstSql, js(page, "window.__copied"));
            assertEquals(firstSql, js(page, "window.getSelection().toString()"));
            assertEquals("복사됨", ((org.htmlunit.html.HtmlElement) page.querySelectorAll("#guide .msg").get(0)).getTextContent());
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
            // 1-45 — 비밀번호는 프로필 password 칸에서 읽는다. 화면 입력은 없고, 없는 접속에 넣을 자리를 알린다
            assertEquals(null, page.getElementById("pw"));
            assertEquals(null, page.getElementById("pwSave"));
            assertTrue(page.getElementById("conns").getTextContent().contains("비밀번호 없음"), page.getElementById("conns").getTextContent());
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

    /** 0-44 — 저장 알림 한 꼴: 파일 하나면 이름까지 전체 경로, 여럿이면 폴더 */
    @Test
    void savedTextShowsFileOrFolder() throws Exception {
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/index.html");
            wc.waitForBackgroundJavaScript(3000);
            assertEquals("저장 C:\\o\\p\\a.sql", page.executeJavaScript("TB.savedText(['C:\\\\o\\\\p\\\\a.sql'])").getJavaScriptResult());
            assertEquals("저장 2개 — C:\\o\\p", page.executeJavaScript("TB.savedText(['C:\\\\o\\\\p\\\\a.sql', 'C:\\\\o\\\\p\\\\b.sql'])").getJavaScriptResult());
            assertEquals("C:\\o\\dto\\A.java", page.executeJavaScript("TB.joinPath('C:\\\\o\\\\dto\\\\', 'A.java')").getJavaScriptResult());
            assertEquals("/o/gen/x/A.java", page.executeJavaScript("TB.joinPath('/o/gen', 'x/A.java')").getJavaScriptResult());
            assertEquals("", page.executeJavaScript("TB.savedText([])").getJavaScriptResult());
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
        // 1-38 — 위험 있는 파일(인라인 사이 공백이 바뀐다). 위험 있는 것은 처음에 체크가 꺼져 있어 뒤 단언(덮어씀 2/2)은 그대로
        Files.writeString(web.resolve("d.jsp"), "<div><span>가</span><span>나</span></div>\n", StandardCharsets.UTF_8);
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
            assertEquals(4, page.querySelectorAll("#dirTable tr").size(), msg + " / " + page.getElementById("dirTable").getTextContent());
            assertTrue(msg.contains("덮어쓸 대상 2개"), msg + " / " + page.getElementById("dirTable").getTextContent());
            assertEquals("", page.getElementById("cmp").getTextContent(), "폴더 검사는 붙여넣기 비교 칸을 안 쓴다(4-12)");
            // 4-19 — 머리 전체선택: 위험 있는 d.jsp 는 처음에 꺼져 있어 중간 상태 → 누르면 셋 다 → 끄면 0 → 다시 켜면 3
            assertFalse(((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dirAll")).isChecked(), "d.jsp 가 꺼져 있어 전부는 아니다");
            assertEquals(Boolean.TRUE, page.executeJavaScript("document.getElementById('dirAll').indeterminate").getJavaScriptResult(), "일부만 — 중간 상태");
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dirAll")).click();
            assertTrue(page.getElementById("dirSum").getTextContent().endsWith("덮어쓸 대상 3"), page.getElementById("dirSum").getTextContent());
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dirAll")).click();
            assertTrue(page.getElementById("dirSum").getTextContent().endsWith("덮어쓸 대상 0"), page.getElementById("dirSum").getTextContent());
            assertTrue(((org.htmlunit.html.HtmlButton) page.getElementById("dirApply")).isDisabled(), "고른 것이 없으면 덮어쓰기 꺼짐");
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dirAll")).click();
            assertTrue(page.getElementById("dirSum").getTextContent().endsWith("덮어쓸 대상 3"), page.getElementById("dirSum").getTextContent());
            // 1-38 — 「위험 있는 것만」 거름 뒤엔 보이는 행(d.jsp)에만 머리가 적용된다. 숨은 a·b 는 체크가 남는다
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dirOnlyRisk")).click();
            assertEquals(2, page.querySelectorAll("#dirTable tr").size(), "머리 + 위험 있는 d.jsp — " + page.getElementById("dirTable").getTextContent());
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dirAll")).click();
            assertTrue(page.getElementById("dirSum").getTextContent().endsWith("덮어쓸 대상 2"), "숨은 a·b 는 안 꺼진다 — " + page.getElementById("dirSum").getTextContent());
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("dirOnlyRisk")).click();
            assertEquals(4, page.querySelectorAll("#dirTable tr").size(), page.getElementById("dirTable").getTextContent());
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
            // 0-44 — 여러 파일이라 백업 폴더 전체 경로(stamp 글자가 아니라)
            assertTrue(msg.contains("백업 " + tmp.resolve("out").resolve("t").toAbsolutePath()) && msg.endsWith("backup"), msg);
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

    /**
     * 3-12 — 산출물(05)을 만들면 미등록 약어 링크가 뜨고, 그 링크(logical_name.html?snapshot=&scope=deliverable)로 열면
     * 그 스냅샷·산출물 범위가 골라진 채 바로 변환한다
     */
    @Test
    void logicalNameOpensFromDeliverable(@TempDir Path tmp) throws Exception {
        try (java.sql.Connection h = java.sql.DriverManager.getConnection("jdbc:h2:mem:smoke312;DB_CLOSE_DELAY=-1", "sa", "pw");
                java.sql.Statement st = h.createStatement()) {
            st.execute("CREATE TABLE TB_ZZQX (ZZQX_CD VARCHAR(5), USE_YN CHAR(1))");
            Path profiles = tmp.resolve("profiles");
            Files.createDirectories(profiles);
            Files.writeString(profiles.resolve("t.yaml"), "name: t\n"
                    + "connections:\n  - id: h2\n    dialect: h2\n    url: jdbc:h2:mem:smoke312;DB_CLOSE_DELAY=-1\n    user: sa\n"
                    + "deliverable:\n  filter:\n    exclude: { prefixes: [ZZ_] }\n" // 3-15 — 체크가 켜진 채 시작하려면 filter 가 있어야 한다(TB_ZZQX 는 안 걸린다)
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
                String id = null;
                for (int i = 0; i < 100 && id == null; i++) {
                    com.fasterxml.jackson.databind.JsonNode l = new com.fasterxml.jackson.databind.ObjectMapper().readTree(http.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/meta/snapshots")).build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body());
                    if (l.size() > 0) {
                        id = l.get(0).get("id").asText();
                    } else {
                        Thread.sleep(100);
                    }
                }
                // 산출물 화면 — 05 만 만들면 미등록 약어(ZZQX) 링크
                HtmlPage page = wc.getPage(base + "/tools/deliverable_sql.html");
                wc.waitForBackgroundJavaScript(3000);
                assertEquals("산출물 범위: 제외 접두 ZZ_ — 표 1 → 1", page.getElementById("scopeMsg").getTextContent()); // 3-15 — 만들기 전에도
                assertEquals("scope-line on", page.getElementById("scopeMsg").getAttribute("class")); // 3-16 — 상자 줄, 거름
                assertEquals("산출물 범위", page.querySelector("#scopeMsg b").getTextContent());
                for (Object o : page.querySelectorAll("#docChecks input")) {
                    org.htmlunit.html.HtmlCheckBoxInput c = (org.htmlunit.html.HtmlCheckBoxInput) o;
                    c.setChecked("05".equals(c.getAttribute("data-no")));
                }
                ((org.htmlunit.html.HtmlButton) page.getElementById("build")).click();
                String done = "";
                for (int i = 0; i < 200 && !done.startsWith("완료"); i++) {
                    wc.waitForBackgroundJavaScript(100);
                    done = page.getElementById("buildMsg").getTextContent();
                }
                assertTrue(done.startsWith("완료") && done.contains("미등록 약어 "), done);
                List<?> links = page.querySelectorAll("#buildLink a");
                assertEquals(1, links.size(), page.getElementById("buildLink").getTextContent());
                org.htmlunit.html.HtmlAnchor a = (org.htmlunit.html.HtmlAnchor) links.get(0);
                assertEquals("logical_name.html?snapshot=" + id + "&scope=deliverable", a.getHrefAttribute());
                assertTrue(a.getTextContent().startsWith("미등록 약어 ") && a.getTextContent().contains("표준 사전 · 논리명"), a.getTextContent());
                // 링크로 연 표준 사전 · 논리명 화면 — 그 스냅샷 · 산출물 범위 · 바로 변환
                HtmlPage ln = wc.getPage(base + "/tools/" + a.getHrefAttribute());
                wc.waitForBackgroundJavaScript(5000);
                assertEquals(id, ((org.htmlunit.html.HtmlSelect) ln.getElementById("snap")).getSelectedOptions().get(0).getValueAttribute());
                assertTrue(((org.htmlunit.html.HtmlCheckBoxInput) ln.getElementById("delivScope")).isChecked());
                assertFalse(((org.htmlunit.html.HtmlCheckBoxInput) ln.getElementById("delivScope")).isDisabled());
                assertEquals("산출물 범위: 제외 접두 ZZ_", ln.getElementById("delivScopeMsg").getTextContent());
                assertEquals("scope-line on", ln.getElementById("delivScopeMsg").getAttribute("class"));
                // 3-13 — 스냅샷 카드가 골라진다. H2 는 DB 버전 글로 DB 유형을 못 정해 스냅샷 카드에 고르기 칸이 뜬다
                assertTrue(ln.getElementById("optSnap").getAttribute("class").contains("on"), ln.getElementById("optSnap").getAttribute("class"));
                assertEquals("optSnap", js(ln, "document.getElementById('dialectRow').parentNode.id"));
                assertEquals("false", js(ln, "document.getElementById('dialectRow').hidden"));
                assertTrue(ln.getElementById("snapDb").getTextContent().contains("못 정했다"), ln.getElementById("snapDb").getTextContent());
                String run = ln.getElementById("runMsg").getTextContent();
                assertTrue(run.startsWith("산출물 범위 · 컬럼 "), run);
                assertTrue(ln.getElementById("rank").getTextContent().contains("ZZQX"), ln.getElementById("rank").getTextContent());
            } finally {
                own.stop();
            }
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
                // 1-35 — DDL 복사: 「복사했다」 가 아니라 「복사됨」
                stubClipboard(page);
                ((org.htmlunit.html.HtmlButton) page.getElementById("ddlCopy")).click();
                wc.waitForBackgroundJavaScript(2000);
                assertEquals(out, js(page, "window.__copied"));
                assertEquals("ddlOut", js(page, "document.activeElement.id"));
                assertEquals("복사됨", page.getElementById("ddlMsg").getTextContent());
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
                HtmlPage page = wc.getPage(base + "/tools/spring_source_generator.html");
                wc.waitForBackgroundJavaScript(3000);
                assertEquals("Table → Spring 소스 생성", page.getTitleText(), "7-14 화면 이름");
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
                // 1-38 — 찾기로 숨어도 고른 표는 sel 에 남는다(7-13)
                page.executeJavaScript("var f=document.getElementById('fT'); f.value='zzz_none'; f.oninput();");
                assertEquals(0, page.querySelectorAll("#tables tbody input[type=checkbox]").size(), page.getElementById("tables").getTextContent());
                assertTrue(page.getElementById("tCount").getTextContent().endsWith("고름 1"), "숨어도 고름 유지 — " + page.getElementById("tCount").getTextContent());
                page.executeJavaScript("var f=document.getElementById('fT'); f.value=''; f.oninput();");
                assertTrue(page.getElementById("tCount").getTextContent().endsWith("고름 1"), page.getElementById("tCount").getTextContent());
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
        // 6-19 — add.do 가 COMTNBBS 를 INSERT(Board.insert)하고 UPDATE(Login.updateIncorrectGNR)도 해 한 칸에 낱말 둘
        Path login = proj.resolve("src/main/resources/mapper/Login_SQL.xml");
        Files.writeString(login, Files.readString(login, StandardCharsets.UTF_8).replace("UPDATE COMTNGNR", "UPDATE COMTNBBS"),
                StandardCharsets.UTF_8);
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
            // 6-18 — 표시 이름: CRUD 는 낱말, 「뷰」 → view, view: → jsp:, 찾은 방법은 사람 말, 종류 view → page
            StringBuilder details = new StringBuilder();
            for (Object row : rows) {
                ((org.htmlunit.html.HtmlElement) row).click();
                details.append(page.getElementById("detail").getTextContent()).append('\n');
            }
            String all = details.toString();
            assertTrue(all.contains("\nview\n") && all.contains("  jsp: ") && all.contains("(문자열 그대로)"), all);
            assertTrue(all.matches("(?s).*\\n  [A-Z_]+  (Create|Read|Update|Delete)( · (Create|Read|Update|Delete))*\\n.*"), all);
            assertFalse(all.contains("(literal)") || all.contains("\n뷰\n") || all.contains("view:"), all);
            // C→R→U→D 순(PR #47 리뷰) — 낱말 줄마다 순서가 어긋나면 빨강
            java.util.regex.Matcher crudLine = java.util.regex.Pattern
                    .compile("\n  [A-Z_]+  ((?:Create|Read|Update|Delete)(?: · (?:Create|Read|Update|Delete))*)(?=\n)").matcher(all);
            List<String> order = List.of("Create", "Read", "Update", "Delete");
            int crudLines = 0;
            while (crudLine.find()) {
                List<String> ws = List.of(crudLine.group(1).split(" · "));
                assertEquals(ws.stream().sorted(java.util.Comparator.comparingInt(order::indexOf)).toList(), ws, crudLine.group());
                crudLines++;
            }
            assertTrue(crudLines > 0, all);
            assertTrue(all.contains("\n  COMTNBBS  Create · Update\n"), "한 칸 낱말 둘 — " + all);
            String progs = page.getElementById("programs").getTextContent();
            assertTrue(progs.contains("view") && progs.contains("page") && !progs.contains("뷰"), progs);
            ((org.htmlunit.html.HtmlElement) page.getElementById("tabCrud")).click();
            assertEquals("C=Create · R=Read · U=Update · D=Delete", page.getElementById("crudLegend").getTextContent());
            assertTrue(page.querySelectorAll("#crud thead th").size() >= 3, page.getElementById("crudCount").getTextContent());
            assertTrue(page.getElementById("crud").getTextContent().contains("COMTNBBS"), page.getElementById("crudCount").getTextContent());
            // 6-21 — 기본은 모듈 매트릭스(행 = 표, 열 = 모듈). 프로그램 거르기는 잠김. 칸을 누르면 아래에 그 모듈·표의 프로그램
            assertTrue(((org.htmlunit.html.HtmlTextInput) page.getElementById("fProg")).isDisabled());
            List<?> heads = page.querySelectorAll("#crud thead th");
            assertEquals("표", ((org.htmlunit.html.HtmlElement) heads.get(0)).getTextContent());
            StringBuilder hs = new StringBuilder();
            for (Object h : heads) {
                hs.append(((org.htmlunit.html.HtmlElement) h).getTextContent()).append('|');
            }
            assertTrue(hs.toString().contains("|bbs|"), hs.toString());
            org.htmlunit.html.HtmlElement readCell = null;
            for (Object o : page.querySelectorAll("#crud tbody tr")) {
                org.htmlunit.html.HtmlElement tr = (org.htmlunit.html.HtmlElement) o;
                if (tr.getTextContent().startsWith("COMTNBBS")) {
                    for (Object td : tr.querySelectorAll("td.c")) {
                        if (((org.htmlunit.html.HtmlElement) td).getTextContent().contains("R")) {
                            readCell = (org.htmlunit.html.HtmlElement) td;
                        }
                    }
                }
            }
            assertTrue(readCell != null, page.getElementById("crud").getTextContent());
            readCell.click();
            String cd = page.getElementById("crudDetail").getTextContent();
            assertTrue(cd.contains(" · COMTNBBS — 프로그램 ") && cd.contains("Read"), cd);
            ((org.htmlunit.html.HtmlElement) page.getElementById("crudModeList")).click();
            List<?> listHeads = page.querySelectorAll("#crud thead th");
            assertEquals(5, listHeads.size());
            assertEquals("프로그램", ((org.htmlunit.html.HtmlElement) listHeads.get(0)).getTextContent());
            assertFalse(((org.htmlunit.html.HtmlTextInput) page.getElementById("fProg")).isDisabled());
            assertTrue(page.getElementById("crudCount").getTextContent().startsWith("쌍 "), page.getElementById("crudCount").getTextContent());
            assertEquals(null, page.getElementById("allRows"), "넓은 격자 옵션은 걷었다");
            ((org.htmlunit.html.HtmlElement) page.getElementById("tabUnresolved")).click();
            assertTrue(page.querySelectorAll("#unresolved tbody tr").size() >= 1, page.getElementById("unCount").getTextContent());
            // 6-22 — 종류 칩: 「전체 n」 + 한글 이름(title 영문). 칩을 누르면 거르고 뜻·푸는 법 한 줄, 표 종류 칸도 한글
            List<?> chips = page.querySelectorAll("#kindChips .t");
            assertTrue(chips.size() >= 2, page.getElementById("kindChips").getTextContent());
            assertTrue(((org.htmlunit.html.HtmlElement) chips.get(0)).getTextContent().startsWith("전체 "));
            org.htmlunit.html.HtmlElement chip = (org.htmlunit.html.HtmlElement) chips.get(1);
            String code = chip.getAttribute("title");
            assertTrue(code.matches("[a-zA-Z]+") && chip.getTextContent().matches("[가-힣A-Z].*"), code + " " + chip.getTextContent());
            assertFalse(chip.getTextContent().startsWith(code + " "), "칩은 영문 코드가 아니라 이름 — " + chip.getTextContent());
            int want = Integer.parseInt(chip.getTextContent().replaceAll(".* ", ""));
            chip.click();
            wc.waitForBackgroundJavaScript(500);
            assertTrue(page.getElementById("kindHelp").getTextContent().contains("푸는 법: "), page.getElementById("kindHelp").getTextContent());
            List<?> unRows = page.querySelectorAll("#unresolved tbody tr");
            assertEquals(want, unRows.size(), page.getElementById("unCount").getTextContent());
            org.htmlunit.html.HtmlElement firstKind = (org.htmlunit.html.HtmlElement) ((org.htmlunit.html.HtmlElement) unRows.get(0)).querySelector("td");
            assertEquals(code, firstKind.getAttribute("title"));
            assertFalse(firstKind.getTextContent().equals(code), "종류 칸은 이름 — " + firstKind.getTextContent());
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
            assertEquals("", page.getElementById("conScope").getTextContent(), "스냅샷 없이 — 범위 줄 없음(6-23)");
            String conPane = page.getElementById("paneConsistency").getTextContent();
            assertTrue(conPane.contains("view 가 안 가리키는 JSP") && !conPane.contains("뷰"), conPane); // 6-19 — 6-18 표기
        } finally {
            own.stop();
        }
    }

    /**
     * 3-16 — 스냅샷이 있고 filter 가 없는 프로필: 스냅샷 카드가 골라진 채(srcMode('snap'))에도 범위 체크는 잠겨 있다.
     * 3-15 에선 srcMode 가 disabled = csv 로 잠금을 풀었다(스냅샷 없는 프로필만 재서 놓침)
     */
    @Test
    void logicalNameScopeStaysLockedWithSnapshot(@TempDir Path tmp) throws Exception {
        try (java.sql.Connection h = java.sql.DriverManager.getConnection("jdbc:h2:mem:smoke316;DB_CLOSE_DELAY=-1", "sa", "pw");
                java.sql.Statement st = h.createStatement()) {
            st.execute("CREATE TABLE TB_A (A INT)");
            Path profiles = tmp.resolve("profiles");
            Files.createDirectories(profiles);
            Files.writeString(profiles.resolve("t.yaml"), "name: t\n"
                    + "connections:\n  - id: h2\n    dialect: h2\n    url: jdbc:h2:mem:smoke316;DB_CLOSE_DELAY=-1\n    user: sa\n",
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
                boolean has = false;
                for (int i = 0; i < 100 && !has; i++) {
                    has = new com.fasterxml.jackson.databind.ObjectMapper().readTree(http.send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/meta/snapshots")).build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body()).size() > 0;
                    if (!has) {
                        Thread.sleep(100);
                    }
                }
                assertTrue(has, "스냅샷이 안 생겼다");
                HtmlPage ln = wc.getPage(base + "/tools/logical_name.html");
                wc.waitForBackgroundJavaScript(3000);
                assertTrue(ln.getElementById("optSnap").getAttribute("class").contains("on"), "스냅샷 카드가 골라져 있다");
                org.htmlunit.html.HtmlCheckBoxInput c = (org.htmlunit.html.HtmlCheckBoxInput) ln.getElementById("delivScope");
                assertTrue(c.isDisabled() && !c.isChecked(), "filter 없음 — 스냅샷 카드에서도 잠김: " + c.asXml());
                assertEquals("scope-line none", ln.getElementById("delivScopeMsg").getAttribute("class"));
            } finally {
                own.stop();
            }
        }
    }

    /** 3-15 — 프로필에 deliverable.filter 가 없으면 표준 사전의 범위 체크는 꺼지고 잠기며, 산출물 화면은 「없음(전부)」 를 보인다 */
    @Test
    void deliverableScopeLockedWithoutFilter(@TempDir Path tmp) throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\n", StandardCharsets.UTF_8);
        Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try (WebClient wc = client(true)) {
            String base = "http://127.0.0.1:" + own.port();
            HtmlPage ln = wc.getPage(base + "/tools/logical_name.html");
            wc.waitForBackgroundJavaScript(3000);
            org.htmlunit.html.HtmlCheckBoxInput c = (org.htmlunit.html.HtmlCheckBoxInput) ln.getElementById("delivScope");
            assertTrue(c.isDisabled() && !c.isChecked(), c.asXml());
            assertEquals("프로필에 deliverable.filter 없음 — 전부 변환", ln.getElementById("delivScopeMsg").getTextContent());
            assertEquals("scope-line none", ln.getElementById("delivScopeMsg").getAttribute("class")); // 3-16 — 주의 노랑
            HtmlPage d = wc.getPage(base + "/tools/deliverable_sql.html");
            wc.waitForBackgroundJavaScript(3000);
            assertEquals("산출물 범위: 없음(전부)", d.getElementById("scopeMsg").getTextContent());
            assertEquals("scope-line none", d.getElementById("scopeMsg").getAttribute("class"));
        } finally {
            own.stop();
        }
    }

    /** 5-21 — 코드 검사 중지: 폴더 검사 중 중지를 누르면 「중지함」, 이력이 안 는다(파일 1,000 개 — 실측 약 8초) */
    @Test
    void codeCheckStops(@TempDir Path tmp) throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Path proj = tmp.resolve("proj");
        Files.createDirectories(proj.resolve("a"));
        String body = "        System.out.println(1);\n".repeat(200);
        for (int i = 0; i < 1000; i++) {
            Files.writeString(proj.resolve("a/C" + i + ".java"), "package a;\n\npublic class C" + i + " {\n    void f() {\n" + body + "    }\n}\n",
                    StandardCharsets.UTF_8);
        }
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nproject:\n  root: '" + proj + "'\n  encoding: UTF-8\n  lineEnding: LF\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage("http://127.0.0.1:" + own.port() + "/tools/code_check.html");
            wc.waitForBackgroundJavaScript(3000);
            int runsBefore = checkRuns(own);
            org.htmlunit.html.HtmlButton stop = (org.htmlunit.html.HtmlButton) page.getElementById("runStop");
            assertTrue(stop.isDisabled(), "검사 전 중지 꺼짐");
            ((org.htmlunit.html.HtmlButton) page.getElementById("runDir")).click();
            for (int i = 0; i < 50 && stop.isDisabled(); i++) {
                wc.waitForBackgroundJavaScript(100);
            }
            assertTrue(!stop.isDisabled(), "검사 중 중지 켜짐 — " + page.getElementById("msg").getTextContent());
            stop.click();
            String msg = "";
            for (int i = 0; i < 200 && !msg.startsWith("중지함") && !msg.startsWith("파일 "); i++) {
                wc.waitForBackgroundJavaScript(100);
                msg = page.getElementById("msg").getTextContent();
            }
            assertEquals("중지함 — 이력에 남기지 않았다", msg);
            assertTrue(stop.isDisabled() && !((org.htmlunit.html.HtmlButton) page.getElementById("runDir")).isDisabled(), "중지 뒤 버튼");
            assertEquals(runsBefore, checkRuns(own), "이력이 안 는다");
        } finally {
            own.stop();
        }
    }

    private static int checkRuns(Javalin app) throws Exception {
        java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest
                .newBuilder(java.net.URI.create("http://127.0.0.1:" + app.port() + "/api/check/runs")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(r.body()).size();
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
        // 5-23 — CRLF 파일 하나(프로필 LF) → 파일 단위 지적 file.lineEnding
        Files.writeString(proj.resolve("a/Crlf.java"), "/** 수정일 */\r\nclass Crlf {\r\n}\r\n", StandardCharsets.UTF_8);
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
            // 5-23 — 파일 단위 지적의 줄 칸은 「파일」, 줄 단위는 수
            for (Object o : rows) {
                List<?> tds = ((org.htmlunit.html.HtmlElement) o).querySelectorAll("td");
                String rule = ((org.htmlunit.html.HtmlElement) tds.get(3)).getTextContent();
                String line = ((org.htmlunit.html.HtmlElement) tds.get(1)).getTextContent();
                if (rule.equals("file.lineEnding")) {
                    assertEquals("파일", line);
                }
                if (rule.equals("common.sysout")) {
                    assertTrue(line.matches("\\d+"), line);
                }
            }
            assertTrue(page.getElementById("result").getTextContent().contains("file.lineEnding"), msg);
            ((org.htmlunit.html.HtmlElement) rows.get(0)).click();
            wc.waitForBackgroundJavaScript(5000);
            assertTrue(page.getElementById("preview").getTextContent().contains("a/A.java:"), page.getElementById("preview").getTextContent());
            // 1-35 — 거른 행 복사: 고를 칸이 없어 글로 「복사됨: n행」
            stubClipboard(page);
            ((org.htmlunit.html.HtmlButton) page.getElementById("copy")).click();
            wc.waitForBackgroundJavaScript(2000);
            assertTrue(js(page, "window.__copied").startsWith("파일\t줄\t"), js(page, "window.__copied"));
            assertEquals("복사됨: " + rows.size() + "행", page.getElementById("msg").getTextContent());
            ((org.htmlunit.html.HtmlButton) page.getElementById("xlsx")).click();
            wc.waitForBackgroundJavaScript(5000);
            assertTrue(page.getElementById("msg").getTextContent().startsWith("xlsx"), page.getElementById("msg").getTextContent());
            // 0-44 — 저장 알림은 파일 이름까지 전체 경로
            assertTrue(page.getElementById("msg").getTextContent().matches("(?s).*저장 .*코드검사-\\d+\\.xlsx$"), page.getElementById("msg").getTextContent());
            ((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("r_common.todo")).setChecked(false);
            ((org.htmlunit.html.HtmlButton) page.getElementById("saveRules")).click();
            wc.waitForBackgroundJavaScript(5000);
            assertEquals("저장 " + yaml.toAbsolutePath(), page.getElementById("ruleMsg").getTextContent());
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
