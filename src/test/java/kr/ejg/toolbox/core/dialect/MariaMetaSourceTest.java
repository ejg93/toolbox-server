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
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("db")
@Testcontainers
class MariaMetaSourceTest {

    @Container
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11")
            .withInitScript("sample/mariadb.sql");

    @Test
    void vendorCollectMatchesGolden() throws Exception {
        try (Connection c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            MariaMetaSource src = new MariaMetaSource(c);
            assertEquals(List.of("test"), src.listSchemas(), "DB 가 카탈로그로 온다 — 시스템 DB 는 뺀다");
            List<Schema> schemas = src.collect(new Scope(List.of("test"), null, null, null));
            assertEquals(List.of(), src.warnings(), "최신판에선 벤더 SQL 이 물러서지 않는다(1-13)");
            assertEquals(8, schemas.get(0).tables().size());
            GoldenFiles.assertSchemas("meta/mariadb.json", schemas);
        }
    }
}
