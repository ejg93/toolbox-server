package kr.ejg.toolbox.web;

import java.sql.Connection;
import kr.ejg.toolbox.DbCorpus;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V-21 — PostgreSQL 12 에 최신판과 같은 표본(eGov postgres·chinook PostgreSql) */
@Testcontainers
class Postgres12CorpusTest extends DbCorpusBase {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:12-alpine");

    @Override
    DbCorpus.Dialect dialect() {
        return DbCorpus.Dialect.POSTGRES;
    }

    @Override
    String goldenKey() {
        return "postgres12";
    }

    @Override
    Connection open() throws Exception {
        return firstOf(DB.getMappedPort(5432), DB.getUsername(), DB.getPassword(), classpath("postgresql", DB.getJdbcUrl()));
    }
}
