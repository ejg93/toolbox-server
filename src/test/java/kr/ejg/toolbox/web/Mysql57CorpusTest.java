package kr.ejg.toolbox.web;

import java.sql.Connection;
import java.time.Duration;
import kr.ejg.toolbox.DbCorpus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** V-21 — MySQL 5.7 에 MariaDB 와 같은 표본(eGov maria·chinook MySql). 드라이버는 mariadb-java-client → alt mysql-connector-j */
@Testcontainers
class Mysql57CorpusTest extends DbCorpusBase {

    @Container
    static final GenericContainer<?> DB = mysql("mysql:5.7");

    /**
     * MySQL 공식 이미지 — 사용자·DB 는 MariaDBContainer 와 같은 test/test/test. 5.7 의 기본 문자 집합은 latin1 이라 utf8mb4 로 띄운다
     * (한글 코멘트). 첫 기동은 임시 서버(port: 0)를 띄웠다 내리니 port: 3306 줄을 기다린다
     */
    static GenericContainer<?> mysql(String image) {
        return new GenericContainer<>(DockerImageName.parse(image))
                .withEnv("MYSQL_ROOT_PASSWORD", "test").withEnv("MYSQL_DATABASE", "test")
                .withEnv("MYSQL_USER", "test").withEnv("MYSQL_PASSWORD", "test")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")
                .withExposedPorts(3306)
                .waitingFor(Wait.forLogMessage(".*port: 3306.*", 1).withStartupTimeout(Duration.ofMinutes(5)));
    }

    static Connection open(DbCorpusBase t, GenericContainer<?> db) throws Exception {
        int port = db.getMappedPort(3306);
        String hostPort = db.getHost() + ":" + port + "/test";
        return t.firstOf(port, "test", "test", classpath("mariadb-java-client", "jdbc:mariadb://" + hostPort),
                alt("mysql-connector-j", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://" + hostPort));
    }

    @Override
    DbCorpus.Dialect dialect() {
        return DbCorpus.Dialect.MARIA;
    }

    @Override
    String goldenKey() {
        return "mysql57";
    }

    @Override
    Connection open() throws Exception {
        return open(this, DB);
    }
}
