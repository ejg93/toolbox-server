package kr.ejg.toolbox.core.conn;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.profile.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 활성 프로필의 접속 목록 + 메모리 비밀번호(절대 규칙 2 — 파일·H2·로그에 안 쓴다).
 * 비밀번호는 세션 동안 재접속에 쓰려고 남겨 두고, {@link #forget}·{@link #clearAll}(서버 stop) 에서 0 으로 지운다.
 * 드라이버 예외 메시지는 사용자에게 돌려주되 비밀번호 문자열이 섞여 있으면 가린다.
 */
public final class ConnectionRegistry {

    /** 접속 시험 결과. message 는 실패 사유(비밀번호 가림) */
    public record TestResult(boolean ok, String productName, String productVersion, String message) {
    }

    /** 목록 응답 — 비밀번호 값은 없고 입력했는지만 */
    public record Entry(String id, String dialect, String url, String user, boolean hasPassword) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionRegistry.class);
    /** 접속 실패 안내(1-18) — 클래스 로드 때 읽어 정규식이 틀리면 기동이 실패한다 */
    private static final ConnHints HINTS = ConnHints.load();

    private final Supplier<Optional<Profile>> activeProfile;
    private final Map<String, char[]> passwords = new ConcurrentHashMap<>();

    public ConnectionRegistry(Supplier<Optional<Profile>> activeProfile) {
        this.activeProfile = activeProfile;
    }

    public List<Entry> list() {
        return connections().stream()
                .map(c -> new Entry(c.id(), c.dialect(), c.url(), c.user(), passwords.containsKey(c.id())))
                .toList();
    }

    public Optional<Profile.Connection> find(String id) {
        return connections().stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** 받은 배열을 복사해 두고 원본은 지운다 */
    public void setPassword(String id, char[] password) {
        char[] copy = Arrays.copyOf(password, password.length);
        Arrays.fill(password, '\0');
        char[] old = passwords.put(id, copy);
        if (old != null) {
            Arrays.fill(old, '\0');
        }
    }

    public void forget(String id) {
        char[] old = passwords.remove(id);
        if (old != null) {
            Arrays.fill(old, '\0');
        }
    }

    public void clearAll() {
        passwords.values().forEach(p -> Arrays.fill(p, '\0'));
        passwords.clear();
    }

    /** 새 JDBC 연결. 부른 쪽이 닫는다. 접속이 없으면 {@link IllegalArgumentException} */
    public Connection open(String id) throws SQLException {
        Profile.Connection c = find(id).orElseThrow(() -> new IllegalArgumentException("접속이 없다: " + id));
        Properties props = new Properties();
        if (c.user() != null) {
            props.setProperty("user", c.user());
        }
        char[] pw = passwords.get(id);
        if (pw != null) {
            props.setProperty("password", new String(pw));
        }
        try {
            return DriverManager.getConnection(c.url(), props);
        } catch (SQLException e) {
            throw new SQLException(mask(e.getMessage(), pw), e.getSQLState(), e.getErrorCode());
        } finally {
            props.clear();
        }
    }

    public TestResult test(String id) {
        char[] pw = passwords.get(id);
        try (Connection conn = open(id); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(testSql(find(id).map(Profile.Connection::dialect).orElse("")))) {
            rs.next();
            DatabaseMetaData md = conn.getMetaData();
            LOG.info("접속 시험 {} 성공", id);
            return new TestResult(true, md.getDatabaseProductName(), md.getDatabaseProductVersion(), null);
        } catch (SQLException | RuntimeException e) {
            LOG.info("접속 시험 {} 실패 ({})", id, e.getClass().getSimpleName());
            String masked = mask(e.getMessage(), pw); // 가림은 여기 한 자리 — 안내는 가린 글 뒤에 잇기만 한다(1-27)
            String message = e instanceof SQLException se
                    ? HINTS.hint(find(id).map(Profile.Connection::dialect).orElse(""), se).map(h -> masked + "\n→ " + h).orElse(masked)
                    : masked;
            return new TestResult(false, null, null, message);
        }
    }

    /** 방언별 한 줄 — Oracle 계열은 DUAL */
    static String testSql(String dialect) {
        String d = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
        return d.equals("oracle") || d.equals("tibero") ? "SELECT 1 FROM DUAL" : "SELECT 1";
    }

    static String mask(String message, char[] password) {
        if (message == null) {
            return null;
        }
        if (password == null || password.length == 0) {
            return message;
        }
        return message.replace(new String(password), "****");
    }

    private List<Profile.Connection> connections() {
        return activeProfile.get().map(Profile::connections).orElse(List.of());
    }
}
