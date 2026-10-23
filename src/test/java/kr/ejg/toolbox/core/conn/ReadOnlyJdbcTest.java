package kr.ejg.toolbox.core.conn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 9-1a — 읽기 전용 래퍼: 통과·거절 표, 실물에 닿는 길, 거절 뒤 표 그대로, 메시지에 SQL 본문 없음 */
class ReadOnlyJdbcTest {

    private Connection raw;
    private Connection ro;

    @BeforeEach
    void up() throws SQLException {
        raw = DriverManager.getConnection("jdbc:h2:mem:ro" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement st = raw.createStatement()) {
            st.execute("CREATE TABLE T(A INT)");
            st.execute("INSERT INTO T VALUES(1)");
            st.execute("CREATE TABLE T2(UPDATE_RULE VARCHAR(10), \"UPDATE\" INT)");
            st.execute("INSERT INTO T2 VALUES('C', 1)");
        }
        ro = ReadOnlyJdbc.wrap(raw);
    }

    @AfterEach
    void down() throws SQLException {
        raw.close();
    }

    @Test
    void readsPass() {
        for (String sql : List.of("SELECT 1", "select 1", "WITH c(X) AS (SELECT 1) SELECT X FROM c", "/* 주석 */ -- 줄\nSELECT 1", "SELECT 1;",
                "SELECT 'INSERT INTO x' AS S", "SELECT \"UPDATE\" FROM T2", "SELECT UPDATE_RULE FROM T2")) {
            assertDoesNotThrow(() -> {
                try (Statement st = ro.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                    assertTrue(rs.next(), sql);
                }
            }, sql);
        }
    }

    @Test
    void writesRefused() throws SQLException {
        for (String sql : List.of("INSERT INTO T VALUES(2)", "UPDATE T SET A=1", "DELETE FROM T", "CREATE TABLE Z(A INT)", "DROP TABLE T",
                "MERGE INTO T KEY(A) VALUES(3)", "CALL 1", "SELECT * INTO Z FROM T", "SELECT * FROM T FOR UPDATE", "SELECT 1; SELECT 2",
                "WITH x AS (SELECT 1) DELETE FROM T", "   ")) {
            try (Statement st = ro.createStatement()) {
                SQLException e = assertThrows(SQLFeatureNotSupportedException.class, () -> st.execute(sql), sql);
                assertFalse(e.getMessage().contains("VALUES") || e.getMessage().contains("FROM"), "메시지에 SQL 본문: " + e.getMessage());
            }
            assertThrows(SQLFeatureNotSupportedException.class, () -> ro.prepareStatement(sql), sql);
        }
        assertEquals(1, count(), "거절 뒤 표 그대로");
        try (Statement st = raw.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'Z'")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "CREATE 는 안 돌았다");
        }
    }

    @Test
    void writePathsRefused() throws SQLException {
        assertThrows(SQLFeatureNotSupportedException.class, () -> ro.prepareCall("CALL 1"));
        assertThrows(SQLFeatureNotSupportedException.class, () -> ro.setReadOnly(false));
        assertThrows(SQLFeatureNotSupportedException.class, () -> ro.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE));
        assertThrows(SQLFeatureNotSupportedException.class,
                () -> ro.prepareStatement("SELECT A FROM T", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE));
        try (PreparedStatement ps = ro.prepareStatement("SELECT A FROM T")) {
            assertThrows(SQLFeatureNotSupportedException.class, ps::executeUpdate);
            assertThrows(SQLFeatureNotSupportedException.class, ps::executeBatch);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertThrows(SQLFeatureNotSupportedException.class, rs::updateRow);
                assertThrows(SQLFeatureNotSupportedException.class, rs::deleteRow);
                assertSame(ps, rs.getStatement(), "결과 집합의 문장도 래퍼");
            }
        }
        try (Statement st = ro.createStatement()) {
            assertThrows(SQLFeatureNotSupportedException.class, () -> st.addBatch("INSERT INTO T VALUES(9)"));
            assertThrows(SQLFeatureNotSupportedException.class, () -> st.executeUpdate("SELECT 1"));
            assertThrows(SQLFeatureNotSupportedException.class, st::executeBatch);
            assertSame(ro, st.getConnection(), "문장의 연결은 래퍼");
            assertTrue(Proxy.isProxyClass(st.getClass()));
        }
        assertThrows(SQLFeatureNotSupportedException.class, () -> ro.unwrap(Connection.class.getClass()));
        assertSame(ro, ro.unwrap(Connection.class), "unwrap 은 실물을 안 준다");
        assertEquals(1, count());
    }

    @Test
    void metadataWorksWithoutLeak() throws SQLException {
        try (ResultSet rs = ro.getMetaData().getTables(null, null, "T", null)) {
            assertTrue(rs.next());
            assertEquals("T", rs.getString("TABLE_NAME"));
        }
        assertSame(ro, ro.getMetaData().getConnection(), "메타데이터의 연결은 래퍼");
    }

    private int count() throws SQLException {
        try (Statement st = raw.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM T")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
