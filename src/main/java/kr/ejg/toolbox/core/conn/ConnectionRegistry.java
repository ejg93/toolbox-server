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
 * 활성 프로필의 접속 목록 + 비밀번호. 비밀번호는 프로필 {@code password}(1-43), 메모리 덮어쓰기({@code /api/conn/{id}/password} —
 * CLI·시험)가 있으면 그것이 앞선다. 값은 응답·로그·H2 로 안 나간다(절대 규칙 2).
 * 메모리 덮어쓰기는 {@link #forget}·{@link #clearAll}(프로필 전환·서버 stop) 에서 0 으로 지운다.
 * 드라이버 예외 메시지는 사용자에게 돌려주되 비밀번호 문자열이 섞여 있으면 가린다.
 */
public final class ConnectionRegistry {

    /** 접속 시험 결과. message 는 실패 사유(비밀번호 가림) */
    public record TestResult(boolean ok, String productName, String productVersion, String message) {
    }

    /** 목록 응답 — 비밀번호 값은 없고 있는지만(프로필 또는 메모리) */
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
                .map(c -> new Entry(c.id(), c.dialect(), c.url(), c.user(), passwords.containsKey(c.id()) || c.password() != null))
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
        char[] pw = password(c);
        if (pw != null) {
            props.setProperty("password", new String(pw));
        }
        try {
            return DriverManager.getConnection(c.url(), props);
        } catch (SQLException e) {
            String message = mask(e.getMessage(), pw);
            if (pw == null) { // 비밀번호 없이도 시도는 한다(비밀번호 없는 DB). 실패하면 넣을 자리를 알린다
                message += "\n→ 비밀번호가 없다 — profiles/" + activeProfile.get().map(Profile::name).orElse("<프로필>")
                        + ".yaml 접속 " + id + " 의 password 칸에 넣는다";
            }
            throw new SQLException(message, e.getSQLState(), e.getErrorCode());
        } finally {
            props.clear();
        }
    }

    public TestResult test(String id) {
        char[] pw = find(id).map(this::password).orElse(null);
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

    /** 실효 비밀번호 — 메모리 덮어쓰기가 있으면 그것, 없으면 프로필 password. 없으면 null */
    private char[] password(Profile.Connection c) {
        char[] mem = passwords.get(c.id());
        if (mem != null) {
            return mem;
        }
        return c.password() == null ? null : c.password().toCharArray();
    }

    private List<Profile.Connection> connections() {
        return activeProfile.get().map(Profile::connections).orElse(List.of());
    }
}
