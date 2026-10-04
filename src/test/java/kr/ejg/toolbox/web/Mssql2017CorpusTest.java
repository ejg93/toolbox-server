package kr.ejg.toolbox.web;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;
import kr.ejg.toolbox.DbCorpus;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V-21 — SQL Server 2017 에 chinook SqlServer 원본. 접속·DB 만들기는 {@link MssqlCorpusTest} 와 같다 */
@Testcontainers
class Mssql2017CorpusTest extends DbCorpusBase {

    @Container
    static final MSSQLServerContainer<?> DB = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2017-latest").acceptLicense();

    @Override
    DbCorpus.Dialect dialect() {
        return DbCorpus.Dialect.MSSQL;
    }

    @Override
    String goldenKey() {
        return "mssql2017";
    }

    @Override
    Set<String> skipped() {
        return META_ONLY;
    }

    @Override
    Connection open() throws Exception {
        try (Connection c = firstOf(DB.getMappedPort(1433), DB.getUsername(), DB.getPassword(), classpath("mssql-jdbc", DB.getJdbcUrl()));
                java.sql.Statement s = c.createStatement()) {
            s.execute("IF DB_ID('corpus') IS NULL CREATE DATABASE corpus");
        }
        return DriverManager.getConnection(DB.getJdbcUrl() + ";databaseName=corpus", DB.getUsername(), DB.getPassword());
    }
}
