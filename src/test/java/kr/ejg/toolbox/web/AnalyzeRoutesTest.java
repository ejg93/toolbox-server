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
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 6-4 — 분석 실행(job) → 이력 조회. 6-2·6-3 픽스처를 한 폴더에 복사해 프로그램·CRUD·미해결 값을 본다 */
class AnalyzeRoutesTest {

    static Javalin app;
    static Path project;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nframework: egov35\noutput:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/')
                + "\n", StandardCharsets.UTF_8);
        project = project(tmp.resolve("proj"));
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    /** 6-2·6-3 픽스처 + 그 문장과 맞는 매퍼 둘 + JSP 둘(6-6) — 프로그램 13. 화면 스모크(6-5)도 쓴다 */
    static Path project(Path dir) throws Exception {
        copy(Path.of("src/test/resources/fixtures/analyze/java"), dir.resolve("src/main/java"));
        copy(Path.of("src/test/resources/fixtures/analyze/mapper"), dir.resolve("src/main/resources/mapper"));
        // 픽스처 Java 의 문장(Board.*)과 맞는 매퍼 하나
        Files.writeString(dir.resolve("src/main/resources/mapper/Board_SQL.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="Board">
                    <select id="selectList">SELECT * FROM COMTNBBS a JOIN COMVNUSERMASTER b ON a.ID = b.ID</select>
                    <insert id="insert">INSERT INTO COMTNBBS (ID) VALUES (#{id})</insert>
                    <select id="selectDetail">SELECT * FROM COMTNBBS</select>
                    <select id="unusedOne">SELECT * FROM COMTNBBS</select>
                </mapper>
                """, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("src/main/resources/mapper/Login_SQL.xml"), """
                <mapper namespace="Login">
                    <update id="updateIncorrectUSR">UPDATE COMTNUSER SET X = 1</update>
                    <update id="updateIncorrectGNR">UPDATE COMTNGNR SET X = 1</update>
                </mapper>
                """, StandardCharsets.UTF_8);
        // 6-6 — /bbs/list.do 를 c:url 로 부르는 JSP 하나, 경로 중간 EL(jspUrl) 하나
        Path jsp = dir.resolve("src/main/webapp/WEB-INF/jsp/bbs");
        Files.createDirectories(jsp);
        Files.writeString(jsp.resolve("BoardList.jsp"), "<a href=\"<c:url value='/bbs/list.do'/>\">목록</a>\n", StandardCharsets.UTF_8);
        Files.writeString(jsp.resolve("Stf.jsp"), "<a href=\"/cop/stf${prefix}/a.do\">x</a>\n", StandardCharsets.UTF_8);
        // 6-11 — 뷰 sample/bbs/BoardDetail 이 가리키는 JSP(고아 아님)
        Path view = dir.resolve("src/main/webapp/WEB-INF/jsp/sample/bbs");
        Files.createDirectories(view);
        Files.writeString(view.resolve("BoardDetail.jsp"), "<p>상세</p>\n", StandardCharsets.UTF_8);
        return dir;
    }

    static void copy(Path from, Path to) throws Exception {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Path d = to.resolve(from.relativize(p).toString());
                Files.createDirectories(d.getParent());
                Files.copy(p, d);
            }
        }
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    static HttpResponse<String> post(String path, Object body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> raw(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode get(String path) throws Exception {
        HttpResponse<String> r = raw(path);
        assertEquals(200, r.statusCode(), path + " " + r.body());
        return JSON.readTree(r.body());
    }

    static JsonNode waitJob(HttpResponse<String> r) throws Exception {
        assertEquals(202, r.statusCode(), r.body());
        String jobId = JSON.readTree(r.body()).get("jobId").asText();
        JsonNode job = null;
        long end = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < end) {
            job = get("/api/jobs/" + jobId);
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        return job.get("result");
    }

    @Test
    void runAndHistory() throws Exception {
        JsonNode res = waitJob(post("/api/analyze/run", Map.of("path", project.toString())));
        long runId = res.get("runId").asLong();
        assertEquals(13, res.get("programs").size(), res.toString());

        JsonNode crud = get("/api/analyze/runs/" + runId + "/crud");
        Map<String, String> byUrl = new java.util.TreeMap<>();
        for (JsonNode row : crud.get("rows")) {
            byUrl.put(row.get("verb").asText() + " " + row.get("url").asText() + " " + row.get("params").asText() + " " + row.get("method").asText(),
                    row.get("crud").toString());
        }
        assertEquals("{\"COMTNBBS\":\"R\",\"COMVNUSERMASTER\":\"R\"}", byUrl.get("ANY /bbs/list.do  list"));
        assertEquals("{\"COMTNBBS\":\"C\",\"COMTNGNR\":\"U\",\"COMTNUSER\":\"U\"}", byUrl.get("POST /bbs/add.do cmd=Regist add"),
                "접두 Login.updateIncorrect → 문장 둘");
        assertTrue(crud.get("tables").toString().contains("COMTNUSER"), crud.toString());

        JsonNode un = get("/api/analyze/runs/" + runId + "/unresolved");
        String kinds = un.toString();
        for (String k : new String[] {"\"parse\"", "\"prefix\"", "\"statement\"", "\"ambiguous\"", "\"missing\"", "\"viewDynamic\""}) {
            assertTrue(kinds.contains(k), k + " — " + kinds);
        }
        assertTrue(kinds.contains("Board.selectVar"), "색인에 없는 ns.id 는 missing — " + kinds);

        JsonNode programs = get("/api/analyze/runs/" + runId + "/programs");
        assertEquals(13, programs.size());
        assertTrue(get("/api/analyze/runs").size() >= 1, "impact 시험도 실행을 남긴다");
        assertEquals(404, raw("/api/analyze/runs/999/crud").statusCode());
        assertEquals(404, post("/api/analyze/run", Map.of("path", tmp.resolve("none").toString())).statusCode());
        assertEquals(400, post("/api/analyze/run", Map.of()).statusCode());
    }

    /** 6-7 — xlsx 둘을 POI 로 다시 읽는다: 행 수 = 프로그램 수, 머리 열, 매트릭스 칸 글자 */
    /** 6-15 — JPA 픽스처 실행: 저장소 파생·상속 메서드·EntityManager·커스텀 구현이 CRUD 매트릭스에 엔티티 표로 나온다 */
    @Test
    void jpaProject() throws Exception {
        Path jp = tmp.resolve("jpaproj");
        copy(Path.of("src/test/resources/fixtures/analyze/jpa"), jp);
        JsonNode res = waitJob(post("/api/analyze/run", Map.of("path", jp.toString())));
        long runId = res.get("runId").asLong();
        JsonNode crud = get("/api/analyze/runs/" + runId + "/crud");
        Map<String, String> byUrl = new java.util.TreeMap<>();
        for (JsonNode row : crud.get("rows")) {
            byUrl.put(row.get("verb").asText() + " " + row.get("url").asText(), row.get("crud").toString());
        }
        assertEquals("{\"TB_PRODUCT\":\"R\"}", byUrl.get("GET /products"), byUrl.toString());
        assertEquals("{\"TB_PRODUCT\":\"CU\"}", byUrl.get("POST /products"), "persist C + save CU");
        assertEquals("{\"TB_PRODUCT\":\"U\"}", byUrl.get("POST /products/bulk"), "커스텀 구현의 JPQL update");
        assertEquals("{\"TB_MEMBER\":\"RU\"}", byUrl.get("GET /members"), "find·findById R + 네이티브 UPDATE");
        assertTrue(crud.get("tables").toString().contains("TB_MEMBER"), crud.toString());
    }

    @Test
    void exportXlsx() throws Exception {
        JsonNode res = waitJob(post("/api/analyze/run", Map.of("path", project.toString())));
        long runId = res.get("runId").asLong();
        HttpResponse<String> r = post("/api/analyze/runs/" + runId + "/export", Map.of());
        assertEquals(200, r.statusCode(), r.body());
        JsonNode out = JSON.readTree(r.body());
        assertEquals(2, out.get("files").size());
        Path list = Path.of(out.get("files").get(0).get("path").asText());
        Path matrix = Path.of(out.get("files").get(1).get("path").asText());
        assertEquals(list.getParent(), matrix.getParent(), "같은 시각 폴더");
        assertTrue(list.startsWith(tmp.resolve("out")), "프로필 output.dir 아래 — " + list);
        try (InputStream in = Files.newInputStream(list); Workbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            assertEquals("클래스", s.getRow(0).getCell(0).getStringCellValue());
            assertEquals("설명", s.getRow(0).getCell(8).getStringCellValue());
            assertEquals(13, s.getLastRowNum(), "머리 + 프로그램 13");
        }
        try (InputStream in = Files.newInputStream(matrix); Workbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            assertEquals("프로그램", s.getRow(0).getCell(0).getStringCellValue());
            assertEquals(13, s.getLastRowNum(), "CRUD 없는 프로그램 행도 넣는다");
            int col = -1;
            for (int c = 0; c < s.getRow(0).getLastCellNum(); c++) {
                if (s.getRow(0).getCell(c).getStringCellValue().equals("COMVNUSERMASTER")) {
                    col = c;
                }
            }
            assertTrue(col >= 2, "표 열");
            boolean r1 = false;
            for (int i = 1; i <= s.getLastRowNum(); i++) {
                if (s.getRow(i).getCell(1).getStringCellValue().equals("/bbs/list.do")) {
                    r1 = "R".equals(s.getRow(i).getCell(col).getStringCellValue());
                }
            }
            assertTrue(r1, "/bbs/list.do × COMVNUSERMASTER = R");
        }
        assertEquals(400, post("/api/analyze/runs/" + runId + "/export", Map.of("format", "hwp")).statusCode());
        assertEquals(404, post("/api/analyze/runs/999/export", Map.of()).statusCode());
    }

    /** 6-11 — 안 불리는 문장(Board.unusedOne)·뷰가 안 가리키는 JSP(Stf.jsp)·스냅샷 없으면 표 목록 빔·없는 스냅샷 404 */
    @Test
    void consistency() throws Exception {
        JsonNode res = waitJob(post("/api/analyze/run", Map.of("path", project.toString())));
        long runId = res.get("runId").asLong();
        JsonNode c = get("/api/analyze/runs/" + runId + "/consistency");
        assertTrue(c.get("deadStatements").toString().contains("Board.unusedOne"), c.toString());
        assertTrue(!c.get("deadStatements").toString().contains("\"Board.selectList\""), "불리는 문장은 아니다 — " + c);
        assertTrue(c.get("orphanJsps").toString().contains("jsp/bbs/Stf.jsp"), c.toString());
        assertTrue(!c.get("orphanJsps").toString().contains("sample/bbs/BoardDetail.jsp"), "뷰가 가리킨다 — " + c);
        assertEquals(0, c.get("missingInDb").size());
        assertEquals(0, c.get("unusedInCode").size());
        assertEquals(404, raw("/api/analyze/runs/" + runId + "/consistency?snapshotId=999").statusCode());
        assertEquals(400, raw("/api/analyze/runs/" + runId + "/consistency?snapshotId=x").statusCode());
        assertEquals(404, raw("/api/analyze/runs/999/consistency").statusCode());
    }

    @Test
    void impact() throws Exception {
        JsonNode res = waitJob(post("/api/analyze/run", Map.of("path", project.toString())));
        long runId = res.get("runId").asLong();
        JsonNode im = get("/api/analyze/runs/" + runId + "/impact?table=comtnbbs");
        assertEquals("COMTNBBS", im.get("table").asText());
        assertTrue(im.get("rows").size() >= 1, im.toString());
        assertEquals("[\"src/main/webapp/WEB-INF/jsp/bbs/BoardList.jsp\"]", im.get("jsps").toString(), im.toString());
        String un = get("/api/analyze/runs/" + runId + "/unresolved").toString();
        assertTrue(un.contains("\"jspUrl\"") && un.contains("/cop/stf${}/a.do") && !un.contains("${prefix}"), "EL 식은 비운다(6-10) — " + un);
        assertEquals(0, get("/api/analyze/runs/" + runId + "/impact?table=NOPE").get("rows").size());
        assertEquals(400, raw("/api/analyze/runs/" + runId + "/impact?table=").statusCode());
        assertEquals(400, raw("/api/analyze/runs/" + runId + "/impact").statusCode());
        assertEquals(404, raw("/api/analyze/runs/999/impact?table=X").statusCode());
    }
}
