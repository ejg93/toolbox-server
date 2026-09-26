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
