package kr.ejg.toolbox.core.dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;

/**
 * MariaDB·MySQL — DB 가 곧 스키마인데 JDBC 는 그것을 **카탈로그**로 준다. 목록은 getCatalogs, 메타데이터 호출은
 * 카탈로그 인자로 넘긴다. 코멘트·행수(InnoDB 근사)·생성은 information_schema.TABLES·COLUMNS,
 * UNIQUE 는 TABLE_CONSTRAINTS 'UNIQUE'. DDL 변경 시각은 없다(순수본 01번 ★).
 */
public class MariaMetaSource extends VendorMetaSource {

    private static final Set<String> SYSTEM_DBS = Set.of("information_schema", "mysql", "performance_schema", "sys");

    private static final String TAB_INFO =
            "SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS, CREATE_TIME, TABLE_TYPE FROM information_schema.TABLES "
            + "WHERE TABLE_SCHEMA = ?";
    private static final String COL_COMMENTS =
            "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ?";
    private static final String UNIQUES =
            "SELECT tc.CONSTRAINT_NAME, k.COLUMN_NAME FROM information_schema.TABLE_CONSTRAINTS tc "
            + "JOIN information_schema.KEY_COLUMN_USAGE k ON k.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA "
            + "AND k.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND k.TABLE_NAME = tc.TABLE_NAME "
            + "WHERE tc.TABLE_SCHEMA = ? AND tc.TABLE_NAME = ? AND tc.CONSTRAINT_TYPE = 'UNIQUE' "
            + "ORDER BY tc.CONSTRAINT_NAME, k.ORDINAL_POSITION";

    public MariaMetaSource(Connection conn) {
        super(conn);
    }

    @Override
    protected String catalog(String schema) {
        return schema;
    }

    @Override
    protected String schemaArg(String schema) {
        return null;
    }

    @Override
    public List<String> listSchemas() throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = md().getCatalogs()) {
            while (rs.next()) {
                String c = rs.getString("TABLE_CAT");
                if (c != null && !SYSTEM_DBS.contains(c.toLowerCase(Locale.ROOT))) {
                    out.add(c);
                }
            }
        }
        out.sort(null);
        return out;
    }

    @Override
    public Schema loadComments(Schema s) throws SQLException {
        Map<String, String> tables = new HashMap<>();
        Map<String, Map<String, String>> cols = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(TAB_INFO)) {
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
        Map<String, LocalDateTime> created = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(TAB_INFO)) {
            ps.setString(1, s.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!"BASE TABLE".equals(rs.getString(5))) {
                        continue;
                    }
                    rows.put(rs.getString(1), longOrNull(rs, 3));
                    created.put(rs.getString(1), ts(rs, 4));
                }
            }
        }
        return applyStats(s, rows, created);
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
