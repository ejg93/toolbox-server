package kr.ejg.toolbox.core.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ejg.toolbox.core.meta.MetaSource;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 1-12 — 벤더 딕셔너리 SQL 이 실패하면 JDBC 뼈대로 물러선다. H2 에는 ALL_TAB_COMMENTS 등이 없어
 * Oracle 수집기의 세 벤더 SQL 이 다 깨진다 — 그래도 표·컬럼·PK 는 나오고 경고가 세 종류 남는다.
 * 1-13 — PG·MSSQL 수집기도 H2 에 한 번씩(벤더 SQL 이 H2 에서 다 깨진다).
 */
class VendorFallbackTest {

    @Test
    void oracleSourceOnH2FallsBackToJdbcWithWarnings() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:fallback" + System.nanoTime(), "sa", "");
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE A_ITEM(ID INT PRIMARY KEY, NAME VARCHAR(20))");
            st.execute("CREATE TABLE B_CODE(CD VARCHAR(4) PRIMARY KEY, NM VARCHAR(20), CONSTRAINT UK_B_NM UNIQUE (NM))");
            st.execute("COMMENT ON TABLE A_ITEM IS '항목'");

            OracleMetaSource src = new OracleMetaSource(c);
            List<Schema> schemas = src.collect(new Scope(List.of("PUBLIC"), null, null, null));

            List<Table> tables = schemas.get(0).tables();
            assertEquals(List.of("A_ITEM", "B_CODE"), tables.stream().map(Table::name).toList());
            assertEquals(2, tables.get(0).columns().size());
            assertEquals(List.of("ID"), tables.get(0).pk().columns());
            assertEquals("항목", tables.get(0).comment(), "코멘트는 JDBC REMARKS 로 물러선다");
            assertNull(tables.get(0).rowCount(), "행 수는 null 로 물러선다");
            assertTrue(tables.get(1).uniques().stream().anyMatch(u -> u.columns().equals(List.of("NM"))),
                    "UNIQUE 는 유니크 인덱스로 물러선다: " + tables.get(1).uniques());

            Set<String> kinds = src.warnings().stream().map(MetaSource.Warning::kind).collect(Collectors.toSet());
            assertEquals(Set.of("comments", "stats", "uniques", "sorts", "checks", "size"), kinds); // sorts·checks·size — 1-20·1-21·1-23
            for (MetaSource.Warning w : src.warnings()) {
                assertEquals("42S02", w.sqlState(), w.toString()); // H2 「표가 없다」
                assertTrue(w.count() >= 1, w.toString());
            }
            int uniques = src.warnings().stream().filter(w -> w.kind().equals("uniques")).mapToInt(MetaSource.Warning::count).sum();
            assertEquals(2, uniques, "표마다 한 번");
        }
    }

    /** 1-13 — 남은 방언. MariaDB 는 catalog 를 덮어써 H2 에서 뼈대가 안 맞아 뺀다(이력) */
    @ParameterizedTest
    @ValueSource(strings = {"postgresql", "mssql"})
    void otherVendorsFallBackOnH2(String dialect) throws Exception {
        Function<Connection, VendorMetaSource> make = switch (dialect) {
            case "postgresql" -> PostgresMetaSource::new;
            default -> MssqlMetaSource::new;
        };
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:fallback" + dialect + System.nanoTime(), "sa", "");
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE A_ITEM(ID INT PRIMARY KEY, NAME VARCHAR(20))");
            st.execute("CREATE TABLE B_CODE(CD VARCHAR(4) PRIMARY KEY, NM VARCHAR(20), CONSTRAINT UK_B_NM UNIQUE (NM))");

            VendorMetaSource src = make.apply(c);
            List<Table> tables = src.collect(new Scope(List.of("PUBLIC"), null, null, null)).get(0).tables();
            assertEquals(List.of("A_ITEM", "B_CODE"), tables.stream().map(Table::name).toList());
            assertEquals(List.of("ID"), tables.get(0).pk().columns());
            assertNull(tables.get(0).rowCount());
            Set<String> kinds = src.warnings().stream().map(MetaSource.Warning::kind).collect(Collectors.toSet());
            assertEquals(Set.of("comments", "stats", "uniques", "checks", "size"), kinds, src.warnings().toString()); // checks·size — 1-22·1-23
        }
    }
}
