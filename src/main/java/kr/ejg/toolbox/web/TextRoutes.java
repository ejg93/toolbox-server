package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.util.Map;
import kr.ejg.toolbox.core.text.LogSql;

/**
 * 4-8 — `POST /api/text/logsql {text}` → `{items[{sql, params[], restored, warning}]}`.
 * 붙여 넣은 로그·복원문은 응답으로만 — 로그·파일에 안 남긴다(절대 규칙 3). 화면은 4-9(dev_tools 「로그 SQL 복원」 탭).
 */
final class TextRoutes {

    /** 붙여넣기 상한 — 로그 통째로 붙이는 실수를 막는다 */
    static final int MAX_CHARS = 5_000_000;

    private TextRoutes() {
    }

    record LogSqlRequest(String text) {
    }

    static void register(Javalin app) {
        app.post("/api/text/logsql", ctx -> {
            LogSqlRequest req = ctx.bodyAsClass(LogSqlRequest.class);
            if (req.text() == null || req.text().isBlank()) {
                ctx.status(400).json(Map.of("message", "text 가 비었다"));
                return;
            }
            if (req.text().length() > MAX_CHARS) {
                ctx.status(413).json(Map.of("message", "5백만 자를 넘는다 — 필요한 부분만 붙일 것"));
                return;
            }
            ctx.json(Map.of("items", LogSql.restore(req.text())));
        });
    }
}
