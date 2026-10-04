package kr.ejg.toolbox.core.dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.JdbcMetaSource;
import kr.ejg.toolbox.core.meta.MetaSource;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;

/**
 * 벤더 구현의 공통 — 뼈대는 JDBC(부모), 코멘트·통계·UNIQUE 제약은 벤더 딕셔너리 SQL 로 덧씌운다(5-2, 1-3).
 * 딕셔너리 SQL 은 하위 클래스의 상수 + 바인드만 쓴다(SpotBugs SQL_INJECTION_JDBC, 0-22).
 * 딕셔너리 SQL 은 {@link #vendor} 로 감싼다 — 권한이 모자라거나 옛 판에 뷰가 없으면 JDBC 값으로 물러서고 경고만 남긴다(1-12).
 */
abstract class VendorMetaSource extends JdbcMetaSource {

    /** 딕셔너리 SQL 한 문의 시간 제한(초) */
    static final int QUERY_TIMEOUT_SECONDS = 60;

    /** (종류, SQLState, 벤더 코드) → 건수 */
    private final Map<List<Object>, Integer> warnings = new LinkedHashMap<>();

    VendorMetaSource(Connection conn) {
        super(conn);
    }

    @FunctionalInterface
    interface VendorCall<T> {
        T call() throws SQLException;
    }

    /**
     * 벤더 SQL 을 돌린다. {@link SQLException} 이면 fallback 을 돌려주고 종류·SQLState·벤더 코드만 적는다.
     * 인터럽트(작업 취소)된 스레드의 실패와 SQLException 밖의 예외(취소 예외 포함)는 그대로 던진다.
     */
    <T> T vendor(String kind, T fallback, VendorCall<T> call) throws SQLException {
        try {
            return call.call();
        } catch (SQLException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            warnings.merge(List.of(kind, String.valueOf(e.getSQLState()), e.getErrorCode()), 1, Integer::sum);
            return fallback;
        }
    }

    /** 시간 제한을 건 PreparedStatement */
    PreparedStatement prepare(String sql) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        } catch (SQLException | RuntimeException e) {
            ps.close();
            throw e;
        }
        return ps;
    }

    @Override
    public List<MetaSource.Warning> warnings() {
        List<MetaSource.Warning> out = new ArrayList<>();
        warnings.forEach((k, n) -> out.add(new MetaSource.Warning((String) k.get(0), (String) k.get(1), (Integer) k.get(2), n)));
        return out;
    }

    /** 테이블명 → 코멘트, (테이블명 → 컬럼명 → 코멘트). 딕셔너리에 있는 값이 JDBC REMARKS 를 이긴다. 없으면 그대로 */
    static Schema applyComments(Schema s, Map<String, String> tableComments, Map<String, Map<String, String>> columnComments) {
        List<Table> out = new ArrayList<>();
        for (Table t : s.tables()) {
            String tc = blank(tableComments.get(t.name())) ? t.comment() : tableComments.get(t.name());
            Map<String, String> cc = columnComments.getOrDefault(t.name(), Map.of());
            List<Column> cols = new ArrayList<>();
            for (Column c : t.columns()) {
                String v = cc.get(c.name());
                cols.add(blank(v) ? c : c.withComment(v));
            }
            out.add(t.withComment(tc).withColumns(cols));
        }
        return s.withTables(out);
    }

    /** 테이블명 → 행수·생성시각. lastDdlAt 은 채우지 않는다 — Oracle LAST_DDL_TIME 은 GRANT·COMMENT 에도 바뀌어 믿을 수 없다(db_docs 「확인된 사실」) */
    static Schema applyStats(Schema s, Map<String, Long> rows, Map<String, LocalDateTime> created) {
        List<Table> out = new ArrayList<>();
        for (Table t : s.tables()) {
            out.add(t.withStats(rows.get(t.name()), created.get(t.name()), null));
        }
        return s.withTables(out);
    }

    /** 스키마 용량 한 값(1-23). SUM 이 null(세그먼트·표 없음)이면 0. 실패(권한·뷰 없음)하면 그대로(null) + 경고 size */
    Schema withSize(Schema s, String sql, String bind) throws SQLException {
        return vendor("size", s, () -> {
            try (PreparedStatement ps = prepare(sql)) {
                if (bind != null) {
                    ps.setString(1, bind);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return s.withSizeBytes(rs.next() ? rs.getLong(1) : 0L);
                }
            }
        });
    }

    /** (이름, 조건 글) 행 → CHECK 목록(1-22). 조건이 null 인 행은 뺀다 */
    static List<Check> checks(ResultSet rs) throws SQLException {
        List<Check> out = new ArrayList<>();
        while (rs.next()) {
            String cond = rs.getString(2);
            if (cond != null) {
                out.add(new Check(rs.getString(1), cond.strip()));
            }
        }
        return out;
    }

    /** 표 하나의 CHECK — SQL 은 (스키마, 표) 두 바인드 */
    Table withChecks(Table t, String sql) throws SQLException {
        try (PreparedStatement ps = prepare(sql)) {
            ps.setString(1, t.schema());
            ps.setString(2, t.name());
            try (ResultSet rs = ps.executeQuery()) {
                return t.withChecks(checks(rs));
            }
        }
    }

    /** (제약명, 컬럼명) 행들 — 컬럼은 순번 순으로 온다는 전제. 제약은 자바 문자열 순(DB 콜레이션과 무관하게 JDBC 경로·스냅샷 읽기와 같은 순서) */
    static List<UniqueKey> uniques(ResultSet rs) throws SQLException {
        Map<String, List<String>> byName = new java.util.TreeMap<>();
        while (rs.next()) {
            byName.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
        }
        List<UniqueKey> out = new ArrayList<>();
        byName.forEach((n, cols) -> out.add(new UniqueKey(n, cols)));
        return out;
    }

    /** 2단 맵에 넣는다 */
    static void put(Map<String, Map<String, String>> m, String table, String column, String value) {
        m.computeIfAbsent(table, k -> new LinkedHashMap<>()).put(column, value);
    }

    static LocalDateTime ts(ResultSet rs, int col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    static Long longOrNull(ResultSet rs, int col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
