package kr.ejg.toolbox.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 8-4 — deliverable·ddl·dto·generate 를 H2 스냅샷 하나로 */
class DocCliTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tmp;

    CliFixture fx;

    @BeforeEach
    void up() throws Exception {
        fx = new CliFixture(tmp,
                "CREATE TABLE TB_DEPT (DEPT_NO INTEGER PRIMARY KEY, DEPT_NM VARCHAR(30))",
                "COMMENT ON TABLE TB_DEPT IS '부서'",
                "CREATE TABLE TB_EMP (EMP_NO INTEGER PRIMARY KEY, EMP_NM VARCHAR(30), DEPT_NO INTEGER REFERENCES TB_DEPT(DEPT_NO))")
                .withTemplates();
        fx.snapshot();
    }

    @AfterEach
    void down() throws Exception {
        fx.close();
    }

    @Test
    void ddlWritesFile() throws Exception {
        CliFixture.Run r = fx.run("ddl", "--snapshot", "latest", "--target", "postgresql", "--profile", "t", "--json");
        assertEquals(0, r.code(), r.err());
        JsonNode n = JSON.readTree(r.out());
        assertTrue(Files.readString(Path.of(n.path("path").asText())).contains("CREATE TABLE TB_EMP ("), r.out());
        assertEquals(2, n.path("tables").asInt());
        CliFixture.Run bad = fx.run("ddl", "--snapshot", "latest", "--target", "sybase", "--profile", "t");
        assertEquals(1, bad.code());
        assertTrue(bad.err().contains("대상 방언"), bad.err());
    }

    /** 8-15 — 구조 점검 xlsx. H2 는 FK 인덱스를 스스로 만들어 fkNoIndex 0, TB_DEPT 는 가리켜지고 TB_EMP 는 FK 가 있어 noRelation 0 */
    @Test
    void lintWritesFile() throws Exception {
        CliFixture.Run r = fx.run("lint", "--snapshot", "latest", "--profile", "t", "--json");
        assertEquals(0, r.code(), r.err());
        JsonNode n = JSON.readTree(r.out());
        assertTrue(Files.exists(Path.of(n.path("path").asText())), r.out());
        assertTrue(n.path("path").asText().endsWith(".xlsx"), r.out());
        assertEquals(2, n.path("tables").asInt());
        assertEquals(15, n.path("rules").size());
        java.util.List<String> rules = new java.util.ArrayList<>();
        n.path("findings").forEach(f -> rules.add(f.path("rule").asText()));
        assertTrue(!rules.contains("fkNoIndex") && !rules.contains("noRelation"), r.out());
        CliFixture.Run plain = fx.run("lint", "--snapshot", "latest", "--profile", "t");
        assertEquals(0, plain.code(), plain.err());
        assertTrue(plain.out().contains(" — 표 2 · 지적 "), plain.out());
    }

    @Test
    void dtoWritesFiles() throws Exception {
        CliFixture.Run r = fx.run("dto", "--snapshot", "latest", "--tables", "TB_EMP,TB_DEPT", "--package", "kr.go.t", "--profile", "t");
        assertEquals(0, r.code(), r.err());
        Path dir = Path.of(r.out().split(" — ")[0]);
        try (var s = Files.list(dir)) {
            assertEquals(2, s.filter(p -> p.toString().endsWith(".java")).count(), r.out());
        }
        assertTrue(Files.readString(emp(dir)).contains("@Size(max = 30)"), "1-31c 기본은 검증 어노테이션");
    }

    /** 1-31c — --no-validation 이면 검증 어노테이션이 없다 */
    @Test
    void dtoNoValidation() throws Exception {
        CliFixture.Run r = fx.run("dto", "--snapshot", "latest", "--tables", "TB_EMP", "--no-validation", "--profile", "t");
        assertEquals(0, r.code(), r.err());
        Path dir = Path.of(r.out().split(" — ")[0]);
        assertTrue(!Files.readString(emp(dir)).contains("validation.constraints"), r.out());
    }

    static Path emp(Path dir) throws java.io.IOException {
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith("Emp.java")).findFirst().orElseThrow();
        }
    }

    @Test
    void generateWritesSources() throws Exception {
        Path out = tmp.resolve("proj");
        Files.createDirectories(out);
        CliFixture.Run r = fx.run("generate", "--snapshot", "latest", "--tables", "TB_EMP", "--set", "egov35", "--package", "kr.go.t",
                "--out", "proj", "--profile", "t", "--json");
        assertEquals(0, r.code(), r.err());
        JsonNode n = JSON.readTree(r.out());
        assertEquals(10, n.path("created").asInt(), r.out());
        assertEquals(out.toAbsolutePath().normalize(), Path.of(n.path("outDir").asText()).toAbsolutePath().normalize(), "상대 --out 은 부른 폴더 기준");
        for (JsonNode f : n.path("files")) {
            assertTrue(Files.exists(out.resolve(f.path("rel").asText())), f.toString());
        }
        CliFixture.Run none = fx.run("generate", "--snapshot", "latest", "--tables", "NOPE", "--out", "proj", "--profile", "t");
        assertEquals(1, none.code(), none.err());
    }

    @Test
    void deliverableBuildsXlsx() throws Exception {
        CliFixture.Run r = fx.run("deliverable", "--snapshot", "latest", "--docs", "01,02,03", "--author", "시험", "--profile", "t", "--json");
        assertEquals(0, r.code(), r.err());
        JsonNode n = JSON.readTree(r.out());
        assertEquals(3, n.path("files").size(), r.out());
        for (JsonNode f : n.path("files")) {
            assertTrue(Files.exists(Path.of(f.asText())), f.asText());
        }
    }

    /** 8-24(G-14) — --docs 로 고른 문서가 건너뛰어지면 끝 코드 1(08 은 배치가 코드 표를 못 보낸다) */
    /** 8-25(G4-03) — 「만들었나」 는 응답의 made — 파일 이름이 번호로 시작하지 않아도(사용자 양식 이름) 건너뜀 알림이 있어도 성공 */
    @Test
    void madeDecidesNotFileNames() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode made = om.readTree("{\"files\":[\"/o/tabledef.xlsx\"],\"skipped\":[\"02 — 알림\"],\"made\":[\"02\"]}");
        assertFalse(DocCommands.Deliverable.skippedChosen(java.util.List.of("02"), made));
        JsonNode none = om.readTree("{\"files\":[],\"skipped\":[\"08 — 접속 없음\"],\"made\":[]}");
        assertTrue(DocCommands.Deliverable.skippedChosen(java.util.List.of("08"), none));
    }

    @Test
    void chosenSkippedDocFails() throws Exception {
        CliFixture.Run r = fx.run("deliverable", "--snapshot", "latest", "--docs", "08", "--code-conn", "h2", "--profile", "t");
        assertEquals(1, r.code(), r.out() + r.err());
        assertTrue(r.err().contains("[건너뜀] 08"), r.err());
    }

    /** G2-K15 — --docs 의 모르는 번호는 끝 코드 2, 한 자리 수는 앞에 0 */
    @Test
    void unknownDocNumberIsRefused() throws Exception {
        CliFixture.Run bad = fx.run("deliverable", "--snapshot", "latest", "--docs", "01,99", "--profile", "t");
        assertEquals(2, bad.code(), bad.err());
        assertTrue(bad.err().contains("모르는 문서 번호: 99"), bad.err());
        CliFixture.Run ok = fx.run("deliverable", "--snapshot", "latest", "--docs", "1, 2", "--profile", "t", "--json");
        assertEquals(0, ok.code(), ok.err());
        assertEquals(2, JSON.readTree(ok.out()).path("files").size(), ok.out());
    }
}
