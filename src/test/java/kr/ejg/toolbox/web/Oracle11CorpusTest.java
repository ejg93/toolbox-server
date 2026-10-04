package kr.ejg.toolbox.web;

import java.sql.Connection;
import java.time.Duration;
import java.util.Set;
import kr.ejg.toolbox.DbCorpus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** V-21 — Oracle 11g XE 에 최신판과 같은 표본(HR·eGov oracle·chinook Oracle). 드라이버는 ojdbc11 → alt ojdbc8 → ojdbc6 */
@Testcontainers
class Oracle11CorpusTest extends DbCorpusBase {

    @Container
    static final GenericContainer<?> DB = new GenericContainer<>(DockerImageName.parse("gvenzl/oracle-xe:11-slim"))
            .withEnv("ORACLE_PASSWORD", "test").withEnv("APP_USER", "test").withEnv("APP_USER_PASSWORD", "test")
            .withExposedPorts(1521).withSharedMemorySize(1024L * 1024 * 1024)
            .waitingFor(Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1).withStartupTimeout(Duration.ofMinutes(10)));

    @Override
    DbCorpus.Dialect dialect() {
        return DbCorpus.Dialect.ORACLE;
    }

    @Override
    String goldenKey() {
        return "oracle11";
    }

    @Override
    Set<String> skipped() {
        return META_ONLY;
    }

    @Override
    Connection open() throws Exception {
        int port = DB.getMappedPort(1521);
        String url = "jdbc:oracle:thin:@//" + DB.getHost() + ":" + port + "/XE";
        return firstOf(port, "test", "test", classpath("ojdbc11", url), alt("ojdbc8", "oracle.jdbc.OracleDriver", url),
                alt("ojdbc6", "oracle.jdbc.OracleDriver", url));
    }
}
