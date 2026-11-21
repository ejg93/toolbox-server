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
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** 4-7 — 값 규칙·MERGE·CSV 모드·FK 실존 값·H2 실행. 골든은 PG 벤더 메타 products·users × 방언 6 */
class InsertGenTest {

    static final LocalDate BASE = LocalDate.of(2026, 1, 31);
    static final String TWO_CHECKS = "CREATE TABLE a (status VARCHAR(10) CHECK (status IN ('NEW','PAID')));"
            + " CREATE TABLE b (status NUMBER(1) CHECK (status IN (0, 1)));";

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

    /** V-6 실물 HR — NUMBER(2,2) 는 정수 자리가 없어 0.xx 여야 들어간다 */
    @Test
    void numberWithoutIntegerDigits() {
        assertEquals("0.01", InsertGen.number(1, 2, 2));
        assertEquals("0.05", InsertGen.number(5, 2, 2));
        assertEquals("0.001", InsertGen.number(1, 3, 3));
        assertEquals("5.05", InsertGen.number(5, 12, 2), "정수 자리가 있으면 그대로");
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
        assertTrue(my.startsWith("INSERT INTO products ("), "MySQL·MariaDB 는 표 이름 대소문자 그대로(V-10): " + my);
    }

    @Test
    void pkIgnoresQuotedDefault() {
        InsertGen.Col pk = new InsertGen.Col("TABLE_NAME", "VARCHAR", 20, 0, 0, true, "''", List.of(), true, false, false);
        InsertGen.Col plain = new InsertGen.Col("NOTE", "VARCHAR", 20, 0, 0, true, "''", List.of(), false, false, false);
        assertEquals("'TABLE_NAME_001'", InsertGen.genVal(pk, 0, "mysql", BASE), "PK 는 기본값을 안 쓴다 — MariaDB DEFAULT '' (V-10)");
        assertEquals("''", InsertGen.genVal(plain, 0, "mysql", BASE));
    }

    @Test
    void mergeAllKeysHasNoUpdate() {
        List<InsertGen.Col> cols = List.of(
                new InsertGen.Col("A_ID", "NUMBER", 0, 10, 0, true, "", List.of(), true, false, false),
                new InsertGen.Col("B_ID", "NUMBER", 0, 10, 0, true, "", List.of(), true, false, false));
        String ora = InsertGen.buildUpsert("oracle", "AB", cols, c -> "1", new ArrayList<>());
        assertTrue(!ora.contains("WHEN MATCHED THEN") && ora.contains("WHEN NOT MATCHED THEN"), "키 컬럼을 UPDATE 하면 ORA-38104(V-10): " + ora);
    }

    /** V-49a — DDL 에서 이름만 읽은 표(typed·CTAS·LIKE — 컬럼 0)는 예외가 아니라 빈 SQL + 경고 */
    @Test
    void tableWithoutColumnsIsWarning() {
        Table t = DdlReader.read("CREATE TABLE t2 OF typ2").tables().get(0);
        InsertGen.Result r = InsertGen.generate(t, Map.of(), new InsertGen.Options("oracle", 3, false, true, LocalDate.of(2026, 1, 31)), null);
        assertEquals("", r.sql());
        assertEquals(List.of("컬럼이 없다: t2 — DDL 이 컬럼을 안 적은 표(OF 형식·AS SELECT·LIKE)"), r.warnings());
    }

    /** 0-40 — 전부 키 표의 pg·mysql UPSERT 가 빈 SET 을 안 낸다(순수본 b6a87d5 와 같게). 단일 컬럼 PK 표가 그 꼴 */
    @Test
    void upsertAllKeysHasNoEmptySet() {
        InsertGen.Col only = new InsertGen.Col("CODE", "VARCHAR", 10, 0, 0, true, "", List.of(), true, false, false);
        String pg1 = InsertGen.buildUpsert("pg", "T1", List.of(only), c -> "'A'", new ArrayList<>());
        String my1 = InsertGen.buildUpsert("mysql", "t1", List.of(only), c -> "'A'", new ArrayList<>());
        assertTrue(pg1.contains("ON CONFLICT (CODE) DO NOTHING;\n") && !pg1.contains("DO UPDATE SET"), pg1);
        assertTrue(my1.contains("ON DUPLICATE KEY UPDATE\n\t  CODE = CODE;\n"), my1);
        List<InsertGen.Col> two = List.of(
                new InsertGen.Col("A_ID", "NUMBER", 0, 10, 0, true, "", List.of(), true, false, false),
                new InsertGen.Col("B_ID", "NUMBER", 0, 10, 0, true, "", List.of(), true, false, false));
        String pg2 = InsertGen.buildUpsert("pg", "AB", two, c -> "1", new ArrayList<>());
        String my2 = InsertGen.buildUpsert("mysql", "ab", two, c -> "1", new ArrayList<>());
        assertTrue(pg2.contains("ON CONFLICT (A_ID, B_ID) DO NOTHING;") && !pg2.contains("EXCLUDED"), "키 컬럼을 갱신하지 않는다: " + pg2);
        assertTrue(my2.contains("\t  A_ID = A_ID;\n") && !my2.contains("VALUES(B_ID)"), my2);
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

    /** V-29 실물 표본(RuoYi MariaDB)에서 드러남 — 불 꼴 컬럼의 true/false 를 감싸지 않는다. tinyint(1)·BIT → 1/0, PG boolean 은 true/false */
    @Test
    void csvBooleanLikeValues() {
        InsertGen.Col tiny = new InsertGen.Col("MENU_CHECK_STRICTLY", "TINYINT", 1, 0, 0, false, "", List.of(), false, false, false);
        InsertGen.Col bit = new InsertGen.Col("FLAG", "BIT", 1, 0, 0, false, "", List.of(), false, false, false);
        InsertGen.Col pgBool = new InsertGen.Col("ACTIVE", "BOOLEAN", 0, 0, 0, false, "", List.of(), false, false, false);
        InsertGen.Col tinyInt = new InsertGen.Col("SORT", "TINYINT", 4, 0, 0, false, "", List.of(), false, false, false);
        List<String> w = new ArrayList<>();
        assertEquals(List.of("1", "0", "1", "true", "0"), List.of(InsertGen.csvLiteral(tiny, "true", "mariadb", 2, w),
                InsertGen.csvLiteral(tiny, "FALSE", "mariadb", 2, w), InsertGen.csvLiteral(bit, "True", "mssql", 2, w),
                InsertGen.csvLiteral(pgBool, "true", "pg", 2, w), InsertGen.csvLiteral(pgBool, "FALSE", "oracle", 2, w)));
        assertEquals(List.of(), w);
        assertEquals("B'1'", InsertGen.csvLiteral(bit, "true", "pg", 2, w), "PG bit 은 비트 글자");
        assertEquals("'true'", InsertGen.csvLiteral(tinyInt, "true", "mariadb", 2, w), "1자리가 아닌 TINYINT 는 불 꼴이 아니다 — 경고와 함께 그대로");
        assertEquals(List.of("2행 SORT: 숫자가 아니다"), w);
    }

    static Table orderDetails() {
        return Table.of(null, "Order Details", "TABLE", null)
                .withColumns(List.of(new Column("ID", 1, "INT", null, null, 10, 0, false, null, null, null),
                        new Column("ORDER", 2, "INT", null, null, 10, 0, true, null, null, null),
                        new Column("Unit Price", 3, "INT", null, null, 10, 0, true, null, null, null)))
                .withConstraints(new PrimaryKey("PK_OD", List.of("ID")), List.of(), List.of());
    }

    static String sql(String dialect, boolean upsert) {
        return InsertGen.generate(orderDetails(), Map.of(), new InsertGen.Options(dialect, 2, upsert, false, BASE), null).sql();
    }

    /** 4-33(A-06) — 예약어·공백 든 이름은 방언 따옴표로. 예약어는 접는 방언이면 접은 글자로 */
    @Test
    void reservedAndSpacedNamesAreQuoted() throws Exception {
        String ora = sql("oracle", false);
        assertTrue(ora.contains("INSERT INTO \"Order Details\"(") && ora.contains("\t\"ORDER\"") && ora.contains("\t\"Unit Price\""), ora);
        assertTrue(sql("postgresql", false).contains("\t\"order\""), sql("postgresql", false));
        String maria = sql("mariadb", false);
        assertTrue(maria.contains("`Order Details`") && maria.contains("`ORDER`") && maria.contains("`Unit Price`"), maria);
        String ms = sql("mssql", false);
        assertTrue(ms.contains("[Order Details]") && ms.contains("[ORDER]") && ms.contains("[Unit Price]"), ms);
        // 4-36 — $ 는 다섯 방언 다 맨 이름, # 는 oracle·mssql·sybase 만
        Table dollar = Table.of(null, "t436", "TABLE", null)
                .withColumns(List.of(new Column("emp$no", 1, "INT", null, null, 10, 0, false, null, null, null),
                        new Column("tmp#1", 2, "INT", null, null, 10, 0, true, null, null, null)))
                .withConstraints(new PrimaryKey(null, List.of("emp$no")), List.of(), List.of());
        java.util.function.Function<String, String> gen = d -> InsertGen.generate(dollar, Map.of(), new InsertGen.Options(d, 2, false, false, BASE), null).sql();
        String oraD = gen.apply("oracle");
        assertTrue(oraD.contains("\temp$no") && oraD.contains("\ttmp#1"), oraD);
        String mariaD = gen.apply("mariadb");
        assertTrue(mariaD.contains("`tmp#1`") && mariaD.contains("\temp$no"), mariaD);
        assertTrue(gen.apply("postgresql").contains("\"tmp#1\""), gen.apply("postgresql"));
        String merge = sql("oracle", true);
        assertTrue(merge.contains("MERGE INTO \"Order Details\" T") && merge.contains("T.\"ORDER\" = S.\"ORDER\""), merge);
        assertTrue(sql("postgresql", true).contains("ON CONFLICT (ID)"), sql("postgresql", true));
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:insert433;DB_CLOSE_DELAY=-1", "sa", "");
                Statement s = c.createStatement()) {
            s.execute("CREATE TABLE \"Order Details\" (\"ID\" INT PRIMARY KEY, \"ORDER\" INT, \"Unit Price\" INT)");
            for (String stmt : ora.split("\n\n")) {
                s.execute(stmt.substring(0, stmt.lastIndexOf(';')));
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM \"Order Details\"")) {
                rs.next();
                assertEquals(2, rs.getInt(1));
            }
        }
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

    static List<String> keyWarns(String ddl, int rows, boolean upsert) {
        Table t = DdlReader.read(ddl).tables().get(0);
        return InsertGen.generate(t, InsertGen.checkIns(ddl), new InsertGen.Options("oracle", rows, upsert, false, BASE), null)
                .warnings();
    }

    /** 4-35(A-05) — CHECK IN 은 그 표 것만 */
    @Test
    void checkInsFromTable() {
        List<Table> ts = DdlReader.read(TWO_CHECKS).tables();
        assertEquals(Map.of("STATUS", List.of("'NEW'", "'PAID'")), InsertGen.checkIns(ts.get(0)));
        assertEquals(Map.of("STATUS", List.of("0", "1")), InsertGen.checkIns(ts.get(1)));
        // A-32 — 값 안 괄호가 IN 목록을 일찍 닫지 않는다
        Table k = DdlReader.read("CREATE TABLE tk (k VARCHAR(10), CHECK (k IN ('m(2)', 'n')))").tables().get(0);
        assertEquals(Map.of("K", List.of("'m(2)'", "'n'")), InsertGen.checkIns(k));
    }

    /** A-08 — GENERATED … AS IDENTITY · AUTO_INCREMENT · IDENTITY(1,1) 도 자동증가 경고 */
    @Test
    void autoColumnsAreWarned() {
        for (String ddl : List.of("CREATE TABLE t (id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, nm VARCHAR(10))",
                "CREATE TABLE t (id INT AUTO_INCREMENT PRIMARY KEY, nm VARCHAR(10))", "CREATE TABLE t (id INT IDENTITY(1,1) PRIMARY KEY, nm VARCHAR(10))")) {
            assertTrue(String.join("\n", keyWarns(ddl, 3, false)).contains("자동증가 컬럼: ID"), ddl + " " + keyWarns(ddl, 3, false));
        }
    }

    /** A-25 — 행 수는 1~1000 으로, 바꿨으면 경고 */
    @Test
    void rowsAreClampedWithWarning() {
        String ddl = "CREATE TABLE t (id INT PRIMARY KEY)";
        Table t = DdlReader.read(ddl).tables().get(0);
        InsertGen.Result big = InsertGen.generate(t, Map.of(), new InsertGen.Options("oracle", 5000, false, false, BASE), null);
        assertEquals(1000, big.sql().split("INSERT INTO", -1).length - 1);
        assertTrue(big.warnings().contains("행 수 5000 → 1000(1~1000 만)"), big.warnings().toString());
        InsertGen.Result zero = InsertGen.generate(t, Map.of(), new InsertGen.Options("oracle", 0, false, false, BASE), null);
        assertEquals(1, zero.sql().split("INSERT INTO", -1).length - 1);
        assertTrue(zero.warnings().stream().anyMatch(w -> w.startsWith("행 수 0 → 1")), zero.warnings().toString());
    }

    static String sqlOf(String ddl, String dialect, int rows) {
        return InsertGen.generate(DdlReader.read(ddl).tables().get(0), Map.of(), new InsertGen.Options(dialect, rows, false, false, BASE), null).sql();
    }

    /** 4-34(A-04·A-07) — 길이 4 키 문자는 순번이 안 잘린다 · 짧은 키·TINYINT 키 겹침 경고 · TIME·YEAR·INTERVAL 값 */
    @Test
    void shortKeysAndTimeTypes() {
        String keys = sqlOf("CREATE TABLE t (k CHAR(4) PRIMARY KEY, c CHAR(4))", "oracle", 12);
        for (int i = 1; i <= 12; i++) {
            assertTrue(keys.contains(String.format(java.util.Locale.ROOT, "\t'%04d',", i)), keys);
        }
        assertTrue(keys.contains("\t'C_00'\n"), "키 아닌 CHAR(4) 는 지금 값: " + keys);
        assertTrue(String.join("\n", keyWarns("CREATE TABLE t (k CHAR(2) PRIMARY KEY)", 150, false)).contains("허용 값이 100개인 키 컬럼"));
        assertTrue(String.join("\n", keyWarns("CREATE TABLE t (k TINYINT PRIMARY KEY)", 130, false)).contains("허용 값이 128개인 키 컬럼"));
        String pg = sqlOf("CREATE TABLE t (id INT PRIMARY KEY, tm TIME, yr YEAR, iv INTERVAL)", "postgresql", 2);
        assertTrue(pg.contains("\t'00:00:00',") && pg.contains("\t'01:01:00',"), pg);
        assertTrue(pg.contains("\t2026,") && pg.contains("\t2025,"), pg);
        assertTrue(pg.contains("\t'1 days'\n"), pg);
        String ora = sqlOf("CREATE TABLE t (id INT PRIMARY KEY, iv INTERVAL DAY TO SECOND, ym INTERVAL YEAR TO MONTH)", "oracle", 1);
        assertTrue(ora.contains("\tINTERVAL '1' DAY,") && ora.contains("\tINTERVAL '1' MONTH\n"), ora);
    }

    /** 4-24 — 순수본 #31 키 겹침 경고와 같은 글. 좁은 컬럼만 든 키 묶음의 값수 LCM < 행 수일 때 */
    @Test
    void keyOverlapByGroup() {
        assertEquals(List.of("USE_YN: 허용 값이 2개인 키 컬럼이라 5행이면 값이 겹친다 — 행 수를 2 이하로 두거나 값을 손으로 바꿀 것"),
                keyWarns("CREATE TABLE t (use_yn CHAR(1) PRIMARY KEY, nm VARCHAR2(10))", 5, false));
        assertEquals(List.of(), keyWarns("CREATE TABLE t (use_yn CHAR(1), seq NUMBER(5), PRIMARY KEY (use_yn, seq))", 5, false));
        String pair = "CREATE TABLE t (use_yn CHAR(1), st VARCHAR2(1) CHECK (st IN ('A','B','C')), PRIMARY KEY (use_yn, st))";
        assertEquals(List.of("(USE_YN, ST): 값 조합이 6가지인 키라 7행이면 값이 겹친다 — 행 수를 6 이하로 두거나 값을 손으로 바꿀 것"),
                keyWarns(pair, 7, false));
        assertEquals(List.of(), keyWarns(pair, 6, false));
        assertEquals(List.of(), keyWarns(pair, 7, true));
    }

    /** 4-26 — CREATE 문의 UNIQUE 만으로(손으로 넣은 UniqueKey 없이) 키 겹침 경고가 나고, UNIQUE 컬럼은 기본값을 안 쓴다 */
    @Test
    void keyOverlapFromDdlUnique() {
        assertEquals(List.of("(USE_YN, DEL_YN): 값 조합이 2가지인 키라 5행이면 값이 겹친다 — 행 수를 2 이하로 두거나 값을 손으로 바꿀 것"),
                keyWarns("CREATE TABLE t (use_yn CHAR(1), del_yn CHAR(1), nm VARCHAR2(10), CONSTRAINT uq UNIQUE (use_yn, del_yn))", 5, false));
        String ddl = "CREATE TABLE t (cd CHAR(2) DEFAULT 'ZZ' UNIQUE, nm VARCHAR2(5) DEFAULT 'ZZ')";
        List<String> stmts = List.of(InsertGen.generate(DdlReader.read(ddl).tables().get(0), Map.of(),
                new InsertGen.Options("oracle", 2, false, false, BASE), null).sql().split("\n\n"));
        assertTrue(stmts.get(0).contains("\t'01',") && stmts.get(1).contains("\t'02',"), "UNIQUE 는 순번: " + stmts);
        assertTrue(stmts.get(0).contains("\t'ZZ'"), "UNIQUE 아닌 컬럼은 기본값: " + stmts.get(0));
    }

    /** 4-25a — 순수본 genVal 의 ENUM·SET 갈래: 나열 값 순환, 키 아닌 컬럼의 기본값이 먼저, CHECK IN 이 더 먼저. 키 겹침 값수도 나열 수 */
    @Test
    void enumSetCycleLikePure() {
        String ddl = "CREATE TABLE t (id INT PRIMARY KEY, st ENUM('A','B','C') NOT NULL, kd ENUM('P','Q') DEFAULT 'Q',"
                + " fl SET('X','Y') NOT NULL, ck ENUM('M','N') CHECK (ck IN ('K')))";
        List<String> stmts = List.of(InsertGen.generate(DdlReader.read(ddl).tables().get(0), InsertGen.checkIns(ddl),
                new InsertGen.Options("mariadb", 4, false, false, BASE), null).sql().split("\n\n"));
        assertEquals(4, stmts.size());
        String[] st = {"'A'", "'B'", "'C'", "'A'"};
        String[] fl = {"'X'", "'Y'", "'X'", "'Y'"};
        for (int i = 0; i < 4; i++) {
            String s = stmts.get(i);
            assertTrue(s.contains("\t" + st[i] + ",\n\t'Q',\n\t" + fl[i] + ",\n\t'K'"), "행 " + (i + 1) + ": " + s);
        }
        assertEquals(List.of("ST: 허용 값이 2개인 키 컬럼이라 3행이면 값이 겹친다 — 행 수를 2 이하로 두거나 값을 손으로 바꿀 것"),
                keyWarns("CREATE TABLE t (st ENUM('A','B') PRIMARY KEY, nm VARCHAR(10))", 3, false));
    }

    /** 4-24 — 표 수준 UNIQUE·유니크 인덱스도 묶음. JDBC 처럼 UNIQUE 와 같은 컬럼의 유니크 인덱스가 둘 다 와도 한 줄, 일반 인덱스는 안 본다 */
    @Test
    void keyOverlapUniqueAndIndexMerged() {
        Table d = DdlReader.read("CREATE TABLE t (use_yn CHAR(1), del_yn CHAR(1), nm VARCHAR2(10))").tables().get(0);
        Table t = d.withConstraints(d.pk(), d.fks(), List.of(new UniqueKey("UQ_T", List.of("use_yn", "del_yn"))))
                .withIndexes(List.of(new Index("UQ_T", true, List.of("USE_YN", "DEL_YN"), null),
                        new Index("UX_T", true, List.of("USE_YN"), null), new Index("IX_T", false, List.of("DEL_YN"), null)));
        assertEquals(List.of("(USE_YN, DEL_YN): 값 조합이 2가지인 키라 5행이면 값이 겹친다 — 행 수를 2 이하로 두거나 값을 손으로 바꿀 것",
                "USE_YN: 허용 값이 2개인 키 컬럼이라 5행이면 값이 겹친다 — 행 수를 2 이하로 두거나 값을 손으로 바꿀 것"),
                InsertGen.generate(t, Map.of(), new InsertGen.Options("oracle", 5, false, false, BASE), null).warnings());
    }

    /** 1-88(A-28) — mysql(mariadb) 리터럴 안 역슬래시는 겹친다. postgresql 은 그대로 */
    @Test
    void mysqlBackslash() throws IOException {
        String csv = "product_id,code\n1,C:\\temp\\\n";
        String maria = InsertGen.fromCsv(table("products"), csv, new InsertGen.Options("mariadb", 0, false, false, BASE)).sql();
        assertTrue(maria.contains("'C:\\\\temp\\\\'"), maria);
        String pg = InsertGen.fromCsv(table("products"), csv, new InsertGen.Options("postgresql", 0, false, false, BASE)).sql();
        assertTrue(pg.contains("'C:\\temp\\'") && !pg.contains("\\\\"), pg);
        InsertGen.Col c = new InsertGen.Col("NM", "VARCHAR", 10, 0, 0, false, "", List.of(), false, false, false);
        assertEquals("'a\\\\b'", InsertGen.literal(c, "a\\b", "mysql"));
        assertEquals("'a\\b'", InsertGen.literal(c, "a\\b", "oracle"));
    }
}
