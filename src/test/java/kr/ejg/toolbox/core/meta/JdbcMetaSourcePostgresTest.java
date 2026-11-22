package kr.ejg.toolbox.core.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import kr.ejg.toolbox.core.profile.Scope;

@Tag("db")
@Testcontainers
class JdbcMetaSourcePostgresTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("sample/postgres.sql");

    @Test
    void collectsSampleSchemaLikeGolden() throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            JdbcMetaSource src = new JdbcMetaSource(c);
            assertTrue(src.dbVersion().startsWith("PostgreSQL 17"), src.dbVersion());

            List<Schema> schemas = src.collect(new Scope(List.of("public"), null, null, null));
            assertEquals(1, schemas.size());
            assertEquals(8, schemas.get(0).tables().size());

            // 버전은 이미지 갱신마다 바뀌니 골든에선 가린다
            List<Schema> masked = schemas.stream().map(s -> new Schema(s.name(), "<version>", s.tables())).toList();
            GoldenFiles.assertJson("meta/postgres.json", masked);
        }
    }

    @Test
    void scopeExcludesBeforeLoading() throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            Scope scope = new Scope(List.of("public"), new Scope.Exclude(List.of("code"), null, null, List.of("logs")), null, null);
            List<String> names = new JdbcMetaSource(c).collect(scope).get(0).tables().stream().map(Table::name).toList();
            assertEquals(List.of("categories", "order_items", "orders", "products", "users"), names);
        }
    }

    /** 1-126(F-04) — 선언적 파티션의 부모(PARTITIONED TABLE)도 표로 싣는다. 자식 파티션은 그대로 표 */
    @Test
    void partitionedParentIsListed() throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                java.sql.Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA t91");
            st.execute("CREATE TABLE t91.t91_orders (id int, dt date) PARTITION BY RANGE (dt)");
            st.execute("CREATE TABLE t91.t91_orders_a PARTITION OF t91.t91_orders FOR VALUES FROM ('2024-01-01') TO ('2024-02-01')");
            st.execute("CREATE TABLE t91.t91_orders_b PARTITION OF t91.t91_orders FOR VALUES FROM ('2024-02-01') TO ('2024-03-01')");
            List<Table> tables = new JdbcMetaSource(c).collect(new Scope(List.of("t91"), null, null, null)).get(0).tables();
            assertEquals(List.of("t91_orders", "t91_orders_a", "t91_orders_b"), tables.stream().map(Table::name).toList());
            Table parent = tables.get(0);
            assertEquals("TABLE", parent.type());
            assertEquals(2, parent.columns().size());
        }
    }
}
