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
import java.io.InputStream;
import java.util.List;
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
        // 6-24 — 정합성 시트용 스냅샷 하나(H2 mem 표 COMTNBBS)
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nframework: egov35\n"
                + "connections:\n  - id: h2\n    dialect: h2\n    url: jdbc:h2:mem:analyzeroutes;DB_CLOSE_DELAY=-1\n    user: sa\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
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
        // 6-21 — 세로 목록 = CRUD 쌍 수, 모듈 매트릭스 열 bbs·other(클래스 @RequestMapping("/other") + 메서드 /both.do 라 /other/both.do)
        int pairs = 0;
        for (JsonNode row : crud.get("rows")) {
            pairs += row.get("crud").size();
        }
        assertEquals(pairs, crud.get("longRows").size(), crud.get("longRows").toString());
        String modules = crud.get("moduleMatrix").get("modules").toString();
        assertEquals("[\"bbs\",\"other\"]", modules);
        boolean bbsRead = false;
        for (JsonNode r : crud.get("moduleMatrix").get("rows")) {
            if (r.get("table").asText().equals("COMTNBBS")) {
                bbsRead = r.get("cells").path("bbs").asText().contains("R");
            }
        }
        assertTrue(bbsRead, crud.get("moduleMatrix").toString());

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

    /** 6-28 — 화면 전수: /bbs/list.do 는 view sample/bbs/BoardList(JSP 파일 없음), jsp/bbs/BoardList.jsp 가 링크로 부른다 · json 은 제외 */
    @Test
    void screens() throws Exception {
        JsonNode res = waitJob(post("/api/analyze/run", Map.of("path", project.toString())));
        long runId = res.get("runId").asLong();
        JsonNode s = get("/api/analyze/runs/" + runId + "/screens");
        JsonNode list = null;
        for (JsonNode r : s.get("rows")) {
            if (r.get("url").asText().equals("/bbs/list.do")) {
                list = r;
            }
        }
        assertTrue(list != null, s.toString());
        assertEquals("[\"sample/bbs/BoardList\"]", list.get("views").toString());
        assertEquals("없음", list.get("jspFile").asText());
        assertEquals("bbs", list.get("module").asText());
        assertEquals("[{\"jsp\":\"src/main/webapp/WEB-INF/jsp/bbs/BoardList.jsp\",\"kinds\":[\"link\"]}]", list.get("callers").toString());
        assertTrue(s.get("excluded").toString().contains("\"json\""), s.get("excluded").toString());
        assertFalse(s.get("menuLoaded").asBoolean());
        assertEquals(404, raw("/api/analyze/runs/999/screens").statusCode());

        // 6-29 — 메뉴를 올리면 일치·메뉴만, xlsx 에 「메뉴만」 시트(화면전수 뒤). 끝에 지워 다른 시험에 안 번지게
        assertEquals(200, post("/api/analyze/menu", Map.of("csv", MENU_CSV)).statusCode());
        try {
            JsonNode m = get("/api/analyze/runs/" + runId + "/screens");
            assertTrue(m.get("menuLoaded").asBoolean());
            for (JsonNode r : m.get("rows")) {
                if (r.get("url").asText().equals("/bbs/list.do")) {
                    assertEquals("일치", r.get("menuBasis").asText(), r.toString());
                    assertEquals("[\"게시판 > 목록\"]", r.get("menuPaths").toString());
                }
            }
            assertTrue(m.get("menuOnly").toString().contains("/nope.do"), m.get("menuOnly").toString());
            JsonNode x = JSON.readTree(post("/api/analyze/runs/" + runId + "/export", Map.of()).body());
            assertEquals("[\"프로그램목록\",\"CRUD목록\",\"CRUD모듈\",\"화면전수\",\"메뉴만\",\"미해결\"]", names(x.get("sheets")));
        } finally {
            delete("/api/analyze/menu");
        }
        JsonNode x2 = JSON.readTree(post("/api/analyze/runs/" + runId + "/export", Map.of()).body());
        assertFalse(names(x2.get("sheets")).contains("메뉴만"), "메뉴를 지우면 시트도 빠진다");
    }

    static final String MENU_CSV = "메뉴,URL,사용여부\n게시판 > 목록,/bbs/list.do,Y\n게시판 > 없는,/nope.do,N\n관리,,Y\n";

    static HttpResponse<String> delete(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** 6-29 — 메뉴 CSV: 프로필마다 한 벌, 두 번 올려도 통째로 바뀐다 · 틀린 헤더 400 · 지우기 */
    @Test
    void menuCsv() throws Exception {
        delete("/api/analyze/menu");
        assertFalse(get("/api/analyze/menu").get("loaded").asBoolean());
        HttpResponse<String> up = post("/api/analyze/menu", Map.of("csv", MENU_CSV));
        assertEquals(200, up.statusCode(), up.body());
        assertEquals(3, JSON.readTree(up.body()).get("rows").asInt());
        assertEquals(2, JSON.readTree(up.body()).get("withUrl").asInt());
        assertEquals(200, post("/api/analyze/menu", Map.of("csv", MENU_CSV)).statusCode(), "두 번째도 PK 충돌 없이 — 통째로 바꾼다");
        JsonNode info = get("/api/analyze/menu");
        assertTrue(info.get("loaded").asBoolean());
        assertEquals(3, info.get("rows").asInt());
        HttpResponse<String> bad = post("/api/analyze/menu", Map.of("csv", "이름,값\nA,B\n"));
        assertEquals(400, bad.statusCode());
        assertTrue(bad.body().contains("메뉴 경로 열"), bad.body());
        assertEquals(400, post("/api/analyze/menu", Map.of("csv", " ")).statusCode());
        assertEquals(3, JSON.readTree(delete("/api/analyze/menu").body()).get("deleted").asInt());
        assertFalse(get("/api/analyze/menu").get("loaded").asBoolean());
    }

    /** 6-22 — 미해결 종류 글: KINDS 순서 18, 셋 다 있음, runAndHistory 가 보는 여섯 포함 */
    @Test
    void unresolvedKinds() throws Exception {
        JsonNode k = get("/api/analyze/unresolved-kinds");
        assertEquals(18, k.size(), k.toString());
        assertEquals("parse", k.get(0).get("kind").asText());
        java.util.Set<String> kinds = new java.util.HashSet<>();
        for (JsonNode x : k) {
            kinds.add(x.get("kind").asText());
            for (String f : new String[] {"name", "meaning", "fix"}) {
                assertTrue(!x.get(f).asText().isBlank(), x.toString());
            }
        }
        assertTrue(kinds.containsAll(java.util.List.of("parse", "prefix", "statement", "ambiguous", "missing", "viewDynamic")), kinds.toString());
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
        // 6-24 — 한 파일 시트 넷(스냅샷 없이 — 정합성 없음)
        assertEquals(1, out.get("files").size());
        Path list = Path.of(out.get("files").get(0).get("path").asText());
        assertEquals("프로그램분석-" + runId + ".xlsx", list.getFileName().toString());
        assertTrue(list.startsWith(tmp.resolve("out")), "프로필 output.dir 아래 — " + list);
        assertEquals("[\"프로그램목록\",\"CRUD목록\",\"CRUD모듈\",\"화면전수\",\"미해결\"]", names(out.get("sheets")));
        int pairs = get("/api/analyze/runs/" + runId + "/crud").get("longRows").size();
        try (InputStream in = Files.newInputStream(list); Workbook wb = new XSSFWorkbook(in)) {
            assertEquals(5, wb.getNumberOfSheets());
            Sheet s = wb.getSheetAt(0);
            assertEquals("클래스", s.getRow(0).getCell(0).getStringCellValue());
            assertEquals("설명", s.getRow(0).getCell(8).getStringCellValue());
            // 6-18 — 화면과 같은 표시 이름: 머리 view, 값 jsp:·page. 저장 값(view)은 안 바뀐다
            assertEquals("view", s.getRow(0).getCell(6).getStringCellValue());
            StringBuilder kinds = new StringBuilder();
            StringBuilder views = new StringBuilder();
            for (int i = 1; i <= s.getLastRowNum(); i++) {
                kinds.append(s.getRow(i).getCell(5).getStringCellValue()).append(' ');
                views.append(s.getRow(i).getCell(6).getStringCellValue()).append(' ');
            }
            assertTrue(kinds.toString().contains("page") && !kinds.toString().contains("view"), kinds.toString());
            assertTrue(views.toString().contains("jsp: ") && !views.toString().contains("view:"), views.toString());
            assertEquals(13, s.getLastRowNum(), "머리 + 프로그램 13");
            // CRUD목록 — 세로 목록, 행 수 = /crud longRows
            Sheet l = wb.getSheetAt(1);
            assertEquals("프로그램|URL|모듈|표|CRUD", head(l, 5));
            assertEquals(pairs, l.getLastRowNum(), "머리 + CRUD 쌍");
            boolean r1 = false;
            for (int i = 1; i <= l.getLastRowNum(); i++) {
                if (l.getRow(i).getCell(1).getStringCellValue().equals("/bbs/list.do") && l.getRow(i).getCell(3).getStringCellValue().equals("COMVNUSERMASTER")) {
                    r1 = "R".equals(l.getRow(i).getCell(4).getStringCellValue()) && "bbs".equals(l.getRow(i).getCell(2).getStringCellValue());
                }
            }
            assertTrue(r1, "/bbs/list.do × COMVNUSERMASTER = R, 모듈 bbs");
            // CRUD모듈 — 행 = 표, 열 = 모듈
            Sheet m = wb.getSheetAt(2);
            assertEquals("표|bbs|other", head(m, 3));
            // 미해결 — 코드 옆에 이름·뜻
            // 6-28 화면전수 — 행 수 = /screens rows
            Sheet sc = wb.getSheetAt(3);
            assertEquals("No|모듈|URL", head(sc, 3));
            assertEquals(get("/api/analyze/runs/" + runId + "/screens").get("rows").size(), sc.getLastRowNum());
            Sheet u = wb.getSheetAt(4);
            assertEquals("종류|이름|뜻|파일|줄|식별자", head(u, 6));
            assertTrue(u.getLastRowNum() >= 1);
            assertTrue(u.getRow(1).getCell(1).getStringCellValue().matches("[가-힣A-Z].*"), u.getRow(1).getCell(1).getStringCellValue());
        }
        // 스냅샷을 고르면 정합성 시트 — 사유 열(프로필 scope 없음 → 「없음」)
        long snap = snapshot();
        JsonNode out2 = JSON.readTree(post("/api/analyze/runs/" + runId + "/export", Map.of("snapshotId", snap)).body());
        assertEquals("[\"프로그램목록\",\"CRUD목록\",\"CRUD모듈\",\"화면전수\",\"미해결\",\"정합성\"]", names(out2.get("sheets")));
        try (InputStream in = Files.newInputStream(Path.of(out2.get("files").get(0).get("path").asText())); Workbook wb = new XSSFWorkbook(in)) {
            Sheet c = wb.getSheet("정합성");
            assertEquals("구분|이름|스키마|종류|프로그램 수|사유", head(c, 6));
            StringBuilder rows = new StringBuilder();
            for (int i = 1; i <= c.getLastRowNum(); i++) {
                for (int k = 0; k < 6; k++) {
                    rows.append(c.getRow(i).getCell(k) == null ? "" : c.getRow(i).getCell(k).getStringCellValue()).append('|');
                }
                rows.append('\n');
            }
            assertTrue(rows.toString().contains("DB 에 없는 표|COMVNUSERMASTER|||2|없음|"), rows.toString());
            assertTrue(rows.toString().contains("안 불리는 문장|Board.unusedOne|"), rows.toString());
            assertTrue(rows.toString().contains("없는 JSP|sample/bbs/BoardList|"), rows.toString()); // 6-26
            assertFalse(rows.toString().contains("DB 에 없는 표|COMTNBBS|"), "스냅샷에 있는 표 — " + rows);
        }
        assertEquals(404, post("/api/analyze/runs/" + runId + "/export", Map.of("snapshotId", 999)).statusCode());
        assertEquals(400, post("/api/analyze/runs/" + runId + "/export", Map.of("format", "hwp")).statusCode());
        assertEquals(404, post("/api/analyze/runs/999/export", Map.of()).statusCode());
    }

    static String names(JsonNode sheets) {
        List<String> out = new java.util.ArrayList<>();
        sheets.forEach(x -> out.add(x.get("name").asText()));
        try {
            return JSON.writeValueAsString(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String head(Sheet s, int n) {
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(s.getRow(0).getCell(i).getStringCellValue());
        }
        return String.join("|", out);
    }

    static Long snapId;

    /** 6-24 — H2 mem 표 COMTNBBS 하나를 스냅샷으로(프로필 scope 없음 → Scope.all() 저장). 한 번만 찍는다 */
    static synchronized long snapshot() throws Exception {
        if (snapId != null) {
            return snapId;
        }
        try (java.sql.Connection h = java.sql.DriverManager.getConnection("jdbc:h2:mem:analyzeroutes;DB_CLOSE_DELAY=-1", "sa", "pw");
                java.sql.Statement st = h.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS COMTNBBS (ID INT)");
        }
        assertEquals(200, post("/api/conn/h2/password", Map.of("password", "pw")).statusCode());
        waitJob(post("/api/meta/snapshot", Map.of("connId", "h2")));
        snapId = get("/api/meta/snapshots").get(0).get("id").asLong();
        return snapId;
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
        // 6-26 — view 가 가리키는데 없는 JSP: 픽스처 JSP 는 jsp/bbs/BoardList.jsp 라 view sample/bbs/BoardList 와 안 맞는다
        String mj = c.get("missingJsps").toString();
        assertTrue(mj.contains("\"sample/bbs/BoardList\"") && !mj.contains("sample/bbs/BoardDetail"), mj);
        assertEquals(0, c.get("unusedInCode").size());
        // 6-23 — 스냅샷을 고르면 범위 한 줄과 사유
        JsonNode cs = get("/api/analyze/runs/" + runId + "/consistency?snapshotId=" + snapshot());
        assertEquals("없음(전부)", cs.get("scopeSummary").asText(), cs.toString());
        assertTrue(cs.get("missingInDb").size() >= 1, cs.toString());
        cs.get("missingInDb").forEach(x -> assertEquals("없음", x.get("reason").asText(), x.toString()));
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
