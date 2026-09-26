package kr.ejg.toolbox.core.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("db")
@Testcontainers
class PostgresMetaSourceTest {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("sample/postgres.sql");

    @Test
    void vendorCollectMatchesGolden() throws Exception {
        try (Connection c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            List<Schema> schemas = MetaSources.forDialect("postgresql", c).collect(new Scope(List.of("public"), null, null, null));
            assertEquals(8, schemas.get(0).tables().size());
            Table users = schemas.get(0).tables().stream().filter(t -> t.name().equals("users")).findFirst().orElseThrow();
            assertEquals(List.of("uq_users_login"), users.uniques().stream().map(u -> u.name()).toList(),
                    "UNIQUE 는 제약만 — 유니크 인덱스 ux_users_email 은 indexes 에만");
            GoldenFiles.assertSchemas("meta/postgres-vendor.json", schemas);
        }
    }
}
