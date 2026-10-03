package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.LocalConnector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 8-1 — 듣는 포트 없는 서버: 같은 라우트 왕복 · 커넥터는 LocalConnector 하나 · 가드가 산다 · 닫으면 H2 도 닫힌다 */
class LocalAppTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tmp;

    AppConfig config() throws Exception {
        Files.createDirectories(tmp.resolve("profiles"));
        return new AppConfig(0, null, tmp.resolve("data"), tmp.resolve("profiles"), tmp.resolve("drivers"), false);
    }

    @Test
    void sameRoutesWithoutSocket() throws Exception {
        try (LocalApp app = LocalApp.start(config())) {
            LocalApp.Response ping = app.send("GET", "/api/ping", null);
            assertEquals(200, ping.status(), ping.text());
            assertTrue(ping.contentType().startsWith("application/json"), ping.contentType());

            LocalApp.Response log = app.send("POST", "/api/text/logsql",
                    JSON.writeValueAsString(Map.of("text",
                            "2026-10-03 10:00:00.101 DEBUG 1 --- [main] k.g.x.EmpMapper.select : ==>  Preparing: SELECT * FROM TB_EMP WHERE EMP_NM = ?\n"
                            + "2026-10-03 10:00:00.102 DEBUG 1 --- [main] k.g.x.EmpMapper.select : ==> Parameters: 홍길동(String)\n")));
            assertEquals(200, log.status(), log.text());
            JsonNode items = JSON.readTree(log.text()).get("items");
            assertTrue(items.isArray(), log.text());
            assertTrue(log.text().contains("홍길동"), "한글 본문이 왕복한다: " + log.text());

            LocalApp.Response root = app.send("GET", "/", null);
            assertEquals(302, root.status());

            // 큰 정적 파일이 바이트 그대로
            LocalApp.Response big = app.send("GET", "/tools/dev_tools.html", null);
            assertEquals(200, big.status());
            try (InputStream in = LocalAppTest.class.getResourceAsStream("/tools/dev_tools.html")) {
                assertArrayEquals(in.readAllBytes(), big.body());
            }

            // 가드가 산다 — 다른 출처의 쓰기는 403
            LocalApp.Response foreign = app.send("POST", "/api/text/logsql", "{\"text\":\"x\"}", Map.of("Origin", "http://evil.example"));
            assertEquals(403, foreign.status());
        }
    }

    @Test
    void onlyLocalConnector() throws Exception {
        try (LocalApp app = LocalApp.start(config())) {
            java.lang.reflect.Field f = LocalApp.class.getDeclaredField("app");
            f.setAccessible(true);
            io.javalin.Javalin j = (io.javalin.Javalin) f.get(app);
            Connector[] cs = j.jettyServer().server().getConnectors();
            assertEquals(1, cs.length);
            assertTrue(cs[0] instanceof LocalConnector, cs[0].getClass().getName());
        }
    }

    @Test
    void refusesInjectionInRequestLine() throws Exception {
        try (LocalApp app = LocalApp.start(config())) {
            assertThrows(IllegalArgumentException.class, () -> app.send("GET", "/api/ping HTTP/1.1\r\nX: y", null));
            assertThrows(IllegalArgumentException.class, () -> app.send("GET", "/api/ping x", null));
            assertThrows(IllegalArgumentException.class, () -> app.send("GET", "/api/사원", null));
            assertThrows(IllegalArgumentException.class, () -> app.send("PATCH", "/api/ping", null));
            assertThrows(IllegalArgumentException.class, () -> app.send("GET", "/api/ping", null, Map.of("X", "a\r\nY: b")));
        }
    }

    /** 닫으면 H2 가 닫혀 같은 data 폴더로 다시 연다(잠긴 폴더 → LockedException 은 DbTest 가 자식 JVM 으로 잰다) */
    @Test
    void closeReleasesH2() throws Exception {
        AppConfig c = config();
        try (LocalApp app = LocalApp.start(c)) {
            assertEquals(200, app.send("GET", "/api/ping", null).status());
        }
        try (LocalApp again = LocalApp.start(c)) {
            assertEquals(200, again.send("GET", "/api/ping", null).status());
        }
    }
}
