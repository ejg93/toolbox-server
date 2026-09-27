package kr.ejg.toolbox.core.sqlrun;

import java.io.IOException;
import java.io.Reader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Timestamp;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 쓴 SQL 을 그 접속 권한으로 그대로 실행한다(2.1 부산물·2.5, 1-7). 문장을 나누지 않는다.
 * <b>SQL·결과를 로그에 남기지 않는다</b>(절대 규칙 3) — 이 클래스는 로거가 없다.
 */
public final class SqlRunner {

    public static final int DEFAULT_MAX_ROWS = 1000;
    public static final int DEFAULT_TIMEOUT_SEC = 60;

    private SqlRunner() {
    }

    /**
     * @param binds    `?` 자리 값(순서대로). 없으면 빈 목록
     * @param maxRows  이만큼만 담고 더 있으면 truncated
     */
    public static ResultTable run(Connection conn, String sql, List<Object> binds, int maxRows, int timeoutSec)
            throws SQLException {
        try {
            return run(conn, sql, binds, maxRows, timeoutSec, true);
        } catch (SQLException e) {
            // SQL Server 드라이버는 setMaxRows 를 SET ROWCOUNT 로 건다 — ROWCOUNT 가 걸리면 NEXT VALUE FOR 가 막힌다(11739, V-8 실물 스니펫).
            // 행 상한은 아래 읽기 루프가 지키므로 이 오류만 상한 없이 한 번 더
            if (e.getErrorCode() == 11739) {
                return run(conn, sql, binds, maxRows, timeoutSec, false);
            }
            throw e;
        }
    }

    private static ResultTable run(Connection conn, String sql, List<Object> binds, int maxRows, int timeoutSec, boolean driverLimit)
            throws SQLException {
        long start = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try {
                ps.setQueryTimeout(timeoutSec);
            } catch (SQLFeatureNotSupportedException ignored) {
                // 타임아웃을 모르는 드라이버 — 그대로 돈다(행의 사다리)
            }
            if (driverLimit) {
                ps.setMaxRows(maxRows + 1);
            }
            for (int i = 0; i < binds.size(); i++) {
                ps.setObject(i + 1, binds.get(i));
            }
            boolean query = ps.execute();
            if (!query) {
                return new ResultTable(List.of(), List.of(), false, ps.getUpdateCount(), ms(start));
            }
            try (ResultSet rs = ps.getResultSet()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                List<ResultTable.Col> cols = new ArrayList<>(n);
                for (int i = 1; i <= n; i++) {
                    cols.add(new ResultTable.Col(md.getColumnLabel(i), md.getColumnTypeName(i)));
                }
                List<List<Object>> rows = new ArrayList<>();
                boolean truncated = false;
                while (rs.next()) {
                    if (rows.size() == maxRows) {
                        truncated = true;
                        break;
                    }
                    List<Object> row = new ArrayList<>(n);
                    for (int i = 1; i <= n; i++) {
                        row.add(value(rs.getObject(i)));
                    }
                    rows.add(row);
                }
                return new ResultTable(cols, rows, truncated, -1, ms(start));
            }
        }
    }

    /** JSON·엑셀에 바로 쓸 값으로 — 날짜는 ISO 문자열, BLOB·이진은 크기만, CLOB 은 글자 */
    static Object value(Object v) throws SQLException {
        if (v == null || v instanceof Number || v instanceof Boolean || v instanceof String) {
            return v;
        }
        if (v instanceof Timestamp t) {
            return t.toLocalDateTime().toString();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof java.sql.Time t) {
            return t.toLocalTime().toString();
        }
        if (v instanceof TemporalAccessor) {
            return v.toString();
        }
        if (v instanceof byte[] b) {
            return "(BLOB " + b.length + " bytes)";
        }
        if (v instanceof Blob b) {
            return "(BLOB " + b.length() + " bytes)";
        }
        if (v instanceof Clob c) {
            try (Reader r = c.getCharacterStream()) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int k;
                while ((k = r.read(buf)) > 0) {
                    sb.append(buf, 0, k);
                }
                return sb.toString();
            } catch (IOException e) {
                throw new SQLException("CLOB 을 못 읽었다", e);
            }
        }
        return v.toString();
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
