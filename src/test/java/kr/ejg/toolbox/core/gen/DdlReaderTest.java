package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.logical.DomainMatcher;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * 1-9 — 경량 DDL 읽기의 정확도를 실물 DB 결과로 잰다. 컨테이너에 넣었던 샘플 DDL 넷을 읽어 1-3 이 그 DB 에서 떠 온
 * 스냅샷 골든과 테이블·컬럼 이름·타입 계열·NULL·PK·코멘트를 견준다. MSSQL 코멘트는 `sp_addextendedproperty` 라 안 읽는다.
 */
class DdlReaderTest {

    static final String LIKE = "like";
    static final String TYPED = "typed";
    static final String CTAS = "ctas";
    static final String EMP_NAME = "name";

    /** V·C·N(3-6 규칙) · 날짜 D · 나머지는 이름 그대로 */
    static String family(String nativeType) {
        String u = TypeMapping.norm(nativeType);
        String c = DomainMatcher.typeCode(u);
        if (!c.isEmpty()) {
            return c;
        }
        return u.contains("DATE") || u.contains("TIME") ? "D" : u;
    }

    @ParameterizedTest
    @CsvSource({"postgres, postgres-vendor, true", "mariadb, mariadb, true", "mssql, mssql, false", "oracle, oracle, true"})
    void matchesSnapshotTakenFromTheSameDdl(String sample, String golden, boolean comments) throws Exception {
        DdlReader.Result r = DdlReader.read(Files.readString(Path.of("src/test/resources/sample/" + sample + ".sql"), StandardCharsets.UTF_8));
        assertEquals(List.of(), r.unreadable(), sample);
        List<Schema> snap = GoldenFiles.schemas("meta/" + golden + ".json");
        Map<String, Table> expected = snap.get(0).tables().stream()
                .collect(Collectors.toMap(t -> t.name().toUpperCase(Locale.ROOT), Function.identity()));
        assertEquals(expected.keySet(), r.tables().stream().map(t -> t.name().toUpperCase(Locale.ROOT)).collect(Collectors.toSet()), sample);
        for (Table t : r.tables()) {
            Table e = expected.get(t.name().toUpperCase(Locale.ROOT));
            String where = sample + " " + t.name();
            assertEquals(e.columns().size(), t.columns().size(), where);
            for (int i = 0; i < e.columns().size(); i++) {
                Column ec = e.columns().get(i);
                Column ac = t.columns().get(i);
                String at = where + "." + ec.name();
                assertTrue(ec.name().equalsIgnoreCase(ac.name()), at + " 이름 " + ac.name());
                assertEquals(family(ec.nativeType()), family(ac.nativeType()), at + " 타입 " + ec.nativeType() + " / " + ac.nativeType());
                assertEquals(ec.nullable(), ac.nullable(), at + " NULL");
                if (comments) {
                    assertEquals(ec.comment(), ac.comment(), at + " 코멘트");
                }
            }
            assertEquals(e.pk() == null ? List.of() : e.pk().columns().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList(),
                    t.pk() == null ? List.of() : t.pk().columns().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList(), where + " PK");
            if (t.pk() != null && t.pk().name() != null) { // 1-70 — DDL 에 PK 이름이 있으면 JDBC 와 같다(mariadb·postgres 샘플은 이름 없음)
                assertTrue(t.pk().name().equalsIgnoreCase(e.pk().name()), where + " PK 이름 " + t.pk().name() + " / " + e.pk().name());
            }
            // 4-26 — 제약 UNIQUE(샘플은 전부 uq_*). CREATE UNIQUE INDEX(ux_*) 출신은 뺀다 — JDBC 골든이 방언마다 uniques 에 넣기도 안 넣기도 한다(1-68)
            Set<String> fromIndex = t.indexes().stream().map(x -> x.name().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
            assertEquals(e.uniques().stream().filter(u -> u.name().toUpperCase(Locale.ROOT).startsWith("UQ_"))
                            .map(u -> u.name().toUpperCase(Locale.ROOT) + ":" + u.columns().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList())
                            .collect(Collectors.toSet()),
                    t.uniques().stream().filter(u -> !fromIndex.contains(String.valueOf(u.name()).toUpperCase(Locale.ROOT)))
                            .map(u -> String.valueOf(u.name()).toUpperCase(Locale.ROOT) + ":"
                            + u.columns().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList()).collect(Collectors.toSet()), where + " UNIQUE");
            // 1-68 — DDL 의 인덱스는 JDBC 인덱스에 이름·unique·컬럼이 같게 있다(⊆ — MySQL FK 자동 인덱스는 DDL 에 없다)
            Set<String> jdbcIndexes = e.indexes().stream().map(DdlReaderTest::indexKey).collect(Collectors.toSet());
            for (Index x : t.indexes()) {
                assertTrue(jdbcIndexes.contains(indexKey(x)), where + " 인덱스 " + indexKey(x) + " / " + jdbcIndexes);
            }
            if (comments) {
                assertEquals(e.comment(), t.comment(), where + " 테이블 코멘트");
            }
        }
    }

    static String indexKey(Index x) {
        return x.name().toUpperCase(Locale.ROOT) + ":" + x.unique() + ":" + x.columns().stream().map(c -> c.toUpperCase(Locale.ROOT)).toList();
    }

    /** 1-95a(A-03) — ALTER 의 ADD·DROP·MODIFY·CHANGE·COMMENT·제약이 표에 들어간다. 모델에 영향 없는 동작은 조용히 */
    @Test
    void alterColumns() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (id INT PRIMARY KEY, a INT, old_nm VARCHAR(10));\n"
                + "ALTER TABLE t ADD c1 INT;\nALTER TABLE t ADD COLUMN c2 VARCHAR(20) NOT NULL;\nALTER TABLE t ADD (c3 NUMBER(5), c4 DATE);\n"
                + "ALTER TABLE t ADD c5 INT, c6 INT;\nALTER TABLE t DROP COLUMN a;\nALTER TABLE t MODIFY c1 BIGINT NOT NULL;\n"
                + "ALTER TABLE t CHANGE old_nm nm VARCHAR(50) COMMENT '이름';\nALTER TABLE t ADD CONSTRAINT ck CHECK (c1 > 0);\n"
                + "ALTER TABLE t COMMENT = '표';\nALTER TABLE t OWNER TO x;\n"
                + "ALTER TABLE t ADD CONSTRAINT fk FOREIGN KEY (c1) REFERENCES t (id), ADD CONSTRAINT uq UNIQUE (c2);");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals(List.of("id", "nm", "c1", "c2", "c3", "c4", "c5", "c6"), t.columns().stream().map(Column::name).toList());
        Column nm = t.columns().get(1);
        assertEquals("VARCHAR:50:이름", nm.nativeType() + ":" + nm.length() + ":" + nm.comment());
        Column c1 = t.columns().get(2);
        assertEquals("BIGINT:false", c1.nativeType() + ":" + c1.nullable());
        assertEquals("1:1:1:표", t.checks().size() + ":" + t.fks().size() + ":" + t.uniques().size() + ":" + t.comment());
    }

    /** 1-125(A1-12) — 정규식 다섯이 긴 공백·낱말 줄기에 선형(스택 넘침 없음) · 지킴이: 크기·CTAS 별칭·NATURAL JOIN 결과 그대로 */
    @Test
    void regexesAreLinear() {
        Table s = DdlReader.read("CREATE TABLE t (a VARCHAR2(10 BYTE), b NUMBER(10, 2), c NUMBER( 5 ))").tables().get(0);
        assertEquals("10:10:2:5", s.columns().get(0).length() + ":" + s.columns().get(1).precision() + ":" + s.columns().get(1).scale() + ":"
                + s.columns().get(2).precision());
        DdlReader.Result ctas = DdlReader.read("CREATE TABLE a (x INT);\nCREATE TABLE t AS SELECT a.x AS y FROM a");
        assertEquals(List.of("y"), ctas.tables().stream().filter(t -> "t".equals(t.name())).findFirst().orElseThrow().columns().stream()
                .map(Column::name).toList());
        DdlReader.Result nat = DdlReader.read("CREATE TABLE a (x INT, k INT);\nCREATE TABLE b (y INT, k INT);\nCREATE TABLE t AS SELECT * FROM a NATURAL LEFT OUTER JOIN b");
        assertEquals("[] {ctas=" + DdlReader.NAME_ONLY.get("ctas") + "}", nat.tables().stream().filter(t -> "t".equals(t.name())).findFirst().orElseThrow().columns().stream()
                .map(Column::name).toList() + " " + nat.notes().getOrDefault("T", java.util.Map.of()));
        String sp = " ".repeat(100_000);
        List<String> inputs = List.of("CREATE TABLE t TABLESPACE ts AS" + sp + "x", "CREATE TABLE t (a INT) COMMENT" + sp + "x", "CREATE TABLE t (a VARCHAR(1" + sp + "x))",
                "CREATE TABLE t AS SELECT a" + sp + "x FROM y", "CREATE TABLE t AS SELECT * FROM a NATURAL " + "w ".repeat(5_000) + "x");
        for (String in : inputs) {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> DdlReader.read(in), in.substring(0, 30));
        }
    }


    /** 1-129(M15) — COMMENT ON COLUMN db.스키마.표.컬럼(마디 넷)의 코멘트 · 긴 사슬도 선형 */
    @Test
    void commentOnFourPartName() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE s.t (c INT, d INT);\nCOMMENT ON COLUMN db.s.t.c IS 'x';\nCOMMENT ON COLUMN [db].[s].[t].[d] IS 'y';\n"
                + "COMMENT ON TABLE db.s.t IS 'z';");
        assertEquals(1, r.tables().size());
        Table t = r.tables().get(0);
        assertEquals("x", t.columns().get(0).comment());
        assertEquals("y", t.columns().get(1).comment());
        assertEquals("z", t.comment());
        String chain = "COMMENT ON COLUMN " + "a.".repeat(20_000) + "b IS 'x'";
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> DdlReader.read(chain));
    }

    /** 1-124(A1-09) — CRLF 덤프의 GO 줄도 문장 경계 */
    @Test
    void crlfGo() {
        Table t = DdlReader.read("CREATE TABLE a (id INT NOT NULL)\r\nGO\r\nALTER TABLE a ADD CONSTRAINT pk_a PRIMARY KEY (id)\r\nGO\r\n").tables().get(0);
        assertEquals("pk_a", t.pk() == null ? null : t.pk().name());
    }

    /** 1-124(A1-10·A1-22) — 표 안 인덱스 꼴: NONCLUSTERED·UNIQUE·IF NOT EXISTS·USING·이름 없는 KEY */
    @Test
    void inlineIndexShapes() {
        Table t = DdlReader.read("CREATE TABLE t (a INT NOT NULL, b INT, INDEX ix_a NONCLUSTERED (a), INDEX ix_b UNIQUE NONCLUSTERED (b), "
                + "KEY IF NOT EXISTS ix_c (b), KEY (a), KEY ix_d USING BTREE (b))").tables().get(0);
        assertEquals(List.of("a", "b"), t.columns().stream().map(Column::name).toList());
        assertEquals(List.of("ix_a:false:[a]", "ix_b:true:[b]", "ix_c:false:[b]", "null:false:[a]", "ix_d:false:[b]"),
                t.indexes().stream().map(x -> x.name() + ":" + x.unique() + ":" + x.columns()).toList());
    }

    /** 1-124(A1-20) — 인라인 CHECK 괄호 안 IS NOT NULL 은 컬럼의 NOT NULL 이 아니다 */
    @Test
    void checkNotNullIsNotTheColumn() {
        Table t = DdlReader.read("CREATE TABLE t (a INT, e INT CHECK (e IS NOT NULL OR a > 0), f INT NOT NULL CHECK (f > 0))").tables().get(0);
        assertTrue(t.columns().get(1).nullable(), "e");
        assertFalse(t.columns().get(2).nullable(), "f");
    }

    /** 1-124(A1-16) — 스키마.이름 을 못 찾으면 스키마가 다른 같은 이름 표로 물러서지 않는다 */
    @Test
    void otherSchemaSameName() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE s1.emp (id INT);\nALTER TABLE s2.emp ADD x INT;\nCOMMENT ON TABLE s2.emp IS 'c';\n"
                + "ALTER TABLE s2.emp ADD CONSTRAINT uq UNIQUE (id);");
        Table t = r.tables().get(0);
        assertEquals(List.of("id"), t.columns().stream().map(Column::name).toList());
        assertEquals(null, t.comment());
        assertEquals(List.of(), t.uniques());
    }

    /** 1-123(A1-07) — 공백 든 따옴표 이름: 키 목록·참조 표·ALTER 머리·COMMENT ON 이 공백에서 안 자른다 */
    @Test
    void quotedNamesWithSpaces() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE [Order Master] ([Order ID] int NOT NULL, CONSTRAINT [PK_OM] PRIMARY KEY ([Order ID]));\n"
                + "CREATE TABLE [Order Details] (\n  [Order ID] int NOT NULL,\n  [Unit Price] money NOT NULL,\n  CONSTRAINT [PK_OD] PRIMARY KEY ([Order ID]),\n"
                + "  CONSTRAINT [UQ_P] UNIQUE ([Unit Price]),\n  CONSTRAINT [FK_OM] FOREIGN KEY ([Order ID]) REFERENCES [Order Master] ([Order ID])\n);");
        Table od = r.tables().stream().filter(t -> "Order Details".equals(t.name())).findFirst().orElseThrow();
        assertEquals(List.of("Order ID"), od.pk().columns());
        assertEquals(List.of("Unit Price"), od.uniques().get(0).columns());
        assertEquals(List.of("Order ID"), od.fks().get(0).columns());
        assertEquals("Order Master", od.fks().get(0).refTable());
        assertEquals(List.of("Order ID"), od.fks().get(0).refColumns());
        DdlReader.Result a = DdlReader.read("CREATE TABLE [dbo].[Order Details] ([OrderID] int NOT NULL, [ProductID] int NOT NULL);\n"
                + "ALTER TABLE [dbo].[Order Details] ADD [Discount] real NULL;\n"
                + "ALTER TABLE [dbo].[Order Details] ADD CONSTRAINT [PK_OD] PRIMARY KEY ([OrderID], [ProductID]);");
        Table t = a.tables().get(0);
        assertEquals(List.of("OrderID", "ProductID"), t.pk() == null ? null : t.pk().columns());
        assertTrue(t.columns().stream().anyMatch(c -> "Discount".equals(c.name())), t.columns().toString());
        DdlReader.Result c = DdlReader.read("CREATE TABLE \"My Table\" (a int);\nCOMMENT ON TABLE \"My Table\" IS 'x';");
        assertEquals("x", c.tables().get(0).comment());
    }

    /** 1-123(A1-11) — SSMS 대괄호 타입 [int]·[nvarchar](40)·[decimal](18, 2) 를 벗긴다 */
    @Test
    void bracketTypes() {
        Table t = DdlReader.read("CREATE TABLE [dbo].[T] ([a] [int] NOT NULL, [b] [nvarchar](40) NULL, [c] [decimal](18, 2) NULL);").tables().get(0);
        assertEquals("int", t.columns().get(0).nativeType());
        assertEquals("nvarchar:40", t.columns().get(1).nativeType() + ":" + t.columns().get(1).length());
        Column c = t.columns().get(2);
        assertEquals("decimal:18:2", c.nativeType() + ":" + c.precision() + ":" + c.scale());
    }

    /** 1-122(A1-05·A1-06·A1-15) — IF [NOT] EXISTS · 온라인 DDL 옵션 · 긴 공백 정규식 */
    @Test
    void alterIfExistsAndOptions() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (id INT NOT NULL, c VARCHAR(10) NOT NULL);\nALTER TABLE t ADD COLUMN IF NOT EXISTS c VARCHAR(10);\n"
                + "ALTER TABLE t ADD IF NOT EXISTS d INT;\nALTER TABLE t DROP COLUMN IF EXISTS nope;");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals(List.of("id", "c", "d"), t.columns().stream().map(Column::name).toList());
        assertFalse(t.columns().get(1).nullable(), "c 는 NOT NULL 그대로");
        DdlReader.Result o = DdlReader.read("CREATE TABLE t (id INT, c INT);\nALTER TABLE t ADD COLUMN x int NULL AFTER c, ALGORITHM=INPLACE, LOCK=NONE;");
        assertEquals(List.of(), o.unreadable());
        assertEquals(List.of("id", "c", "x"), o.tables().get(0).columns().stream().map(Column::name).toList());
        DdlReader.Result f = DdlReader.read("CREATE TABLE t (id INT);\nALTER TABLE t ADD a INT, FOO=1;");
        assertEquals(List.of("id", "a"), f.tables().get(0).columns().stream().map(Column::name).toList());
        assertEquals(1, f.unreadable().size(), f.unreadable().toString());
        String sp = " ".repeat(100_000);
        for (String in : List.of("CREATE TABLE t (a INT);\nALTER TABLE t ADD DEFAULT 0" + sp + "x", "CREATE TABLE t (a INT);\nALTER TABLE t DROP a" + sp + "b")) {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> DdlReader.read(in));
        }
    }

    /** 1-96(A-10) — 긴 공백·괄호·빈 줄 줄기가 제곱 시간이 안 된다 */
    @Test
    void longRunsAreLinear() {
        String sp = " ".repeat(200_000);
        List<String> inputs = List.of("CREATE TABLE t (a" + sp + "x INT)", "CREATE TABLE t (a INT" + "(".repeat(200_000) + ")",
                "CREATE TABLE s" + sp + "t (a INT)", "CREATE TABLE t (a INT); CREATE INDEX i ON t (a" + sp + "x)",
                "CREATE TABLE t AS SELECT a FROM x" + sp + "y");
        for (String in : inputs) {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> DdlReader.read(in), in.substring(0, 30));
        }
        DdlReader.Result r = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> DdlReader.read("\n".repeat(200_000) +"CREATE TABLE t (a INT);"));
        assertEquals(1, r.tables().size());
    }

    /** 1-95b — CREATE 앞에 온 ALTER 도 끝에 다시 본다. 끝까지 표가 없으면 조용히 */
    @Test
    void alterBeforeCreate() {
        DdlReader.Result r = DdlReader.read("ALTER TABLE t ADD c INT;\nALTER TABLE t ADD CONSTRAINT uq UNIQUE (c);\nCREATE TABLE t (id INT);\n"
                + "ALTER TABLE nowhere ADD x INT;");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals(List.of("id", "c"), t.columns().stream().map(Column::name).toList());
        assertEquals(1, t.uniques().size());
    }

    /** 1-95a(A-03) — 못 읽는 ALTER 동작은 그 표의 「못 읽은 줄」. 모르는 표의 ALTER 는 조용히 */
    @Test
    void alterUnsupportedIsReported() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a INT, b INT);\nALTER TABLE t ALTER COLUMN a TYPE BIGINT;\n"
                + "ALTER TABLE t RENAME COLUMN a TO b;\nALTER TABLE t DROP CONSTRAINT ck;\nALTER TABLE t MODIFY (a NOT NULL);\n"
                + "ALTER TABLE t DROP COLUMN nope;\nALTER TABLE nowhere ADD x INT;\n"
                + "ALTER TABLE t ADD PARTITION p1 VALUES LESS THAN (10);\nALTER TABLE t ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;\n"
                + "ALTER TABLE t ADD CONSTRAINT xx EXCLUDE USING gist (a WITH =);");
        assertEquals(8, r.unreadable().size(), r.unreadable().toString());
        assertTrue(r.unreadable().stream().allMatch(u -> "t".equals(u.table()) && u.line().startsWith("ALTER TABLE t ")), r.unreadable().toString());
        assertEquals(List.of("a", "b"), r.tables().get(0).columns().stream().map(Column::name).toList());
    }

    /** 1-101 (라) — MODIFY (…) 괄호 꼴은 안 적은 NULL·DEFAULT 를 남기고, 맨 꼴은 통째 + 지웠으면 「방언 모호」, MODIFY COLUMN 은 통째 */
    @Test
    void modifyKeepsOrWarns() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a VARCHAR2(10) DEFAULT 'x' NOT NULL, b INT NOT NULL, c INT DEFAULT 1 NOT NULL, d INT, e INT NOT NULL);\n"
                + "ALTER TABLE t MODIFY (a VARCHAR2(100));\nALTER TABLE t MODIFY b BIGINT;\nALTER TABLE t MODIFY COLUMN c BIGINT;\n"
                + "ALTER TABLE t MODIFY d BIGINT;\nALTER TABLE t MODIFY (e NUMBER(5) NULL);");
        assertEquals(List.of(), r.unreadable());
        Map<String, Column> cols = r.tables().get(0).columns().stream().collect(Collectors.toMap(Column::name, c -> c));
        Column a = cols.get("a");
        assertEquals(100L, a.length());
        assertFalse(a.nullable());
        assertTrue(a.defaultValue().contains("x"), a.defaultValue());
        Column b = cols.get("b");
        assertEquals("BIGINT", b.nativeType().toUpperCase(Locale.ROOT));
        assertTrue(b.nullable());
        Map<String, String> notes = r.notes().getOrDefault("T", Map.of());
        assertEquals(DdlReader.AMBIGUOUS, notes.get("B"), notes.toString());
        assertTrue(cols.get("c").nullable());
        assertNull(cols.get("c").defaultValue());
        assertFalse(notes.containsKey("C") || notes.containsKey("D"), notes.toString());
        assertTrue(cols.get("e").nullable());
        // 1-106 ① — DEFAULT NULL 의 NULL 은 「NULL 을 적었다」 가 아니다(괄호 꼴 MODIFY 는 옛 NOT NULL 을 남긴다)
        DdlReader.Result dn = DdlReader.read("CREATE TABLE t (a VARCHAR2(10) NOT NULL);\nALTER TABLE t MODIFY (a VARCHAR2(20) DEFAULT NULL);");
        assertFalse(dn.tables().get(0).columns().get(0).nullable());
    }

    /** 1-109(A-22·A-29·A-35·A-08) — 큰 크기 자릿수 · 함수·괄호·N'' 기본값 식 · GENERATED BY DEFAULT · 공백 든 따옴표 이름 · 자동증가 낱말 */
    @Test
    void sizesDefaultsQuotedNamesAuto() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a NUMBER(99999999999999999999), b VARCHAR(99999999999999999999), "
                + "c DATE DEFAULT TO_DATE('2020-01-01','YYYY-MM-DD') NOT NULL, d DATETIME DEFAULT (getdate()), e INT DEFAULT ((0)), "
                + "f NVARCHAR(5) DEFAULT N'abc', g INT GENERATED BY DEFAULT AS IDENTITY, h INT GENERATED ALWAYS AS IDENTITY, i INT AUTO_INCREMENT, "
                + "j INT IDENTITY(1,1), \"Unit Price\" INT, k SERIAL)");
        assertEquals(List.of(), r.unreadable());
        List<Column> cols = r.tables().get(0).columns();
        assertEquals(12, cols.size());
        Map<String, Column> by = cols.stream().collect(Collectors.toMap(Column::name, c -> c));
        assertEquals("TO_DATE('2020-01-01','YYYY-MM-DD')", by.get("c").defaultValue());
        assertFalse(by.get("c").nullable());
        assertEquals("(getdate())", by.get("d").defaultValue());
        assertEquals("((0))", by.get("e").defaultValue());
        assertEquals("N'abc'", by.get("f").defaultValue());
        assertNull(by.get("g").defaultValue());
        assertNull(by.get("h").defaultValue());
        for (String n : List.of("g", "h", "i", "j", "k")) {
            assertTrue(by.get(n).autoIncrement(), n);
        }
        assertFalse(by.get("a").autoIncrement() || by.get("c").autoIncrement());
        assertEquals("Unit Price", cols.get(10).name());
    }

    /** 1-106 ②③ — notes 열쇠는 스키마.표 — 다른 스키마 같은 이름 표가 서로의 경고를 안 받는다. 방언 모호는 다른 notes 에 묻히지 않는다 */
    @Test
    void notesArePerSchemaAndMerge() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE s1.emp (a INT NOT NULL);\nCREATE TABLE s2.emp (a INT NOT NULL);\nALTER TABLE s2.emp MODIFY a BIGINT;");
        assertEquals(java.util.Set.of("S2.EMP"), r.notes().keySet(), r.notes().toString());
        assertTrue(r.notes().get("S2.EMP").get("A").contains(DdlReader.AMBIGUOUS), r.notes().toString());
        assertEquals("S2.EMP", DdlReader.noteKey("s2", "emp"));
        assertEquals("EMP", DdlReader.noteKey(null, "emp"));
    }

    /** 1-100 — ADD KEY·DEFAULT … FOR 를 읽고, DROP·CHANGE 뒤 유니크·인덱스가 지운·옛 컬럼을 안 가리킨다 */
    @Test
    void alterDropRenameFixesKeys() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (id INT, a INT, b INT, old_nm VARCHAR(10), PRIMARY KEY (id), UNIQUE (a, b), "
                + "KEY ix_nm (old_nm), KEY ix_a (a));\nALTER TABLE t ADD KEY ix_b (b);\nALTER TABLE t ADD CONSTRAINT df_b DEFAULT (0) FOR b;\n"
                + "ALTER TABLE t CHANGE old_nm nm VARCHAR(20);\nALTER TABLE t DROP COLUMN a;");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals(List.of(List.of("b")), t.uniques().stream().map(UniqueKey::columns).toList());
        assertEquals(List.of("ix_nm=[nm]", "ix_b=[b]"), t.indexes().stream().map(x -> x.name() + "=" + x.columns()).toList());
        assertTrue(t.columns().stream().filter(c -> "b".equals(c.name())).findFirst().orElseThrow().defaultValue().contains("0"));
        // 마무리 리뷰 I2 — 이름 없는 MSSQL 꼴(SSMS) 도 기본값으로 읽고 가짜 컬럼 DEFAULT 를 안 만든다
        DdlReader.Result bare = DdlReader.read("CREATE TABLE u (id INT, b INT);\nALTER TABLE u ADD DEFAULT ((0)) FOR [b];");
        assertEquals(List.of(), bare.unreadable());
        Table u = bare.tables().get(0);
        assertEquals(List.of("id", "b"), u.columns().stream().map(Column::name).toList());
        assertTrue(u.columns().get(1).defaultValue().contains("0"), u.columns().toString());
    }

    /** 1-94(A-02) — 같은 이름 표가 다른 스키마에 있으면 둘 다 남고, 스키마 붙은 COMMENT·ALTER 는 그 표로 간다 */
    @Test
    void sameNameInTwoSchemas() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE s1.emp (id INT);\nCREATE TABLE s2.emp (id INT, nm VARCHAR(10));\n"
                + "COMMENT ON TABLE s2.emp IS '둘째';\nALTER TABLE s2.emp ADD CONSTRAINT uq UNIQUE (nm);");
        assertEquals(List.of(), r.unreadable());
        assertEquals(List.of("s1.emp:1:null:0", "s2.emp:2:둘째:1"), r.tables().stream()
                .map(t -> t.schema() + "." + t.name() + ":" + t.columns().size() + ":" + t.comment() + ":" + t.uniques().size()).toList());
        DdlReader.Result same = DdlReader.read("CREATE TABLE s1.emp (id INT);\nCREATE TABLE s1.emp (id INT, nm VARCHAR(10));");
        assertEquals(List.of(2), same.tables().stream().map(t -> t.columns().size()).toList(), "같은 스키마·같은 이름은 뒤 것 하나");
        // 1-104 — 맨 이름 ALTER 는 스키마 없는 둘째 표로
        DdlReader.Result bare = DdlReader.read("CREATE TABLE s1.emp (id INT);\nCREATE TABLE emp (id INT);\nALTER TABLE emp ADD c INT;");
        assertEquals(List.of("s1:1", ":2"), bare.tables().stream().map(t -> t.schema() + ":" + t.columns().size()).toList());
    }

    /** 1-93(A-30) — 문장이 열린 채 줄이 SET 으로 시작하면 문장의 일부다. 문장 사이 SET 줄은 지금처럼 지운다 */
    @Test
    void setNullInsideStatementIsKept() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE p (id INT PRIMARY KEY);\nCREATE TABLE c (id INT, pid INT);\n"
                + "ALTER TABLE c ADD CONSTRAINT fk FOREIGN KEY (pid) REFERENCES p (id) ON DELETE\nSET NULL;\nCREATE TABLE z (id INT);");
        assertEquals(List.of(), r.unreadable());
        assertEquals(List.of("p", "c", "z"), r.tables().stream().map(Table::name).toList());
        assertEquals(1, r.tables().get(1).fks().size());
        DdlReader.Result between = DdlReader.read("CREATE TABLE a (id INT);\nSET NULL ''\nSET ECHO ON\nCREATE TABLE b (id INT);");
        assertEquals(List.of(), between.unreadable());
        assertEquals(List.of("a", "b"), between.tables().stream().map(Table::name).toList());
    }

    /** V-4 실물 표본에서 드러남 — SQL*Plus 스크립트(Oracle 샘플 스키마)의 명령 줄이 CREATE 를 가려 0 테이블로 읽었다 */
    @Test
    void sqlPlusCommandLinesAreSkipped() {
        DdlReader.Result r = DdlReader.read("SET ECHO OFF\nSET DEFINE OFF\nrem 고객 표\nPrompt ****** Creating CUSTOMERS table ....\n\n"
                + "CREATE TABLE customers (\n  id INTEGER,\n  name VARCHAR2(20)\n)\n/\n"
                + "@@other_script.sql\nPROMPT next\nCREATE TABLE stores (id INTEGER);\n"
                + "UPDATE stores\nSET id = 1;\n");
        assertEquals(List.of("customers", "stores"), r.tables().stream().map(t -> t.name()).toList());
        assertEquals(List.of("id", "name"), r.tables().get(0).columns().stream().map(c -> c.name()).toList(), "/ 줄은 문장 끝");
    }

    /** V-29 실물 표본(RuoYi)에서 드러남 — CREATE TABLE 괄호 안 컬럼 줄 remark·host·column 은 SQL*Plus 명령이 아니다 */
    @Test
    void sqlPlusWordsInsideTableAreColumns() {
        DdlReader.Result r = DdlReader.read("rem 머리\ncreate table sys_post (\n  post_id bigint(20) not null,\n  host varchar(10),\n"
                + "  column_x int,\n  remark varchar(500) default null comment '备注',\n  primary key (post_id)\n) engine=innodb comment = '岗位信息表';\n"
                + "REMARK 끝\n");
        assertEquals(List.of("post_id", "host", "column_x", "remark"), r.tables().get(0).columns().stream().map(c -> c.name()).toList());
    }

    /** V-4 — 원본 오타로 CREATE 앞에 글이 붙은 문장(eGov PG DDL 「;s」)은 조용히 버리지 않고 unreadable 로 */
    @Test
    void buriedCreateIsReportedNotDropped() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE a (x INT);s\n\n/* 뉴스 */\nCREATE TABLE b (y INT);");
        assertEquals(List.of("a"), r.tables().stream().map(t -> t.name()).toList());
        assertEquals(1, r.unreadable().size());
        assertEquals("s", r.unreadable().get(0).line());
    }

    @Test
    void quotedNamesInlinePkAndSizes() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE IF NOT EXISTS `s`.`t_a` (\n `a` INT NOT NULL,\n [b] VARCHAR2(30 BYTE),\n"
                + " \"c\" NUMBER(12, 2) DEFAULT 0,\n d TIMESTAMP(6) WITH TIME ZONE,\n PRIMARY KEY (`a`)\n) COMMENT='표 ''a''';");
        Table t = r.tables().get(0);
        assertEquals("s", t.schema());
        assertEquals("t_a", t.name());
        assertEquals("표 'a'", t.comment());
        assertEquals(List.of("a", "b", "c", "d"), t.columns().stream().map(Column::name).toList());
        assertEquals(List.of("a"), t.pk().columns());
        assertEquals(30L, t.columns().get(1).length());
        assertEquals(12, t.columns().get(2).precision());
        assertEquals(2, t.columns().get(2).scale());
        assertEquals("0", t.columns().get(2).defaultValue());
        assertEquals("TIMESTAMP WITH TIME ZONE", t.columns().get(3).nativeType());
    }

    @Test
    void quotedNameWithSpacesAndNoCatastrophicBacktracking() {
        Table t = DdlReader.read("CREATE TABLE \"my schema\" . [my table] (A INT)").tables().get(0);
        assertEquals("my schema", t.schema());
        assertEquals("my table", t.name());
        String attack = "CREATE TABLE " + "!.".repeat(5000) + " x";
        long start = System.nanoTime();
        assertEquals(0, DdlReader.read(attack).tables().size());
        assertTrue(System.nanoTime() - start < 2_000_000_000L, "CodeQL 이 짚은 입력이 바로 끝난다");
        String longText = "가".repeat(20_000);
        DdlReader.Result r = DdlReader.read("CREATE TABLE T (A INT COMMENT '" + longText + "', B CHAR(1) DEFAULT '" + longText + "') COMMENT='"
                + longText + "'; COMMENT ON COLUMN T.B IS '" + longText + "'");
        assertEquals(longText, r.tables().get(0).comment(), "긴 문자열에서도 스택이 안 넘친다");
        assertEquals(longText, r.tables().get(0).columns().get(0).comment());
        assertEquals(longText, r.tables().get(0).columns().get(1).comment());
    }

    @Test
    void unreadableLineIsReportedNotDropped() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a INT, 123bad stuff, b VARCHAR(5))");
        assertEquals(1, r.unreadable().size());
        assertEquals("123bad stuff", r.unreadable().get(0).line());
        Table t = r.tables().get(0);
        assertEquals(List.of("a", "unreadable2", "b"), t.columns().stream().map(Column::name).toList(), "자리를 지킨다");
        assertNull(t.columns().get(1).nativeType());
        assertNotNull(r.notes().get("T").get("UNREADABLE2"));
        assertTrue(r.notes().get("T").get("UNREADABLE2").startsWith("TODO 못 읽음: 123bad"));
    }

    @Test
    void commentsAndStringsDoNotConfuseSplitting() {
        DdlReader.Result r = DdlReader.read("-- 머리 주석; 여기도\nCREATE TABLE x ( /* 블록, 주석 */ a CHAR(1) DEFAULT ',' NOT NULL, b INT );\n"
                + "COMMENT ON COLUMN x.a IS '쉼표, 그리고 ; 세미콜론';\nGO\n");
        Table t = r.tables().get(0);
        assertEquals(2, t.columns().size());
        assertEquals("','", t.columns().get(0).defaultValue());
        assertFalse(t.columns().get(0).nullable());
        assertEquals("쉼표, 그리고 ; 세미콜론", t.columns().get(0).comment());
    }

    private static String fks(DdlReader.Result r, String table) {
        return r.tables().stream().filter(t -> t.name().equalsIgnoreCase(table)).findFirst().orElseThrow().fks().stream()
                .map(f -> f.name() + ":" + f.columns() + "->" + (f.refSchema() == null ? "" : f.refSchema() + ".") + f.refTable()
                        + f.refColumns())
                .collect(Collectors.joining(" | "));
    }

    /** 1-10 — eGov 꼴 제약 줄(이름 없음)·HR 꼴 이름 있는 제약 줄·컬럼 REFERENCES */
    @Test
    void readsInlineForeignKeys() {
        DdlReader.Result r = DdlReader.read("""
                CREATE TABLE COMTCCMMNCODE (
                  CODE_ID VARCHAR(6) NOT NULL,
                  CL_CODE CHAR(3),
                  PRIMARY KEY (CODE_ID),
                  FOREIGN KEY (CL_CODE) REFERENCES COMTCCMMNCLCODE(CL_CODE)
                );
                CREATE TABLE COMTCCMMNDETAILCODE (
                  CODE_ID VARCHAR(6) NOT NULL,
                  FOREIGN KEY COMTCCMMNDETAILCODE_FK1 (CODE_ID) REFERENCES COMTCCMMNCODE(CODE_ID)
                );
                CREATE TABLE emp (
                  id NUMBER(6) PRIMARY KEY,
                  dept_id NUMBER(4) REFERENCES dept,
                  job_id VARCHAR2(10),
                  loc_a NUMBER, loc_b NUMBER,
                  CONSTRAINT emp_job_fk FOREIGN KEY (job_id) REFERENCES jobs (job_id),
                  CONSTRAINT emp_loc_fk FOREIGN KEY (loc_a, loc_b) REFERENCES hr.locs (a, b)
                );
                """);
        assertEquals("null:[CL_CODE]->COMTCCMMNCLCODE[CL_CODE]", fks(r, "COMTCCMMNCODE"));
        assertEquals("COMTCCMMNDETAILCODE_FK1:[CODE_ID]->COMTCCMMNCODE[CODE_ID]", fks(r, "COMTCCMMNDETAILCODE"));
        assertEquals("null:[dept_id]->dept[] | emp_job_fk:[job_id]->jobs[job_id] | emp_loc_fk:[loc_a, loc_b]->hr.locs[a, b]",
                fks(r, "emp"));
        assertEquals(5, r.tables().get(2).columns().size());
    }

    /** 1-10 — chinook 꼴 ALTER(이름 따옴표 넷)·HR 꼴 `ADD ( CONSTRAINT …, CONSTRAINT … )` */
    @Test
    void readsAlterForeignKeys() {
        DdlReader.Result r = DdlReader.read("""
                CREATE TABLE `Album` (`AlbumId` INT NOT NULL, `ArtistId` INT NOT NULL, CONSTRAINT `PK_Album` PRIMARY KEY (`AlbumId`));
                CREATE TABLE "track" ("id" INT, "album_id" INT, "genre_id" INT);
                ALTER TABLE `Album` ADD CONSTRAINT `FK_AlbumArtistId`
                    FOREIGN KEY (`ArtistId`) REFERENCES `Artist` (`ArtistId`) ON DELETE NO ACTION ON UPDATE NO ACTION;
                ALTER TABLE "track"
                ADD ( CONSTRAINT track_album_fk FOREIGN KEY ("album_id") REFERENCES "album" ("id"),
                      CONSTRAINT track_genre_fk FOREIGN KEY ("genre_id") REFERENCES "genre" ("id") ) ;
                ALTER TABLE nowhere ADD CONSTRAINT x FOREIGN KEY (a) REFERENCES b (a);
                """);
        assertEquals("FK_AlbumArtistId:[ArtistId]->Artist[ArtistId]", fks(r, "Album"));
        assertEquals("track_album_fk:[album_id]->album[id] | track_genre_fk:[genre_id]->genre[id]", fks(r, "track"));
        assertEquals(List.of("AlbumId"), r.tables().get(0).pk().columns());
    }

    /** 1-10 — MSSQL 대괄호·같은 스키마 접두는 refSchema 없이, `WITH CHECK ADD` · GO 묶음 */
    @Test
    void readsBracketedForeignKeys() {
        DdlReader.Result r = DdlReader.read("""
                CREATE TABLE [dbo].[Album] ([AlbumId] INT NOT NULL, [ArtistId] INT NOT NULL, [OwnerId] INT NULL)
                GO
                ALTER TABLE [dbo].[Album] ADD CONSTRAINT [FK_AlbumArtistId]
                    FOREIGN KEY ([ArtistId]) REFERENCES [dbo].[Artist] ([ArtistId]) ON DELETE NO ACTION ON UPDATE NO ACTION
                GO
                ALTER TABLE [dbo].[Album] WITH CHECK ADD CONSTRAINT [FK_AlbumOwner] FOREIGN KEY([OwnerId]) REFERENCES [sec].[Owner] ([Id])
                GO
                """);
        assertEquals("FK_AlbumArtistId:[ArtistId]->Artist[ArtistId] | FK_AlbumOwner:[OwnerId]->sec.Owner[Id]", fks(r, "Album"));
        assertEquals("dbo", r.tables().get(0).schema());
    }

    /** 1-31a — 컬럼 안 CHECK 와 표 단위 CONSTRAINT … CHECK 를 Table.checks 로(이름·조건 글) */
    @Test
    void readsChecks() {
        Table t = DdlReader.read("CREATE TABLE T (A INT CHECK (A > 0), B CHAR(1) NOT NULL, C VARCHAR(10),"
                + " CONSTRAINT CK_B CHECK (B IN ('Y', 'N')), CONSTRAINT FK_C FOREIGN KEY (C) REFERENCES P (C))").tables().get(0);
        assertEquals(List.of(new kr.ejg.toolbox.core.meta.Check(null, "A > 0"), new kr.ejg.toolbox.core.meta.Check("CK_B", "B IN ('Y', 'N')")),
                t.checks());
        assertEquals(3, t.columns().size());
        assertEquals(1, t.fks().size());
    }

    /** 4-26 — UNIQUE 를 인라인·표 수준(CONSTRAINT 이름·MySQL KEY 이름·이름 없음)·ALTER 에서 읽는다. 타입에 안 섞인다 */
    @Test
    void readsUniqueConstraints() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a INT UNIQUE, b VARCHAR(3), c CHAR(1) COMMENT 'UNIQUE 아님', d INT,"
                + " CONSTRAINT uq_bc UNIQUE (b, c), UNIQUE KEY ix_d (d), UNIQUE NONCLUSTERED ([b]));\n"
                + "ALTER TABLE t ADD CONSTRAINT uq_c UNIQUE (c);\n");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals(List.of("null:[a]", "uq_bc:[b, c]", "ix_d:[d]", "null:[b]", "uq_c:[c]"),
                t.uniques().stream().map(u -> u.name() + ":" + u.columns()).toList());
        assertEquals(List.of("INT", "VARCHAR", "CHAR", "INT"), t.columns().stream().map(Column::nativeType).toList());
    }

    /** 4-25a — ENUM·SET 은 나열 값까지 원문 그대로 타입에(길이 없음). 다른 칸은 그대로 읽는다 */
    @Test
    void readsEnumAndSetValues() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (id INT PRIMARY KEY, st ENUM('A','B','C') NOT NULL DEFAULT 'A' COMMENT '상태',"
                + " fl SET('X', 'Y'), nm VARCHAR(10))");
        assertEquals(List.of(), r.unreadable());
        List<Column> c = r.tables().get(0).columns();
        assertEquals(List.of("INT", "ENUM('A','B','C')", "SET('X', 'Y')", "VARCHAR"), c.stream().map(Column::nativeType).toList());
        assertNull(c.get(1).length());
        assertFalse(c.get(1).nullable());
        assertEquals("'A'", c.get(1).defaultValue());
        assertEquals("상태", c.get(1).comment());
        assertEquals(10L, c.get(3).length());
    }

    /** 마무리 리뷰 — 나열 값 안의 'not null'·'check'·'unique' 글이 타입을 자르거나 속성으로 읽히지 않는다. 따옴표 글 안 「UNIQUE (x)」 도 키가 아니다 */
    @Test
    void enumValuesAndQuotedTextDoNotLeak() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (st ENUM('pending check','not null','unique') DEFAULT 'pending check',"
                + " nm VARCHAR(10) COMMENT \"고유 UNIQUE 번호\", cd INT, KEY ix (cd) COMMENT 'UNIQUE (nm) 설명')");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals("ENUM('pending check','not null','unique')", t.columns().get(0).nativeType());
        assertTrue(t.columns().get(0).nullable(), "나열 값 'not null' 은 NOT NULL 이 아니다");
        assertEquals("'pending check'", t.columns().get(0).defaultValue());
        assertEquals(List.of(), t.uniques(), "나열 값·코멘트 글의 UNIQUE 는 키가 아니다");
        assertEquals(List.of(), t.checks());
    }

    /** 1-66(설계 46) — CTAS 는 컬럼 0 표 + notes ctas. unreadable 로 빼지 않는다(사용자 2026-10-31) */
    @Test
    void ctasIsTableWithoutColumns() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE T2 AS SELECT A FROM T1");
        assertEquals(List.of("T2"), r.tables().stream().map(Table::name).toList());
        assertEquals(List.of(), r.tables().get(0).columns());
        assertNotNull(r.notes().get("T2").get(CTAS));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-66 — CTAS 꼴 일곱(MariaDB 의 AS 없는 SELECT · AS (WITH …) · Oracle NOLOGGING·STORAGE·ON COMMIT · 컬럼 이름 목록) */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "CREATE OR REPLACE TEMPORARY TABLE `db`.T AS SELECT * FROM X",
        "CREATE TABLE T SELECT * FROM X",
        "CREATE TABLE T AS (WITH C AS (SELECT 1) SELECT * FROM C)",
        "CREATE TABLE T NOLOGGING AS SELECT * FROM X",
        "CREATE TABLE T STORAGE (INITIAL 1M) AS SELECT * FROM X",
        "CREATE GLOBAL TEMPORARY TABLE T ON COMMIT PRESERVE ROWS AS SELECT * FROM X",
        "CREATE TABLE T (C1, C2) AS SELECT A, B FROM X"})
    void ctasShapes(String ddl) {
        DdlReader.Result r = DdlReader.read(ddl);
        assertEquals(List.of("T"), r.tables().stream().map(Table::name).toList(), ddl);
        assertEquals(List.of(), r.tables().get(0).columns(), ddl);
        assertEquals(List.of(), r.unreadable(), ddl);
    }

    /** 1-66 — PG PARTITION OF 는 컬럼 0 표 + notes partition */
    @Test
    void partitionOf() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE P1 PARTITION OF P FOR VALUES FROM (1) TO (10);\n"
                +"CREATE TABLE P2 PARTITION OF P (CONSTRAINT X CHECK (A > 0)) FOR VALUES IN ('a');");
        assertEquals(List.of("P1", "P2"), r.tables().stream().map(Table::name).toList());
        assertNotNull(r.notes().get("P1").get("partition"));
        assertNotNull(r.notes().get("P2").get("partition"));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-66 — Oracle 18c PRIVATE TEMPORARY 는 보통 표처럼 컬럼까지 */
    @Test
    void privateTemporary() {
        DdlReader.Result r = DdlReader.read("CREATE PRIVATE TEMPORARY TABLE ORA$PTT_X (ID NUMBER)");
        assertEquals(List.of("ORA$PTT_X"), r.tables().stream().map(Table::name).toList());
        assertEquals(1, r.tables().get(0).columns().size());
    }

    /** 1-66 — ; 없이 이어진 CREATE(tsql 파일)는 줄머리 CREATE 에서 가른다. 「;s」 꼴은 buriedCreateIsReportedNotDropped 그대로 */
    @Test
    void consecutiveCreatesWithoutSemicolon() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE A (X INT)\n\nCREATE TABLE B (Y INT)");
        assertEquals(List.of("A", "B"), r.tables().stream().map(Table::name).toList());
        assertEquals(1, r.tables().get(1).columns().size());
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-66 음성 — 계산 컬럼의 AS (식) 는 CTAS 가 아니다. 따옴표 글 안 「as select」 도 */
    @Test
    void normalCreateUnchanged() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE T (ID INT GENERATED ALWAYS AS (1) STORED, NM VARCHAR(10) DEFAULT 'as select')");
        assertEquals(2, r.tables().get(0).columns().size());
        assertNull(r.notes().get("T"));
    }

    /** 1-67(설계 47 §3-18) — CREATE TABLE y LIKE x 는 컬럼 0 표 + notes like. unreadable 로 빼지 않는다 */
    @Test
    void likeHead() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE Y LIKE X");
        assertEquals(List.of("Y"), r.tables().stream().map(Table::name).toList());
        assertEquals(List.of(), r.tables().get(0).columns());
        assertNotNull(r.notes().get("Y").get(LIKE));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-67 — PG·MySQL 의 괄호 안 LIKE x [INCLUDING …] 도 같다 */
    @Test
    void likeInParens() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE Y (LIKE X INCLUDING ALL)");
        assertEquals(List.of("Y"), r.tables().stream().map(Table::name).toList());
        assertEquals(List.of(), r.tables().get(0).columns());
        assertNotNull(r.notes().get("Y").get(LIKE));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-67 음성 — LIKE 로 시작하는 컬럼 이름은 컬럼이다 */
    @Test
    void likeColumnNameStillColumn() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE T (LIKE_CNT INT)");
        assertEquals(List.of("LIKE_CNT"), r.tables().get(0).columns().stream().map(c -> c.name()).toList());
        assertNull(r.notes().get("T"));
    }

    /** 1-68 — CREATE [UNIQUE] INDEX · 표 안 KEY/INDEX. ASC·DESC 는 떼고 함수식은 글 그대로, FULLTEXT 는 안 읽는다. 유니크는 uniques 에도 */
    @Test
    void readsCreateIndexAndInlineKey() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a INT, b VARCHAR(10), KEY k_b (b), FULLTEXT KEY ft (b));\n"
                + "CREATE INDEX i_ab ON t (a ASC, b DESC);\nCREATE UNIQUE INDEX u_a ON t (a);\nCREATE INDEX i_up ON t (UPPER(b));\n"
                + "CREATE INDEX i_none ON nowhere (x);");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals(List.of("k_b:false:[b]", "i_ab:false:[a, b]", "u_a:true:[a]", "i_up:false:[UPPER(b)]"),
                t.indexes().stream().map(x -> x.name() + ":" + x.unique() + ":" + x.columns()).toList());
        assertEquals(List.of("u_a"), t.uniques().stream().map(u -> u.name()).toList());
        assertEquals(1, r.tables().size(), "앞에 없는 표의 인덱스는 버린다");
    }

    /** 1-92(A-01) — KEY·INDEX·PERIOD 라는 이름의 컬럼은 컬럼이다. 뒤가 인덱스 꼴일 때만 제약 줄 */
    @Test
    void keyNamedColumnsAreColumns() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE conf (id INT, KEY VARCHAR(50), PERIOD INT, INDEX INT NOT NULL, KEY2 INT, KEY ENUM('a','b'),"
                + " KEY ix_a (id), INDEX ix_b (id) USING BTREE, PERIOD FOR SYSTEM_TIME (s, e));");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals(List.of("id", "KEY", "PERIOD", "INDEX", "KEY2", "KEY"), t.columns().stream().map(Column::name).toList());
        assertEquals(List.of("ix_a", "ix_b"), t.indexes().stream().map(x -> x.name()).toList());
        // 1-104 — 괄호 안이 *·「낱말, 숫자)」·MAX) 면 타입 크기
        DdlReader.Result r2 = DdlReader.read("CREATE TABLE conf2 (id INT, KEY NUMBER(*,0), INDEX geometry(Point,4326), KEY ix_t (id));\n"
                + "CREATE TABLE conf3 (KEY VARCHAR(MAX), max INT, KEY ix_max (max));");
        assertEquals(List.of(), r2.unreadable());
        assertEquals(List.of("id", "KEY", "INDEX"), r2.tables().get(0).columns().stream().map(Column::name).toList());
        String key = t.columns().get(1).name();
        assertEquals(List.of("ix_t"), r2.tables().get(0).indexes().stream().map(x -> x.name()).toList());
        assertEquals(List.of(key, "max"), r2.tables().get(1).columns().stream().map(Column::name).toList());
        assertEquals(List.of("ix_max"), r2.tables().get(1).indexes().stream().map(x -> x.name()).toList());
    }

    /** 1-68 — HR 꼴: ALTER TABLE … ADD ( CONSTRAINT … PRIMARY KEY … , CONSTRAINT … FOREIGN KEY … ). PK 이름과 같은 유니크 인덱스는 뺀다 */
    @Test
    void alterAddParenthesizedPrimaryKey() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE r (id INT, n VARCHAR(10));\nCREATE UNIQUE INDEX r_pk ON r (id);\n"
                + "CREATE TABLE c (id INT, r_id INT);\n"
                + "ALTER TABLE c ADD ( CONSTRAINT c_pk PRIMARY KEY (id) , CONSTRAINT c_r_fk FOREIGN KEY (r_id) REFERENCES r(id) );\n"
                + "ALTER TABLE r ADD ( CONSTRAINT r_pk PRIMARY KEY (id) );");
        Table rt = r.tables().get(0);
        Table ct = r.tables().get(1);
        assertEquals(List.of("ID"), ct.pk().columns().stream().map(x -> x.toUpperCase(Locale.ROOT)).toList());
        assertEquals(1, ct.fks().size());
        assertEquals(List.of("id"), rt.pk().columns());
        assertEquals(List.of(), rt.indexes(), "PK 이름 인덱스는 뺀다");
        assertEquals(List.of(), rt.uniques());
        assertFalse(rt.columns().get(0).nullable());
        assertEquals("r_pk", rt.pk().name(), "1-70 — ALTER 의 PK 이름");
        assertEquals("c_pk", ct.pk().name());
    }

    /** 1-70 — PK 이름은 표 몸 제약 줄·컬럼 줄 CONSTRAINT·ALTER 셋에서. 이름 없으면 null */
    @Test
    void pkNameFromBodyColumnAndAlter() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE a (id INT, CONSTRAINT pk_a PRIMARY KEY (id)); CREATE TABLE b (id INT CONSTRAINT pk_b PRIMARY KEY); "
                + "CREATE TABLE c (id INT PRIMARY KEY); CREATE TABLE d (id INT, PRIMARY KEY (id)); "
                + "CREATE TABLE e ([id] INT, CONSTRAINT [PK_e] PRIMARY KEY CLUSTERED ([id]));");
        assertEquals(List.of(), r.unreadable());
        assertEquals(java.util.Arrays.asList("pk_a", "pk_b", null, null, "PK_e"), r.tables().stream().map(t -> t.pk().name()).toList());
        assertEquals(List.of(List.of("id"), List.of("id"), List.of("id"), List.of("id"), List.of("id")), r.tables().stream().map(t -> t.pk().columns()).toList());
    }

    /** 1-70 — 접두 길이 (n) 는 괄호 앞이 그 표의 컬럼일 때만 뗀다. 함수식 f(10)·LEFT(b, 10) 은 글 그대로 */
    @Test
    void prefixLengthOnlyForColumns() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a INT, b VARCHAR(30)); CREATE INDEX i6 ON t (f(10)); CREATE INDEX i7 ON t (b(10)); "
                + "CREATE INDEX i8 ON t (LEFT(b, 10));");
        assertEquals(List.of(), r.unreadable());
        assertEquals(List.of("i6:[f(10)]", "i7:[b]", "i8:[LEFT(b, 10)]"), r.tables().get(0).indexes().stream().map(x -> x.name() + ":" + x.columns()).toList());
    }

    /** 7-18b 음성 — 표 안 UNIQUE KEY·SPATIAL INDEX·FULLTEXT INDEX 는 indexes 가 아니다(UNIQUE KEY 는 uniques) */
    @Test
    void inlineUniqueAndSpatialAreNotIndexes() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a INT, g GEOMETRY, UNIQUE KEY uk_a (a), SPATIAL INDEX sp_g (g), FULLTEXT INDEX ft_a (a))");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals(List.of(), t.indexes());
        assertEquals(List.of("uk_a"), t.uniques().stream().map(u -> u.name()).toList());
    }

    /** 7-18b 양성 — (NON)CLUSTERED·CONCURRENTLY·ON ONLY 머리, 컬럼 끝 NULLS 단독·MySQL 접두 길이 (n) 를 뗀다 */
    @Test
    void indexHeadsAndColumnDecorations() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a INT, b VARCHAR(30), c INT);\nCREATE NONCLUSTERED INDEX i1 ON t (a);\n"
                + "CREATE INDEX CONCURRENTLY i2 ON ONLY t (b);\nCREATE INDEX i3 ON t (c NULLS LAST);\nCREATE INDEX i4 ON t (b(10));\n"
                + "CREATE INDEX i5 ON t (a DESC NULLS FIRST, b(5));");
        assertEquals(List.of(), r.unreadable());
        assertEquals(List.of("i1:[a]", "i2:[b]", "i3:[c]", "i4:[b]", "i5:[a, b]"),
                r.tables().get(0).indexes().stream().map(x -> x.name() + ":" + x.columns()).toList());
    }

    /** 1-69 — PG typed table CREATE TABLE x OF 형식 [(…)] 은 컬럼 0 표 + notes typed */
    @Test
    void typedTable() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE employees OF employee_type (PRIMARY KEY (name));\nCREATE TABLE t2 OF typ2;");
        assertEquals(List.of("employees", "t2"), r.tables().stream().map(Table::name).toList());
        assertEquals(List.of(List.of(), List.of()), r.tables().stream().map(Table::columns).toList());
        assertNotNull(r.notes().get("EMPLOYEES").get(TYPED));
        assertNotNull(r.notes().get("T2").get(TYPED));
        assertEquals(List.of(), r.unreadable());
        assertEquals(List.of(EMP_NAME), r.tables().get(0).pk().columns(), "1-71 — 형식이 없어도 몸의 PK 는 읽는다");
    }

    static List<String> colNames(Table t) {
        return t.columns().stream().map(Column::name).toList();
    }

    /** 1-71 — 같은 입력의 CREATE TYPE 속성을 끌어온다(PG AS · Oracle OID·AS OBJECT·UNDER·MEMBER 줄). 끌어오면 notes 없음 */
    @Test
    void typedTableFromType() {
        DdlReader.Result pg = DdlReader.read("CREATE TYPE employee_type AS (name text, salary numeric); CREATE TABLE employees OF employee_type "
                + "(name WITH OPTIONS NOT NULL, PRIMARY KEY (name), salary WITH OPTIONS DEFAULT 1000);");
        Table e = pg.tables().get(0);
        assertEquals(List.of("name", "salary"), colNames(e));
        assertFalse(e.columns().get(0).nullable());
        assertEquals(List.of(EMP_NAME), e.pk().columns());
        assertEquals("1000", e.columns().get(1).defaultValue(), "1-79 — typed WITH OPTIONS DEFAULT 값");
        assertNull(pg.notes().get("EMPLOYEES"));
        assertEquals(List.of(), pg.unreadable());

        DdlReader.Result ora = DdlReader.read("CREATE TYPE cat_typ OID 'X' AS OBJECT (id NUMBER(2), nm VARCHAR2(50), NOT INSTANTIABLE MEMBER FUNCTION f RETURN VARCHAR2) "
                + "NOT INSTANTIABLE NOT FINAL; CREATE TYPE leaf_typ UNDER cat_typ (extra VARCHAR2(10), OVERRIDING MEMBER FUNCTION f RETURN VARCHAR2); "
                + "CREATE TABLE cats OF leaf_typ (id PRIMARY KEY) NESTED TABLE x STORE AS y;");
        Table c = ora.tables().get(0);
        assertEquals(List.of("id", "nm", "extra"), colNames(c));
        assertEquals(List.of(1, 2, 3), c.columns().stream().map(Column::ordinal).toList());
        assertEquals(List.of("id"), c.pk().columns());
        assertFalse(c.columns().get(0).nullable());
        assertEquals(List.of(), ora.unreadable());
        assertNull(ora.notes().get("CATS"));

        DdlReader.Result en = DdlReader.read("CREATE TYPE mood AS ENUM ('a','b'); CREATE TABLE t OF mood; CREATE TYPE BODY x AS BEGIN NULL; END; CREATE TABLE u OF x;");
        assertEquals(List.of(List.of(), List.of()), en.tables().stream().map(Table::columns).toList(), "ENUM·TYPE BODY 는 속성 형식이 아니다");
        assertNotNull(en.notes().get("T").get(TYPED));
        assertNotNull(en.notes().get("U").get(TYPED));
    }

    /** 1-71 — LIKE 원본이 같은 입력에 있으면 컬럼 + PK(이름까지). 괄호 안 LIKE 는 그 자리에 */
    @Test
    void likeFromSource() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE X (A INT NOT NULL, B VARCHAR(10) DEFAULT 'x', CONSTRAINT pk_x PRIMARY KEY (A)); CREATE TABLE Y LIKE X; "
                + "CREATE TABLE Z (LIKE X INCLUDING ALL, C INT);");
        Table y = r.tables().get(1);
        Table z = r.tables().get(2);
        assertEquals(List.of("A", "B"), colNames(y));
        assertEquals(List.of("A"), y.pk().columns());
        assertEquals("pk_x", y.pk().name());
        assertEquals("'x'", y.columns().get(1).defaultValue());
        assertNull(r.notes().get("Y"));
        assertEquals(List.of("A", "B", "C"), colNames(z));
        assertEquals(List.of(1, 2, 3), z.columns().stream().map(Column::ordinal).toList());
        assertEquals(List.of("A"), z.pk().columns());
        assertNull(r.notes().get("Z"));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-72 — PG 괄호 안 LIKE 는 마지막 INCLUDING|EXCLUDING INDEXES|ALL 절이 INCLUDING 일 때만 PK(앞 인라인 PK 는 덮임). MySQL 머리 LIKE 는 늘 PK */
    @Test
    void likeIncludingIndexesOnly() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE X (A INT NOT NULL, B INT, CONSTRAINT pk_x PRIMARY KEY (A)); CREATE TABLE Z1 (LIKE X); "
                + "CREATE TABLE Z2 (LIKE X INCLUDING INDEXES); CREATE TABLE Z3 (LIKE X INCLUDING ALL EXCLUDING INDEXES); "
                + "CREATE TABLE Z4 (C INT PRIMARY KEY, LIKE X INCLUDING ALL); CREATE TABLE Z5 (LIKE X INCLUDING CONSTRAINTS); CREATE TABLE Y LIKE X;");
        Map<String, Table> t = r.tables().stream().collect(Collectors.toMap(Table::name, Function.identity()));
        for (String n : List.of("Z1", "Z3", "Z5")) {
            assertNull(t.get(n).pk(), n);
            assertEquals(List.of("A", "B"), colNames(t.get(n)), n);
        }
        for (String n : List.of("Z2", "Z4", "Y")) {
            assertEquals(List.of("A"), t.get(n).pk().columns(), n);
            assertEquals("pk_x", t.get(n).pk().name(), n);
        }
        assertEquals(List.of("C", "A", "B"), colNames(t.get("Z4")));
        assertEquals(List.of("A", "B"), colNames(t.get("Y")));
        assertTrue(r.notes().isEmpty(), r.notes().toString());
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-72 — typed 몸 「컬럼 WITH OPTIONS REFERENCES|CHECK」 도 컬럼 줄처럼 FK·CHECK 를 남긴다. DEFAULT 만 있으면 없음 */
    @Test
    void typedOptionFkAndCheck() {
        DdlReader.Result r = DdlReader.read("CREATE TYPE ty AS (id int, pid int, qty int, status text, note text, memo text); CREATE TABLE p (id INT PRIMARY KEY); "
                + "CREATE TABLE t OF ty (id WITH OPTIONS PRIMARY KEY, pid WITH OPTIONS REFERENCES p(id), qty WITH OPTIONS CHECK (qty > 0), "
                + "status WITH OPTIONS CHECK (status IN ('A','B')), CONSTRAINT ck2 CHECK (id > 0)); "
                + "CREATE TABLE u OF ty (qty WITH OPTIONS DEFAULT 0, note WITH OPTIONS DEFAULT 'PRIMARY KEY', memo WITH OPTIONS DEFAULT 'A''B');");
        Map<String, Table> m = r.tables().stream().collect(Collectors.toMap(Table::name, Function.identity()));
        Table t = m.get("t");
        assertEquals(List.of("id", "pid", "qty", "status", "note", "memo"), colNames(t));
        assertEquals(List.of("id"), t.pk().columns());
        assertFalse(t.columns().get(0).nullable(), "1-79 — PK 는 NOT NULL");
        assertEquals(java.util.Arrays.asList("0", null, "'PRIMARY KEY'", "'A''B'"),
                m.get("u").columns().subList(2, 6).stream().map(Column::defaultValue).toList(), "1-79 — DEFAULT 리터럴 보존(status 는 없음)");
        assertEquals(1, t.fks().size());
        assertEquals(List.of(List.of("pid"), "p", List.of("id")), List.of(t.fks().get(0).columns(), t.fks().get(0).refTable(), t.fks().get(0).refColumns()));
        // 1-74·1-78 — CHECK 식의 따옴표 글은 원문 자리에서 자른다(컬럼 가지와 같은 마스크 규칙)
        assertEquals(List.of(new kr.ejg.toolbox.core.meta.Check(null, "qty > 0"), new kr.ejg.toolbox.core.meta.Check(null, "status IN ('A','B')"),
                new kr.ejg.toolbox.core.meta.Check("ck2", "id > 0")), t.checks());
        assertNull(m.get("u").pk(), "DEFAULT 'PRIMARY KEY' 의 글 안 낱말은 PK 가 아니다 — 사본 매칭이 산다");
        assertEquals(List.of(0, 0), List.of(m.get("u").fks().size(), m.get("u").checks().size()));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-78 — 따옴표 글 안 낱말(PRIMARY KEY·NOT NULL·REFERENCES·CHECK)은 제약이 아니다 — 컬럼 가지·typed 가지 둘 다. 값은 원문에서 자른다 */
    @Test
    void quotedWordsAreNotConstraints() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE p (id INT PRIMARY KEY); CREATE TABLE q (id INT NOT NULL, a VARCHAR(20) DEFAULT 'PRIMARY KEY', "
                + "b VARCHAR(20) DEFAULT 'NOT NULL', c VARCHAR(30) DEFAULT 'REFERENCES p(id)', d VARCHAR(20) DEFAULT 'CHECK (1)', "
                + "e VARCHAR(9) DEFAULT 'x' CHECK (e <> ')'), PRIMARY KEY (id)); CREATE TYPE ty AS (x text, y text); "
                + "CREATE TABLE t OF ty (x WITH OPTIONS DEFAULT 'REFERENCES p(id)' CHECK (x <> ')'), y WITH OPTIONS DEFAULT 'CHECK (1)');");
        Map<String, Table> m = r.tables().stream().collect(Collectors.toMap(Table::name, Function.identity()));
        Table q = m.get("q");
        assertEquals(List.of("id"), q.pk().columns());
        assertEquals(List.of(true, true), List.of(q.columns().get(1).nullable(), q.columns().get(2).nullable()));
        assertEquals(0, q.fks().size());
        assertEquals(List.of(new kr.ejg.toolbox.core.meta.Check(null, "e <> ')'")), q.checks());
        assertEquals(List.of("'PRIMARY KEY'", "'NOT NULL'", "'REFERENCES p(id)'", "'CHECK (1)'", "'x'"),
                q.columns().subList(1, 6).stream().map(Column::defaultValue).toList(), "마스크가 값을 안 깬다");
        Table t = m.get("t");
        assertEquals(0, t.fks().size());
        assertEquals(List.of(new kr.ejg.toolbox.core.meta.Check(null, "x <> ')'")), t.checks());
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-82 — 글 안 COMMENT '…' 는 컬럼 주석이 아니다 — 마스크에서 찾고 값은 원문에서 자른다 */
    @Test
    void inlineCommentIgnoresQuotedText() {
        Table t = DdlReader.read("CREATE TABLE t (a VARCHAR(20) DEFAULT 'x COMMENT ''y''' COMMENT 'real', b VARCHAR(5) DEFAULT 'COMMENT ''no''', "
                + "c INT COMMENT 'it''s');").tables().get(0);
        assertEquals("real", t.columns().get(0).comment());
        assertEquals("'x COMMENT ''y'''", t.columns().get(0).defaultValue(), "1-78 — 값은 원문 그대로");
        assertNull(t.columns().get(1).comment(), "DEFAULT 글 안의 COMMENT");
        assertEquals("it's", t.columns().get(2).comment());
    }

    /** 1-71·1-80·1-81 — CTAS 는 단순 SELECT 만 컬럼: *·t.*·맨 이름(원본 전체에서 하나)·한정 이름·AS 별칭 · FROM 원본 여럿(쉼표·ANSI JOIN, ON·USING 괄호 안 쉼표 포함). 집계·식·서브쿼리·없는 원본·모호한 맨 이름·NATURAL·USING 의 *·t.* 는 이름만 + notes */
    @Test
    void ctasFromSource() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE T1 (A INT NOT NULL, B VARCHAR(10), PRIMARY KEY (A)); CREATE TABLE T2 AS SELECT * FROM T1; "
                + "CREATE TABLE T3 AS SELECT B, t1.A AS A2 FROM T1 t1 WHERE A > 0; CREATE TABLE T4 (C1, C2) AS SELECT A, B FROM T1; "
                + "CREATE TABLE T5 AS SELECT A, COUNT(*) FROM T1 GROUP BY A; CREATE TABLE T6 AS SELECT A FROM T1 JOIN T99 ON 1 = 1; "
                + "CREATE TABLE T0 (K INT, D VARCHAR(5), B INT); "
                + "CREATE TABLE T7 AS SELECT t.A, s.D AS DD, t.B FROM T1 t LEFT OUTER JOIN T0 s ON t.A = s.K WHERE t.A > 0; "
                + "CREATE TABLE T8 AS SELECT A, K FROM T1, T0; CREATE TABLE T9 AS SELECT B FROM T1 t JOIN T0 s ON t.A = s.K; "
                + "CREATE TABLE TA AS SELECT t.* FROM T1 t INNER JOIN T0 s ON t.A = s.K; "
                + "CREATE TABLE TB AS SELECT t.A, s.D FROM T1 t JOIN T0 s USING (K, D); CREATE TABLE TC AS SELECT * FROM T1 NATURAL JOIN T0; "
                + "CREATE TABLE TD AS SELECT t.* FROM T1 t JOIN T0 s USING (B); CREATE TABLE TE AS SELECT t.A FROM T1 t JOIN T0 s ON t.A IN (1, 2); "
                + "CREATE TABLE TF AS SELECT A, UPPER(B) AS UB, COUNT(*) AS N FROM T1 GROUP BY A; CREATE TABLE TG AS SELECT A, B + 1 FROM T1;");
        Map<String, Table> t = r.tables().stream().collect(Collectors.toMap(Table::name, Function.identity()));
        assertEquals(List.of("A", "B"), colNames(t.get("T2")));
        assertNull(t.get("T2").pk(), "CTAS 는 제약을 안 옮긴다");
        assertNull(r.notes().get("T2"));
        assertEquals(List.of("B", "A2"), colNames(t.get("T3")));
        assertEquals(t.get("T1").columns().get(0).nativeType(), t.get("T3").columns().get(1).nativeType(), "원 컬럼 형식 그대로");
        assertFalse(t.get("T3").columns().get(1).nullable());
        assertNull(r.notes().get("T3"));
        assertEquals(List.of("C1", "C2"), colNames(t.get("T4")));
        assertEquals(List.of(), t.get("T5").columns());
        assertNotNull(r.notes().get("T5").get(CTAS));
        assertEquals(List.of(), t.get("T6").columns());
        assertNotNull(r.notes().get("T6").get(CTAS));
        // 1-80 — ANSI·쉼표 조인 · 한정 이름은 그 원본 · 맨 이름은 모든 원본에서 꼭 하나 · t.* 는 그 원본 전부
        assertEquals(List.of("A", "DD", "B"), colNames(t.get("T7")));
        assertEquals("VARCHAR", t.get("T7").columns().get(1).nativeType());
        assertNull(r.notes().get("T7"));
        assertEquals(List.of("A", "K"), colNames(t.get("T8")));
        assertEquals(List.of(), t.get("T9").columns(), "B 가 T1·T0 둘 다에 있다 — 모호하면 추측 안 함");
        assertNotNull(r.notes().get("T9").get(CTAS));
        assertEquals(List.of("A", "B"), colNames(t.get("TA")));
        // 1-81 — 괄호 안 쉼표는 조인 구분이 아니다 · NATURAL·USING 은 공통 컬럼을 합치니 *·t.* 는 추측 안 함
        assertEquals(List.of("A", "D"), colNames(t.get("TB")));
        assertNull(r.notes().get("TB"));
        assertEquals(List.of(), t.get("TC").columns(), "NATURAL JOIN 의 * — 공통 컬럼을 하나로 합친다");
        assertNotNull(r.notes().get("TC").get(CTAS));
        assertEquals(List.of(), t.get("TD").columns(), "USING 의 t.*");
        assertNotNull(r.notes().get("TD").get(CTAS));
        assertEquals(List.of("A"), colNames(t.get("TE")));
        // 1-83 — 식·함수 항목은 AS 별칭이 있으면 이름만(형식 null) + notes ctasExpr, 별칭 없으면 지금처럼 notes ctas
        assertEquals(List.of("A", "UB", "N"), colNames(t.get("TF")));
        assertNull(t.get("TF").columns().get(1).nativeType(), "형식을 모른다");
        assertNull(t.get("TF").columns().get(2).nativeType());
        assertTrue(t.get("TF").columns().get(1).nullable() && t.get("TF").columns().get(2).nullable());
        assertEquals(List.of(1, 2, 3), t.get("TF").columns().stream().map(Column::ordinal).toList());
        assertNotNull(r.notes().get("TF").get(DdlReader.CTAS_EXPR));
        assertNull(r.notes().get("TF").get(CTAS));
        assertEquals(List.of(), t.get("TG").columns(), "별칭 없는 식 — 추측 안 함");
        assertNotNull(r.notes().get("TG").get(CTAS));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-71 — PARTITION OF 부모가 같은 입력에 있으면 컬럼 + PK */
    @Test
    void partitionFromParent() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE P (ID INT, DT DATE, PRIMARY KEY (ID, DT)) PARTITION BY RANGE (DT); "
                + "CREATE TABLE P1 PARTITION OF P FOR VALUES FROM (1) TO (10);");
        Table p1 = r.tables().get(1);
        assertEquals(List.of("ID", "DT"), colNames(p1));
        assertEquals(List.of("ID", "DT"), p1.pk().columns());
        assertNull(r.notes().get("P1"));
    }

    /** 1-69 음성 — OF 로 시작하는 표·컬럼 이름은 보통 표다 */
    @Test
    void ofColumnNameStillTable() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE OF_LIST (a INT, OF_CNT INT)");
        assertEquals(2, r.tables().get(0).columns().size());
        assertNull(r.notes().get("OF_LIST"));
    }

    /** 마무리 리뷰 — MySQL 겹따옴표 COMMENT 안 「as select」 도 CTAS 가 아니다 */
    @Test
    void doubleQuotedCommentIsNotCtas() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE T (A INT COMMENT \"used as select flag\")");
        assertEquals(1, r.tables().get(0).columns().size());
        assertNull(r.notes().get("T"));
    }
}
