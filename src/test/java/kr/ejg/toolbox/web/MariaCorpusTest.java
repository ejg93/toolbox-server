package kr.ejg.toolbox.web;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import kr.ejg.toolbox.DbCorpus;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 번들 10 — MariaDB 컨테이너에 eGov maria·chinook MySql 원본 */
@Testcontainers
class MariaCorpusTest extends DbCorpusBase {

    @Container
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11");

    @Override
    DbCorpus.Dialect dialect() {
        return DbCorpus.Dialect.MARIA;
    }

    @Override
    Connection open() throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }
}
