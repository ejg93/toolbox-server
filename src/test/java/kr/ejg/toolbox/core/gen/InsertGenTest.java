package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** 4-7 — 값 규칙·MERGE·CSV 모드·FK 실존 값·H2 실행. 골든은 PG 벤더 메타 products·users × 방언 6 */
class InsertGenTest {

    static final LocalDate BASE = LocalDate.of(2026, 1, 31);

    static Table table(String name) throws IOException {
        for (Schema s : GoldenFiles.schemas("meta/postgres-vendor.json")) {
            for (Table t : s.tables()) {
                if (t.name().equals(name)) {
                    return t;
                }
            }
        }
        throw new IllegalStateException(name);
    }

    static String golden(Table t, String dialect) {
        InsertGen.Result ins = InsertGen.generate(t, Map.of(), new InsertGen.Options(dialect, 3, false, true, BASE), null);
        InsertGen.Result up = InsertGen.generate(t, Map.of(), new InsertGen.Options(dialect, 3, true, true, BASE), null);
        StringBuilder s = new StringBuilder();
        ins.warnings().forEach(w -> s.append("-- warning: ").append(w).append('\n'));
        s.append('\n').append(ins.sql()).append("\n\n-- upsert\n").append(up.sql()).append('\n');
        return s.toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"oracle", "tibero", "postgresql", "mariadb", "mssql", "sybase"})
    void productsGolden(String dialect) throws IOException {
        GoldenFiles.assertText("insert/products-" + dialect + ".sql", golden(table("products"), dialect));
    }

    @ParameterizedTest
    @ValueSource(strings = {"oracle", "tibero", "postgresql", "mariadb", "mssql", "sybase"})
    void usersGolden(String dialect) throws IOException {
        GoldenFiles.assertText("insert/users-" + dialect + ".sql", golden(table("users"), dialect));
    }

    static final String DDL = """
            CREATE TABLE t_rule (
              id        NUMBER(10) PRIMARY KEY,
              stat_cd   VARCHAR2(2) NOT NULL CHECK (stat_cd IN ('A','B','C')),
              del_yn    CHAR(1) DEFAULT 'N',
              kind      CHAR(3),
              amt       NUMBER(7,2),
              photo     BLOB,
              note      CLOB,
              reg_dt    DATE
            )
            """;

    @Test
    void valueRules() {
        Table t = DdlReader.read(DDL).tables().get(0);
        InsertGen.Result r = InsertGen.generate(t, InsertGen.checkIns(DDL), new InsertGen.Options("oracle", 4, false, false, BASE), null);
        List<String> stmts = List.of(r.sql().split("\n\n"));
        assertEquals(4, stmts.size());
        String first = stmts.get(0);
        String second = stmts.get(1);
        assertTrue(first.contains("\t1,\n") && stmts.get(3).contains("\t4,\n"), "PK 순번: " + first);
        assertTrue(first.contains("\t'A',") && second.contains("\t'B',") && stmts.get(3).contains("\t'A',"), "CHECK IN 순환");
        assertTrue(first.contains("\t'Y',") && second.contains("\t'N',"), "_YN CHAR(1) 은 기본값보다 먼저 Y/N 교대");
        assertTrue(first.contains("\t'001',"), "길이 3 이하면 순번만: " + first);
        assertTrue(first.contains("\t1.01,") && stmts.get(3).contains("\t4.04,"), "수 결정적(정수 idx+1, 소수 0 채움): " + first);
        assertTrue(first.contains("\tNULL,"), "BLOB NULL");
        assertTrue(first.contains("'SAMPLE_1'"), "CLOB");
        assertTrue(first.contains("TO_DATE('2026-01-31 00:00:00','YYYY-MM-DD HH24:MI:SS')")
                && second.contains("TO_DATE('2026-01-30 00:00:00'"), "날짜 = 기준일 − idx");
    }

    @Test
    void upsertThreeShapes() throws IOException {
        Table p = table("products");
        String ora = InsertGen.generate(p, Map.of(), new InsertGen.Options("oracle", 1, true, false, BASE), null).sql();
        String pg = InsertGen.generate(p, Map.of(), new InsertGen.Options("postgresql", 1, true, false, BASE), null).sql();
        String my = InsertGen.generate(p, Map.of(), new InsertGen.Options("mariadb", 1, true, false, BASE), null).sql();
        assertTrue(ora.startsWith("MERGE INTO PRODUCTS T") && ora.contains("#{productId} AS PRODUCT_ID") && ora.contains("FROM DUAL"), ora);
        assertTrue(pg.contains("ON CONFLICT (PRODUCT_ID) DO UPDATE SET") && pg.contains("CODE = EXCLUDED.CODE"), pg);
        assertTrue(my.contains("ON DUPLICATE KEY UPDATE") && my.contains("NAME = VALUES(NAME)"), my);
    }

    @Test
    void csvModeWarnsButKeepsValues() throws IOException {
        String csv = "PRODUCT_ID,category_id,code,name,price,created_at\n"
                + "1,10,C-1,상품,1234.5,2026-01-02\n"
                + "x,,C-2,이름이 매우 긴 상품,12345678901.5,2026/01/02\n";
        InsertGen.Result r = InsertGen.fromCsv(table("products"), csv, new InsertGen.Options("oracle", 0, false, false, BASE));
        assertTrue(r.sql().contains("\t1234.5,") && r.sql().contains("TO_TIMESTAMP('2026-01-02 00:00:00'"), r.sql());
        assertTrue(r.sql().contains("\t'x',"), "숫자가 아니어도 값은 그대로: " + r.sql());
        assertEquals(List.of("3행 PRODUCT_ID: 숫자가 아니다", "3행 CATEGORY_ID: NOT NULL 인데 비었다",
                "3행 PRICE: 정수 자리 10 을 넘는다", "3행 CREATED_AT: 날짜 모양(YYYY-MM-DD[ HH:MI[:SS]])이 아니다"), r.warnings());
        String up = InsertGen.fromCsv(table("products"), "product_id,code\n1,O'Neil\n", new InsertGen.Options("postgresql", 0, true, false, BASE)).sql();
        assertTrue(up.contains("'O''Neil'") && up.contains("ON CONFLICT (PRODUCT_ID)"), up);
        assertThrows(IllegalArgumentException.class, () -> InsertGen.fromCsv(table("products"), "nope\n1\n",
                new InsertGen.Options("oracle", 0, false, false, BASE)));
    }

    /** FK 실존 값(H2) + PG 방언 생성문을 H2 products 에 실제로 넣는다 */
    @Test
    void fkValuesAndRunOnH2() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:insertgen;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE categories (category_id INTEGER PRIMARY KEY, parent_id INTEGER, name VARCHAR(100) NOT NULL)");
                s.execute("CREATE TABLE products (product_id BIGINT PRIMARY KEY, category_id INTEGER NOT NULL, code VARCHAR(30) NOT NULL,"
                        + " name VARCHAR(200) NOT NULL, price NUMERIC(12,2) DEFAULT 0 NOT NULL,"
                        + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,"
                        + " CONSTRAINT uq_products_code UNIQUE (code),"
                        + " CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(category_id))");
                s.execute("INSERT INTO categories VALUES (10, NULL, 'a'), (20, NULL, 'b')");
            }
            InsertGen.FkValues fromH2 = (ForeignKey fk, int max) -> {
                List<List<Object>> rows = new ArrayList<>();
                try (Statement s = c.createStatement();
                        ResultSet rs = s.executeQuery("SELECT DISTINCT " + fk.refColumns().get(0) + " FROM " + fk.refTable() + " ORDER BY 1")) {
                    while (rs.next() && rows.size() < max) {
                        rows.add(List.of(rs.getObject(1)));
                    }
                }
                return rows;
            };
            InsertGen.Result r = InsertGen.generate(table("products"), Map.of(),
                    new InsertGen.Options("postgresql", 3, false, true, BASE), fromH2);
            assertEquals(List.of(), r.warnings(), "부모 값이 있으면 FK 경고가 없다");
            assertTrue(r.sql().contains("\t10,") && r.sql().contains("\t20,"), "부모 값 순환: " + r.sql());
            try (Statement s = c.createStatement()) {
                for (String stmt : r.sql().split("\n\n")) {
                    s.execute(stmt.substring(0, stmt.lastIndexOf(';')));
                }
                try (ResultSet rs = s.executeQuery("SELECT COUNT(*), COUNT(DISTINCT category_id) FROM products")) {
                    rs.next();
                    assertEquals(3, rs.getInt(1));
                    assertEquals(2, rs.getInt(2));
                }
            }
        }
    }

    @Test
    void fkLookupFailureBecomesWarning() throws IOException {
        InsertGen.Result r = InsertGen.generate(table("products"), Map.of(), new InsertGen.Options("oracle", 1, false, false, BASE),
                (fk, max) -> {
                    throw new SQLException("없는 테이블");
                });
        assertEquals(List.of("FK category_id → categories: 부모 값 조회 실패 — 순번으로 채웠다"), r.warnings());
        assertThrows(IllegalArgumentException.class, () -> InsertGen.generate(table("products"), Map.of(),
                new InsertGen.Options("db2", 1, false, false, BASE), null));
    }
}
