package kr.ejg.toolbox.web;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import kr.ejg.toolbox.DbCorpus;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 번들 10 — PostgreSQL 컨테이너에 eGov postgres·chinook PostgreSql 원본 */
@Testcontainers
class PostgresCorpusTest extends DbCorpusBase {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");

    @Override
    DbCorpus.Dialect dialect() {
        return DbCorpus.Dialect.POSTGRES;
    }

    @Override
    Connection open() throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }
}
