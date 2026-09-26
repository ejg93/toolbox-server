package kr.ejg.toolbox.core.conn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 1-4 — 절대 규칙 2: 비밀번호는 메모리만. 틀린 비밀번호가 응답·로그에 안 남는다 */
class ConnectionRegistryTest {

    private static final String URL = "jdbc:h2:mem:conntest;DB_CLOSE_DELAY=-1";
    private static final String RIGHT = "right-Pw-7431";
    private static final String WRONG = "wrong-Pw-9902";

    private Connection holder;
    private ListAppender<ILoggingEvent> logs;
    private Logger root;

    static Profile profile(String url) {
        return new Profile("t", null, List.of(new Profile.Connection("h2", "h2", url, "sa")), "h2",
                null, null, null, null, null, null, null, null);
    }

    @BeforeEach
    void up() throws Exception {
        holder = DriverManager.getConnection(URL, "sa", RIGHT); // H2 in-memory 는 첫 접속의 비밀번호로 만들어진다
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void down() throws Exception {
        root.detachAppender(logs);
        holder.close();
    }

    private void assertNoSecretInLogs() {
        for (ILoggingEvent e : logs.list) {
            String line = e.getFormattedMessage();
            assertFalse(line.contains(RIGHT) || line.contains(WRONG), "로그에 비밀번호: " + line);
        }
    }

    @Test
    void wrongPasswordFailsWithoutLeaking() {
        ConnectionRegistry reg = new ConnectionRegistry(() -> Optional.of(profile(URL)));
        reg.setPassword("h2", WRONG.toCharArray());
        ConnectionRegistry.TestResult r = reg.test("h2");
        assertFalse(r.ok());
        assertTrue(r.message() != null && !r.message().contains(WRONG), "응답에 비밀번호 없음: " + r.message());
        assertNoSecretInLogs();
    }

    @Test
    void rightPasswordPasses() {
        ConnectionRegistry reg = new ConnectionRegistry(() -> Optional.of(profile(URL)));
        char[] pw = RIGHT.toCharArray();
        reg.setPassword("h2", pw);
        assertEquals('\0', pw[0], "넘겨준 배열은 지운다");
        ConnectionRegistry.TestResult r = reg.test("h2");
        assertTrue(r.ok(), String.valueOf(r.message()));
        assertEquals("H2", r.productName());
        assertTrue(reg.list().get(0).hasPassword());
        reg.forget("h2");
        assertFalse(reg.list().get(0).hasPassword());
        assertNoSecretInLogs();
    }

    @Test
    void maskHidesPasswordInDriverMessage() {
        assertEquals("login failed for **** at host", ConnectionRegistry.mask("login failed for " + WRONG + " at host", WRONG.toCharArray()));
        assertEquals("SELECT 1 FROM DUAL", ConnectionRegistry.testSql("tibero"));
        assertEquals("SELECT 1", ConnectionRegistry.testSql("mariadb"));
    }

    @Test
    void unknownConnectionIsNotOk() {
        ConnectionRegistry reg = new ConnectionRegistry(() -> Optional.of(profile(URL)));
        assertFalse(reg.test("nope").ok());
    }

    /**
     * jar 사본은 target/ 아래 — 클래스로더가 jar 를 쥐고 있어 Windows 에선 @TempDir 을 못 지운다.
     * 등록한 드라이버는 끝에 해제한다 — 남기면 같은 JVM 의 다른 테스트가 jdbc:h2:mem 을 사본 드라이버(별도 인메모리 저장소)로 연다.
     */
    @Test
    void driverJarsAreLoadedFromFolder() throws Exception {
        Path dir = Path.of("target", "test-driver-jars", Long.toString(System.nanoTime()));
        Files.createDirectories(dir);
        Path h2 = Path.of(org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.copy(h2, dir.resolve("h2-copy.jar"));
        try {
            assertEquals(List.of("org.h2.Driver"), DriverLoader.load(dir));
            assertEquals(List.of(), DriverLoader.load(dir), "같은 jar 는 두 번 안 연다");
            assertEquals(List.of(), DriverLoader.load(dir.resolve("none")), "폴더가 없으면 건너뛴다");
        } finally {
            for (java.sql.Driver d : java.util.Collections.list(DriverManager.getDrivers())) {
                if (d instanceof DriverShim) {
                    DriverManager.deregisterDriver(d);
                }
            }
        }
    }
}
