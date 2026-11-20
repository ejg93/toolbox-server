package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.FkRule;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.SortOrder;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;
import org.junit.jupiter.api.Test;

/** 7-6 — 대상 다섯 골든 · 같은 방언은 원본 타입 · 경고 · DdlReader 왕복(oracle·postgresql) */
class DdlGenTest {

    static Column col(String name, int ord, String type, Long len, Integer p, Integer s, boolean nullable, String def, String comment) {
        return new Column(name, ord, type, null, len, p, s, nullable, def, comment, null);
    }

    /** 7-2 픽스처 표 + FK·유니크·인덱스·기본값(SYSDATE·'Y'·식)·예약어 컬럼·없는 표를 가리키는 FK 가 든 표 */
    static List<Table> fixture() {
        Table hist = GenModelTest.empHist();
        Table lvl = new Table("HR", "TB_EMP_LVL", "TABLE", "사원 등급's", List.of(
                col("LVL_ID", 1, "NUMBER", null, 10, 0, false, null, "등급 ID"),
                col("EMP_NO", 2, "NUMBER", null, 6, 0, false, null, "사원 번호"),
                col("START_DATE", 3, "DATE", null, null, null, false, null, null),
                col("LEVEL", 4, "NUMBER", null, 2, 0, true, "0", "레벨"),
                col("USE_AT", 5, "CHAR", 1L, null, null, false, "'Y' ", "사용 여부"),
                col("REG_DT", 6, "DATE", null, null, null, true, "SYSDATE", null),
                col("REG_YEAR", 7, "VARCHAR2", 4L, null, null, true, "TO_CHAR(SYSDATE,'YYYY')", null),
                col("RATE", 8, "NUMBER", null, null, null, true, null, null),
                col("DEPT_ID", 9, "NUMBER", null, 4, 0, true, null, null)),
                new PrimaryKey("SYS_C0012345", List.of("LVL_ID")),
                List.of(new ForeignKey("FK_LVL_HIST", List.of("EMP_NO", "START_DATE"), "HR", "TB_EMP_HIST", List.of("EMP_NO", "START_DATE")),
                        new ForeignKey("FK_LVL_DEPT", List.of("DEPT_ID"), "HR", "TB_DEPT", List.of("DEPT_ID"))),
                List.of(new UniqueKey("UK_LVL", List.of("EMP_NO", "LEVEL"))),
                List.of(new Index("SYS_C0012345", true, List.of("LVL_ID")), new Index("UK_LVL", true, List.of("EMP_NO", "LEVEL")),
                        new Index("IX_LVL_REG", false, List.of("REG_DT"))),
                null, null, null);
        return List.of(hist, lvl);
    }

    /** 0-85h — 스냅샷 기본값의 긴 리터럴(PG 캐스트 꼴 포함)이 교대 반복 재귀로 스택을 넘기지 않는다(소유 수량자) */
    @Test
    void longDefaultDoesNotOverflow() {
        String lit = "'" + "a".repeat(200_000) + "'";
        Table t = new Table("HR", "TB_LONG", "TABLE", null, List.of(col("A", 1, "VARCHAR2", 10L, null, null, true, lit, null),
                col("B", 2, "VARCHAR2", 10L, null, null, true, lit + "::character varying", null)),
                null, List.of(), List.of(), List.of(), null, null, null);
        for (String source : List.of("oracle", "postgresql")) {
            for (String target : DdlGen.targets()) {
                assertEquals(1, DdlGen.generate(List.of(t), opts(source, target), GenModelTest.TYPES).tables(), source + "→" + target);
            }
        }
    }

    static DdlGen.Options opts(String source, String target) {
        return new DdlGen.Options(source, target, "HR", true, true, true, "");
    }

    @Test
    void goldenPerTarget() {
        for (String target : DdlGen.targets()) {
            DdlGen.Result r = DdlGen.generate(fixture(), opts("oracle", target), GenModelTest.TYPES);
            assertEquals(2, r.tables());
            GoldenFiles.assertText("gen/ddl-" + target + ".sql", r.sql());
        }
        assertEquals(List.of("oracle", "tibero", "postgresql", "mariadb", "mssql"), DdlGen.targets());
    }

    @Test
    void sameDialectKeepsNativeTypes() {
        String sql = DdlGen.generate(fixture(), opts("oracle", "oracle"), GenModelTest.TYPES).sql();
        assertTrue(sql.contains("SALARY NUMBER(8,2)"), sql);
        assertTrue(sql.contains("START_DATE DATE NOT NULL"), sql);
        assertTrue(sql.contains("USE_AT CHAR(1) DEFAULT 'Y' NOT NULL"), sql);
        String pg = DdlGen.generate(fixture(), opts("oracle", "postgresql"), GenModelTest.TYPES).sql();
        assertTrue(pg.contains("START_DATE TIMESTAMP NOT NULL"), "Oracle DATE 는 시각을 갖는다 → TIMESTAMP\n" + pg);
        assertTrue(pg.contains("NOTE TEXT"), pg);
        assertTrue(pg.contains("REG_DT TIMESTAMP DEFAULT CURRENT_TIMESTAMP"), pg);
    }


    /** 1-64 — 인덱스 DESC 는 대상 다섯 모두 컬럼 뒤에 붙는다, ASC·모름은 안 붙인다 */
    @Test
    void descIndexKeepsSort() {
        Table lvl = fixture().get(1);
        Table sorted = new Table(lvl.schema(), lvl.name(), lvl.type(), lvl.comment(), lvl.columns(), lvl.pk(), lvl.fks(), lvl.uniques(),
                List.of(new Index("IX_LVL_REG", false, List.of("REG_DT", "RATE"), List.of(SortOrder.DESC, SortOrder.ASC))), null, null, null);
        for (String target : DdlGen.targets()) {
            String sql = DdlGen.generate(List.of(fixture().get(0), sorted), opts("oracle", target), GenModelTest.TYPES).sql();
            assertTrue(sql.contains("(REG_DT DESC, RATE);"), target + "\n" + sql);
        }
    }
    @Test
    void warnings() {
        DdlGen.Result r = DdlGen.generate(fixture(), opts("oracle", "mssql"), GenModelTest.TYPES);
        String w = String.join("\n", r.warnings());
        assertTrue(w.contains("LEVEL: 예약어"), w);
        assertTrue(w.contains("TB_EMP_LVL.REG_YEAR: 기본값 식은 옮기지 않았다"), w);
        assertTrue(w.contains("참조 표 TB_DEPT 가 대상에 없어 건너뜀"), w);
        assertTrue(r.sql().contains("[LEVEL]"), r.sql());
        assertFalse(r.sql().contains("SYS_C0012345"), "DB 가 지은 이름은 다시 짓는다\n" + r.sql());
        assertTrue(r.sql().contains("CONSTRAINT PK_TB_EMP_LVL PRIMARY KEY"), r.sql());
        assertFalse(r.sql().contains("CREATE UNIQUE INDEX UK_LVL"), "유니크 키와 같은 인덱스는 뺀다\n" + r.sql());
        assertTrue(r.sql().contains("CREATE INDEX IX_LVL_REG"), r.sql());
        String maria = DdlGen.generate(fixture(), opts("oracle", "mariadb"), GenModelTest.TYPES).sql();
        assertTrue(maria.contains(") COMMENT = '사원 등급''s';"), maria);
        assertFalse(maria.contains("COMMENT ON"), maria);
        assertThrows(IllegalArgumentException.class, () -> DdlGen.generate(fixture(), opts("oracle", "sybase"), GenModelTest.TYPES));
    }

    @Test
    void prefixAndNoSchema() {
        DdlGen.Options o = new DdlGen.Options("oracle", "postgresql", null, true, false, false, "G18_");
        String sql = DdlGen.generate(fixture(), o, GenModelTest.TYPES).sql();
        assertTrue(sql.contains("CREATE TABLE G18_TB_EMP_HIST ("), sql);
        assertTrue(sql.contains("REFERENCES G18_TB_EMP_HIST (EMP_NO, START_DATE)"), sql);
        assertFalse(sql.contains("CREATE INDEX"), sql);
        assertFalse(sql.contains("COMMENT ON"), sql);
    }

    /** 생성한 DDL 을 DdlReader 로 다시 읽어 표·컬럼·널 허용·PK·FK 수·코멘트가 원본과 같다 */
    @Test
    void roundTripThroughDdlReader() {
        for (String target : List.of("oracle", "postgresql")) {
            String sql = DdlGen.generate(fixture(), opts("oracle", target), GenModelTest.TYPES).sql();
            DdlReader.Result back = DdlReader.read(sql);
            assertEquals(shape(fixture(), 1), shape(back.tables(), 0), target + "\n" + sql);
            assertEquals(fixture().stream().mapToLong(t -> t.indexes().stream().filter(x -> !x.unique()).count()).sum(),
                    back.tables().stream().mapToLong(t -> t.indexes().stream().filter(x -> !x.unique()).count()).sum(), target + " 비유니크 인덱스 수");
        }
    }

    /** 표 → 「컬럼:널허용:코멘트 있음 …|PK|FK 수」. skippedFk 는 원본에서 빼는 FK 수(대상에 없는 표) */
    static Map<String, String> shape(List<Table> tables, int skippedFk) {
        Map<String, String> m = new TreeMap<>();
        for (Table t : tables) {
            List<String> cols = new ArrayList<>();
            t.columns().stream().sorted((a, b) -> Integer.compare(a.ordinal(), b.ordinal())).forEach(c -> cols.add(
                    c.name().toUpperCase(Locale.ROOT) + ":" + c.nullable() + ":" + (c.comment() != null && !c.comment().isBlank())));
            long fks = t.fks().stream().filter(f -> !f.refTable().equalsIgnoreCase("TB_DEPT") || skippedFk == 0).count();
            m.put(t.name().toUpperCase(Locale.ROOT), String.join(" ", cols) + "|" + (t.pk() == null ? "" : t.pk().columns()) + "|" + fks
                    + "|" + (t.comment() != null && !t.comment().isBlank()));
        }
        return m;
    }

    /** 1-87(설계 71 §3-2) — 원본에서 따옴표로 만든 이름은 글자 그대로, 예약어는 대상이 접는 글자로, 지은 이름·접두는 안 감싼다 */
    @Test
    void identifiers() {
        Table pg = Table.of("public", "t86", "TABLE", null)
                .withColumns(List.of(col("userId", 1, "int4", null, null, null, true, null, null),
                        col("userid", 2, "int4", null, null, null, true, null, null)));
        DdlGen.Result r = DdlGen.generate(List.of(pg), new DdlGen.Options("postgresql", "postgresql", null, false, false, false, ""),
                GenModelTest.TYPES);
        assertTrue(r.sql().contains("\"userId\""), r.sql());
        assertTrue(r.sql().contains(" userid "), r.sql());
        assertTrue(r.warnings().contains("userId: 대소문자 섞인 이름 — 따옴표로 감쌌다"), r.warnings().toString());

        Table ora = Table.of("HR", "T86", "TABLE", null)
                .withColumns(List.of(col("LEFT", 1, "NUMBER", null, 10, 0, true, null, null),
                        col("FETCH", 2, "NUMBER", null, 10, 0, true, null, null),
                        col("START", 3, "NUMBER", null, 10, 0, true, null, null),
                        col("emp_no", 4, "NUMBER", null, 10, 0, true, null, null)));
        Map<String, String> want = Map.of("oracle", "\"LEFT\"", "postgresql", "\"left\"", "mariadb", "`LEFT`", "mssql", "[LEFT]");
        for (Map.Entry<String, String> e : want.entrySet()) {
            String sql = DdlGen.generate(List.of(ora), new DdlGen.Options("oracle", e.getKey(), null, false, false, false, ""),
                    GenModelTest.TYPES).sql();
            assertTrue(sql.contains(e.getValue()), e.getKey() + " " + sql);
            assertTrue(sql.contains("\"emp_no\"") || sql.contains("`emp_no`") || sql.contains("[emp_no]"), e.getKey() + " " + sql);
        }
        assertTrue(DdlGen.reserved("left"));

        Table ms = Table.of("dbo", "T86", "TABLE", null)
                .withColumns(List.of(col("ArtistId", 1, "int", null, null, null, true, null, null)));
        String fromMs = DdlGen.generate(List.of(ms), new DdlGen.Options("mssql", "oracle", null, false, false, false, ""),
                GenModelTest.TYPES).sql();
        assertTrue(fromMs.contains(" ArtistId ") && !fromMs.contains("\"ArtistId\""), fromMs);

        Table users = Table.of("public", "users", "TABLE", null)
                .withColumns(List.of(col("id", 1, "int4", null, null, null, false, null, null)))
                .withConstraints(new PrimaryKey(null, List.of("id")), List.of(), List.of());
        String pre = DdlGen.generate(List.of(users), new DdlGen.Options("postgresql", "postgresql", null, false, false, false, "G1_"),
                GenModelTest.TYPES).sql();
        assertTrue(pre.contains("CREATE TABLE G1_users"), pre);
        assertTrue(pre.contains("CONSTRAINT G1_PK_users"), pre);

        Table mixed = Table.of("public", "UserTable", "TABLE", null)
                .withColumns(List.of(col("id", 1, "int4", null, null, null, false, null, null)));
        String mx = DdlGen.generate(List.of(mixed), new DdlGen.Options("postgresql", "postgresql", null, false, false, false, ""),
                GenModelTest.TYPES).sql();
        assertTrue(mx.contains("CREATE TABLE \"UserTable\""), mx);
    }

    static Column auto(String name, int ord, String type) {
        return new Column(name, ord, type, null, null, 10, 0, false, null, null, null, true);
    }

    /** 1-89 — 부모·자식 두 표: FK 규칙 · CHECK · 자동증가(키 아닌 것 하나 포함) */
    static List<Table> rulesFixture(String type) {
        Table p = Table.of("public", "p86", "TABLE", null)
                .withColumns(List.of(auto("id", 1, type)))
                .withConstraints(new PrimaryKey(null, List.of("id")), List.of(), List.of());
        Table c = Table.of("public", "c86", "TABLE", null)
                .withColumns(List.of(auto("id", 1, type), col("p_id", 2, "int4", null, 10, 0, true, null, null),
                        col("qty", 3, "int4", null, 10, 0, true, null, null), auto("seq", 4, type)))
                .withConstraints(new PrimaryKey(null, List.of("id")),
                        List.of(new ForeignKey("FK_C86_P", List.of("p_id"), null, "p86", List.of("id"), FkRule.CASCADE, FkRule.SET_NULL)),
                        List.of())
                .withChecks(List.of(new Check("CK_C86_QTY", "QTY >= 0")));
        return List.of(p, c);
    }

    /** 1-89(A-11) — 변환 DDL 에 FK 규칙 · CHECK · 자동증가 절이 실린다. 못 싣는 것은 경고 */
    @Test
    void rulesChecksAuto() {
        DdlGen.Result pg = DdlGen.generate(rulesFixture("int4"), new DdlGen.Options("postgresql", "postgresql", null, true, false, false, ""),
                GenModelTest.TYPES);
        assertTrue(pg.sql().contains("REFERENCES p86 (id) ON DELETE CASCADE ON UPDATE SET NULL;"), pg.sql());
        assertTrue(pg.sql().contains("ADD CONSTRAINT CK_C86_QTY CHECK (QTY >= 0);"), pg.sql());
        assertTrue(pg.sql().contains("id int4 GENERATED BY DEFAULT AS IDENTITY NOT NULL"), pg.sql());

        DdlGen.Result serial = DdlGen.generate(rulesFixture("serial"), new DdlGen.Options("postgresql", "postgresql", null, true, false, false, ""),
                GenModelTest.TYPES);
        assertFalse(serial.sql().contains("IDENTITY") || serial.sql().contains("DEFAULT"), serial.sql());
        assertTrue(serial.warnings().stream().noneMatch(w -> w.contains("nextval")), serial.warnings().toString());

        DdlGen.Result ora = DdlGen.generate(rulesFixture("int4"), new DdlGen.Options("postgresql", "oracle", null, true, false, false, ""),
                GenModelTest.TYPES);
        assertTrue(ora.sql().contains("ON DELETE CASCADE;") && !ora.sql().contains("ON UPDATE"), ora.sql());
        assertTrue(ora.warnings().stream().anyMatch(w -> w.contains("ON UPDATE SET NULL")), ora.warnings().toString());
        assertTrue(ora.sql().contains("-- [확인] c86 CHECK (QTY >= 0) — 방언이 달라 옮기지 않았다"), ora.sql());
        assertTrue(ora.warnings().contains("c86: CHECK 1개는 방언이 달라 옮기지 않았다 — 주석으로 남겼다"), ora.warnings().toString());
        assertTrue(ora.warnings().stream().anyMatch(w -> w.startsWith("c86.id: 자동증가 — oracle")), ora.warnings().toString());

        DdlGen.Result maria = DdlGen.generate(rulesFixture("int4"), new DdlGen.Options("postgresql", "mariadb", null, true, false, false, ""),
                GenModelTest.TYPES);
        assertTrue(maria.sql().contains("NOT NULL AUTO_INCREMENT"), maria.sql());
        assertFalse(maria.sql().matches("(?s).*seq [^,\\n]*AUTO_INCREMENT.*"), maria.sql());
        assertTrue(maria.warnings().contains("c86.seq: 자동증가 — 키 컬럼이 아니라 mariadb 가 못 받는다, 뺐다"), maria.warnings().toString());

        DdlGen.Result ms = DdlGen.generate(rulesFixture("int4"), new DdlGen.Options("postgresql", "mssql", null, true, false, false, ""),
                GenModelTest.TYPES);
        assertTrue(ms.sql().contains(" IDENTITY(1,1)"), ms.sql());

        // 1-103 — CREATE TABLE 안에는 PK 만 있다: 유니크 키 자동증가는 빼고 경고
        Table u = Table.of("public", "u86", "TABLE", null).withColumns(List.of(auto("id", 1, "int4")))
                .withConstraints(null, List.of(), List.of(new UniqueKey("uk_u86", List.of("id"))));
        DdlGen.Result uq = DdlGen.generate(List.of(u), new DdlGen.Options("postgresql", "mariadb", null, true, false, false, ""), GenModelTest.TYPES);
        assertFalse(uq.sql().contains("AUTO_INCREMENT"), uq.sql());
        assertTrue(uq.warnings().stream().anyMatch(w -> w.startsWith("u86.id: 자동증가 — PK 가 아닌 키라")), uq.warnings().toString());
    }

    /** V-65(9-8 실물 MSSQL) — 수집 타입 이름 「int identity」 에 IDENTITY 절을 또 붙이지 않는다 */
    @Test
    void mssqlIdentityTypeOnce() {
        String sql = DdlGen.generate(rulesFixture("int identity"), new DdlGen.Options("mssql", "mssql", null, true, false, false, ""),
                GenModelTest.TYPES).sql();
        assertTrue(sql.contains("id int IDENTITY(1,1) NOT NULL"), sql);
        assertFalse(sql.toUpperCase(java.util.Locale.ROOT).matches("(?s).*IDENTITY[^,\\n]*IDENTITY.*"), sql);
        // 1-102 — V016 전 스냅샷: 자동증가 표시 없이 타입 글만 「int identity」
        Table old = Table.of("dbo", "t87", "TABLE", null).withColumns(List.of(col("id", 1, "int identity", null, 10, 0, false, null, null)))
                .withConstraints(new PrimaryKey(null, List.of("id")), List.of(), List.of());
        String same = DdlGen.generate(List.of(old), new DdlGen.Options("mssql", "mssql", null, true, false, false, ""), GenModelTest.TYPES).sql();
        assertTrue(same.contains("id int IDENTITY(1,1) NOT NULL"), same);
        String pg = DdlGen.generate(List.of(old), new DdlGen.Options("mssql", "postgresql", null, true, false, false, ""), GenModelTest.TYPES).sql();
        assertTrue(pg.contains("GENERATED BY DEFAULT AS IDENTITY"), pg);
    }

    static String ddl(List<Table> ts, String source, String target) {
        return DdlGen.generate(ts, new DdlGen.Options(source, target, null, false, true, false, ""), GenModelTest.TYPES).sql();
    }

    static List<String> warns(List<Table> ts, String source, String target) {
        return DdlGen.generate(ts, new DdlGen.Options(source, target, null, false, true, false, ""), GenModelTest.TYPES).warnings();
    }

    /** 1-90(R-A-I1·R-A-I13·A-16·A-09) — 길이 상한 · BOOLEAN 기본값 · 길이 없는 키 문자 · 스키마 단위 인덱스 이름 겹침 */
    @Test
    void sizesDefaultsKeysIndexNames() {
        Table big = Table.of("dbo", "t1", "TABLE", null).withColumns(List.of(
                col("a", 1, "nvarchar", 2147483647L, null, null, true, null, null),
                col("b", 2, "varchar", 8000L, null, null, true, null, null)));
        String ms = ddl(List.of(big), "mssql", "mssql");
        assertTrue(ms.contains("a nvarchar(MAX)") && ms.contains("b varchar(8000)"), ms);
        Table pgBig = Table.of("public", "t1", "TABLE", null).withColumns(List.of(
                col("a", 1, "varchar", 2147483647L, null, null, true, null, null)));
        String pg = ddl(List.of(pgBig), "postgresql", "postgresql");
        assertTrue(pg.contains("a varchar,") || pg.contains("a varchar\n"), pg);

        Table bits = Table.of("dbo", "t2", "TABLE", null).withColumns(List.of(
                col("on1", 1, "bit", null, null, null, true, "((1))", null),
                col("off1", 2, "bit", null, null, null, true, "((0))", null)));
        String toPg = ddl(List.of(bits), "mssql", "postgresql");
        assertTrue(toPg.contains("on1 BOOLEAN DEFAULT TRUE") && toPg.contains("off1 BOOLEAN DEFAULT FALSE"), toPg);
        assertTrue(ddl(List.of(bits), "mssql", "oracle").contains("DEFAULT 1"));

        Table keys = Table.of("public", "t3", "TABLE", null).withColumns(List.of(
                col("id", 1, "uuid", null, null, null, false, null, null),
                col("code", 2, "text", null, null, null, true, null, null),
                col("memo", 3, "text", null, null, null, true, null, null),
                col("big", 4, "text", 2147483647L, null, null, true, null, null)))
                .withConstraints(new PrimaryKey(null, List.of("id")), List.of(), List.of(new UniqueKey("uk_t3", List.of("code")),
                        new UniqueKey("uk_t3b", List.of("big"))));
        String ora = ddl(List.of(keys), "postgresql", "oracle");
        assertTrue(ora.contains("ID VARCHAR2(36)") || ora.contains("id VARCHAR2(36)"), ora);
        assertTrue(ora.contains("code VARCHAR2(255)") && ora.contains("memo CLOB"), ora);
        assertTrue(warns(List.of(keys), "postgresql", "oracle").contains("t3.id: 길이 없는 문자 타입이 키 컬럼 — 길이 36 으로 냈다"));
        // 1-103 — pgjdbc text 길이 2147483647 도 키 컬럼이면 255
        assertTrue(ora.contains("big VARCHAR2(255)"), ora);
        assertTrue(warns(List.of(keys), "postgresql", "oracle").contains("t3.big: 길이 2147483647 문자 타입이 키 컬럼 — 대상 상한을 넘어 길이 255 으로 냈다"),
                warns(List.of(keys), "postgresql", "oracle").toString());

        Table x = Table.of("db", "ta", "TABLE", null).withColumns(List.of(col("v", 1, "int", null, 10, 0, true, null, null)))
                .withIndexes(List.of(new Index("idx_name", false, List.of("v"), List.of(SortOrder.ASC))));
        Table y = Table.of("db", "tb", "TABLE", null).withColumns(List.of(col("v", 1, "int", null, 10, 0, true, null, null)))
                .withIndexes(List.of(new Index("idx_name", false, List.of("v"), List.of(SortOrder.ASC))));
        String ix = ddl(List.of(x, y), "mariadb", "postgresql");
        assertTrue(ix.contains("CREATE INDEX idx_name ON ta") && ix.contains("CREATE INDEX IX_tb_1 ON tb"), ix);
        assertTrue(warns(List.of(x, y), "mariadb", "postgresql").contains("tb: 인덱스 이름 idx_name 가 다른 표와 겹쳐 IX_tb_1 로 바꿨다"));
        String same = ddl(List.of(x, y), "mariadb", "mariadb");
        assertTrue(same.contains("CREATE INDEX idx_name ON tb"), same);
    }

    /** 1-88(A-28) — mariadb 기본 sql_mode 는 역슬래시가 탈출 글자라 리터럴 안에서 겹친다. 다른 방언은 그대로 */
    @Test
    void mariadbBackslash() {
        Table t = Table.of("HR", "T86", "TABLE", "경로 C:\\")
                .withColumns(List.of(col("ID", 1, "NUMBER", null, 10, 0, true, null, "a\\b'c")));
        String maria = DdlGen.generate(List.of(t), new DdlGen.Options("oracle", "mariadb", null, false, false, true, ""),
                GenModelTest.TYPES).sql();
        assertTrue(maria.contains("COMMENT = '경로 C:\\\\'"), maria);
        assertTrue(maria.contains("COMMENT 'a\\\\b''c'"), maria);
        String ora = DdlGen.generate(List.of(t), new DdlGen.Options("oracle", "oracle", null, false, false, true, ""),
                GenModelTest.TYPES).sql();
        assertTrue(ora.contains("'경로 C:\\'") && !ora.contains("C:\\\\"), ora);
    }
}
