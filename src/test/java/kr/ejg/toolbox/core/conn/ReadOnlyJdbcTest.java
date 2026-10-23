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
                "SELECT 'a' AS S", "SELECT A FROM T WHERE 'x' = 'x'", "SELECT UPDATE_RULE FROM T2")) {
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

    /**
     * 번들 48 마무리 리뷰 — DB 마다 다른 인용·주석 규칙으로 ; 뒤 문장을 숨기는 꼴. 원문으로 재므로 전부 거절.
     * 리터럴·따옴표 식별자 안의 쓰기 단어·; 도 거절한다(엄격한 쪽 — 도구가 만드는 SQL 에는 없다)
     */
    @Test
    void quotingBypassesRefused() throws SQLException {
        for (String sql : List.of("SELECT $$'$$; DELETE FROM T; --'", "SELECT E'\\''; DELETE FROM T; --'", "SELECT 1 # x\n; DELETE FROM T",
                "SELECT 1--1;DELETE FROM T", "SELECT 'a;b'", "SELECT 'INSERT INTO x' AS S", "SELECT \"UPDATE\" FROM T2",
                "SELECT A FROM T FOR SHARE", "SELECT A FROM T FOR KEY SHARE", "{call x()}")) {
            assertThrows(SQLFeatureNotSupportedException.class, () -> ReadOnlyJdbc.check(sql), sql);
        }
        assertEquals(1, count());
    }

    @Test
    void writePathsRefused() throws Exception {
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
        assertThrows(SQLFeatureNotSupportedException.class, () -> ro.unwrap(raw.getClass()), "실물 클래스로 꺼내기");
        assertFalse(ro.isWrapperFor(raw.getClass()));
        assertSame(ro, ro.unwrap(Connection.class), "unwrap 은 실물을 안 준다");
        for (String create : List.of("createBlob", "createClob", "createNClob", "createSQLXML")) {
            java.lang.reflect.Method m = Connection.class.getMethod(create);
            java.lang.reflect.InvocationTargetException e = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> m.invoke(ro), create);
            assertTrue(e.getCause() instanceof SQLFeatureNotSupportedException, create);
        }
        assertEquals(1, count());
    }

    /** 결과의 LOB 는 읽기만 */
    @Test
    void lobsAreReadOnly() throws SQLException {
        try (Statement st = raw.createStatement()) {
            st.execute("CREATE TABLE L(C CLOB)");
            st.execute("INSERT INTO L VALUES('가나다')");
        }
        try (Statement st = ro.createStatement(); ResultSet rs = st.executeQuery("SELECT C FROM L")) {
            assertTrue(rs.next());
            java.sql.Clob c = rs.getClob(1);
            assertEquals("가나다", c.getSubString(1, 3));
            assertThrows(SQLFeatureNotSupportedException.class, () -> c.setString(1, "x"));
            assertThrows(SQLFeatureNotSupportedException.class, () -> c.truncate(0));
            Object o = rs.getObject(1);
            assertTrue(Proxy.isProxyClass(o.getClass()), "getObject 의 LOB 도 래퍼");
        }
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
