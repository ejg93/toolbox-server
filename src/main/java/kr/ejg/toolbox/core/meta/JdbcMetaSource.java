package kr.ejg.toolbox.core.meta;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@link DatabaseMetaData} 로 뼈대를 채운다(5-2).
 * <ul>
 *   <li>길이는 문자·이진 타입만 {@code length}, 수는 NUMERIC·DECIMAL 만 {@code precision}·{@code scale}. 나머지는 null</li>
 *   <li>JDBC 는 UNIQUE 제약과 유니크 인덱스를 못 가른다 — {@code uniques} 는 PK 를 뺀 유니크 인덱스, {@code indexes} 는 PK 를 뺀 전부</li>
 *   <li>PK 인덱스는 PK 이름과 같은 인덱스로 보고 뺀다</li>
 *   <li>목록은 이름순(컬럼은 순번) — 골든 비교가 흔들리지 않게</li>
 * </ul>
 */
public class JdbcMetaSource implements MetaSource {

    /** 1-30 — 드라이버 ASC_OR_DESC 가 준 모르는 정렬 글의 수(글은 안 남긴다). 경고 sorts(SQLState 빈칸) */
    private int unknownSorts;

    @Override
    public List<Warning> warnings() {
        return unknownSorts == 0 ? List.of() : List.of(new Warning("sorts", "", 0, unknownSorts));
    }

    /** 벤더 공통으로 보이는 시스템 스키마 — 범위가 비었을 때 뺀다 */
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "INFORMATION_SCHEMA", "PG_CATALOG", "PG_TOAST", "SYS", "SYSTEM", "MYSQL", "PERFORMANCE_SCHEMA",
            "GUEST", "DB_OWNER", "DB_ACCESSADMIN", "DB_SECURITYADMIN", "DB_DDLADMIN", "DB_BACKUPOPERATOR",
            "DB_DATAREADER", "DB_DATAWRITER", "DB_DENYDATAREADER", "DB_DENYDATAWRITER");

    private static final Set<Integer> LENGTH_TYPES = Set.of(
            Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR,
            Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY);

    private static final Set<Integer> DECIMAL_TYPES = Set.of(Types.NUMERIC, Types.DECIMAL);

    protected final Connection conn;

    /** 생성자는 예외를 안 던진다 — 메타데이터는 쓸 때 얻는다(SpotBugs CT_CONSTRUCTOR_THROW) */
    public JdbcMetaSource(Connection conn) {
        this.conn = conn;
    }

    protected DatabaseMetaData md() throws SQLException {
        return conn.getMetaData();
    }

    /**
     * 메타데이터 API 의 카탈로그 인자. 스키마를 스키마로 주는 DB 는 null.
     * MariaDB·MySQL 은 DB 를 카탈로그로 준다 — 그 벤더가 override 해서 스키마 이름을 카탈로그로 넘긴다(1-3).
     */
    protected String catalog(String schema) {
        return null;
    }

    /** 메타데이터 API 의 스키마 인자. 카탈로그로 넘기는 벤더는 null */
    protected String schemaArg(String schema) {
        return schema;
    }

    @Override
    public String dbVersion() throws SQLException {
        DatabaseMetaData md = md();
        return md.getDatabaseProductName() + " " + md.getDatabaseProductVersion();
    }

    @Override
    public List<String> listSchemas() throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = md().getSchemas()) {
            while (rs.next()) {
                String s = rs.getString("TABLE_SCHEM");
                if (s != null && !SYSTEM_SCHEMAS.contains(s.toUpperCase(Locale.ROOT))
                        && !s.toLowerCase(Locale.ROOT).startsWith("pg_temp") && !s.toLowerCase(Locale.ROOT).startsWith("pg_toast")) {
                    out.add(s);
                }
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    @Override
    public List<Table> listTables(String schema) throws SQLException {
        List<Table> out = new ArrayList<>();
        DatabaseMetaData md = md();
        try (ResultSet rs = md.getTables(catalog(schema), pattern(md, schemaArg(schema)), "%", new String[] {"TABLE", "VIEW"})) {
            while (rs.next()) {
                if (!sameName(schema, rs.getString("TABLE_SCHEM"))) {
                    continue;
                }
                out.add(Table.of(schema, rs.getString("TABLE_NAME"), rs.getString("TABLE_TYPE"), blankToNull(rs.getString("REMARKS"))));
            }
        }
        out.sort(Comparator.comparing(Table::name));
        return out;
    }

    @Override
    public Table loadColumns(Table t) throws SQLException {
        List<Column> cols = new ArrayList<>();
        DatabaseMetaData md = md();
        try (ResultSet rs = md.getColumns(catalog(t.schema()), pattern(md, schemaArg(t.schema())), pattern(md, t.name()), "%")) {
            while (rs.next()) {
                // 열 번호 순서대로 한 번씩만 읽는다 — Oracle 은 COLUMN_DEF(13번)를 LONG 으로 줘서
                // 그 뒤에 앞 열을 읽으면 ORA-17027(스트림이 이미 닫힘). JDBC 순번: 2 SCHEM·3 TABLE·4 COLUMN·5 DATA_TYPE·
                // 6 TYPE_NAME·7 SIZE·9 DIGITS·11 NULLABLE·12 REMARKS·13 COLUMN_DEF·17 ORDINAL
                String schem = rs.getString("TABLE_SCHEM");
                String table = rs.getString("TABLE_NAME");
                String name = rs.getString("COLUMN_NAME");
                int jdbcType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                Integer size = intOrNull(rs, "COLUMN_SIZE");
                Integer digits = intOrNull(rs, "DECIMAL_DIGITS");
                int nullable = rs.getInt("NULLABLE");
                String remarks = rs.getString("REMARKS");
                String def = rs.getString("COLUMN_DEF");
                int ordinal = rs.getInt("ORDINAL_POSITION");
                if (!sameName(t.schema(), schem) || !t.name().equals(table)) {
                    continue;
                }
                Long length = LENGTH_TYPES.contains(jdbcType) && size != null ? Long.valueOf(size) : null;
                boolean decimal = DECIMAL_TYPES.contains(jdbcType);
                cols.add(new Column(
                        name,
                        ordinal,
                        typeName,
                        jdbcType,
                        length,
                        decimal ? size : null,
                        decimal ? digits : null,
                        nullable != DatabaseMetaData.columnNoNulls,
                        blankToNull(def == null ? null : def.strip()),
                        blankToNull(remarks),
                        null));
            }
        }
        cols.sort(Comparator.comparingInt(Column::ordinal));
        return t.withColumns(cols);
    }

    @Override
    public Table loadConstraints(Table t) throws SQLException {
        return t.withConstraints(primaryKey(t), foreignKeys(t), uniqueIndexes(t));
    }

    @Override
    public Table loadIndexes(Table t) throws SQLException {
        String pkName = t.pk() == null ? null : t.pk().name();
        List<Index> out = new ArrayList<>();
        for (Map.Entry<String, IndexCols> e : indexInfo(t, true).entrySet()) {
            if (e.getKey().equals(pkName)) {
                continue;
            }
            out.add(new Index(e.getKey(), e.getValue().unique, e.getValue().columns(), e.getValue().sorts()));
        }
        return t.withIndexes(out);
    }

    protected PrimaryKey primaryKey(Table t) throws SQLException {
        Map<Integer, String> cols = new TreeMap<>();
        String name = null;
        try (ResultSet rs = md().getPrimaryKeys(catalog(t.schema()), schemaArg(t.schema()), t.name())) {
            while (rs.next()) {
                cols.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME"));
                name = rs.getString("PK_NAME");
            }
        }
        return cols.isEmpty() ? null : new PrimaryKey(name, List.copyOf(cols.values()));
    }

    protected List<ForeignKey> foreignKeys(Table t) throws SQLException {
        record Part(String refSchema, String refTable, FkRule deleteRule, FkRule updateRule, Map<Integer, String[]> cols) {
        }
        Map<String, Part> byName = new TreeMap<>();
        try (ResultSet rs = md().getImportedKeys(catalog(t.schema()), schemaArg(t.schema()), t.name())) {
            while (rs.next()) {
                String name = rs.getString("FK_NAME");
                Part p = byName.computeIfAbsent(name, k -> {
                    try {
                        return new Part(rs.getString("PKTABLE_SCHEM"), rs.getString("PKTABLE_NAME"), rule(rs, "DELETE_RULE"),
                                rule(rs, "UPDATE_RULE"), new TreeMap<>());
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                });
                p.cols().put(rs.getInt("KEY_SEQ"), new String[] {rs.getString("FKCOLUMN_NAME"), rs.getString("PKCOLUMN_NAME")});
            }
        }
        List<ForeignKey> out = new ArrayList<>();
        for (Map.Entry<String, Part> e : byName.entrySet()) {
            List<String> cols = new ArrayList<>();
            List<String> refCols = new ArrayList<>();
            for (String[] pair : e.getValue().cols().values()) {
                cols.add(pair[0]);
                refCols.add(pair[1]);
            }
            String refSchema = e.getValue().refSchema();
            out.add(new ForeignKey(e.getKey(), cols, refSchema != null && refSchema.equals(t.schema()) ? null : refSchema,
                    e.getValue().refTable(), refCols, e.getValue().deleteRule(), e.getValue().updateRule()));
        }
        return out;
    }

    /** 규칙 열 — NULL 은 getShort 가 0(= CASCADE)으로 읽으니 wasNull 로 가른다. Oracle 은 UPDATE_RULE 이 NULL(1-19 실측) */
    private static FkRule rule(ResultSet rs, String column) throws SQLException {
        short v = rs.getShort(column);
        return rs.wasNull() ? null : FkRule.jdbc(v);
    }

    protected List<UniqueKey> uniqueIndexes(Table t) throws SQLException {
        PrimaryKey pk = primaryKey(t);
        String pkName = pk == null ? null : pk.name();
        List<UniqueKey> out = new ArrayList<>();
        for (Map.Entry<String, IndexCols> e : indexInfo(t, false).entrySet()) {
            if (e.getValue().unique && !e.getKey().equals(pkName)) {
                out.add(new UniqueKey(e.getKey(), e.getValue().columns()));
            }
        }
        return out;
    }

    protected static final class IndexCols {
        boolean unique;
        final Map<Integer, String> cols = new TreeMap<>();
        /** 순번 → ASC·DESC·""(ASC_OR_DESC 가 null — Oracle 은 늘 null, 1-20 실측) */
        final Map<Integer, SortOrder> sorts = new TreeMap<>();

        List<String> columns() {
            return List.copyOf(cols.values());
        }

        List<SortOrder> sorts() {
            return List.copyOf(sorts.values());
        }
    }

    /** 인덱스 이름 → 유니크 여부·컬럼(순번순). 통계 행은 뺀다. 이름순. 모르는 정렬 글은 countSorts 일 때만 센다 — 유니크·인덱스 두 자리가 읽어 두 번 셌다(1-39) */
    protected Map<String, IndexCols> indexInfo(Table t, boolean countSorts) throws SQLException {
        Map<String, IndexCols> out = new TreeMap<>();
        try (ResultSet rs = md().getIndexInfo(catalog(t.schema()), schemaArg(t.schema()), t.name(), false, true)) {
            while (rs.next()) {
                if (rs.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String name = rs.getString("INDEX_NAME");
                String col = rs.getString("COLUMN_NAME");
                if (name == null || col == null) {
                    continue;
                }
                IndexCols ic = out.computeIfAbsent(name, k -> new IndexCols());
                ic.unique = !rs.getBoolean("NON_UNIQUE");
                int pos = rs.getShort("ORDINAL_POSITION");
                ic.cols.put(pos, col);
                String ad = rs.getString("ASC_OR_DESC");
                SortOrder so = SortOrder.of(ad);
                if (countSorts && so == SortOrder.UNKNOWN && ad != null && !ad.isBlank()) {
                    unknownSorts++; // 1-30 — null 은 JDBC 규격의 「정렬 없음」, 모르는 글만 센다
                }
                ic.sorts.put(pos, so);
            }
        }
        return new LinkedHashMap<>(out);
    }

    /**
     * getTables·getColumns 의 스키마·테이블 인자는 LIKE 패턴이다 — `_` 가 한 글자 와일드카드라
     * `A_B` 를 읽을 때 `AXB` 가 섞인다(2026-09-27 리뷰). 드라이버의 이스케이프 문자로 `_`·`%` 를 막는다.
     * 이스케이프를 모르는 드라이버도 있어 부른 쪽이 돌아온 이름을 {@link #sameName} 으로 한 번 더 거른다.
     */
    protected static String pattern(DatabaseMetaData md, String name) throws SQLException {
        if (name == null) {
            return null;
        }
        String esc = md.getSearchStringEscape();
        if (esc == null || esc.isEmpty()) {
            return name;
        }
        return name.replace(esc, esc + esc).replace("_", esc + "_").replace("%", esc + "%");
    }

    /** 스키마가 없는 DB(MariaDB 는 카탈로그)는 TABLE_SCHEM 이 null — 그때는 통과 */
    protected static boolean sameName(String expected, String actual) {
        return expected == null || actual == null || expected.equals(actual);
    }

    private static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    protected static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
