package kr.ejg.toolbox.core.conn;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 외부 DB 연결을 읽기 전용으로 감싼다(9-1a, 설계 31 — 절대 규칙 6). {@link ConnectionRegistry#open} 이 주는 연결은 전부 이 래퍼다.
 * <ul>
 *   <li>문장은 {@link #check} 를 지나야 한다 — 문장 하나, 첫 단어 SELECT·WITH, 쓰기·잠금 단어 없음(리터럴·따옴표 식별자·주석은 빼고 본다)</li>
 *   <li>{@code executeUpdate}·배치 실행·{@code prepareCall}·{@code setReadOnly(false)}·갱신 가능 커서·{@code ResultSet} 의 행 쓰기는 거절</li>
 *   <li>{@code getConnection()}·{@code getStatement()}·{@code unwrap} 으로 실물에 닿지 않게 메타데이터·결과 집합도 감싼다</li>
 * </ul>
 * 거절은 {@link SQLFeatureNotSupportedException}. 메시지에 SQL 본문을 넣지 않는다(절대 규칙 3) — 첫 단어까지만.
 */
public final class ReadOnlyJdbc {

    private static final Set<String> FIRST = Set.of("SELECT", "WITH");
    private static final Pattern WRITE = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|CALL|EXEC|EXECUTE|INTO|LOCK)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> REFUSED_STATEMENT = Set.of("executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch");
    private static final Set<String> REFUSED_ROWS = Set.of("insertRow", "updateRow", "deleteRow");

    private ReadOnlyJdbc() {
    }

    public static Connection wrap(Connection real) {
        return proxy(Connection.class, new Conn(real));
    }

    /** 읽기 문장인가 — 아니면 {@link SQLFeatureNotSupportedException} */
    static void check(String sql) throws SQLException {
        String s = strip(sql == null ? "" : sql).strip();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).strip();
        }
        if (s.isEmpty()) {
            throw refuse("빈 문장");
        }
        if (s.indexOf(';') >= 0) {
            throw refuse("여러 문장");
        }
        int end = 0;
        while (end < s.length() && Character.isLetter(s.charAt(end))) {
            end++;
        }
        String first = s.substring(0, end).toUpperCase(Locale.ROOT);
        if (!FIRST.contains(first)) {
            throw refuse(first.isEmpty() ? "SELECT·WITH 가 아닌 문장" : first + " 문장");
        }
        java.util.regex.Matcher m = WRITE.matcher(s);
        if (m.find()) {
            throw refuse(m.group(1).toUpperCase(Locale.ROOT) + " 이 든 문장");
        }
    }

    /** 문자열 리터럴·따옴표 식별자(" ` [])·주석(-- /* *&#47;)을 공백으로 */
    static String strip(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
                out.append(' ');
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int e = sql.indexOf("*/", i + 2);
                i = e < 0 ? n : e + 2;
                out.append(' ');
            } else if (c == '\'' || c == '"' || c == '`' || c == '[') {
                char close = c == '[' ? ']' : c;
                i++;
                while (i < n) {
                    if (sql.charAt(i) == close) {
                        if (close != ']' && i + 1 < n && sql.charAt(i + 1) == close) {
                            i += 2; // '' · "" 이스케이프
                            continue;
                        }
                        break;
                    }
                    i++;
                }
                i++;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static SQLFeatureNotSupportedException refuse(String what) {
        return new SQLFeatureNotSupportedException("읽기 전용 연결 — " + what + "은 하지 않는다");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler h) {
        return (T) Proxy.newProxyInstance(ReadOnlyJdbc.class.getClassLoader(), new Class<?>[] {type}, h);
    }

    /** 갱신 가능 커서(CONCUR_UPDATABLE) — createStatement(type, concurrency…)·prepareStatement(sql, type, concurrency…) 의 둘째 int */
    private static boolean updatable(Object[] args, int at) {
        return args != null && args.length > at && args[at] instanceof Integer i && i == ResultSet.CONCUR_UPDATABLE;
    }

    /** 공통 — equals·hashCode·unwrap·isWrapperFor 는 실물을 내주지 않는다 */
    private abstract static class Base implements InvocationHandler {
        final Object real;

        Base(Object real) {
            this.real = real;
        }

        abstract Object handle(Object proxy, Method m, Object[] args) throws Throwable;

        @Override
        public final Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            switch (m.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "ReadOnlyJdbc(" + real.getClass().getSimpleName() + ")";
                case "unwrap":
                    if (((Class<?>) args[0]).isInstance(proxy)) {
                        return proxy;
                    }
                    throw refuse("실물 꺼내기(unwrap)");
                case "isWrapperFor":
                    return ((Class<?>) args[0]).isInstance(proxy);
                default:
                    return handle(proxy, m, args);
            }
        }

        Object call(Method m, Object[] args) throws Throwable {
            try {
                return m.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static final class Conn extends Base {
        Conn(Connection real) {
            super(real);
        }

        @Override
        Object handle(Object proxy, Method m, Object[] args) throws Throwable {
            Connection self = (Connection) proxy;
            switch (m.getName()) {
                case "prepareCall":
                    throw refuse("저장 프로시저 호출(prepareCall)");
                case "setReadOnly":
                    if (Boolean.FALSE.equals(args[0])) {
                        throw refuse("읽기 전용 끄기");
                    }
                    return call(m, args);
                case "createStatement":
                    if (updatable(args, 1)) {
                        throw refuse("갱신 가능 커서");
                    }
                    return proxy(Statement.class, new Stmt((Statement) call(m, args), self));
                case "prepareStatement":
                    check((String) args[0]);
                    if (updatable(args, 2)) {
                        throw refuse("갱신 가능 커서");
                    }
                    return proxy(PreparedStatement.class, new Stmt((Statement) call(m, args), self));
                case "getMetaData":
                    return proxy(DatabaseMetaData.class, new Meta((DatabaseMetaData) call(m, args), self));
                default:
                    return call(m, args);
            }
        }
    }

    private static final class Stmt extends Base {
        private final Connection conn;

        Stmt(Statement real, Connection conn) {
            super(real);
            this.conn = conn;
        }

        @Override
        Object handle(Object proxy, Method m, Object[] args) throws Throwable {
            String name = m.getName();
            if (REFUSED_STATEMENT.contains(name)) {
                throw refuse(name);
            }
            if ((name.equals("execute") || name.equals("executeQuery") || name.equals("addBatch")) && args != null && args.length > 0
                    && args[0] instanceof String sql) {
                check(sql);
            }
            if (name.equals("getConnection")) {
                return conn;
            }
            Object r = call(m, args);
            return r instanceof ResultSet rs ? proxy(ResultSet.class, new Rows(rs, (Statement) proxy)) : r;
        }
    }

    private static final class Meta extends Base {
        private final Connection conn;

        Meta(DatabaseMetaData real, Connection conn) {
            super(real);
            this.conn = conn;
        }

        @Override
        Object handle(Object proxy, Method m, Object[] args) throws Throwable {
            if (m.getName().equals("getConnection")) {
                return conn;
            }
            Object r = call(m, args);
            return r instanceof ResultSet rs ? proxy(ResultSet.class, new Rows(rs, null)) : r;
        }
    }

    private static final class Rows extends Base {
        private final Statement stmt;

        Rows(ResultSet real, Statement stmt) {
            super(real);
            this.stmt = stmt;
        }

        @Override
        Object handle(Object proxy, Method m, Object[] args) throws Throwable {
            if (REFUSED_ROWS.contains(m.getName())) {
                throw refuse(m.getName());
            }
            if (m.getName().equals("getStatement")) {
                return stmt;
            }
            return call(m, args);
        }
    }
}
