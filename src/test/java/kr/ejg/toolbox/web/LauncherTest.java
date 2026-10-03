package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LauncherTest {

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
    void rootRedirectsToLauncher() throws Exception {
        HttpResponse<String> res = AppTest.get(app, "/");
        assertEquals(302, res.statusCode());
        assertEquals("/tools/index.html", res.headers().firstValue("location").orElse(""));
    }

    @Test
    void launcherHasElevenCards() throws Exception {
        HttpResponse<String> res = AppTest.get(app, "/tools/index.html");
        assertEquals(200, res.statusCode());
        String body = res.body();
        assertEquals(11, count(body, "data-file=\""));
        assertEquals(11, count(body, "data-status=\"ready\""), "7-4 에서 crud_generator 가 ready — 카드 전부");
        assertTrue(body.contains("/tools/common.js"));
    }

    private static int count(String s, String needle) {
        Matcher m = Pattern.compile(Pattern.quote(needle)).matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
