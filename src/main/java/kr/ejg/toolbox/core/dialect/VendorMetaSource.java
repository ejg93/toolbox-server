package kr.ejg.toolbox.core.dialect;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.JdbcMetaSource;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;

/**
 * 벤더 구현의 공통 — 뼈대는 JDBC(부모), 코멘트·통계·UNIQUE 제약은 벤더 딕셔너리 SQL 로 덧씌운다(5-2, 1-3).
 * 딕셔너리 SQL 은 하위 클래스의 상수 + 바인드만 쓴다(SpotBugs SQL_INJECTION_JDBC, 0-22).
 */
abstract class VendorMetaSource extends JdbcMetaSource {

    VendorMetaSource(Connection conn) {
        super(conn);
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
        return new Schema(s.name(), s.dbVersion(), out);
    }

    /** 테이블명 → 행수·생성시각. lastDdlAt 은 채우지 않는다 — Oracle LAST_DDL_TIME 은 GRANT·COMMENT 에도 바뀌어 믿을 수 없다(db_docs 「확인된 사실」) */
    static Schema applyStats(Schema s, Map<String, Long> rows, Map<String, LocalDateTime> created) {
        List<Table> out = new ArrayList<>();
        for (Table t : s.tables()) {
            out.add(t.withStats(rows.get(t.name()), created.get(t.name()), null));
        }
        return new Schema(s.name(), s.dbVersion(), out);
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
