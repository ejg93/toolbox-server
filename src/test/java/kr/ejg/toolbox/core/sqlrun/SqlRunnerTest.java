package kr.ejg.toolbox.core.sqlrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 1-7 — H2 in-memory. select·바인드·maxRows 잘림·타임아웃·갱신·값 변환 */
class SqlRunnerTest {

    Connection c;

    @BeforeEach
    void up() throws Exception {
        c = DriverManager.getConnection("jdbc:h2:mem:sqlrun" + System.nanoTime(), "sa", "");
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE T (ID INT PRIMARY KEY, NAME VARCHAR(20), AT TIMESTAMP, D DATE, BIN VARBINARY(10))");
            for (int i = 1; i <= 5; i++) {
                st.execute("INSERT INTO T VALUES (" + i + ", '이름" + i + "', TIMESTAMP '2026-09-27 10:00:0" + i
                        + "', DATE '2026-09-2" + i + "', X'0102')");
            }
        }
    }

    @AfterEach
    void down() throws Exception {
        c.close();
    }

    @Test
    void selectWithValuesConverted() throws Exception {
        ResultTable r = SqlRunner.run(c, "SELECT ID, NAME, AT, D, BIN FROM T WHERE ID = 1", List.of(), 1000, 10);
        assertEquals(List.of("ID", "NAME", "AT", "D", "BIN"), r.columns().stream().map(ResultTable.Col::name).toList());
        assertEquals(List.of(1, "이름1", "2026-09-27T10:00:01", "2026-09-21", "(BLOB 2 bytes)"), r.rows().get(0));
        assertFalse(r.truncated());
        assertEquals(-1, r.updateCount());
    }

    /** F-K18 — CLOB 은 100만 자까지만 읽고 꼬리에 전체 길이 */
    @Test
    void longClobIsCut() throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE L(C CLOB)");
        }
        try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO L VALUES (?)")) {
            ps.setString(1, "a".repeat(1_500_000));
            ps.executeUpdate();
        }
        String v = (String) SqlRunner.run(c, "SELECT C FROM L", List.of(), 10, 30).rows().get(0).get(0);
        assertTrue(v.startsWith("a".repeat(1_000_000)), String.valueOf(v.length()));
        assertTrue(v.length() < 1_000_100 && v.contains("1500000자"), v.substring(1_000_000));
    }

    /** F-K20 — 배열은 원소 값 [a, b] 꼴(읽기 전용 래퍼를 지나도), XML 은 글 */
    @Test
    void arraysAndXmlAreValues() throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE A(TAGS VARCHAR ARRAY)");
            st.execute("INSERT INTO A VALUES (ARRAY['x','y'])");
        }
        Object v = SqlRunner.run(kr.ejg.toolbox.core.conn.ReadOnlyJdbc.wrap(c), "SELECT TAGS FROM A", List.of(), 10, 10).rows().get(0).get(0);
        assertEquals("[x, y]", v);
    }

    @Test
    void bindParameters() throws Exception {
        ResultTable r = SqlRunner.run(c, "SELECT NAME FROM T WHERE ID > ? AND NAME <> ? ORDER BY ID", List.of(2, "이름4"), 1000, 10);
        assertEquals(List.of(List.of("이름3"), List.of("이름5")), r.rows());
    }

    @Test
    void maxRowsTruncates() throws Exception {
        ResultTable r = SqlRunner.run(c, "SELECT ID FROM T ORDER BY ID", List.of(), 3, 10);
        assertEquals(3, r.rows().size());
        assertTrue(r.truncated());
        assertFalse(SqlRunner.run(c, "SELECT ID FROM T", List.of(), 5, 10).truncated(), "딱 맞으면 안 잘림");
    }

    @Test
    void updateReturnsCount() throws Exception {
        ResultTable r = SqlRunner.run(c, "UPDATE T SET NAME = ? WHERE ID <= 2", List.of("x"), 1000, 10);
        assertEquals(2, r.updateCount());
        assertTrue(r.columns().isEmpty());
    }

    @Test
    void timeoutThrows() {
        String slow = "WITH RECURSIVE R(N) AS (SELECT 1 UNION ALL SELECT N + 1 FROM R WHERE N < 100000000) SELECT COUNT(*) FROM R";
        assertThrows(SQLException.class, () -> SqlRunner.run(c, slow, List.of(), 10, 1));
    }
}
