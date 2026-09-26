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

    @Test
    void unknownToolIs404() throws Exception {
        assertEquals(404, AppTest.get(app, "/tools/nope.js").statusCode());
    }
}
