package kr.ejg.toolbox.core.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 2026-09-27 리뷰 지적 — DatabaseMetaData 의 패턴 인자에 이름을 그대로 넘기면 `_` 가 와일드카드가 된다.
 * H2 in-memory 로 잰다(컨테이너 없음).
 */
class JdbcMetaSourceWildcardTest {

    @Test
    void underscoreInNameIsNotAWildcard() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:wild;DB_CLOSE_DELAY=-1", "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA S_1");
            st.execute("CREATE SCHEMA SX1");
            st.execute("CREATE TABLE S_1.A_B (ONLY_AB INT)");
            st.execute("CREATE TABLE S_1.AXB (ONLY_AXB INT)");
            st.execute("CREATE TABLE SX1.A_B (OTHER_SCHEMA INT)");

            JdbcMetaSource src = new JdbcMetaSource(c);
            List<String> cols = src.loadColumns(Table.of("S_1", "A_B", "TABLE", null))
                    .columns().stream().map(Column::name).toList();
            assertEquals(List.of("ONLY_AB"), cols);

            List<String> tables = src.listTables("S_1").stream().map(Table::name).toList();
            assertEquals(List.of("AXB", "A_B"), tables, "S_1 스키마 것만 — SX1 이 섞이지 않는다");
        }
    }
}
