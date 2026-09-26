package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import kr.ejg.toolbox.core.dict.Dictionaries;
import kr.ejg.toolbox.core.logical.LogicalRun;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 3-4 — 사전 API·localStorage 가져오기·기관 파일(multipart)·내보내기 */
class DictRoutesTest {

    static Javalin app;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\n", StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    static HttpResponse<String> send(String method, String path, String contentType, byte[] body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path));
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode json(String method, String path, String body) throws Exception {
        HttpResponse<String> r = send(method, path, "application/json", body == null ? null : body.getBytes(StandardCharsets.UTF_8));
        assertTrue(r.statusCode() < 300, path + " → " + r.statusCode() + " " + r.body());
        return JSON.readTree(r.body());
    }

    @Test
    void userDictAndLocalStorageImport() throws Exception {
        assertTrue(json("GET", "/api/dict/import/needed", null).get("needed").asBoolean());

        JsonNode imp = json("POST", "/api/dict/import",
                "{\"lnUserDict\":{\"upd\":\"수정\",\"ORD\":\"주문\",\"TEL\":\"전화\"},\"lnSkipTok\":\"TB, TMP\",\"lnDialect\":\"oracle\"}");
        assertEquals(3, imp.get("imported").asInt());
        assertEquals("[\"TB\",\"TMP\"]", imp.get("skipTokens").toString());
        assertEquals("oracle", imp.get("dialect").asText());
        JsonNode again = json("POST", "/api/dict/import", "{\"lnUserDict\":{\"UPD\":\"다른값\",\"ORD\":\"주문\",\"TEL\":\"전화\"}}");
        assertEquals(0, again.get("imported").asInt());
        assertEquals(3, again.get("skipped").asInt(), "서버에 있으면 서버 쪽을 둔다");
        assertFalse(json("GET", "/api/dict/import/needed", null).get("needed").asBoolean());

        json("PUT", "/api/dict/user/email", "{\"ko\":\"이메일\"}");
        assertEquals("이메일", json("GET", "/api/dict/words?q=EMAIL&kind=user", null).get(0).get("ko").asText());
        assertTrue(json("DELETE", "/api/dict/user/TEL", null).get("deleted").asBoolean());
        assertEquals(400, send("PUT", "/api/dict/user/X", "application/json", "{\"ko\":\" \"}".getBytes(StandardCharsets.UTF_8)).statusCode());

        HttpResponse<String> exp = send("GET", "/api/dict/export?kind=user", null, null);
        assertTrue(exp.body().startsWith(String.valueOf((char) 0xFEFF) + "영문약어,한글\r\nEMAIL,이메일\r\nORD,주문\r\nUPD,수정"), exp.body());
        assertTrue(exp.headers().firstValue("content-type").orElse("").startsWith("text/csv"));
    }

    @Test
    void orgImportMultipartAndMoiLoaded() throws Exception {
        byte[] csv = Files.readAllBytes(Path.of("src/test/resources/sample/logical/org-words.csv"));
        String boundary = "----tb" + System.nanoTime();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"org.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + csv.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(csv, 0, body, head.length, csv.length);
        System.arraycopy(tail, 0, body, head.length + csv.length, tail.length);
        HttpResponse<String> r = send("POST", "/api/dict/org/import", "multipart/form-data; boundary=" + boundary, body);
        assertEquals(200, r.statusCode(), r.body());
        assertEquals(111, JSON.readTree(r.body()).get("imported").asInt());
        assertEquals(111, json("POST", "/api/dict/org/import", "{\"csv\":" + JSON.writeValueAsString(new String(csv, StandardCharsets.UTF_8)) + "}")
                .get("imported").asInt(), "JSON 본문도 받는다");
        assertTrue(json("GET", "/api/dict/words?q=ACEF&kind=word", null).size() == 1, "기동 때 공통표준단어 적재(3-1)");
    }

    /** 0-32 ② — 새 판 파일을 올리면 판이 파일 이름 날짜로 바뀐다. 머리가 틀리면 400 */
    @Test
    void moiUploadReplacesEdition() throws Exception {
        byte[] csv = Files.readAllBytes(Path.of("src/test/resources/sample/logical/moi-words-20251101.csv"));
        HttpResponse<String> r = send("POST", "/api/dict/moi/import", "multipart/form-data; boundary=B1", multipart("B1",
                "행정안전부_공공데이터 공통표준단어_20991231.csv", csv));
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("moi-20991231", JSON.readTree(r.body()).get("source").asText());
        assertEquals("moi-20991231", json("GET", "/api/dict/moi", null).get("source").asText());
        HttpResponse<String> bad = send("POST", "/api/dict/moi/import", "multipart/form-data; boundary=B2", multipart("B2", "x.csv",
                "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals(400, bad.statusCode(), bad.body());
        assertEquals("moi-20991231", json("GET", "/api/dict/moi", null).get("source").asText(), "틀린 파일은 되돌린다");
    }

    static byte[] multipart(String boundary, String filename, byte[] content) {
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        return body;
    }

    @Test
    void rankConflictLikePure() {
        Dictionaries d = new Dictionaries(Map.of("CD", "코드"), Map.of("TB", "테이블"), Map.of("CD", "코드값", "TB", "표", "NEW", "신규"),
                Map.of(), Map.of());
        assertEquals(new LogicalRun.Conflict(LogicalRun.Conflict.SHADOWED_BY_ORG, "테이블"), LogicalRun.conflict("TB", d, true));
        assertEquals(new LogicalRun.Conflict(LogicalRun.Conflict.SHADOWS_ORG, "테이블"), LogicalRun.conflict("TB", d, false));
        assertEquals(new LogicalRun.Conflict(LogicalRun.Conflict.SHADOWS_WORD, "코드"), LogicalRun.conflict("CD", d, true));
        assertEquals(null, LogicalRun.conflict("NEW", d, true));
        assertEquals(null, LogicalRun.conflict("NONE", d, true));
    }
}
