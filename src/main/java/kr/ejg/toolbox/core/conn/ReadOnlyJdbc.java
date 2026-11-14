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

/**
 * 외부 DB 연결을 읽기 전용으로 감싼다(9-1a, 설계 31 — 절대 규칙 6). {@link ConnectionRegistry#open} 이 주는 연결은 전부 이 래퍼다.
 * <ul>
 *   <li>문장은 {@link #check} 를 지나야 한다 — 주석 꼴(실행·안 닫힘·겹침 거절), 첫 낱말 SELECT·WITH, 문장 하나, 쓰기·잠금 낱말 없음(원문 낱말 — 리터럴·주석 안도 본다, 9-6)</li>
 *   <li>{@code executeUpdate}·배치 실행·{@code prepareCall}·{@code setReadOnly(false)}·갱신 가능 커서·{@code ResultSet} 의 행 쓰기는 거절</li>
 *   <li>{@code getConnection()}·{@code getStatement()}·{@code unwrap} 으로 실물에 닿지 않게 메타데이터·결과 집합도 감싼다</li>
 * </ul>
 * 거절은 {@link SQLFeatureNotSupportedException}. 메시지에 SQL 본문을 넣지 않는다(절대 규칙 3) — 첫 단어까지만.
 */
public final class ReadOnlyJdbc {

    /** 거절 오류문의 머리 — 쓰는 쪽(SqlProbe 6-34)이 「DB 오류」 와 「래퍼 거절」 을 가른다 */
    public static final String REFUSED = "읽기 전용 연결 — ";

    private static final Set<String> FIRST = Set.of("SELECT", "WITH");
    private static final String FOR = "FOR";
    private static final String SHARE = "SHARE";
    private static final String GET_CONNECTION = "getConnection";
    private static final String GET_STATEMENT = "getStatement";
    /** 쓰기·잠금 낱말(대문자, NFKC 뒤) — 원문 낱말로 잰다 */
    private static final Set<String> WRITE = Set.of("INSERT", "UPDATE", "DELETE", "MERGE", "DROP", "ALTER", "CREATE", "TRUNCATE", "GRANT", "REVOKE",
            "CALL", "EXEC", "EXECUTE", "INTO", "LOCK",
            // 9-6: ; 없이 이어지는 문장(SQL Server 배치)
            "SHUTDOWN", "BACKUP", "RESTORE", "KILL", "DENY", "DBCC", "DISABLE", "ENABLE", "RECONFIGURE", "CHECKPOINT", "WAITFOR", "SETUSER", "REVERT",
            "WRITETEXT", "UPDATETEXT",
            // 9-6: SQL Server 잠금 힌트
            "UPDLOCK", "HOLDLOCK", "TABLOCK", "TABLOCKX", "XLOCK", "PAGLOCK", "SERIALIZABLE", "REPEATABLEREAD");
    private static final Set<String> REFUSED_STATEMENT = Set.of("executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch");
    /** LOB·배열을 새로 만드는 것 — 드라이버 API 로 대형 객체를 쓸 수 있다 */
    private static final Set<String> REFUSED_CREATE = Set.of("createBlob", "createClob", "createNClob", "createSQLXML", "createArrayOf", "createStruct");
    private static final Set<String> REFUSED_ROWS = Set.of("insertRow", "updateRow", "deleteRow");

    private ReadOnlyJdbc() {
    }

    public static Connection wrap(Connection real) {
        return proxy(Connection.class, new Conn(real));
    }

    /**
     * 읽기 문장인가 — 아니면 {@link SQLFeatureNotSupportedException}. 9-6(설계 69) 순서: ① 끝 공백·{@code ;} 뗌, 비면 거절
     * ② 블록 주석 꼴 — 실행 주석({@code /*!}·{@code /*M!})·안 닫힘·겹침 거절 ③ 앞 공백·{@code --} 줄·블록 주석을 건너뛴 첫 낱말이 SELECT·WITH
     * ④ 원문에 {@code ;} 면 거절 ⑤ NFKC + 대문자로 맞춘 원문의 낱말에 쓰기·잠금 낱말이 있거나 FOR 뒤 SHARE 면 거절.
     * 인용·주석 규칙이 DB 마다 달라(PostgreSQL {@code $$}·{@code E'\''}, MariaDB {@code #}·{@code /*!}, 겹친 주석) <b>원문으로</b> 잰다 —
     * 리터럴·주석 안의 {@code ;}·쓰기 낱말도 거절한다(엄격한 쪽, 번들 48 마무리 리뷰)
     */
    static void check(String sql) throws SQLException {
        String raw = trimEnd(sql == null ? "" : sql);
        if (raw.isEmpty()) {
            throw refuse("빈 문장");
        }
        comments(raw);
        String first = firstWord(raw);
        if (!FIRST.contains(first)) {
            throw refuse(first.isEmpty() ? "SELECT·WITH 가 아닌 문장" : first + " 문장");
        }
        if (raw.indexOf(';') >= 0) {
            throw refuse("여러 문장(리터럴·주석 안의 ; 포함)");
        }
        words(raw);
    }

    /** 끝 공백·{@code ;} 를 뗀다 */
    private static String trimEnd(String s) {
        String t = s.strip();
        while (t.endsWith(";")) {
            t = t.substring(0, t.length() - 1).strip();
        }
        return t;
    }

    /** 블록 주석 꼴 — DB 마다 다르게 읽히는 것은 전부 거절: 실행 주석(/*! /*M!), 안 닫힘, 겹침. 따옴표 상태는 안 본다(엄격한 쪽) */
    private static void comments(String raw) throws SQLException {
        int i = raw.indexOf("/*");
        while (i >= 0) {
            if (raw.startsWith("!", i + 2) || raw.regionMatches(true, i + 2, "M!", 0, 2)) {
                throw refuse("실행 주석(/*! … */)");
            }
            int e = raw.indexOf("*/", i + 2);
            if (e < 0) {
                throw refuse("안 닫힌 주석");
            }
            int inner = raw.indexOf("/*", i + 2);
            if (inner >= 0 && inner < e) {
                throw refuse("겹친 주석");
            }
            i = raw.indexOf("/*", e + 2);
        }
    }

    /** 앞의 공백·{@code --} 줄·블록 주석을 건너뛴 자리의 ASCII 글자 낱말(대문자). 글자가 아니면 "" */
    private static String firstWord(String raw) {
        int i = 0;
        int n = raw.length();
        while (i < n) {
            if (Character.isWhitespace(raw.charAt(i))) {
                i++;
            } else if (raw.startsWith("--", i)) {
                int e = raw.indexOf('\n', i);
                i = e < 0 ? n : e + 1;
            } else if (raw.startsWith("/*", i)) {
                i = raw.indexOf("*/", i + 2) + 2; // comments 가 닫힘을 확인했다
            } else {
                break;
            }
        }
        int end = i;
        while (end < n && raw.charAt(end) < 128 && Character.isLetter(raw.charAt(end))) {
            end++;
        }
        return raw.substring(i, end).toUpperCase(Locale.ROOT);
    }

    /** NFKC + 대문자로 맞춘 원문의 낱말 — 쓰기·잠금 낱말, FOR 뒤 SHARE 면 거절. 따옴표·주석 안도 본다 */
    private static void words(String raw) throws SQLException {
        String s = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);
        boolean seenFor = false;
        int i = 0;
        while (i < s.length()) {
            int e = wordEnd(s, i);
            String w = s.substring(i, e);
            if (WRITE.contains(w)) {
                throw refuse(w + " 이 든 문장");
            }
            if (FOR.equals(w)) {
                seenFor = true;
            } else if (seenFor && SHARE.equals(w)) {
                throw refuse("행 잠금(FOR SHARE)");
            }
            i = e;
        }
    }

    /** 낱말 끝 — 글자·_ 로 시작하면 글자·숫자·_ 끝까지, 숫자로 시작하면 숫자·. 끝까지(1INTO 의 INTO 는 새 낱말), 그 밖은 한 글자 */
    private static int wordEnd(String s, int i) {
        char c = s.charAt(i);
        int e = i + 1;
        if (Character.isLetter(c) || c == '_') {
            while (e < s.length() && (Character.isLetterOrDigit(s.charAt(e)) || s.charAt(e) == '_')) {
                e++;
            }
        } else if (Character.isDigit(c)) {
            while (e < s.length() && (Character.isDigit(s.charAt(e)) || s.charAt(e) == '.')) {
                e++;
            }
        }
        return e;
    }

    private static SQLFeatureNotSupportedException refuse(String what) {
        return new SQLFeatureNotSupportedException(REFUSED + what + "은 하지 않는다");
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
            if (REFUSED_CREATE.contains(m.getName())) {
                throw refuse(m.getName());
            }
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
            if (("execute".equals(name) || "executeQuery".equals(name) || "addBatch".equals(name)) && args != null && args.length > 0
                    && args[0] instanceof String sql) {
                check(sql);
            }
            if (GET_CONNECTION.equals(name)) {
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
            if (GET_CONNECTION.equals(m.getName())) {
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
            if (GET_STATEMENT.equals(m.getName())) {
                return stmt;
            }
            return lob(call(m, args));
        }
    }

    /** 결과의 LOB·배열은 읽기만 — set*·truncate 거절, 배열의 결과 집합도 감싼다 */
    static Object lob(Object r) {
        if (r instanceof java.sql.NClob x) {
            return proxy(java.sql.NClob.class, new Lob(x));
        }
        if (r instanceof java.sql.Clob x) {
            return proxy(java.sql.Clob.class, new Lob(x));
        }
        if (r instanceof java.sql.Blob x) {
            return proxy(java.sql.Blob.class, new Lob(x));
        }
        if (r instanceof java.sql.SQLXML x) {
            return proxy(java.sql.SQLXML.class, new Lob(x));
        }
        if (r instanceof java.sql.Array x) {
            return proxy(java.sql.Array.class, new Lob(x));
        }
        return r;
    }

    private static final class Lob extends Base {
        Lob(Object real) {
            super(real);
        }

        @Override
        Object handle(Object proxy, Method m, Object[] args) throws Throwable {
            if (m.getName().startsWith("set") || "truncate".equals(m.getName())) {
                throw refuse("LOB 쓰기(" + m.getName() + ")");
            }
            Object r = call(m, args);
            return r instanceof ResultSet rs ? proxy(ResultSet.class, new Rows(rs, null)) : r;
        }
    }
}
