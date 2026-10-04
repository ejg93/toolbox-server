package kr.ejg.toolbox.core.conn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 8-13 — 같은 드라이버 클래스를 든 jar 가 둘이면 WARN 한 줄, 다르면 없음.
 * 시험마다 이름이 다른 가짜 드라이버를 컴파일해 묶는다 — 등록 기록이 JVM 전역이라 다른 시험과 안 섞이게.
 * jar 는 target/ 아래 — 클래스로더가 jar 를 쥐고 있어 Windows 에선 @TempDir 을 못 지운다(ConnectionRegistryTest 와 같다).
 */
public class DriverLoaderTest {

    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void up() {
        logger = (Logger) LoggerFactory.getLogger(DriverLoader.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void down() {
        logger.detachAppender(logs);
    }

    /** 새 jar 폴더 — target/test-driver-jars/ 아래 */
    public static Path newDir() throws IOException {
        Path dir = Path.of("target", "test-driver-jars", Long.toString(System.nanoTime()));
        return Files.createDirectories(dir);
    }

    /** 아무 URL 도 안 받는 {@code java.sql.Driver} 를 컴파일해 jar 하나로 — 서비스 등록 파일까지 */
    public static void fakeDriverJar(Path jar, String className) throws IOException {
        int dot = className.lastIndexOf('.');
        String pkg = className.substring(0, dot);
        String simple = className.substring(dot + 1);
        Path work = Files.createTempDirectory(Files.createDirectories(Path.of("target", "test-driver-src")), "fakedrv");
        Path src = work.resolve(simple + ".java");
        Files.writeString(src, "package " + pkg + ";\n"
                + "public class " + simple + " implements java.sql.Driver {\n"
                + "  public java.sql.Connection connect(String u, java.util.Properties p) { return null; }\n"
                + "  public boolean acceptsURL(String u) { return false; }\n"
                + "  public java.sql.DriverPropertyInfo[] getPropertyInfo(String u, java.util.Properties p) { return new java.sql.DriverPropertyInfo[0]; }\n"
                + "  public int getMajorVersion() { return 1; }\n"
                + "  public int getMinorVersion() { return 0; }\n"
                + "  public boolean jdbcCompliant() { return false; }\n"
                + "  public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }\n"
                + "}\n", StandardCharsets.UTF_8);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        Path classes = Files.createDirectories(work.resolve("classes"));
        int rc = javac.run(null, null, null, "--release", "17", "-d", classes.toString(), src.toString());
        assertEquals(0, rc, "가짜 드라이버 컴파일");
        String entry = className.replace('.', '/') + ".class";
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(os)) {
            out.putNextEntry(new JarEntry(entry));
            out.write(Files.readAllBytes(classes.resolve(entry)));
            out.closeEntry();
            out.putNextEntry(new JarEntry("META-INF/services/java.sql.Driver"));
            out.write((className + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    private List<String> warns() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void sameDriverClassInTwoJarsWarnsOnce() throws Exception {
        String cls = "fake0813.Dup" + System.nanoTime();
        Path dir = newDir();
        fakeDriverJar(dir.resolve("b-driver.jar"), cls);
        fakeDriverJar(dir.resolve("a-driver.jar"), cls);

        assertEquals(List.of(cls, cls), DriverLoader.load(dir));

        List<String> w = warns();
        assertEquals(1, w.size(), w.toString());
        assertEquals("같은 드라이버 클래스가 jar 둘에 있다: " + cls + " — a-driver.jar·b-driver.jar. 이름 순 첫째(a-driver.jar)가 쓰인다. "
                + "하나만 두고 나머지는 drivers/alt/ 로", w.get(0));
        assertTrue(!w.get(0).contains(dir.toString()) && !w.get(0).contains("target"), "jar 이름만 — 경로 없음");
        assertEquals(List.of("a-driver.jar", "b-driver.jar"), DriverLoader.duplicates().get(cls));
    }

    @Test
    void differentDriverClassesDoNotWarn() throws Exception {
        String one = "fake0813.One" + System.nanoTime();
        String two = "fake0813.Two" + System.nanoTime();
        Path dir = newDir();
        fakeDriverJar(dir.resolve("one.jar"), one);
        fakeDriverJar(dir.resolve("two.jar"), two);

        assertEquals(List.of(one, two), DriverLoader.load(dir));

        assertEquals(List.of(), warns());
        assertTrue(!DriverLoader.duplicates().containsKey(one) && !DriverLoader.duplicates().containsKey(two));
    }
}
