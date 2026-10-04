package kr.ejg.toolbox.core.dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;

/**
 * Oracle — 오라클 JDBC 는 `remarksReporting` 없이는 REMARKS 를 안 준다. 코멘트는 ALL_TAB_COMMENTS·ALL_COL_COMMENTS,
 * 행수는 ALL_TABLES.NUM_ROWS(통계 기준 — 통계 전이면 null), 생성은 ALL_OBJECTS.CREATED. UNIQUE 는 ALL_CONSTRAINTS 'U'.
 * 순수본 `산출물_sql.html` 01·03 번 쿼리에서 옮겼다.
 */
public class OracleMetaSource extends VendorMetaSource {

    private static final String TAB_COMMENTS =
            "SELECT TABLE_NAME, COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER = ?";
    private static final String COL_COMMENTS =
            "SELECT TABLE_NAME, COLUMN_NAME, COMMENTS FROM ALL_COL_COMMENTS WHERE OWNER = ?";
    private static final String STATS =
            "SELECT T.TABLE_NAME, T.NUM_ROWS, O.CREATED FROM ALL_TABLES T "
            + "JOIN ALL_OBJECTS O ON O.OWNER = T.OWNER AND O.OBJECT_NAME = T.TABLE_NAME AND O.OBJECT_TYPE = 'TABLE' "
            + "WHERE T.OWNER = ?";
    private static final String UNIQUES =
            "SELECT C.CONSTRAINT_NAME, CC.COLUMN_NAME FROM ALL_CONSTRAINTS C "
            + "JOIN ALL_CONS_COLUMNS CC ON CC.OWNER = C.OWNER AND CC.CONSTRAINT_NAME = C.CONSTRAINT_NAME "
            + "AND CC.TABLE_NAME = C.TABLE_NAME "
            + "WHERE C.OWNER = ? AND C.TABLE_NAME = ? AND C.CONSTRAINT_TYPE = 'U' "
            + "ORDER BY C.CONSTRAINT_NAME, CC.POSITION";

    public OracleMetaSource(Connection conn) {
        super(conn);
    }

    @Override
    public Schema loadComments(Schema s) throws SQLException {
        Map<String, String> tables = vendor("comments", Map.of(), () -> {
            Map<String, String> m = new HashMap<>();
            try (PreparedStatement ps = prepare(TAB_COMMENTS)) {
                ps.setString(1, s.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        m.put(rs.getString(1), rs.getString(2));
                    }
                }
            }
            return m;
        });
        Map<String, Map<String, String>> cols = vendor("comments", Map.of(), () -> {
            Map<String, Map<String, String>> m = new HashMap<>();
            try (PreparedStatement ps = prepare(COL_COMMENTS)) {
                ps.setString(1, s.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        put(m, rs.getString(1), rs.getString(2), rs.getString(3));
                    }
                }
            }
            return m;
        });
        return applyComments(s, tables, cols);
    }

    @Override
    public Schema loadStats(Schema s) throws SQLException {
        Map<String, Long> rows = new HashMap<>();
        Map<String, LocalDateTime> created = new HashMap<>();
        boolean ok = vendor("stats", false, () -> {
            try (PreparedStatement ps = prepare(STATS)) {
                ps.setString(1, s.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.put(rs.getString(1), longOrNull(rs, 2));
                        created.put(rs.getString(1), ts(rs, 3));
                    }
                }
            }
            return true;
        });
        if (!ok) {
            // 중간에 깨졌으면 반쯤 읽은 값도 버린다 — 행 수 null
            rows.clear();
            created.clear();
        }
        return applyStats(s, rows, created);
    }

    @Override
    public Table loadConstraints(Table t) throws SQLException {
        Table base = super.loadConstraints(t);
        return vendor("uniques", base, () -> {
            try (PreparedStatement ps = prepare(UNIQUES)) {
                ps.setString(1, t.schema());
                ps.setString(2, t.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return base.withConstraints(base.pk(), base.fks(), uniques(rs));
                }
            }
        });
    }
}
