package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.util.Map;
import kr.ejg.toolbox.core.conn.ConnectionRegistry;

/**
 * 접속 — `GET /api/conn`(목록, 비밀번호 입력 여부만) · `POST /api/conn/{id}/password {password}`(메모리) ·
 * `DELETE /api/conn/{id}/password` · `POST /api/conn/{id}/test`. 비밀번호는 응답·로그에 안 나간다(절대 규칙 2).
 */
final class ConnRoutes {

    /** Jackson 이 JSON 문자열을 char[] 로 받는다 — 본문 String 은 요청이 끝나면 버려진다 */
    record PasswordBody(char[] password) {
    }

    private ConnRoutes() {
    }

    static void register(Javalin app, ConnectionRegistry conns) {
        app.get("/api/conn", ctx -> ctx.json(conns.list()));

        app.post("/api/conn/{id}/password", ctx -> {
            String id = ctx.pathParam("id");
            if (conns.find(id).isEmpty()) {
                ctx.status(404).json(Map.of("message", "접속이 없다: " + id));
                return;
            }
            PasswordBody body = ctx.bodyAsClass(PasswordBody.class);
            if (body.password() == null) {
                ctx.status(400).json(Map.of("message", "password 가 없다"));
                return;
            }
            conns.setPassword(id, body.password());
            ctx.json(Map.of("ok", true));
        });

        app.delete("/api/conn/{id}/password", ctx -> {
            conns.forget(ctx.pathParam("id"));
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/conn/{id}/test", ctx -> {
            String id = ctx.pathParam("id");
            if (conns.find(id).isEmpty()) {
                ctx.status(404).json(Map.of("message", "접속이 없다: " + id));
                return;
            }
            ctx.json(conns.test(id));
        });
    }
}
