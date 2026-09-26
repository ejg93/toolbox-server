package kr.ejg.toolbox.core.db;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Flyway 없이 — {@code schema_version} 표 + classpath {@code db/migration/V###__이름.sql} 을 번호 순으로.
 * 이미 적용한 번호는 건너뛴다. 한 파일은 한 트랜잭션.
 */
public final class Migrator {

    static final String LOCATION = "db/migration";
    private static final Pattern NAME = Pattern.compile("V(\\d{3})__[A-Za-z0-9_]+\\.sql");

    private Migrator() {
    }

    /** 적용한 파일 수 */
    public static int migrate(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "version INT PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        }
        int current = currentVersion(c);
        int applied = 0;
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            for (Map.Entry<Integer, String> m : migrations().entrySet()) {
                if (m.getKey() <= current) {
                    continue;
                }
                try (Statement st = c.createStatement()) {
                    for (String sql : split(read(m.getValue()))) {
                        st.execute(sql);
                    }
                    try (PreparedStatement ins = c.prepareStatement("INSERT INTO schema_version(version) VALUES (?)")) {
                        ins.setInt(1, m.getKey());
                        ins.executeUpdate();
                    }
                    c.commit();
                    applied++;
                } catch (SQLException e) {
                    c.rollback();
                    throw new SQLException(m.getValue() + ": " + e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
                }
            }
        } finally {
            c.setAutoCommit(auto);
        }
        return applied;
    }

    public static int currentVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 번호 → classpath 경로, 번호 순 */
    static Map<Integer, String> migrations() {
        Map<Integer, String> out = new TreeMap<>();
        for (String name : listNames()) {
            Matcher m = NAME.matcher(name);
            if (m.matches()) {
                String prev = out.put(Integer.parseInt(m.group(1)), LOCATION + "/" + name);
                if (prev != null) {
                    throw new IllegalStateException("마이그레이션 번호 중복: " + prev + ", " + name);
                }
            }
        }
        return out;
    }

    private static List<String> listNames() {
        URL url = Migrator.class.getClassLoader().getResource(LOCATION);
        if (url == null) {
            return List.of();
        }
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                FileSystem fs;
                try {
                    fs = FileSystems.newFileSystem(uri, Map.of());
                } catch (FileSystemAlreadyExistsException e) {
                    fs = FileSystems.getFileSystem(uri);
                }
                return names(fs.getPath(LOCATION));
            }
            return names(Path.of(uri));
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException("마이그레이션 목록을 못 읽었다", e);
        }
    }

    private static List<String> names(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).toList();
        }
    }

    private static String read(String resource) {
        try (InputStream in = Migrator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("없다: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("못 읽었다: " + resource, e);
        }
    }

    /** 줄 주석(--) 을 걷고 줄 끝 세미콜론으로 나눈다. 문자열 안 세미콜론은 쓰지 않는 전제 */
    static List<String> split(String script) {
        StringBuilder clean = new StringBuilder();
        for (String line : script.split("\\R")) {
            String t = line.strip();
            if (t.startsWith("--") || t.isEmpty()) {
                continue;
            }
            clean.append(line).append('\n');
        }
        List<String> out = new ArrayList<>();
        for (String part : clean.toString().split(";\\s*\\n")) {
            String s = part.strip();
            if (s.endsWith(";")) {
                s = s.substring(0, s.length() - 1).strip();
            }
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
}
