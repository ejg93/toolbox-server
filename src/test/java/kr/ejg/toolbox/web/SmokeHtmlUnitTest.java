package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        "logical_name", "deliverable_sql", "special_chars", "code_check"
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
            assertEquals(11, page.querySelectorAll("#docChecks input").size());
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

    /**
     * 4-5 — sql_snippets 실행: h2 접속 + 파라미터 값 → 실행 → 결과 표 / 안 도는 SQL → 오류 문구.
     * 방언 탭(PostgreSQL)과 접속(h2)이 달라 경고가 뜬다. 결과 xlsx 가 저장소 out/ 에 안 떨어지게 앱을 따로 띄운다.
     */
    @Test
    void sqlSnippetsRunOnConnection(@TempDir Path tmp) throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nconnections:\n  - id: h2\n    dialect: h2\n"
                + "    url: jdbc:h2:mem:snip;DB_CLOSE_DELAY=-1\n    user: sa\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        String base = "http://127.0.0.1:" + own.port() + "/tools/sql_snippets.html";
        try (WebClient wc = client(true)) {
            HtmlPage page = wc.getPage(base + "#grp_count:pg");
            wc.waitForBackgroundJavaScript(3000);
            ((org.htmlunit.html.HtmlSelect) page.getElementById("conn")).setSelectedAttribute("h2", true);
            ((org.htmlunit.html.HtmlTextInput) page.getElementById("param_grp_tbl")).type("INFORMATION_SCHEMA.TABLES");
            ((org.htmlunit.html.HtmlTextInput) page.getElementById("param_grp_col")).type("TABLE_TYPE");
            ((org.htmlunit.html.HtmlButton) page.getElementById("runBtn")).click();
            wc.waitForBackgroundJavaScript(5000);
            String msg = page.getElementById("runMsg").getTextContent();
            assertTrue(msg.startsWith("행 "), msg + " / " + page.getElementById("q_output").getTextContent());
            assertTrue(page.querySelectorAll("#runResult table tbody tr").size() >= 1, msg);
            assertTrue(page.getElementById("dialectWarn").getTextContent().contains("PostgreSQL"),
                    page.getElementById("dialectWarn").getTextContent());

            page = wc.getPage(base + "#tbl_list:pg");
            wc.waitForBackgroundJavaScript(3000);
            ((org.htmlunit.html.HtmlSelect) page.getElementById("conn")).setSelectedAttribute("h2", true);
            ((org.htmlunit.html.HtmlButton) page.getElementById("runBtn")).click();
            wc.waitForBackgroundJavaScript(5000);
            msg = page.getElementById("runMsg").getTextContent();
            assertTrue(msg.startsWith("오류: "), "pg_size_pretty 는 h2 에 없다: " + msg);
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
     * 4-4 — jsp_formatter 폴더 일괄: 미리보기 → 표 행 2 → 적용 → 파일 바뀜(인코딩·줄바꿈 그대로)·백업.
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
            ((org.htmlunit.html.HtmlButton) page.getElementById("dirPreview")).click();
            wc.waitForBackgroundJavaScript(10000);
            String msg = page.getElementById("dirMsg").getTextContent();
            assertEquals(3, page.querySelectorAll("#dirTable tr").size(), msg + " / " + page.getElementById("cmp").getTextContent());
            assertTrue(msg.contains("적용 대상 2개"), msg + " / " + page.getElementById("cmp").getTextContent());
            ((org.htmlunit.html.HtmlButton) page.getElementById("dirApply")).click();
            wc.waitForBackgroundJavaScript(10000);
            msg = page.getElementById("dirMsg").getTextContent();
            assertTrue(msg.startsWith("적용 2/2"), msg + " / " + page.getElementById("cmp").getTextContent());
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
            if (kr.ejg.toolbox.core.vcs.Cli.available("git", proj)) {
                Process g = new ProcessBuilder("git", "init", "-q").directory(proj.toFile()).redirectErrorStream(true).start();
                g.getOutputStream().close();
                g.getInputStream().readAllBytes();
                assertEquals(0, g.waitFor());
                ((org.htmlunit.html.HtmlTextInput) page.getElementById("dir")).fireEvent("change");
                wc.waitForBackgroundJavaScript(5000);
                assertTrue(!((org.htmlunit.html.HtmlCheckBoxInput) page.getElementById("changed")).isDisabled(),
                        page.getElementById("vcsInfo").getTextContent());
                assertTrue(page.getElementById("vcsInfo").getTextContent().startsWith("git · 변경 "), page.getElementById("vcsInfo").getTextContent());
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
