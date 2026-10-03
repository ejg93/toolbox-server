package kr.ejg.toolbox.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void dtoWritesFiles() throws Exception {
        CliFixture.Run r = fx.run("dto", "--snapshot", "latest", "--tables", "TB_EMP,TB_DEPT", "--package", "kr.go.t", "--profile", "t");
        assertEquals(0, r.code(), r.err());
        Path dir = Path.of(r.out().split(" — ")[0]);
        try (var s = Files.list(dir)) {
            assertEquals(2, s.filter(p -> p.toString().endsWith(".java")).count(), r.out());
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
}
