package kr.ejg.toolbox.core.conn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * `drivers/` 폴더의 JDBC jar 를 열어 DriverManager 에 등록한다. 현장 WAS lib 의 드라이버를 그대로 넣어 쓰게(11장).
 * 폴더가 없으면 조용히 건너뛴다. 같은 jar 는 두 번 안 연다. classpath 의 드라이버는 그대로 쓴다.
 */
public final class DriverLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DriverLoader.class);
    private static final Set<Path> LOADED = new HashSet<>();

    private DriverLoader() {
    }

    /** 새로 등록한 드라이버 클래스 이름들 */
    public static synchronized List<String> load(Path driversDir) {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(driversDir)) {
            return out;
        }
        List<Path> jars;
        try (Stream<Path> s = Files.list(driversDir)) {
            jars = s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("drivers 폴더를 못 읽었다: " + driversDir, e);
        }
        for (Path jar : jars) {
            Path key = jar.toAbsolutePath().normalize();
            if (!LOADED.add(key)) {
                continue;
            }
            try {
                // 부모는 플랫폼 로더 — 드라이버 jar 가 앱 classpath(같은 드라이버의 다른 판)와 섞이지 않게
                URLClassLoader loader = new URLClassLoader(new URL[] {key.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
                for (Driver d : ServiceLoader.load(Driver.class, loader)) {
                    DriverShim shim = new DriverShim(d);
                    DriverManager.registerDriver(shim);
                    out.add(shim.driverClassName());
                }
            } catch (IOException | SQLException | RuntimeException | java.util.ServiceConfigurationError e) {
                // 깨진 jar·버전 안 맞는 드라이버 — 건너뛰고 나머지를 연다
                LOG.warn("드라이버 jar 를 못 열었다: {} ({})", key.getFileName(), e.getClass().getSimpleName());
            }
        }
        if (!out.isEmpty()) {
            LOG.info("drivers/ 에서 드라이버 {}개 등록: {}", out.size(), out);
        }
        return out;
    }
}
