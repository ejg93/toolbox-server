package kr.ejg.toolbox.web;

import java.sql.Connection;
import kr.ejg.toolbox.DbCorpus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V-21 — MySQL 8.0. 컨테이너·드라이버 후보는 {@link Mysql57CorpusTest} 와 같다 */
@Testcontainers
class Mysql80CorpusTest extends DbCorpusBase {

    @Container
    static final GenericContainer<?> DB = Mysql57CorpusTest.mysql("mysql:8.0");

    @Override
    DbCorpus.Dialect dialect() {
        return DbCorpus.Dialect.MARIA;
    }

    @Override
    String goldenKey() {
        return "mysql80";
    }

    @Override
    Connection open() throws Exception {
        return Mysql57CorpusTest.open(this, DB);
    }
}
