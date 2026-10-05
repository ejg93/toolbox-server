package kr.ejg.toolbox.core.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import kr.ejg.toolbox.core.meta.Table;
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

    /** 1-19~1-23 — 규칙·정렬·CHECK 가 든 표를 잠깐 만들어 수집값을 본다(표본 DDL 은 안 건드린다) */
    @Test
    void metaMore() throws Exception {
        try (Connection c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            Table t = MetaMore.collect(c, "mariadb", "test", "");
            assertEquals(kr.ejg.toolbox.core.meta.FkRule.CASCADE, t.fks().get(0).deleteRule(), "1-19 ON DELETE CASCADE");
            assertEquals(kr.ejg.toolbox.core.meta.FkRule.RESTRICT, t.fks().get(0).updateRule(), "1-19 갱신규칙 — 드라이버가 주는 값");
            kr.ejg.toolbox.core.meta.Index ix = t.indexes().stream().filter(x -> x.name().equalsIgnoreCase("ZZ_C_IX")).findFirst().orElseThrow();
            assertEquals(List.of("V", "PID"), ix.columns().stream().map(x -> x.toUpperCase(java.util.Locale.ROOT)).toList(),
                    "1-20 컬럼 이름(Oracle 은 SYS_NC…$ 를 식으로)");
            assertEquals(List.of(kr.ejg.toolbox.core.meta.SortOrder.DESC, kr.ejg.toolbox.core.meta.SortOrder.ASC), ix.sorts(), "1-20 정렬");
            assertEquals(List.of("ZZ_C_CK"), t.checks().stream().map(k -> k.name().toUpperCase(java.util.Locale.ROOT)).toList(), "1-22 CHECK");
            assertTrue(t.checks().get(0).condition().toUpperCase(java.util.Locale.ROOT).matches(".*V.*>.*0.*"), t.checks().get(0).condition());
        }
    }
}
