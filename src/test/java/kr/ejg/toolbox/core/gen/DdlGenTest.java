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
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.PrimaryKey;
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
}
