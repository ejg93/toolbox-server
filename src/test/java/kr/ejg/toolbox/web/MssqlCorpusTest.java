package kr.ejg.toolbox.web;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import kr.ejg.toolbox.DbCorpus;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 번들 10 — SQL Server 컨테이너에 chinook SqlServer 원본(eGov 공통컴포넌트는 MSSQL DDL 이 없다) */
@Testcontainers
class MssqlCorpusTest extends DbCorpusBase {

    @Container
    static final MSSQLServerContainer<?> DB = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    @Override
    DbCorpus.Dialect dialect() {
        return DbCorpus.Dialect.MSSQL;
    }

    @Override
    Connection open() throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }
}
