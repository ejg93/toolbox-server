package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 2-3~ 산출물 라우트 — H2 메모리 접속으로 스냅샷을 떠서 끝에서 끝 */
class DeliverableRoutesTest {

    static Javalin app;
    static Connection holder;
    static long snapshotId;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        holder = DriverManager.getConnection("jdbc:h2:mem:delivtest;DB_CLOSE_DELAY=-1", "sa", "pw");
        try (Statement st = holder.createStatement()) {
            st.execute("CREATE TABLE TB_CMM_CD (GRP_CD VARCHAR(10), CD VARCHAR(10), CD_NM VARCHAR(50), USE_YN CHAR(1), PRIMARY KEY (GRP_CD, CD))");
            st.execute("COMMENT ON TABLE TB_CMM_CD IS '공통코드'");
            st.execute("INSERT INTO TB_CMM_CD VALUES ('SEX','M','남','Y'), ('SEX','F','여','Y')");
            st.execute("CREATE TABLE IF_ORDER_RCV (ORD_NO VARCHAR(20))");
        }
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\n"
                + "connections:\n  - id: h2\n    dialect: h2\n    url: jdbc:h2:mem:delivtest;DB_CLOSE_DELAY=-1\n    user: sa\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        assertEquals(200, post("/api/conn/h2/password", Map.of("password", "pw")).statusCode());
        String jobId = JSON.readTree(post("/api/meta/snapshot", Map.of("connId", "h2")).body()).get("jobId").asText();
        JsonNode job = null;
        long end = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < end) {
            job = JSON.readTree(HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/jobs/" + jobId)).build(),
                    HttpResponse.BodyHandlers.ofString()).body());
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        snapshotId = job.get("result").get("snapshotId").asLong();
    }

    @AfterAll
    static void down() throws Exception {
        app.stop();
        holder.close();
    }

    static HttpResponse<String> post(String path, Object body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void codeCandidatesThenRows() throws Exception {
        HttpResponse<String> c = post("/api/deliverable/codes/candidates", Map.of("snapshotId", snapshotId));
        assertEquals(200, c.statusCode(), c.body());
        JsonNode cands = JSON.readTree(c.body());
        assertEquals(1, cands.size(), c.body());
        assertEquals("GRP_CD", cands.get(0).get("groupCol").asText());
        HttpResponse<String> r = post("/api/deliverable/codes/rows", Map.of("connId", "h2", "tables", List.of(JSON.convertValue(cands.get(0), Map.class)),
                "org", "기관"));
        assertEquals(200, r.statusCode(), r.body());
        JsonNode doc = JSON.readTree(r.body());
        assertEquals("08", doc.get("no").asText());
        assertEquals(2, doc.get("rows").size());
        assertEquals(400, post("/api/deliverable/codes/rows", Map.of("connId", "h2", "tables",
                List.of(Map.of("table", "X; DROP TABLE TB_CMM_CD", "codeCol", "CD", "nameCol", "CD_NM")))).statusCode(), "이름 검사");
        assertEquals(400, post("/api/deliverable/codes/rows", Map.of("connId", "nope", "tables", List.of())).statusCode());
    }

    @Test
    void buildWritesElevenXlsx() throws Exception {
        JsonNode cands = JSON.readTree(post("/api/deliverable/codes/candidates", Map.of("snapshotId", snapshotId)).body());
        HttpResponse<String> r = post("/api/deliverable/build", Map.of("snapshotId", snapshotId, "author", "홍길동", "codeConnId", "h2",
                "codeTables", List.of(JSON.convertValue(cands.get(0), Map.class))));
        assertEquals(202, r.statusCode(), r.body());
        String jobId = JSON.readTree(r.body()).get("jobId").asText();
        JsonNode job = null;
        long end = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < end) {
            job = JSON.readTree(HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/jobs/" + jobId)).build(),
                    HttpResponse.BodyHandlers.ofString()).body());
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(100);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        JsonNode files = job.get("result").get("files");
        assertEquals(11, files.size(), job.toString());
        for (JsonNode f : files) {
            Path p = Path.of(f.asText());
            assertTrue(Files.size(p) > 0, p.toString());
            assertTrue(p.toString().replace('\\', '/').contains("/t/"), "out/<프로필>/<시각>/산출물/");
        }
        Path t02 = Path.of(files.get(1).asText());
        try (java.io.InputStream in = Files.newInputStream(t02);
                org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            org.apache.poi.ss.usermodel.Sheet s = wb.getSheetAt(0);
            int name = header(s.getRow(1), "영문 테이블명");
            int author = header(s.getRow(1), "최종수정자");
            assertTrue(name >= 0 && author >= 0, "머리글에 표준 열 이름(2-10)");
            assertEquals("IF_ORDER_RCV", s.getRow(2).getCell(name).getStringCellValue(), "스냅샷 테이블이 이름순으로");
            assertEquals("홍길동", s.getRow(2).getCell(author).getStringCellValue(), "요청의 작성자");
        }
        Path t08 = Path.of(files.get(7).asText());
        try (java.io.InputStream in = Files.newInputStream(t08);
                org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            assertEquals(2 + 2, wb.getSheetAt(0).getLastRowNum() + 1, "코드값 2행");
        }
        assertEquals(404, post("/api/deliverable/build", Map.of("snapshotId", 999)).statusCode());
        assertEquals(400, post("/api/deliverable/build", Map.of("snapshotId", snapshotId, "codeConnId", "nope")).statusCode());
    }

    /** 7-7 — 대상 다섯 200 · 모르는 대상 400 · 없는 스냅샷 404 · 고른 표 없음 400 · save 파일 */
    @Test
    void ddlPerTarget() throws Exception {
        for (String target : List.of("oracle", "tibero", "postgresql", "mariadb", "mssql")) {
            HttpResponse<String> r = post("/api/deliverable/ddl", Map.of("snapshotId", snapshotId, "target", target));
            assertEquals(200, r.statusCode(), r.body());
            JsonNode j = JSON.readTree(r.body());
            assertTrue(j.get("sql").asText().contains("CREATE TABLE TB_CMM_CD ("), r.body());
            assertEquals(2, j.get("tables").asInt(), r.body());
        }
        assertEquals(400, post("/api/deliverable/ddl", Map.of("snapshotId", snapshotId, "target", "sybase")).statusCode());
        assertEquals(404, post("/api/deliverable/ddl", Map.of("snapshotId", 9999, "target", "oracle")).statusCode());
        assertEquals(400, post("/api/deliverable/ddl", Map.of("snapshotId", snapshotId, "target", "oracle",
                "tables", List.of(Map.of("name", "NOPE")))).statusCode());
        HttpResponse<String> one = post("/api/deliverable/ddl", Map.of("snapshotId", snapshotId, "target", "postgresql",
                "tables", List.of(Map.of("name", "tb_cmm_cd")), "schema", "APP", "save", true));
        JsonNode j = JSON.readTree(one.body());
        assertEquals(1, j.get("tables").asInt(), one.body());
        assertTrue(j.get("sql").asText().contains("CREATE TABLE APP.TB_CMM_CD ("), one.body());
        assertTrue(j.get("sql").asText().contains("COMMENT ON TABLE APP.TB_CMM_CD IS '공통코드';"), one.body());
        Path saved = Path.of(j.get("path").asText());
        assertTrue(saved.startsWith(tmp.resolve("out")), saved.toString());
        assertEquals(j.get("sql").asText(), Files.readString(saved, StandardCharsets.UTF_8));
    }

    @Test
    void qualitySqlAndNoPk() throws Exception {
        JsonNode kinds = JSON.readTree(HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/quality/kinds")).build(),
                HttpResponse.BodyHandlers.ofString()).body());
        assertEquals(8, kinds.get("kinds").size());
        HttpResponse<String> r = post("/api/quality/sql", Map.of("kind", "format", "dialect", "pg", "schema", "PUBLIC", "table", "TB_CMM_CD",
                "column", "CD_NM"));
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(JSON.readTree(r.body()).get("sql").asText().contains("FROM   PUBLIC.TB_CMM_CD"), r.body());
        assertEquals(400, post("/api/quality/sql", Map.of("kind", "format", "dialect", "pg", "table", "T;DROP", "column", "C")).statusCode());
        HttpResponse<String> n = post("/api/quality/nopk", Map.of("snapshotId", snapshotId));
        assertEquals(200, n.statusCode(), n.body());
        JsonNode list = JSON.readTree(n.body());
        assertEquals(1, list.size(), "PK 있는 TB_CMM_CD 는 빠진다: " + n.body());
        assertEquals("IF_ORDER_RCV", list.get(0).get("table").asText());
    }

    @Test
    void linkCandidates() throws Exception {
        HttpResponse<String> r = post("/api/deliverable/links/candidates", Map.of("snapshotId", snapshotId, "connId", "h2"));
        assertEquals(200, r.statusCode(), r.body());
        JsonNode b = JSON.readTree(r.body());
        assertEquals("IF_ORDER_RCV", b.get("candidates").get(0).get("name").asText());
        assertEquals("수신", b.get("doc").get("rows").get(0).get(1).asText());
        assertTrue(b.get("note").asText().contains("DB링크 조회가 없다"), b.get("note").asText());
        assertEquals(404, post("/api/deliverable/links/candidates", Map.of("snapshotId", 999)).statusCode());
        assertEquals(400, post("/api/deliverable/codes/candidates", Map.of()).statusCode());
    }

    /** 머리글 행에서 열 이름의 위치 — 없으면 -1(열 순서가 표준으로 바뀌어도 단언이 안 흔들린다, 2-10) */
    static int header(org.apache.poi.ss.usermodel.Row row, String name) {
        for (org.apache.poi.ss.usermodel.Cell c : row) {
            if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING && name.equals(c.getStringCellValue())) {
                return c.getColumnIndex();
            }
        }
        return -1;
    }
}
