package kr.ejg.toolbox.core.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbTest {

    private static final List<String> TABLES = List.of(
            "DICT_WORD", "SNAPSHOT", "SNAP_TABLE", "SNAP_COLUMN", "SNAP_CONSTRAINT", "SNAP_INDEX",
            "CHECK_RUN", "CHECK_FINDING");

    @TempDir
    Path tmp;

    private static List<String> columns(Connection c, String table) throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = c.getMetaData().getColumns(null, "PUBLIC", table, null)) {
            while (rs.next()) {
                out.add(rs.getString("COLUMN_NAME"));
            }
        }
        return out;
    }

    @Test
    void firstOpenCreatesFileAndTables() throws Exception {
        try (Db db = Db.open(tmp)) {
            assertTrue(Files.isRegularFile(tmp.resolve("toolbox.mv.db")));
            assertEquals(1, db.schemaVersion());
            try (Connection c = db.connect()) {
                for (String t : TABLES) {
                    assertFalse(columns(c, t).isEmpty(), t + " 가 있어야 한다");
                }
            }
        }
    }

    @Test
    void reopenDoesNotReapply() throws Exception {
        try (Db db = Db.open(tmp); Connection c = db.connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO snapshot(profile, conn_id) VALUES ('p', 'dev')");
        }
        try (Db db = Db.open(tmp); Connection c = db.connect(); Statement st = c.createStatement()) {
            assertEquals(1, db.schemaVersion());
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "V001 은 한 번만");
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM snapshot")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "데이터가 남아 있어야 한다");
            }
        }
    }

    /** 절대 규칙 3 — 검사 이력에 코드 본문을 담을 자리가 없다 */
    @Test
    void checkFindingHasNoBodyColumn() throws Exception {
        try (Db db = Db.open(tmp); Connection c = db.connect()) {
            List<String> cols = columns(c, "CHECK_FINDING");
            assertEquals(List.of("RUN_ID", "FILE", "LINE", "GRP", "RULE", "SEVERITY"), cols);
            for (String t : TABLES) {
                for (String col : columns(c, t)) {
                    String n = col.toLowerCase(Locale.ROOT);
                    boolean forbidden = n.contains("pass") || n.contains("body") || n.contains("snippet")
                            || n.contains("content") || n.contains("text") || n.contains("sql");
                    assertFalse(forbidden, "본문·비밀번호로 보이는 컬럼: " + t + "." + col);
                }
            }
        }
    }

    @Test
    void splitDropsCommentsAndSplitsOnLineEndSemicolons() {
        assertEquals(List.of("CREATE TABLE a (x INT)", "CREATE INDEX i ON a(x)"),
                Migrator.split("-- 주석\nCREATE TABLE a (x INT);\n\n-- 또\nCREATE INDEX i ON a(x);\n"));
    }

    @Test
    void secondProcessGetsLockedException() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                DbHolder.class.getName(), tmp.toString())
                .redirectErrorStream(true)
                .start();
        try {
            BufferedReader out = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
            String line;
            boolean ready = false;
            while ((line = out.readLine()) != null) {
                if (line.contains("ready")) {
                    ready = true;
                    break;
                }
            }
            assertTrue(ready, "자식 JVM 이 H2 를 열어야 한다");
            Db.LockedException e = assertThrows(Db.LockedException.class, () -> Db.open(tmp));
            assertEquals(tmp.toAbsolutePath().normalize().resolve("toolbox.mv.db"), e.file());
        } finally {
            child.getOutputStream().close();
            if (!child.waitFor(10, TimeUnit.SECONDS)) {
                child.destroyForcibly();
            }
        }
    }
}
