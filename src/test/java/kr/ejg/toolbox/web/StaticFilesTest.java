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

    @Test
    void unknownToolIs404() throws Exception {
        assertEquals(404, AppTest.get(app, "/tools/nope.js").statusCode());
    }
}
