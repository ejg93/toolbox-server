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
}
