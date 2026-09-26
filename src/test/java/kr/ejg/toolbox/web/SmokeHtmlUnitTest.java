package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.htmlunit.BrowserVersion;
import org.htmlunit.ScriptException;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.javascript.SilentJavaScriptErrorListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 서버를 띄우고 런처 + 도구를 HtmlUnit 으로 열어 스크립트 예외 0 과 모드 배지를 본다.
 * HtmlUnit 이 못 읽는 최신 문법을 쓰는 도구는 {@link #JS_OFF} 에 넣어 JS 를 끄고 로드만 본다(사유는 PROGRESS 이력).
 */
class SmokeHtmlUnitTest {

    /** HtmlUnit(Rhino) 가 문법을 못 읽는 도구 — JS 끄고 로드만 */
    static final Set<String> JS_OFF = Set.of("dev_tools", "logical_name");

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
        "logical_name", "deliverable_sql", "special_chars"
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
}
