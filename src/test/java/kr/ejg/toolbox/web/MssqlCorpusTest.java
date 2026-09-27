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
        // 원본의 CREATE DATABASE·USE 는 빼고 넣으니 따로 DB 를 만든다 — master 의 dbo 에는 시스템 표(spt_*)가 있어
        // 스냅샷·확장속성(15135)이 그것까지 잡았다
        try (Connection c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
                java.sql.Statement s = c.createStatement()) {
            s.execute("IF DB_ID('corpus') IS NULL CREATE DATABASE corpus");
        }
        return DriverManager.getConnection(DB.getJdbcUrl() + ";databaseName=corpus", DB.getUsername(), DB.getPassword());
    }
}
