package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackendToolsTest {

    static Javalin app;

    @BeforeAll
    static void up() {
        app = App.start(AppTest.config(0));
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "dev_tools", "jsp_formatter", "sql_snippets", "table_builder",
        "logical_name", "deliverable_sql", "special_chars", "code_check", "program_analysis", "spring_source_generator"
    })
    void toolIsServedWithCommonJs(String name) throws Exception {
        HttpResponse<String> res = AppTest.get(app, "/tools/" + name + ".html");
        assertEquals(200, res.statusCode());
        String body = res.body();
        assertTrue(body.contains("<script src=\"/tools/common.js\" defer></script>"), name + " 에 common.js 태그");
        assertTrue(body.indexOf("/tools/common.js") < body.indexOf("</head>"), name + " 의 태그는 </head> 앞");
        // 1-54 — 공용 테마. sql_snippets 는 글자 고정(순수본 + common.js 한 줄)이라 common.js 가 끼운다
        if (!"sql_snippets".equals(name)) {
            assertTrue(body.contains("<link rel=\"stylesheet\" href=\"/tools/common.css\">"), name + " 에 common.css link");
        }
    }
}
