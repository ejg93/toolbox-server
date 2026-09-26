package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.logical.DomainMatcher;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * 1-9 — 경량 DDL 읽기의 정확도를 실물 DB 결과로 잰다. 컨테이너에 넣었던 샘플 DDL 넷을 읽어 1-3 이 그 DB 에서 떠 온
 * 스냅샷 골든과 테이블·컬럼 이름·타입 계열·NULL·PK·코멘트를 견준다. MSSQL 코멘트는 `sp_addextendedproperty` 라 안 읽는다.
 */
class DdlReaderTest {

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
            if (comments) {
                assertEquals(e.comment(), t.comment(), where + " 테이블 코멘트");
            }
        }
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
}
