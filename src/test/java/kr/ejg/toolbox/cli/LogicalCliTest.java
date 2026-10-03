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

/** 8-5 — logical comments·candidates·audit·masking. CSV 입력은 표본 1000컬럼, 스냅샷 입력은 H2 */
class LogicalCliTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String CSV = Path.of("src/test/resources/sample/logical/columns-1000.csv").toAbsolutePath().toString();

    @TempDir
    Path tmp;

    CliFixture fx;

    @BeforeEach
    void up() throws Exception {
        fx = new CliFixture(tmp,
                "CREATE TABLE TB_MBER (MBER_ID VARCHAR(20) PRIMARY KEY, MBER_NM VARCHAR(50), MBTLNUM VARCHAR(20), IHIDNUM VARCHAR(20))",
                "COMMENT ON COLUMN TB_MBER.IHIDNUM IS '주민등록번호'");
        fx.snapshot();
    }

    @AfterEach
    void down() throws Exception {
        fx.close();
    }

    static Path path(CliFixture.Run r) throws Exception {
        return Path.of(JSON.readTree(r.out()).path("path").asText());
    }

    @Test
    void csvInput() throws Exception {
        CliFixture.Run c = fx.run("logical", "comments", "--csv", CSV, "--dialect", "postgresql", "--profile", "t", "--json");
        assertEquals(0, c.code(), c.err());
        assertTrue(Files.readString(path(c)).contains("COMMENT ON COLUMN"), c.out());

        for (String kind : new String[] {"terms", "words", "domains", "wordUse"}) {
            CliFixture.Run k = fx.run("logical", "candidates", "--csv", CSV, "--kind", kind, "--profile", "t", "--json");
            assertEquals(0, k.code(), kind + " " + k.err());
            assertTrue(Files.exists(path(k)), k.out());
        }

        CliFixture.Run m = fx.run("logical", "masking", "--csv", CSV, "--profile", "t", "--json");
        assertEquals(0, m.code(), m.err());
        assertTrue(Files.readString(path(m)).startsWith("-- 개인정보 마스킹 UPDATE"), m.out());
    }

    @Test
    void snapshotInput() throws Exception {
        CliFixture.Run m = fx.run("logical", "masking", "--snapshot", "latest", "--exclude", "TB_MBER.MBTLNUM", "--profile", "t", "--json");
        assertEquals(0, m.code(), m.err());
        JsonNode n = JSON.readTree(m.out());
        boolean rrn = false;
        for (JsonNode c : n.path("candidates")) {
            rrn |= c.path("col").asText().equals("IHIDNUM") && c.path("kind").asText().equals("rrn");
        }
        assertTrue(rrn, "코멘트로 주민번호를 잡는다: " + m.out());
        String sql = Files.readString(path(m));
        assertTrue(sql.contains("IHIDNUM ="), sql);
        assertFalse(sql.contains("MBTLNUM ="), "--exclude 로 뺀 컬럼은 SQL 에 없다");

        CliFixture.Run a = fx.run("logical", "audit", "--snapshot", "latest", "--profile", "t", "--json");
        assertEquals(0, a.code(), a.err());
        assertTrue(Files.exists(path(a)), a.out());
    }

    @Test
    void failures() throws Exception {
        assertEquals(2, fx.run("logical", "comments", "--profile", "t").code(), "입력 없음");
        assertEquals(2, fx.run("logical", "comments", "--csv", CSV, "--snapshot", "latest", "--profile", "t").code(), "입력 둘");
        assertEquals(1, fx.run("logical", "candidates", "--csv", CSV, "--kind", "nope", "--profile", "t").code(), "모르는 kind");
        assertEquals(2, fx.run("logical", "masking", "--csv", CSV, "--exclude", "MBTLNUM", "--profile", "t").code(), "표.컬럼 아님");
        picocli.CommandLine cmd = Main.commandLine();
        java.io.StringWriter out = new java.io.StringWriter();
        cmd.setOut(new java.io.PrintWriter(out));
        assertEquals(0, cmd.execute("logical"), "부모만 부르면 사용법");
        assertTrue(out.toString().contains("masking"), out.toString());
    }
}
