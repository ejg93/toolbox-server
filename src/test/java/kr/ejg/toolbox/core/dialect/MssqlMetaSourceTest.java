package kr.ejg.toolbox.core.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.MetaSource;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import kr.ejg.toolbox.core.meta.Table;
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
            MetaSource src = MetaSources.forDialect("mssql", c);
            List<Schema> schemas = src.collect(new Scope(List.of("sample"), null, null, null));
            assertEquals(List.of(), src.warnings(), "최신판에선 벤더 SQL 이 물러서지 않는다(1-13)");
            assertEquals(8, schemas.get(0).tables().size());
            GoldenFiles.assertSchemas("meta/mssql.json", schemas);
        }
    }

    /** 1-19~1-23 — 규칙·정렬·CHECK 가 든 표를 잠깐 만들어 수집값을 본다(표본 DDL 은 안 건드린다) */
    @Test
    void metaMore() throws Exception {
        try (Connection c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            Table t = MetaMore.collect(c, "mssql", "sample", "sample.");
            assertEquals("CASCADE", t.fks().get(0).deleteRule(), "1-19 ON DELETE CASCADE");
            assertEquals("NO ACTION", t.fks().get(0).updateRule(), "1-19 갱신규칙 — 드라이버가 주는 값");
            kr.ejg.toolbox.core.meta.Index ix = t.indexes().stream().filter(x -> x.name().equalsIgnoreCase("ZZ_C_IX")).findFirst().orElseThrow();
            assertEquals(List.of("V", "PID"), ix.columns().stream().map(x -> x.toUpperCase(java.util.Locale.ROOT)).toList(),
                    "1-20 컬럼 이름(Oracle 은 SYS_NC…$ 를 식으로)");
            assertEquals(List.of("DESC", "ASC"), ix.sorts(), "1-20 정렬");
        }
    }
}
