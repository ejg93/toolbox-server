package kr.ejg.toolbox.core.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.JdbcMetaSource;
import kr.ejg.toolbox.core.meta.MetaSource;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.profile.Scope;
import kr.ejg.toolbox.core.meta.SortOrder;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 1-12 — 벤더 딕셔너리 SQL 이 실패하면 JDBC 뼈대로 물러선다. H2 에는 ALL_TAB_COMMENTS 등이 없어
 * Oracle 수집기의 세 벤더 SQL 이 다 깨진다 — 그래도 표·컬럼·PK 는 나오고 경고가 세 종류 남는다.
 * 1-13 — PG·MSSQL 수집기도 H2 에 한 번씩(벤더 SQL 이 H2 에서 다 깨진다).
 */
class VendorFallbackTest {

    @Test
    void oracleSourceOnH2FallsBackToJdbcWithWarnings() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:fallback" + System.nanoTime(), "sa", "");
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE A_ITEM(ID INT PRIMARY KEY, NAME VARCHAR(20))");
            st.execute("CREATE TABLE B_CODE(CD VARCHAR(4) PRIMARY KEY, NM VARCHAR(20), CONSTRAINT UK_B_NM UNIQUE (NM))");
            st.execute("COMMENT ON TABLE A_ITEM IS '항목'");

            OracleMetaSource src = new OracleMetaSource(c);
            List<Schema> schemas = src.collect(new Scope(List.of("PUBLIC"), null, null, null));

            List<Table> tables = schemas.get(0).tables();
            assertEquals(List.of("A_ITEM", "B_CODE"), tables.stream().map(Table::name).toList());
            assertEquals(2, tables.get(0).columns().size());
            assertEquals(List.of("ID"), tables.get(0).pk().columns());
            assertEquals("항목", tables.get(0).comment(), "코멘트는 JDBC REMARKS 로 물러선다");
            assertNull(tables.get(0).rowCount(), "행 수는 null 로 물러선다");
            assertTrue(tables.get(1).uniques().stream().anyMatch(u -> u.columns().equals(List.of("NM"))),
                    "UNIQUE 는 유니크 인덱스로 물러선다: " + tables.get(1).uniques());

            Set<String> kinds = src.warnings().stream().map(MetaSource.Warning::kind).collect(Collectors.toSet());
            assertEquals(Set.of("comments", "stats", "uniques", "sorts", "fkRules", "checks", "size"), kinds); // sorts·fkRules·checks·size — 1-20·PR #42·1-21·1-23
            for (MetaSource.Warning w : src.warnings()) {
                assertEquals("42S02", w.sqlState(), w.toString()); // H2 「표가 없다」
                assertTrue(w.count() >= 1, w.toString());
            }
            int uniques = src.warnings().stream().filter(w -> w.kind().equals("uniques")).mapToInt(MetaSource.Warning::count).sum();
            assertEquals(2, uniques, "표마다 한 번");
        }
    }

    /** 1-30 — 딕셔너리가 모르는 FK 규칙 글은 드라이버 값으로 접되 경고 fkRules(SQLState 빈칸)로 센다. null 은 안 센다 */
    @Test
    void unknownFkRuleTextIsCounted() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:fkrule" + System.nanoTime(), "sa", "");
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE P(ID INT PRIMARY KEY)");
            st.execute("CREATE TABLE C(ID INT PRIMARY KEY, P_ID INT, CONSTRAINT FK_C_P FOREIGN KEY (P_ID) REFERENCES P(ID))");
            OracleMetaSource src = new OracleMetaSource(c);
            Table t = src.collect(new Scope(List.of("PUBLIC"), null, null, null)).get(0).tables().stream()
                    .filter(x -> x.name().equals("C")).findFirst().orElseThrow();
            var driver = t.fks().get(0);
            Table got = src.withRules(t, java.util.Map.of("FK_C_P", new String[] {"SOMETHING ELSE", null}));
            assertEquals(driver.deleteRule(), got.fks().get(0).deleteRule(), "모르는 글은 드라이버 값");
            List<MetaSource.Warning> unknown = src.warnings().stream().filter(w -> w.sqlState().isEmpty()).toList();
            assertEquals(List.of(new MetaSource.Warning("fkRules", "", 0, 1)), unknown, "갱신 칸 null 은 안 센다");
        }
    }

    /**
     * 1-39 — Oracle DESCEND 가 ASC·DESC 밖이면 UNKNOWN + 경고 sorts(SQLState 빈칸). null 도 센다(딕셔너리는 늘 준다).
     * H2 에 ALL_IND_COLUMNS·ALL_IND_EXPRESSIONS 를 같은 열로 만들어 벤더 SQL 이 그 행을 읽게 한다
     */
    @Test
    void unknownOracleDescendIsCounted() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:descend" + System.nanoTime(), "sa", "");
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE T(ID INT PRIMARY KEY, NM VARCHAR(20), CD VARCHAR(4))");
            st.execute("CREATE INDEX IX_T_NM ON T(NM, CD)");
            st.execute("CREATE INDEX IX_T_CD ON T(CD)");
            st.execute("CREATE TABLE ALL_IND_COLUMNS(INDEX_OWNER VARCHAR(30), INDEX_NAME VARCHAR(30), TABLE_OWNER VARCHAR(30),"
                    + " TABLE_NAME VARCHAR(30), COLUMN_POSITION INT, DESCEND VARCHAR(10))");
            st.execute("CREATE TABLE ALL_IND_EXPRESSIONS(INDEX_OWNER VARCHAR(30), INDEX_NAME VARCHAR(30), COLUMN_POSITION INT,"
                    + " COLUMN_EXPRESSION VARCHAR(100))");
            st.execute("INSERT INTO ALL_IND_COLUMNS VALUES ('PUBLIC','IX_T_NM','PUBLIC','T',1,'DESC'),"
                    + " ('PUBLIC','IX_T_NM','PUBLIC','T',2,'SOMETHING'), ('PUBLIC','IX_T_CD','PUBLIC','T',1,NULL)");
            OracleMetaSource src = new OracleMetaSource(c);
            Table t = src.collect(new Scope(List.of("PUBLIC"), null, null, null)).get(0).tables().stream()
                    .filter(x -> x.name().equals("T")).findFirst().orElseThrow();
            Map<String, List<SortOrder>> sorts = t.indexes().stream().collect(Collectors.toMap(Index::name, Index::sorts));
            assertEquals(List.of(SortOrder.DESC, SortOrder.UNKNOWN), sorts.get("IX_T_NM"));
            assertEquals(List.of(SortOrder.UNKNOWN), sorts.get("IX_T_CD"));
            List<MetaSource.Warning> unknown = src.warnings().stream().filter(w -> w.sqlState().isEmpty()).toList();
            assertEquals(List.of(new MetaSource.Warning("sorts", "", 0, 2)), unknown, "모르는 글 하나 + null 하나");
        }
    }

    /**
     * 1-39 — JDBC ASC_OR_DESC 가 A·D 밖이면 UNKNOWN + 경고 sorts. null·빈칸은 규격의 「정렬 없음」이라 안 센다.
     * H2 는 A·D 만 주어 getIndexInfo 결과의 그 열만 바꿔 끼운다
     */
    @Test
    void unknownJdbcAscOrDescIsCounted() throws Exception {
        try (Connection real = DriverManager.getConnection("jdbc:h2:mem:ascdesc" + System.nanoTime(), "sa", "");
                Statement st = real.createStatement()) {
            st.execute("CREATE TABLE T(ID INT PRIMARY KEY, NM VARCHAR(20), CD VARCHAR(4), YN CHAR(1))");
            st.execute("CREATE INDEX IX_T ON T(NM, CD, YN)");
            Map<String, String> swap = new HashMap<>(Map.of("NM", "X", "CD", " "));
            swap.put("YN", null);
            JdbcMetaSource src = new JdbcMetaSource(sortSwapped(real, swap));
            Table t = src.collect(new Scope(List.of("PUBLIC"), null, null, null)).get(0).tables().get(0);
            Index ix = t.indexes().stream().filter(x -> x.name().equals("IX_T")).findFirst().orElseThrow();
            assertEquals(List.of(SortOrder.UNKNOWN, SortOrder.UNKNOWN, SortOrder.UNKNOWN), ix.sorts());
            assertEquals(List.of(new MetaSource.Warning("sorts", "", 0, 1)), src.warnings(), "X 만 센다");
        }
    }

    /** 1-84 길 ② — 드라이버가 같은 열을 겹쳐 주면(Tibero, 같은 이름 인덱스) 첫 줄만 두고 버린 줄 수를 경고 dupColumns 로 */
    @Test
    void duplicateColumnRowsKeepFirstAndWarn() throws Exception {
        try (Connection real = DriverManager.getConnection("jdbc:h2:mem:dupcols" + System.nanoTime(), "sa", "");
                Statement st = real.createStatement()) {
            st.execute("CREATE TABLE T(ID INT PRIMARY KEY, NM VARCHAR(20))");
            JdbcMetaSource src = new JdbcMetaSource(columnsTwice(real));
            Table t = src.collect(new Scope(List.of("PUBLIC"), null, null, null)).get(0).tables().get(0);
            assertEquals(List.of("ID", "NM"), t.columns().stream().map(kr.ejg.toolbox.core.meta.Column::name).toList());
            assertEquals(List.of(new MetaSource.Warning("dupColumns", "", 0, 2)), src.warnings());
        }
    }

    /** 1-84 길 ① — Tibero 는 열을 ALL_TAB_COLUMNS 에서 형식·길이·정밀도까지 직접(드라이버 getColumns 를 안 탄다, 읽기 전용 관문을 지난다) */
    @Test
    void tiberoReadsColumnsFromDictionary() throws Exception {
        try (Connection real = DriverManager.getConnection("jdbc:h2:mem:tbdict" + System.nanoTime(), "sa", "");
                Statement st = real.createStatement()) {
            st.execute("CREATE TABLE T(ID INT PRIMARY KEY, NM VARCHAR(20))");
            st.execute("CREATE TABLE ALL_TAB_COLUMNS(OWNER VARCHAR(30), TABLE_NAME VARCHAR(30), COLUMN_NAME VARCHAR(30), DATA_TYPE VARCHAR(40),"
                    + " DATA_LENGTH INT, DATA_PRECISION INT, DATA_SCALE INT, NULLABLE VARCHAR(1), COLUMN_ID INT, CHAR_LENGTH INT, DATA_DEFAULT VARCHAR(100))");
            st.execute("INSERT INTO ALL_TAB_COLUMNS VALUES ('PUBLIC','T','ID','NUMBER',22,10,0,'N',1,0,NULL),"
                    + " ('PUBLIC','T','NM','VARCHAR2',80,NULL,NULL,'Y',2,20,'''x'' '),"
                    + " ('PUBLIC','T','AMT','NUMBER',22,NULL,NULL,'Y',3,0,NULL),"
                    + " ('PUBLIC','T','DT','DATE',7,NULL,NULL,'Y',4,0,'SYSDATE'),"
                    + " ('PUBLIC','T','TS','TIMESTAMP(6) WITH TIME ZONE',13,NULL,6,'Y',5,0,NULL),"
                    + " ('PUBLIC','T','BODY','CLOB',4000,NULL,NULL,'Y',6,0,NULL),"
                    + " ('PUBLIC','T','GEO','SDO_GEOMETRY',1,NULL,NULL,'Y',7,0,NULL)");
            TiberoMetaSource src = new TiberoMetaSource(kr.ejg.toolbox.core.conn.ReadOnlyJdbc.wrap(columnsTwice(real)));
            Scope scope = new Scope(List.of("PUBLIC"), new Scope.Exclude(null, null, null, List.of("ALL_TAB_COLUMNS")), null, null);
            Table t = src.collect(scope).get(0).tables().stream().filter(x -> x.name().equals("T")).findFirst().orElseThrow();
            List<kr.ejg.toolbox.core.meta.Column> c = t.columns();
            assertEquals(List.of("ID", "NM", "AMT", "DT", "TS", "BODY", "GEO"), c.stream().map(kr.ejg.toolbox.core.meta.Column::name).toList());
            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), c.stream().map(kr.ejg.toolbox.core.meta.Column::ordinal).toList());
            assertColumn(c.get(0), 2, null, 10, 0, false, null);
            assertColumn(c.get(1), 12, 20L, null, null, true, "'x'");
            assertColumn(c.get(2), 2, null, 0, -127, true, null);
            assertColumn(c.get(3), 93, null, null, null, true, "SYSDATE");
            assertColumn(c.get(4), -101, null, null, null, true, null);
            assertColumn(c.get(5), 2005, null, null, null, true, null);
            assertColumn(c.get(6), 1111, null, null, null, true, null);
            assertEquals("SDO_GEOMETRY", c.get(6).nativeType());
            Set<String> kinds = src.warnings().stream().map(MetaSource.Warning::kind).collect(Collectors.toSet());
            assertTrue(!kinds.contains("columns") && !kinds.contains("dupColumns"), kinds.toString());
        }
    }

    /** 1-84 — 딕셔너리 SQL 이 실패하면 경고 columns 를 남기고 드라이버 길(같은 이름은 첫 줄만)로 물러선다 */
    @Test
    void tiberoFallsBackWhenDictionaryFails() throws Exception {
        try (Connection real = DriverManager.getConnection("jdbc:h2:mem:tbfall" + System.nanoTime(), "sa", "");
                Statement st = real.createStatement()) {
            st.execute("CREATE TABLE T(ID INT PRIMARY KEY, NM VARCHAR(20))");
            TiberoMetaSource src = new TiberoMetaSource(kr.ejg.toolbox.core.conn.ReadOnlyJdbc.wrap(columnsTwice(real)));
            Table t = src.collect(new Scope(List.of("PUBLIC"), null, null, null)).get(0).tables().get(0);
            assertEquals(List.of("ID", "NM"), t.columns().stream().map(kr.ejg.toolbox.core.meta.Column::name).toList());
            List<MetaSource.Warning> w = src.warnings();
            assertTrue(w.stream().anyMatch(x -> x.kind().equals("columns") && x.sqlState().equals("42S02")), w.toString());
            assertTrue(w.contains(new MetaSource.Warning("dupColumns", "", 0, 2)), w.toString());
        }
    }

    private static void assertColumn(kr.ejg.toolbox.core.meta.Column c, int jdbc, Long length, Integer precision, Integer scale, boolean nullable,
            String def) {
        assertEquals(List.of(jdbc, String.valueOf(length), String.valueOf(precision), String.valueOf(scale), nullable, String.valueOf(def)),
                List.of(c.jdbcType(), String.valueOf(c.length()), String.valueOf(c.precision()), String.valueOf(c.scale()), c.nullable(),
                        String.valueOf(c.defaultValue())), c.name());
    }

    /** getColumns 결과의 줄마다 next() 가 두 번 참 — 같은 열을 두 번 주는 드라이버 */
    static Connection columnsTwice(Connection real) {
        DatabaseMetaData md;
        try {
            md = real.getMetaData();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        DatabaseMetaData mdProxy = proxy(DatabaseMetaData.class, (p, m, a) -> {
            Object r = call(md, m, a);
            if (!m.getName().equals("getColumns")) {
                return r;
            }
            ResultSet rs = (ResultSet) r;
            boolean[] again = {false};
            return proxy(ResultSet.class, (p2, m2, a2) -> {
                if (!m2.getName().equals("next")) {
                    return call(rs, m2, a2);
                }
                if (again[0]) {
                    again[0] = false;
                    return true;
                }
                again[0] = rs.next();
                return again[0];
            });
        });
        return proxy(Connection.class, (p, m, a) -> m.getName().equals("getMetaData") ? mdProxy : call(real, m, a));
    }

    /** getIndexInfo 결과의 ASC_OR_DESC 를 컬럼 이름으로 바꿔 끼운 접속(없는 컬럼은 그대로) */
    private static Connection sortSwapped(Connection real, Map<String, String> byColumn) {
        DatabaseMetaData md;
        try {
            md = real.getMetaData();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        DatabaseMetaData mdProxy = proxy(DatabaseMetaData.class, (p, m, a) -> {
            Object r = call(md, m, a);
            if (!m.getName().equals("getIndexInfo")) {
                return r;
            }
            ResultSet rs = (ResultSet) r;
            return proxy(ResultSet.class, (p2, m2, a2) -> {
                if (m2.getName().equals("getString") && "ASC_OR_DESC".equals(a2[0])) {
                    String col = rs.getString("COLUMN_NAME");
                    if (byColumn.containsKey(col)) {
                        return byColumn.get(col);
                    }
                }
                return call(rs, m2, a2);
            });
        });
        return proxy(Connection.class, (p, m, a) -> m.getName().equals("getMetaData") ? mdProxy : call(real, m, a));
    }

    private static <T> T proxy(Class<T> type, InvocationHandler h) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, h));
    }

    private static Object call(Object target, Method m, Object[] a) throws Throwable {
        try {
            return m.invoke(target, a);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** 1-13 — 남은 방언. MariaDB 는 catalog 를 덮어써 H2 에서 뼈대가 안 맞아 뺀다(이력) */
    @ParameterizedTest
    @ValueSource(strings = {"postgresql", "mssql"})
    void otherVendorsFallBackOnH2(String dialect) throws Exception {
        Function<Connection, VendorMetaSource> make = switch (dialect) {
            case "postgresql" -> PostgresMetaSource::new;
            default -> MssqlMetaSource::new;
        };
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:fallback" + dialect + System.nanoTime(), "sa", "");
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE A_ITEM(ID INT PRIMARY KEY, NAME VARCHAR(20))");
            st.execute("CREATE TABLE B_CODE(CD VARCHAR(4) PRIMARY KEY, NM VARCHAR(20), CONSTRAINT UK_B_NM UNIQUE (NM))");

            VendorMetaSource src = make.apply(c);
            List<Table> tables = src.collect(new Scope(List.of("PUBLIC"), null, null, null)).get(0).tables();
            assertEquals(List.of("A_ITEM", "B_CODE"), tables.stream().map(Table::name).toList());
            assertEquals(List.of("ID"), tables.get(0).pk().columns());
            assertNull(tables.get(0).rowCount());
            Set<String> kinds = src.warnings().stream().map(MetaSource.Warning::kind).collect(Collectors.toSet());
            assertEquals(Set.of("comments", "stats", "uniques", "checks", "size"), kinds, src.warnings().toString()); // checks·size — 1-22·1-23
        }
    }
}
