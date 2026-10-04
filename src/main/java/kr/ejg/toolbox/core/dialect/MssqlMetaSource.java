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
 * SQL Server — 코멘트는 확장 속성 MS_Description(mssql-jdbc 는 REMARKS 로 안 준다), 행수는 sys.partitions
 * (index_id 0·1 합), 생성은 sys.tables.create_date. UNIQUE 는 sys.key_constraints 'UQ'.
 */
public class MssqlMetaSource extends VendorMetaSource {

    private static final String TAB_COMMENTS =
            "SELECT o.name, CAST(ep.value AS NVARCHAR(4000)) FROM sys.objects o "
            + "JOIN sys.schemas s ON s.schema_id = o.schema_id "
            + "JOIN sys.extended_properties ep ON ep.major_id = o.object_id AND ep.minor_id = 0 "
            + "AND ep.class = 1 AND ep.name = 'MS_Description' "
            + "WHERE s.name = ? AND o.type IN ('U','V')";
    private static final String COL_COMMENTS =
            "SELECT o.name, c.name, CAST(ep.value AS NVARCHAR(4000)) FROM sys.objects o "
            + "JOIN sys.schemas s ON s.schema_id = o.schema_id "
            + "JOIN sys.columns c ON c.object_id = o.object_id "
            + "JOIN sys.extended_properties ep ON ep.major_id = o.object_id AND ep.minor_id = c.column_id "
            + "AND ep.class = 1 AND ep.name = 'MS_Description' "
            + "WHERE s.name = ? AND o.type IN ('U','V')";
    private static final String STATS =
            "SELECT t.name, (SELECT SUM(p.rows) FROM sys.partitions p "
            + "WHERE p.object_id = t.object_id AND p.index_id IN (0,1)), t.create_date "
            + "FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id WHERE s.name = ?";
    private static final String CHECKS =
            "SELECT CC.NAME, CC.DEFINITION FROM SYS.CHECK_CONSTRAINTS CC "
            + "JOIN SYS.TABLES T ON T.OBJECT_ID = CC.PARENT_OBJECT_ID JOIN SYS.SCHEMAS S ON S.SCHEMA_ID = T.SCHEMA_ID "
            + "WHERE S.NAME = ? AND T.NAME = ? ORDER BY CC.NAME";
    private static final String SIZE =
            "SELECT SUM(A.TOTAL_PAGES) * 8192 FROM SYS.PARTITIONS P "
            + "JOIN SYS.ALLOCATION_UNITS A ON (A.TYPE IN (1, 3) AND A.CONTAINER_ID = P.HOBT_ID) "
            + "OR (A.TYPE = 2 AND A.CONTAINER_ID = P.PARTITION_ID) "
            + "JOIN SYS.TABLES T ON T.OBJECT_ID = P.OBJECT_ID JOIN SYS.SCHEMAS S ON S.SCHEMA_ID = T.SCHEMA_ID WHERE S.NAME = ?";
    private static final String UNIQUES =
            "SELECT kc.name, c.name FROM sys.key_constraints kc "
            + "JOIN sys.tables t ON t.object_id = kc.parent_object_id "
            + "JOIN sys.schemas s ON s.schema_id = t.schema_id "
            + "JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id "
            + "JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id "
            + "WHERE s.name = ? AND t.name = ? AND kc.type = 'UQ' ORDER BY kc.name, ic.key_ordinal";

    public MssqlMetaSource(Connection conn) {
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
        Table withUniques = vendor("uniques", base, () -> {
            try (PreparedStatement ps = prepare(UNIQUES)) {
                ps.setString(1, t.schema());
                ps.setString(2, t.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return base.withConstraints(base.pk(), base.fks(), uniques(rs));
                }
            }
        });
        return vendor("checks", withUniques, () -> withChecks(withUniques, CHECKS));
    }

    @Override
    public Schema loadSize(Schema s) throws SQLException {
        return withSize(s, SIZE, s.name());
    }
}
