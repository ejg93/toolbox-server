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
import kr.ejg.toolbox.core.meta.Table;
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

    /** 1-19~1-23 — 규칙·정렬·CHECK 가 든 표를 잠깐 만들어 수집값을 본다(표본 DDL 은 안 건드린다) */
    @Test
    void metaMore() throws Exception {
        try (Connection c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            Table t = MetaMore.collect(c, "oracle", DB.getUsername().toUpperCase(), "");
            assertEquals(kr.ejg.toolbox.core.meta.FkRule.CASCADE, t.fks().get(0).deleteRule(), "1-19 ON DELETE CASCADE");
            assertEquals(kr.ejg.toolbox.core.meta.FkRule.NO_ACTION, t.fks().get(0).updateRule(), "Oracle 은 ON UPDATE 가 없어 NO ACTION — 딕셔너리 갈래(PR #42)");
            kr.ejg.toolbox.core.meta.Index ix = t.indexes().stream().filter(x -> x.name().equalsIgnoreCase("ZZ_C_IX")).findFirst().orElseThrow();
            assertEquals(List.of("V", "PID"), ix.columns().stream().map(x -> x.toUpperCase(java.util.Locale.ROOT)).toList(),
                    "1-20 컬럼 이름(Oracle 은 SYS_NC…$ 를 식으로)");
            assertEquals(List.of(kr.ejg.toolbox.core.meta.SortOrder.DESC, kr.ejg.toolbox.core.meta.SortOrder.ASC), ix.sorts(), "1-20 정렬");
            assertEquals(List.of("ZZ_C_CK"), t.checks().stream().map(k -> k.name()).toList(), "1-21 CHECK — NOT NULL 자동 제약은 뺀다");
            assertEquals("V > 0", t.checks().get(0).condition());
        }
    }
}
