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
                    + "version INT PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, checksum VARCHAR(64))");
            // 0-30 전에 만든 data 폴더(개발 PC)는 칸이 없다
            st.execute("ALTER TABLE schema_version ADD COLUMN IF NOT EXISTS checksum VARCHAR(64)");
        }
        verifyApplied(c);
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
                    try (PreparedStatement ins = c.prepareStatement("INSERT INTO schema_version(version, checksum) VALUES (?, ?)")) {
                        ins.setInt(1, m.getKey());
                        ins.setString(2, checksum(read(m.getValue())));
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

    /**
     * 적용한 번호의 파일이 적용 때와 같은지(0-30) — 반입된 V 파일을 고쳐 새 jar 를 가져가면 현장 DB 스스로 알아챈다.
     * 해시가 비어 있으면(0-30 전 적용) 지금 해시를 적는다. 줄바꿈은 LF 로 맞춰 잰다(git 의 CRLF 변환에 안 흔들리게).
     */
    static void verifyApplied(Connection c) throws SQLException {
        Map<Integer, String> files = migrations();
        Map<Integer, String> stored = new TreeMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT version, checksum FROM schema_version")) {
            while (rs.next()) {
                stored.put(rs.getInt(1), rs.getString(2));
            }
        }
        for (Map.Entry<Integer, String> e : stored.entrySet()) {
            String file = files.get(e.getKey());
            if (file == null) {
                throw new SQLException("적용된 V" + String.format("%03d", e.getKey()) + " 파일이 이 판에 없다 — 더 새 판이 쓰던 data 폴더다");
            }
            String now = checksum(read(file));
            if (e.getValue() == null) {
                try (PreparedStatement up = c.prepareStatement("UPDATE schema_version SET checksum = ? WHERE version = ?")) {
                    up.setString(1, now);
                    up.setInt(2, e.getKey());
                    up.executeUpdate();
                }
            } else if (!e.getValue().equals(now)) {
                throw new SQLException("반입된 마이그레이션 " + file + " 이 바뀌었다(적용 때 " + e.getValue().substring(0, 12) + "…, 지금 "
                        + now.substring(0, 12) + "…). 적용된 파일은 고치지 않는다 — 바꿀 것은 새 번호 파일로 더한다(0-13·0-20)");
            }
        }
    }

    /** SHA-256(LF 로 맞춘 본문) 16진수 */
    static String checksum(String script) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(script.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
