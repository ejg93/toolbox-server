package kr.ejg.toolbox.core.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 3-9 — 줄마다 실행, 하나가 깨져도 나머지는 들어간다. 타입 없는 MariaDB 컬럼 줄은 건너뛴다 */
class CommentApplyTest {

    @Test
    void appliesEachLineAndKeepsGoingOnFailure() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:applytest;DB_CLOSE_DELAY=-1")) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE T1 (A INT, B VARCHAR(10))");
            }
            List<String> lines = List.of(
                    "COMMENT ON TABLE  PUBLIC.T1 IS '표';",
                    "COMMENT ON COLUMN PUBLIC.T1.NOPE IS '없는 컬럼';",
                    "COMMENT ON COLUMN PUBLIC.T1.B IS '비';",
                    "ALTER TABLE PUBLIC.T1 MODIFY COLUMN A " + CommentApply.NEEDS_TYPE + " COMMENT '에이';");
            CommentApply.Outcome o = CommentApply.apply(c, lines, 3, null);
            assertEquals(2, o.applied());
            assertEquals(4, o.skipped(), "[검토] 3 + 타입 없는 줄 1");
            assertEquals(1, o.failed().size());
            assertEquals(2, o.failed().get(0).line(), "몇째 줄이 깨졌나");
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT REMARKS FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'T1' AND COLUMN_NAME = 'B'")) {
                assertTrue(rs.next());
                assertEquals("비", rs.getString(1), "끝 ; 를 떼고 실행됐다");
            }
        }
    }
}
