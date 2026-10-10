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
            "CHECK_RUN", "CHECK_FINDING",
            "ANALYZE_RUN", "ANALYZE_PROGRAM", "ANALYZE_VIEW", "ANALYZE_STMT", "ANALYZE_CRUD", "ANALYZE_UNRESOLVED",
            "ANALYZE_JSP_LINK", "ANALYZE_ORPHAN", "ANALYZE_VIEW_FILE", "ANALYZE_MENU");

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
            assertEquals(10, db.schemaVersion()); // V001 + V002(6-4) + V003(1-14) + V004~V007(1-19~1-23) + V008(PR #42) + V009(6-13) + V010(6-26~6-29)
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
            assertEquals(10, db.schemaVersion());
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                rs.next();
                assertEquals(10, rs.getInt(1), "V001~V010 각 한 번만");
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM snapshot")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "데이터가 남아 있어야 한다");
            }
        }
    }

    /** 0-30 — 적용 때와 파일 해시가 다르면 다음 기동이 멈춘다. 해시 칸이 빈 옛 행은 채운다 */
    @Test
    void changedAppliedMigrationStopsStartup() throws Exception {
        try (Db db = Db.open(tmp); Connection c = db.connect(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT checksum FROM schema_version WHERE version = 1")) {
                rs.next();
                assertEquals(64, rs.getString(1).length(), "적용 때 SHA-256 을 적는다");
            }
            st.execute("UPDATE schema_version SET checksum = NULL");
        }
        try (Db db = Db.open(tmp); Connection c = db.connect(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT checksum FROM schema_version WHERE version = 1")) {
                rs.next();
                assertEquals(64, rs.getString(1).length(), "0-30 전 행은 다음 기동이 채운다");
            }
            st.execute("UPDATE schema_version SET checksum = '" + "0".repeat(64) + "' WHERE version = 1"); // 반입된 V001 을 고친 것과 같다
        }
        IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> Db.open(tmp).close());
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("V001__init.sql 이 바뀌었다") && e.getMessage().contains("새 번호"),
                e.getMessage());
        assertEquals(Migrator.checksum("a\nb\n"), Migrator.checksum("a\r\nb\r\n"), "줄바꿈에 안 흔들린다");
        IllegalStateException again = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> Db.open(tmp).close());
        org.junit.jupiter.api.Assertions.assertTrue(again.getMessage().contains("바뀌었다"),
                "실패한 열기가 잠금을 풀어 두 번째도 잠금 예외가 아니라 같은 사유: " + again.getMessage());
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
