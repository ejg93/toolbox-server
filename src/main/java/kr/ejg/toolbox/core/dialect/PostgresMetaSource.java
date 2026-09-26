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
 * PostgreSQL — 코멘트는 pg_description(obj_description·col_description), 행수는 pg_class.reltuples
 * (통계 전이면 -1 → null). 생성·DDL 시각은 카탈로그에 없다(null). UNIQUE 는 pg_constraint contype 'u'.
 */
public class PostgresMetaSource extends VendorMetaSource {

    private static final String TAB_COMMENTS =
            "SELECT c.relname, obj_description(c.oid, 'pg_class') FROM pg_class c "
            + "JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ? AND c.relkind IN ('r','p','v')";
    private static final String COL_COMMENTS =
            "SELECT c.relname, a.attname, col_description(c.oid, a.attnum) FROM pg_class c "
            + "JOIN pg_namespace n ON n.oid = c.relnamespace "
            + "JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped "
            + "WHERE n.nspname = ? AND c.relkind IN ('r','p','v')";
    private static final String STATS =
            "SELECT c.relname, c.reltuples::bigint FROM pg_class c "
            + "JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ? AND c.relkind IN ('r','p')";
    private static final String UNIQUES =
            "SELECT con.conname, a.attname FROM pg_constraint con "
            + "JOIN pg_class c ON c.oid = con.conrelid JOIN pg_namespace n ON n.oid = c.relnamespace "
            + "JOIN LATERAL unnest(con.conkey) WITH ORDINALITY AS k(attnum, ord) ON true "
            + "JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = k.attnum "
            + "WHERE n.nspname = ? AND c.relname = ? AND con.contype = 'u' ORDER BY con.conname, k.ord";

    public PostgresMetaSource(Connection conn) {
        super(conn);
    }

    @Override
    public Schema loadComments(Schema s) throws SQLException {
        Map<String, String> tables = new HashMap<>();
        Map<String, Map<String, String>> cols = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(TAB_COMMENTS)) {
            ps.setString(1, s.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(COL_COMMENTS)) {
            ps.setString(1, s.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    put(cols, rs.getString(1), rs.getString(2), rs.getString(3));
                }
            }
        }
        return applyComments(s, tables, cols);
    }

    @Override
    public Schema loadStats(Schema s) throws SQLException {
        Map<String, Long> rows = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(STATS)) {
            ps.setString(1, s.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Long n = longOrNull(rs, 2);
                    rows.put(rs.getString(1), n == null || n < 0 ? null : n);
                }
            }
        }
        return applyStats(s, rows, Map.<String, LocalDateTime>of());
    }

    @Override
    public Table loadConstraints(Table t) throws SQLException {
        Table base = super.loadConstraints(t);
        try (PreparedStatement ps = conn.prepareStatement(UNIQUES)) {
            ps.setString(1, t.schema());
            ps.setString(2, t.name());
            try (ResultSet rs = ps.executeQuery()) {
                return base.withConstraints(base.pk(), base.fks(), uniques(rs));
            }
        }
    }
}
