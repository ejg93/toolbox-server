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
        assertEquals(12, r.counts().size());
        assertEquals(0, r.counts().get(Rule.FK_NO_INDEX), "켠 규칙은 0 도 싣는다");
        assertEquals("끈 규칙 컬럼 하나뿐인 표", r.basis().get(2));
    }

    /** 마무리 리뷰 — 범위 밖 자식이 가리키는 부모 · 긴 숫자 접미 · 다른 스키마의 같은 이름 표를 가리키는 FK 둘 */
    @Test
    void reviewEdgeCases() {
        Table parent = t("PARENT", List.of("ID", "NM"), "ID");
        Table child = t("CHILD", List.of("ID", "PARENT_ID"), "ID").withConstraints(new PrimaryKey("PK_CHILD", List.of("ID")),
                List.of(fk("FK", List.of("PARENT_ID"), "PARENT")), List.of());
        List<Schema> scoped = snap(parent);
        assertEquals(List.of("PARENT"), of(StructureLint.run(scoped, Options.DEFAULT), Rule.NO_RELATION), "거른 스냅샷만 보면 고립");
        assertEquals(List.of(), of(StructureLint.run(scoped, snap(parent, child), Options.DEFAULT), Rule.NO_RELATION), "범위 밖 자식이 가리킨다");

        Table longSuffix = t("LONG", List.of("ID", "C_12345678901234567890", "C_12345678901234567891", "C_12345678901234567892"), "ID");
        assertEquals(List.of(), of(StructureLint.run(snap(longSuffix), Options.DEFAULT), Rule.SERIAL_COLUMNS), "열 자리 넘는 접미는 순번이 아니다(예외 없음)");

        Table two = t("TWO", List.of("ID", "REF_ID"), "ID").withConstraints(new PrimaryKey("PK_TWO", List.of("ID")),
                List.of(new ForeignKey("FK_X", List.of("REF_ID"), "S1", "REF", List.of("ID")), new ForeignKey("FK_Y", List.of("REF_ID"), "S2", "REF", List.of("ID"))),
                List.of());
        assertEquals(List.of(), of(StructureLint.run(snap(two), Options.DEFAULT), Rule.DUP_RELATION), "다른 스키마의 같은 이름 표는 중복이 아니다");
    }

    @Test
    void ruleIds() {
        assertEquals(Rule.FK_NO_INDEX, Rule.byId("fkNoIndex"));
        assertEquals(List.of(Rule.NO_PK, Rule.FK_CYCLE), List.of(Rule.byId("noPk"), Rule.byId("fkCycle")), "설계 51 — 넷은 enum 끝");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Rule.byId("nope"));
        assertTrue(e.getMessage().contains("defaultNullString") && e.getMessage().contains("nope"), e.getMessage());
    }

    static Column typed(String name, int ord, String type, Long len, Integer prec, Integer scale) {
        return new Column(name, ord, type, null, len, prec, scale, true, null, null, null);
    }

    static Table typedTable(String name, String pk, Column... cols) {
        return Table.of("A", name, "TABLE", null).withColumns(List.of(cols)).withConstraints(pk == null ? null : new PrimaryKey("PK_" + name, List.of(pk)),
                List.of(), List.of());
    }

    /** 설계 51 — noPk 는 키가 하나라도 있을 때만(아무것도 없으면 noIndex), 유니크와 인덱스를 가른다 */
    @Test
    void noPkOnlyWhenKeyed() {
        Table ix = t("P1", List.of("A", "B"), null).withIndexes(List.of(new Index("IX_P1", false, List.of("A"))));
        Table bare = t("P2", List.of("A", "B"), null);
        Table pk = t("P3", List.of("A", "B"), "A").withIndexes(List.of(new Index("IX_P3", false, List.of("B"))));
        Table uq = t("P4", List.of("A", "B"), null).withConstraints(null, List.of(), List.of(new UniqueKey("UQ_P4", List.of("A"))))
                .withIndexes(List.of(new Index("UQ_P4", true, List.of("A"))));
        StructureLint.Result r = StructureLint.run(snap(ix, bare, pk, uq), Options.DEFAULT);
        assertEquals(List.of("P1 유니크 0 · 인덱스 1", "P4 유니크 1 · 인덱스 0"), of(r, Rule.NO_PK), "PK 있는 표·아무것도 없는 표는 아니다, 같은 이름 유니크 인덱스는 한 번");
        assertEquals(List.of("P2 행 수 모름"), of(r, Rule.NO_INDEX), "noIndex 와 안 겹친다");
    }

    /** 설계 51 — 형식·길이·정밀도, 둘 다 알 때만, refColumns 가 비면 부모 PK, 부모는 whole 에서 */
    @Test
    void fkTypeMismatchComparesTypeLengthPrecision() {
        Table pv = typedTable("PV", "ID", typed("ID", 1, "VARCHAR2", 10L, null, null));
        Table pn = typedTable("PN", "ID", typed("ID", 1, "NUMBER", null, 8, 2));
        Table c1 = typedTable("C1", null, typed("PV_ID", 1, "VARCHAR2", 20L, null, null))
                .withConstraints(null, List.of(new ForeignKey("FK_C1", List.of("PV_ID"), null, "PV", List.of("ID"))), List.of());
        Table c2 = typedTable("C2", null, typed("PN_ID", 1, "number(10,0)", null, 10, 0))
                .withConstraints(null, List.of(new ForeignKey("FK_C2", List.of("PN_ID"), null, "PN", List.of("id"))), List.of());
        Table c3 = typedTable("C3", null, typed("PN_ID", 1, "VARCHAR2", 10L, null, null))
                .withConstraints(null, List.of(new ForeignKey("FK_C3", List.of("PN_ID"), null, "PN", List.of("ID"))), List.of());
        Table c4 = typedTable("C4", null, typed("PV_ID", 1, "VARCHAR2", null, null, null))
                .withConstraints(null, List.of(new ForeignKey("FK_C4", List.of("PV_ID"), null, "PV", List.of("ID"))), List.of());
        Table c5 = typedTable("C5", null, typed("X", 1, "VARCHAR2", 30L, null, null))
                .withConstraints(null, List.of(new ForeignKey("FK_C5", List.of("X"), null, "PV", List.of())), List.of());
        Table c6 = typedTable("C6", null, typed("X", 1, "NUMBER", null, null, null))
                .withConstraints(null, List.of(new ForeignKey("FK_C6", List.of("X"), null, "OUTSIDE", List.of("ID"))), List.of());
        StructureLint.Result r = StructureLint.run(snap(pv, pn, c1, c2, c3, c4, c5, c6), Options.DEFAULT);
        assertEquals(List.of("C1.PV_ID FK FK_C1 → PV.ID: 길이 20 ≠ 10", "C2.PN_ID FK FK_C2 → PN.ID: 정밀도 10,0 ≠ 8,2",
                "C3.PN_ID FK FK_C3 → PN.ID: 형식 VARCHAR2 ≠ NUMBER", "C5.X FK FK_C5 → PV.ID: 길이 30 ≠ 10"), of(r, Rule.FK_TYPE_MISMATCH),
                "길이 한쪽 모름(C4)·부모가 스냅샷 밖(C6)은 아니다");
        assertEquals(List.of(), of(StructureLint.run(snap(c1), Options.DEFAULT), Rule.FK_TYPE_MISMATCH), "부모가 범위·whole 둘 다 밖");
        assertEquals(List.of("C1.PV_ID FK FK_C1 → PV.ID: 길이 20 ≠ 10"), of(StructureLint.run(snap(c1), snap(pv, c1), Options.DEFAULT), Rule.FK_TYPE_MISMATCH),
                "whole 에만 있는 부모도 비교한다");
    }

    /** 설계 51 — 앞머리 덮임 · 똑같으면 이름 뒤 · PK·유니크가 이긴다 · 유니크는 똑같은 유니크만 · 같은 이름 한 번 · 빈 컬럼 아님 */
    @Test
    void dupIndexPrefixExactAndUniqueRules() {
        Table d1 = t("D1", List.of("ID", "A", "B"), "ID").withIndexes(List.of(new Index("IX_A", false, List.of("A")), new Index("IX_AB", false, List.of("a", "B"))));
        Table d2 = t("D2", List.of("ID", "A"), "ID").withIndexes(List.of(new Index("IX_2", false, List.of("A")), new Index("IX_1", false, List.of("A"))));
        Table d3 = t("D3", List.of("A", "B"), "A").withIndexes(List.of(new Index("IX_D3", false, List.of("A"))));
        Table d4 = t("D4", List.of("ID", "A", "B"), "ID").withConstraints(new PrimaryKey("PK_D4", List.of("ID")), List.of(), List.of(new UniqueKey("UQ_D4", List.of("A"))))
                .withIndexes(List.of(new Index("IX_D4", false, List.of("A", "B"))));
        Table d5 = t("D5", List.of("ID", "A"), "ID").withConstraints(new PrimaryKey("PK_D5", List.of("ID")), List.of(),
                List.of(new UniqueKey("UQ_2", List.of("A")), new UniqueKey("UQ_1", List.of("A"))));
        Table d6 = t("D6", List.of("ID", "A"), "ID").withConstraints(new PrimaryKey("PK_D6", List.of("ID")), List.of(), List.of(new UniqueKey("UQ_D6", List.of("A"))))
                .withIndexes(List.of(new Index("uq_d6", true, List.of("A")), new Index("IX_E", false, List.of())));
        // H2 꼴 — 제약 이름과 그 제약이 만든 유니크 인덱스 이름이 다르다(CONSTRAINT_8 ↔ PRIMARY_KEY_8)
        Table d7 = t("D7", List.of("A", "B"), "A").withConstraints(new PrimaryKey("CONSTRAINT_8", List.of("A")), List.of(), List.of(new UniqueKey("CONSTRAINT_9", List.of("B"))))
                .withIndexes(List.of(new Index("PRIMARY_KEY_8", true, List.of("a")), new Index("CONSTRAINT_INDEX_9", true, List.of("B")), new Index("UX_D7", true, List.of("B"))));
        // JDBC 스냅샷 꼴 — uniques 는 유니크 인덱스에서 와서 PK 를 받치는 인덱스가 uniques·indexes 양쪽에 든다
        Table d9 = t("D9", List.of("A", "B"), "A").withConstraints(new PrimaryKey("CONSTRAINT_8", List.of("A")), List.of(), List.of(new UniqueKey("PRIMARY_KEY_8", List.of("A"))))
                .withIndexes(List.of(new Index("PRIMARY_KEY_8", true, List.of("A"))));
        StructureLint.Result r = StructureLint.run(snap(d1, d2, d3, d4, d5, d6, d7, d9), Options.DEFAULT);
        assertEquals(List.of("D1.A 인덱스 IX_A ⊂ IX_AB(a,B)", "D2.A 인덱스 IX_2 ⊂ IX_1(A)", "D3.A 인덱스 IX_D3 ⊂ PK_D3(A)", "D5.A 인덱스 UQ_2 ⊂ UQ_1(A)",
                "D7.B 인덱스 UX_D7 ⊂ CONSTRAINT_9(B)"), of(r, Rule.DUP_INDEX),
                "유니크 앞머리(D4)·같은 이름 유니크 인덱스(D6)·빈 컬럼·제약을 받치는 다른 이름 유니크 인덱스 첫 것(D7)은 아니다");
        Table d8 = t("D8", List.of("A", "B"), null).withConstraints(null, List.of(), List.of(new UniqueKey("CONSTRAINT_9", List.of("B"))))
                .withIndexes(List.of(new Index("CONSTRAINT_INDEX_9", true, List.of("B"))));
        assertEquals(List.of("D8 유니크 1 · 인덱스 0"), of(StructureLint.run(snap(d8), Options.DEFAULT), Rule.NO_PK), "받치는 인덱스는 인덱스로 안 센다");
    }

    static Table linked(String name, String... parents) {
        List<ForeignKey> fks = new java.util.ArrayList<>();
        for (String p : parents) {
            fks.add(fk("FK_" + name + "_" + p, List.of("ID"), p));
        }
        return t(name, List.of("ID"), "ID").withConstraints(new PrimaryKey("PK_" + name, List.of("ID")), fks, List.of());
    }

    /** 설계 51 — 강연결 성분마다 하나, 자기 참조·순환 밖 꼬리·범위 밖 부모는 아니다 */
    @Test
    void fkCycleReportsEachComponentOnceWithoutSelfRef() {
        assertEquals(List.of("A 표 2: A·B"), of(StructureLint.run(snap(linked("B", "A"), linked("A", "B", "B")), Options.DEFAULT), Rule.FK_CYCLE));
        assertEquals(List.of(), of(StructureLint.run(snap(linked("S", "S")), Options.DEFAULT), Rule.FK_CYCLE), "자기 참조만");
        assertEquals(List.of("X 표 3: X·Y·Z"), of(StructureLint.run(snap(linked("X", "Y"), linked("Y", "Z"), linked("Z", "X"), linked("W", "X")), Options.DEFAULT),
                Rule.FK_CYCLE), "W 는 순환 밖");
        assertEquals(List.of(), of(StructureLint.run(snap(linked("P", "Q")), snap(linked("P", "Q"), linked("Q", "P")), Options.DEFAULT), Rule.FK_CYCLE),
                "부모가 범위 밖");
    }

    /** 2-28b — 프로필 키에서 Options */
    @Test
    void optionsFromProfile() {
        assertEquals(Options.DEFAULT, Options.of(null));
        Options o = Options.of(new kr.ejg.toolbox.core.profile.Profile.Lint(null, null, List.of("fkNoIndex")));
        assertEquals(new Options(0, List.of(), Set.of(Rule.FK_NO_INDEX)), o);
        assertEquals(new Options(30, List.of("REG_DT"), Set.of()), Options.of(new kr.ejg.toolbox.core.profile.Profile.Lint(30, List.of("REG_DT"), null)));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Options.of(new kr.ejg.toolbox.core.profile.Profile.Lint(0, List.of(), List.of("nope"))));
        for (Rule r : Rule.values()) {
            assertTrue(e.getMessage().contains(r.id), e.getMessage());
        }
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
