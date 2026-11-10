package kr.ejg.toolbox.core.dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Table;

/**
 * Tibero — 딕셔너리 뷰 이름·열이 Oracle 과 같다(ALL_TAB_COMMENTS·ALL_TABLES·ALL_OBJECTS·ALL_CONSTRAINTS).
 * 컨테이너가 없어 테스트가 없다 — 현장·개발자판에서 수동 1회 뒤 골든 고정(PLAN 10장 M1).
 * 차이가 드러나면 여기서만 override 한다.
 * 열은 딕셔너리에서 직접(1-84) — 드라이버 getColumns 는 표와 이름이 같은 인덱스가 있으면 같은 열을 두 번 준다.
 * 형식 번호·길이·정밀도의 과녁은 Oracle JDBC 드라이버가 같은 딕셔너리에서 내는 값(OracleMetaSourceTest 가 컨테이너에서 견준다)
 */
public class TiberoMetaSource extends OracleMetaSource {

    /** 1-84 — DATA_DEFAULT 는 LONG 이라 맨 끝 열로 두고 맨 끝에 읽는다 */
    static final String TAB_COLUMNS = "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE, COLUMN_ID, CHAR_LENGTH,"
            + " DATA_DEFAULT FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ? ORDER BY COLUMN_ID";
    private static final int TS_TZ = -101; // oracle.jdbc.OracleTypes.TIMESTAMPTZ
    private static final int TS_LTZ = -102; // TIMESTAMPLTZ
    private static final int INTERVAL_YM = -103;
    private static final int INTERVAL_DS = -104;
    private static final int BINARY_FLOAT = 100;
    private static final int BINARY_DOUBLE = 101;
    private static final int BFILE = -13;
    /** NUMBER(정밀도·스케일 없음)의 드라이버 DECIMAL_DIGITS */
    private static final int NO_SCALE = -127;
    private static final int MAX_PRECISION = 38;
    private static final String NOT_NULL = "N";
    private static final String TIMESTAMP = "TIMESTAMP";
    private static final String INTERVAL = "INTERVAL";
    /** 딕셔너리 DATA_TYPE → Oracle JDBC 가 getColumns 로 주는 형식 번호. 과녁은 드라이버 값 — OracleMetaSourceTest 가 컨테이너에서 견준다 */
    private static final Map<String, Integer> TYPES = Map.ofEntries(
            Map.entry("CHAR", Types.CHAR), Map.entry("VARCHAR2", Types.VARCHAR), Map.entry("VARCHAR", Types.VARCHAR),
            Map.entry("NCHAR", Types.NCHAR), Map.entry("NVARCHAR2", Types.NVARCHAR), Map.entry("NVARCHAR", Types.NVARCHAR),
            Map.entry("NUMBER", Types.NUMERIC), Map.entry("FLOAT", Types.FLOAT), Map.entry("BINARY_FLOAT", BINARY_FLOAT),
            Map.entry("BINARY_DOUBLE", BINARY_DOUBLE), Map.entry("DATE", Types.TIMESTAMP), Map.entry("LONG", Types.LONGVARCHAR),
            Map.entry("RAW", Types.VARBINARY), Map.entry("LONG RAW", Types.LONGVARBINARY), Map.entry("BLOB", Types.BLOB),
            Map.entry("CLOB", Types.CLOB), Map.entry("NCLOB", Types.NCLOB), Map.entry("BFILE", BFILE),
            Map.entry("ROWID", Types.ROWID), Map.entry("UROWID", Types.ROWID), Map.entry("XMLTYPE", Types.SQLXML));
    private static final Set<Integer> CHARS = Set.of(Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR);
    private static final Set<Integer> BYTES = Set.of(Types.VARBINARY, Types.LONGVARCHAR, Types.LONGVARBINARY);

    public TiberoMetaSource(Connection conn) {
        super(conn);
    }

    @Override
    public Table loadColumns(Table t) throws SQLException {
        List<Column> cols = t.schema() == null ? List.of() : vendor("columns", List.of(), () -> dictionary(t));
        return cols.isEmpty() ? super.loadColumns(t) : t.withColumns(cols); // 못 읽었거나 줄이 없으면 드라이버 길(같은 이름은 첫 줄만)
    }

    private List<Column> dictionary(Table t) throws SQLException {
        List<Column> out = new ArrayList<>();
        try (PreparedStatement ps = prepare(TAB_COLUMNS)) {
            ps.setString(1, t.schema());
            ps.setString(2, t.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(column(rs));
                }
            }
        }
        return out;
    }

    /** 한 줄 → Column. 열 번호 차례로 한 번씩 읽는다 */
    private static Column column(ResultSet rs) throws SQLException {
        String name = rs.getString(1);
        String type = rs.getString(2);
        Long bytes = longOrNull(rs, 3);
        Long precision = longOrNull(rs, 4);
        Long scale = longOrNull(rs, 5);
        boolean nullable = !NOT_NULL.equals(rs.getString(6));
        int ordinal = rs.getInt(7);
        Long chars = longOrNull(rs, 8);
        String def = rs.getString(9);
        int jdbc = jdbcType(type);
        Long length = CHARS.contains(jdbc) ? chars : BYTES.contains(jdbc) ? bytes : null;
        boolean number = jdbc == Types.NUMERIC;
        return new Column(name, ordinal, type, jdbc, length, number ? precision(precision, scale) : null, number ? scale(precision, scale) : null,
                nullable, blankToNull(def == null ? null : def.strip()), null, null);
    }

    static int jdbcType(String dataType) {
        String t = dataType == null ? "" : dataType.toUpperCase(Locale.ROOT);
        Integer known = TYPES.get(t);
        if (known != null) {
            return known;
        }
        if (t.startsWith(TIMESTAMP)) {
            return t.contains("LOCAL") ? TS_LTZ : t.contains("TIME ZONE") ? TS_TZ : Types.TIMESTAMP;
        }
        if (t.startsWith(INTERVAL)) {
            return t.contains("YEAR") ? INTERVAL_YM : INTERVAL_DS;
        }
        return Types.OTHER;
    }

    /** 드라이버의 COLUMN_SIZE — 정밀도가 없으면 스케일도 없을 때 0, 스케일만 있으면 38 */
    private static Integer precision(Long precision, Long scale) {
        return precision != null ? Integer.valueOf(precision.intValue()) : Integer.valueOf(scale == null ? 0 : MAX_PRECISION);
    }

    /** 드라이버의 DECIMAL_DIGITS — 정밀도·스케일이 다 없으면 -127 */
    private static Integer scale(Long precision, Long scale) {
        return scale != null ? Integer.valueOf(scale.intValue()) : precision == null ? Integer.valueOf(NO_SCALE) : null;
    }
}
