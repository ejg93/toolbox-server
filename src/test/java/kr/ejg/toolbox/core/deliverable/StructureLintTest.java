package kr.ejg.toolbox.core.deliverable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.deliverable.StructureLint.Finding;
import kr.ejg.toolbox.core.deliverable.StructureLint.Options;
import kr.ejg.toolbox.core.deliverable.StructureLint.Rule;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;
import org.junit.jupiter.api.Test;

/** 2-28a — 구조 점검 규칙 아홉: 규칙마다 양성·음성 + PG 벤더 스냅샷 골든 */
class StructureLintTest {

    static Column col(String name, int ord) {
        return new Column(name, ord, "VARCHAR2", null, 10L, null, null, true, null, null, null);
    }

    static Column colDef(String name, int ord, String def) {
        return new Column(name, ord, "VARCHAR2", null, 10L, null, null, true, def, null, null);
    }

    /** 컬럼 이름들로 표 하나 — pk 는 첫 컬럼(pk 가 null 이면 없음) */
    static Table t(String name, List<String> cols, String pk) {
        List<Column> cs = new java.util.ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            cs.add(col(cols.get(i), i + 1));
        }
        return Table.of("A", name, "TABLE", null).withColumns(cs).withConstraints(pk == null ? null : new PrimaryKey("PK_" + name, List.of(pk)),
                List.of(), List.of());
    }

    static ForeignKey fk(String name, List<String> cols, String ref) {
        return new ForeignKey(name, cols, null, ref, cols);
    }

    static List<Schema> snap(Table... ts) {
        return List.of(new Schema("A", "x", List.of(ts)));
    }

    static List<String> of(StructureLint.Result r, Rule rule) {
        return r.findings().stream().filter(f -> f.rule() == rule).map(f -> f.table() + (f.column() == null ? "" : "." + f.column())
                + (f.detail().isEmpty() ? "" : " " + f.detail())).toList();
    }

    @Test
    void fkNoIndexLooksAtPkUniqueAndIndexHeads() {
        Table cust = t("CUST", List.of("CUST_ID"), "CUST_ID");
        Table ord = t("ORD", List.of("ORD_NO", "CUST_ID"), "ORD_NO").withConstraints(new PrimaryKey("PK_ORD", List.of("ORD_NO")),
                List.of(fk("FK_ORD_CUST", List.of("CUST_ID"), "CUST")), List.of());
        Table ord2 = t("ORD2", List.of("ORD_NO", "CUST_ID", "X"), "ORD_NO").withConstraints(new PrimaryKey("PK_ORD2", List.of("ORD_NO")),
                List.of(fk("FK_ORD2_CUST", List.of("CUST_ID"), "CUST")), List.of()).withIndexes(List.of(new Index("IX_ORD2", false, List.of("cust_id", "X"))));
        Table ord3 = t("ORD3", List.of("CUST_ID", "SEQ"), null).withConstraints(new PrimaryKey("PK_ORD3", List.of("CUST_ID", "SEQ")),
                List.of(fk("FK_ORD3_CUST", List.of("CUST_ID"), "CUST")), List.of());
        Table pair = t("PAIR", List.of("ID", "A", "B"), "ID");
        Table comp = t("COMP", List.of("ID", "A", "B"), "ID").withConstraints(new PrimaryKey("PK_COMP", List.of("ID")),
                List.of(fk("FK_COMP_PAIR", List.of("A", "B"), "PAIR")), List.of(new UniqueKey("UQ_COMP", List.of("B", "A"))));
        Table tail = t("TAIL", List.of("ID", "A", "B"), "ID").withConstraints(new PrimaryKey("PK_TAIL", List.of("ID")),
                List.of(fk("FK_TAIL_PAIR", List.of("A", "B"), "PAIR")), List.of()).withIndexes(List.of(new Index("IX_TAIL", false, List.of("X", "A", "B"))));
        StructureLint.Result r = StructureLint.run(snap(cust, ord, ord2, ord3, pair, comp, tail), Options.DEFAULT);
        assertEquals(List.of("ORD.CUST_ID FK FK_ORD_CUST → CUST", "TAIL.A,B FK FK_TAIL_PAIR → PAIR"), of(r, Rule.FK_NO_INDEX),
                "ix 앞머리·PK 앞머리·유니크 집합 같음은 인덱스가 있다 — 뒤에 있는 컬럼은 아니다");
    }

    @Test
    void noRelationAndNoIndex() {
        Table cust = t("CUST", List.of("CUST_ID", "NM"), "CUST_ID");
        Table ord = t("ORD", List.of("ORD_NO", "CUST_ID"), "ORD_NO").withConstraints(new PrimaryKey("PK_ORD", List.of("ORD_NO")),
                List.of(fk("FK", List.of("CUST_ID"), "cust")), List.of()).withIndexes(List.of(new Index("IX", false, List.of("CUST_ID"))));
        Table alone = t("ALONE", List.of("A", "B"), null).withStats(0L, null, null);
        Table unknown = t("UNKNOWN", List.of("A", "B"), null);
        Table view = Table.of("A", "V_ALONE", "VIEW", null).withColumns(List.of(col("A", 1)));
        StructureLint.Result r = StructureLint.run(snap(cust, ord, alone, unknown, view), Options.DEFAULT);
        assertEquals(List.of("ALONE", "UNKNOWN"), of(r, Rule.NO_RELATION), "가리켜지는 표(대소문자 무시)·FK 있는 표·뷰는 아니다");
        assertEquals(List.of("ALONE 행 0", "UNKNOWN 행 수 모름"), of(r, Rule.NO_INDEX), "0 과 모름을 가른다");
        assertEquals(4, r.tables(), "뷰는 안 센다");
    }

    @Test
    void tooManyColumnsAndMissingRequired() {
        Table wide = t("WIDE", List.of("ID", "A", "REG_DT"), "ID");
        Table thin = t("THIN", List.of("ID", "REG_DT", "REG_ID"), "ID");
        StructureLint.Result r = StructureLint.run(snap(wide, thin), new Options(2, List.of("REG_DT", "reg_id"), Set.of()));
        assertEquals(List.of("THIN 컬럼 3 > 2", "WIDE 컬럼 3 > 2"), of(r, Rule.TOO_MANY_COLUMNS));
        assertEquals(List.of("WIDE REG_ID"), of(r, Rule.MISSING_REQUIRED), "대소문자 무시 · 빠진 것만");
        StructureLint.Result none = StructureLint.run(snap(wide, thin), Options.DEFAULT);
        assertEquals(List.of(), of(none, Rule.TOO_MANY_COLUMNS), "0 이면 안 본다");
        assertEquals(List.of(), of(none, Rule.MISSING_REQUIRED), "비면 안 본다");
        assertEquals(List.of("컬럼 수 상한 2", "필수 컬럼 REG_DT,REG_ID", "끈 규칙 없음"), r.basis());
        assertEquals(List.of("컬럼 수 상한 안 봄", "필수 컬럼 안 봄", "끈 규칙 없음"), none.basis());
    }

    @Test
    void dupRelationSingleColumnSerialAndDefaultNull() {
        Table cust = t("CUST", List.of("CUST_ID"), "CUST_ID");
        Table dup = t("DUP", List.of("ID", "CUST_ID"), "ID").withConstraints(new PrimaryKey("PK_DUP", List.of("ID")),
                List.of(fk("FK1", List.of("CUST_ID"), "CUST"), fk("FK2", List.of("cust_id"), "cust"), fk("FK3", List.of("CUST_ID"), "OTHER")), List.of());
        Table serial = t("ADDR", List.of("ID", "ADDR3", "ADDR1", "ADDR2", "TEL1", "TEL2", "A1"), "ID");
        Table dflt = Table.of("A", "DFLT", "TABLE", null).withColumns(List.of(colDef("A", 1, "'NULL'"), colDef("B", 2, " 'null'::character varying "),
                colDef("C", 3, "NULL"), colDef("D", 4, "'N'"))).withConstraints(new PrimaryKey("PK", List.of("A")), List.of(), List.of());
        StructureLint.Result r = StructureLint.run(snap(cust, dup, serial, dflt), Options.DEFAULT);
        assertEquals(List.of("DUP.CUST_ID FK FK1·FK2"), of(r, Rule.DUP_RELATION), "다른 표를 가리키는 FK3 은 아니다");
        assertEquals(List.of("CUST.CUST_ID"), of(r, Rule.SINGLE_COLUMN));
        assertEquals(List.of("ADDR.ADDR1… ADDR1·ADDR2·ADDR3"), of(r, Rule.SERIAL_COLUMNS), "둘뿐인 TEL·어간 없는 A1 은 아니다, 순번 순");
        assertEquals(List.of("DFLT.A 'NULL'", "DFLT.B  'null'::character varying "), of(r, Rule.DEFAULT_NULL_STRING),
                "따옴표 없는 NULL·다른 글자는 아니다");
    }

    @Test
    void offRulesDisappearFromCountsAndOrderIsRuleThenTable() {
        Table b = t("B", List.of("ID"), null);
        Table a = t("A", List.of("ID"), null);
        StructureLint.Result r = StructureLint.run(snap(b, a), new Options(0, List.of(), Set.of(Rule.SINGLE_COLUMN)));
        assertEquals(List.of(Rule.NO_RELATION, Rule.NO_RELATION, Rule.NO_INDEX, Rule.NO_INDEX), r.findings().stream().map(Finding::rule).toList());
        assertEquals(List.of("A", "B"), r.findings().stream().filter(f -> f.rule() == Rule.NO_RELATION).map(Finding::table).toList());
        assertTrue(!r.counts().containsKey(Rule.SINGLE_COLUMN), "끈 규칙은 counts 에도 없다");
        assertEquals(8, r.counts().size());
        assertEquals(0, r.counts().get(Rule.FK_NO_INDEX), "켠 규칙은 0 도 싣는다");
        assertEquals("끈 규칙 컬럼 하나뿐인 표", r.basis().get(2));
    }

    @Test
    void ruleIds() {
        assertEquals(Rule.FK_NO_INDEX, Rule.byId("fkNoIndex"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Rule.byId("nope"));
        assertTrue(e.getMessage().contains("defaultNullString") && e.getMessage().contains("nope"), e.getMessage());
    }

    /** PG 벤더 스냅샷 — 설계 25 예측 11건(fkNoIndex 2 · noRelation 1 · tooManyColumns 1 · missingRequired 7) */
    @Test
    void goldenOnPostgresVendorSnapshot() throws Exception {
        StructureLint.Result r = StructureLint.run(GoldenFiles.schemas("meta/postgres-vendor.json"), new Options(5, List.of("created_at"), Set.of()));
        GoldenFiles.assertJson("deliverable/pg-lint.json", r.findings());
        assertEquals(2, r.counts().get(Rule.FK_NO_INDEX));
        assertEquals(1, r.counts().get(Rule.NO_RELATION));
        assertEquals(1, r.counts().get(Rule.TOO_MANY_COLUMNS));
        assertEquals(7, r.counts().get(Rule.MISSING_REQUIRED));
        assertEquals(11, r.findings().size());
    }
}
