package kr.ejg.toolbox.core.dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;

/**
 * Oracle — 오라클 JDBC 는 `remarksReporting` 없이는 REMARKS 를 안 준다. 코멘트는 ALL_TAB_COMMENTS·ALL_COL_COMMENTS,
 * 행수는 ALL_TABLES.NUM_ROWS(통계 기준 — 통계 전이면 null), 생성은 ALL_OBJECTS.CREATED. UNIQUE 는 ALL_CONSTRAINTS 'U'.
 * 순수본 `산출물_sql.html` 01·03 번 쿼리에서 옮겼다.
 * 인덱스 정렬은 ALL_IND_COLUMNS.DESCEND — 드라이버의 ASC_OR_DESC 는 늘 null 이고 DESC 컬럼은 함수 기반 인덱스라
 * 이름이 SYS_NC…$ 로 온다. 그 이름은 ALL_IND_EXPRESSIONS 의 식으로 바꾼다(1-20 실측).
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
    private static final String SIZE_USER = "SELECT SUM(BYTES) FROM USER_SEGMENTS";
    private static final String SIZE_DBA = "SELECT SUM(BYTES) FROM DBA_SEGMENTS WHERE OWNER = ?";
    private static final String UNIQUES =
            "SELECT C.CONSTRAINT_NAME, CC.COLUMN_NAME FROM ALL_CONSTRAINTS C "
            + "JOIN ALL_CONS_COLUMNS CC ON CC.OWNER = C.OWNER AND CC.CONSTRAINT_NAME = C.CONSTRAINT_NAME "
            + "AND CC.TABLE_NAME = C.TABLE_NAME "
            + "WHERE C.OWNER = ? AND C.TABLE_NAME = ? AND C.CONSTRAINT_TYPE = 'U' "
            + "ORDER BY C.CONSTRAINT_NAME, CC.POSITION";
    /** FK 삭제규칙 원문(CASCADE·SET NULL·NO ACTION). Oracle 에는 ON UPDATE 가 없어 갱신규칙은 NO ACTION(부모 키를 바꾸면 거절) */
    private static final String FK_RULES =
            "SELECT CONSTRAINT_NAME, DELETE_RULE FROM ALL_CONSTRAINTS WHERE OWNER = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'R'";
    /** SEARCH_CONDITION 은 LONG — 11g 에 SEARCH_CONDITION_VC 가 없어 LONG 을 getString 으로 읽는다(설계 15 실측, 11g·23 같음) */
    private static final String CHECKS =
            "SELECT CONSTRAINT_NAME, SEARCH_CONDITION FROM ALL_CONSTRAINTS "
            + "WHERE OWNER = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'C' ORDER BY CONSTRAINT_NAME";
    /** NOT NULL 컬럼마다 Oracle 이 스스로 만드는 C 형 제약 — CHECK 가 아니다 */
    private static final Pattern AUTO_NOT_NULL = Pattern.compile("(?i)^\\s*\"?[\\w$#]+\"?\\s+IS\\s+NOT\\s+NULL\\s*$");
    private static final String IND_COLUMNS =
            "SELECT C.INDEX_NAME, C.COLUMN_POSITION, C.DESCEND, E.COLUMN_EXPRESSION FROM ALL_IND_COLUMNS C "
            + "LEFT JOIN ALL_IND_EXPRESSIONS E ON E.INDEX_OWNER = C.INDEX_OWNER AND E.INDEX_NAME = C.INDEX_NAME "
            + "AND E.COLUMN_POSITION = C.COLUMN_POSITION "
            + "WHERE C.TABLE_OWNER = ? AND C.TABLE_NAME = ?";

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
                        rules.put(rs.getString(1), new String[] {rs.getString(2), "NO ACTION"});
                    }
                }
            }
            return withRules(withUniques, rules);
        });
        return vendor("checks", withRules, () -> {
            List<Check> out = new ArrayList<>();
            try (PreparedStatement ps = prepare(CHECKS)) {
                ps.setString(1, t.schema());
                ps.setString(2, t.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        String cond = rs.getString(2);
                        if (cond != null && !AUTO_NOT_NULL.matcher(cond).matches()) {
                            out.add(new Check(name, cond.strip()));
                        }
                    }
                }
            }
            return withRules.withChecks(out);
        });
    }

    @Override
    public Table loadIndexes(Table t) throws SQLException {
        Table base = super.loadIndexes(t);
        return vendor("sorts", base, () -> {
            Map<String, String[]> byPos = new HashMap<>(); // 인덱스명#순번 → {DESCEND, 식}
            try (PreparedStatement ps = prepare(IND_COLUMNS)) {
                ps.setString(1, t.schema());
                ps.setString(2, t.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // LONG(COLUMN_EXPRESSION)은 열 순서대로 읽어야 한다
                        String name = rs.getString(1);
                        int pos = rs.getInt(2);
                        String desc = rs.getString(3);
                        byPos.put(name + "#" + pos, new String[] {desc, rs.getString(4)});
                    }
                }
            }
            List<Index> out = new ArrayList<>();
            for (Index ix : base.indexes()) {
                List<String> cols = new ArrayList<>();
                List<kr.ejg.toolbox.core.meta.SortOrder> sorts = new ArrayList<>();
                for (int i = 0; i < ix.columns().size(); i++) {
                    String[] v = byPos.get(ix.name() + "#" + (i + 1));
                    String col = ix.columns().get(i);
                    if (v != null && v[1] != null && col.startsWith("SYS_NC") && col.endsWith("$")) {
                        col = v[1].replace("\"", "").strip();
                    }
                    cols.add(col);
                    kr.ejg.toolbox.core.meta.SortOrder so = v == null ? kr.ejg.toolbox.core.meta.SortOrder.UNKNOWN
                            : kr.ejg.toolbox.core.meta.SortOrder.of(v[0]);
                    if (v != null && so == kr.ejg.toolbox.core.meta.SortOrder.UNKNOWN) {
                        unknown("sorts"); // 1-30 — DESCEND 가 null·ASC·DESC 밖. 전엔 조용히 ASC 로 접었다
                    }
                    sorts.add(so);
                }
                out.add(new Index(ix.name(), ix.unique(), cols, sorts));
            }
            return base.withIndexes(out);
        });
    }

    /** 접속 사용자 스키마면 USER_SEGMENTS, 아니면 DBA_SEGMENTS(권한 없으면 물러섬) */
    @Override
    public Schema loadSize(Schema s) throws SQLException {
        String user = conn.getMetaData().getUserName();
        return s.name().equalsIgnoreCase(user) ? withSize(s, SIZE_USER, null) : withSize(s, SIZE_DBA, s.name());
    }
}
