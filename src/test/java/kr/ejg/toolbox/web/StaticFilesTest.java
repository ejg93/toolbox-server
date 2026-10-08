package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StaticFilesTest {

    static Javalin app;

    @BeforeAll
    static void up() {
        app = App.start(AppTest.config(0));
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    @Test
    void commonJsIsServed() throws Exception {
        HttpResponse<String> res = AppTest.get(app, "/tools/common.js");
        assertEquals(200, res.statusCode());
        String type = res.headers().firstValue("content-type").orElse("");
        assertTrue(type.contains("javascript"), "content-type: " + type);
        assertTrue(res.body().contains("window.TB"));
    }

    /** 도구마다 토큰 이름이 달라(special_chars 는 --card·--ink) 대체값 없는 var() 는 빈 색이 된다(2026-09-27 리뷰) */
    @Test
    void commonJsCssVarsHaveFallbacks() throws Exception {
        String body = AppTest.get(app, "/tools/common.js").body();
        assertTrue(body.contains("var(--"), "색은 토큰으로");
        assertTrue(!java.util.regex.Pattern.compile("var\\(--[a-z-]+\\)").matcher(body).find(),
                "대체값 없는 var(--x) 가 있다");
    }

    /** 0-51 — 공용 테마는 text/css 로 나가고, 테마 스위치(data-theme)와 OS 자동 둘 다 담는다. 화면에 안 붙어 있으면 common.js 가 끼운다 */
    @Test
    void commonCssIsServed() throws Exception {
        HttpResponse<String> res = AppTest.get(app, "/tools/common.css");
        assertEquals(200, res.statusCode());
        String type = res.headers().firstValue("content-type").orElse("");
        assertTrue(type.contains("text/css"), "content-type: " + type);
        String css = res.body();
        for (String need : java.util.List.of("--accent", ":root[data-theme=\"light\"]", "prefers-color-scheme: light", ".btn-p", ".dl")) {
            assertTrue(css.contains(need), need);
        }
        String js = AppTest.get(app, "/tools/common.js").body();
        assertTrue(js.contains("link[href=\"/tools/common.css\"]") && js.contains("insertBefore(l, head.querySelector('style'))"),
                "link 가 없는 화면에 첫 <style> 앞으로 끼운다");
        assertTrue(js.contains("setAttribute('data-theme'") && js.contains("localStorage"), "테마 스위치");
    }

    @Test
    void unknownToolIs404() throws Exception {
        assertEquals(404, AppTest.get(app, "/tools/nope.js").statusCode());
    }
}
