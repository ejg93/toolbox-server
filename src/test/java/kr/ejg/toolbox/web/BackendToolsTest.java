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
        "logical_name", "deliverable_sql", "special_chars", "code_check", "program_analysis", "crud_generator"
    })
    void toolIsServedWithCommonJs(String name) throws Exception {
        HttpResponse<String> res = AppTest.get(app, "/tools/" + name + ".html");
        assertEquals(200, res.statusCode());
        String body = res.body();
        assertTrue(body.contains("<script src=\"/tools/common.js\" defer></script>"), name + " 에 common.js 태그");
        assertTrue(body.indexOf("/tools/common.js") < body.indexOf("</head>"), name + " 의 태그는 </head> 앞");
    }
}
