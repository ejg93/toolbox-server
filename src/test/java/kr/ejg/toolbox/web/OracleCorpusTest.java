package kr.ejg.toolbox.web;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import kr.ejg.toolbox.DbCorpus;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/** 번들 10 — Oracle Free 컨테이너에 Oracle HR·eGov oracle·chinook Oracle 원본(tibero 몫도 이것으로) */
@Testcontainers
class OracleCorpusTest extends DbCorpusBase {

    @Container
    static final OracleContainer DB = new OracleContainer("gvenzl/oracle-free:23-slim-faststart").withStartupTimeout(Duration.ofMinutes(10));

    @Override
    DbCorpus.Dialect dialect() {
        return DbCorpus.Dialect.ORACLE;
    }

    @Override
    Connection open() throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }
}
