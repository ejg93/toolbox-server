package kr.ejg.toolbox.core.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("db")
@Testcontainers
class MssqlMetaSourceTest {

    @Container
    static final MSSQLServerContainer<?> DB = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense()
            .withInitScript("sample/mssql.sql");

    @Test
    void vendorCollectMatchesGolden() throws Exception {
        try (Connection c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            List<Schema> schemas = MetaSources.forDialect("mssql", c).collect(new Scope(List.of("sample"), null, null, null));
            assertEquals(8, schemas.get(0).tables().size());
            GoldenFiles.assertSchemas("meta/mssql.json", schemas);
        }
    }
}
