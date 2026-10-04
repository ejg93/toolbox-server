package kr.ejg.toolbox.core.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.MetaSource;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@Tag("db")
@Testcontainers
class OracleMetaSourceTest {

    @Container
    static final OracleContainer DB = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(10))
            .withInitScript("sample/oracle.sql");

    @Test
    void vendorCollectMatchesGolden() throws Exception {
        try (Connection c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            String owner = DB.getUsername().toUpperCase();
            MetaSource src = MetaSources.forDialect("oracle", c);
            List<Schema> schemas = src.collect(new Scope(List.of(owner), null, null, null));
            assertEquals(8, schemas.get(0).tables().size());
            assertEquals(List.of(), src.warnings(), "실제 Oracle 에선 벤더 SQL 이 물러서지 않는다(1-12)");
            GoldenFiles.assertSchemas("meta/oracle.json", schemas);
        }
    }
}
