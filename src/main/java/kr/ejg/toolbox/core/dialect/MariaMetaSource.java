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
    /** FK 규칙 원문 — 드라이버(mariadb·connector-j)마다 같은 FK 를 NO ACTION·RESTRICT 로 갈라 준다(V-23 실측). 5.7 에도 있다 */
    private static final String FK_RULES =
            "SELECT CONSTRAINT_NAME, DELETE_RULE, UPDATE_RULE FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS "
            + "WHERE CONSTRAINT_SCHEMA = ? AND TABLE_NAME = ?";
    /** MariaDB 는 CHECK_CONSTRAINTS 에 TABLE_NAME 이 있다 */
    private static final String CHECKS =
            "SELECT CONSTRAINT_NAME, CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS "
            + "WHERE CONSTRAINT_SCHEMA = ? AND TABLE_NAME = ? ORDER BY CONSTRAINT_NAME";
    /** MySQL 8.0 은 TABLE_NAME 이 없어(1054) TABLE_CONSTRAINTS 와 잇는다. 5.7 은 표가 없다(1109) — 물러선다(설계 15 실측) */
    private static final String CHECKS_MYSQL =
            "SELECT CC.CONSTRAINT_NAME, CC.CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS CC "
            + "JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS TC ON TC.CONSTRAINT_SCHEMA = CC.CONSTRAINT_SCHEMA "
            + "AND TC.CONSTRAINT_NAME = CC.CONSTRAINT_NAME AND TC.CONSTRAINT_TYPE = 'CHECK' "
            + "WHERE TC.TABLE_SCHEMA = ? AND TC.TABLE_NAME = ? ORDER BY CC.CONSTRAINT_NAME";
    private static final String SIZE =
            "SELECT SUM(DATA_LENGTH + INDEX_LENGTH) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ?";
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
        Map<String, String> tables = vendor("comments", Map.of(), () -> {
            Map<String, String> m = new HashMap<>();
            try (PreparedStatement ps = prepare(TAB_INFO)) {
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
            try (PreparedStatement ps = prepare(TAB_INFO)) {
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
        Table withRules = vendor("fkRules", withUniques, () -> {
            Map<String, String[]> rules = new HashMap<>();
            try (PreparedStatement ps = prepare(FK_RULES)) {
                ps.setString(1, t.schema());
                ps.setString(2, t.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rules.put(rs.getString(1), new String[] {rs.getString(2), rs.getString(3)});
                    }
                }
            }
            return withRules(withUniques, rules);
        });
        return vendor("checks", withRules, () -> {
            try {
                return withChecks(withRules, CHECKS);
            } catch (SQLException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                return withChecks(withRules, CHECKS_MYSQL);
            }
        });
    }

    @Override
    public Schema loadSize(Schema s) throws SQLException {
        return withSize(s, SIZE, s.name());
    }
}
