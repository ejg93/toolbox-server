package kr.ejg.toolbox.core.deliverable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;
import org.junit.jupiter.api.Test;

/** 2-1 — PG 벤더 스냅샷 골든 여섯 + 수기 규칙 R1~R20(작은 픽스처) */
class DefinitionsTest {

    static final Definitions.Options OPT = new Definitions.Options("홍길동", "행정기관", "정보화팀", "민원", "민원 DB", null, "민원DB", "Linux");

    static Column col(String name, int ord, String type, Long len, Integer p, Integer s, boolean nullable, String def, String comment) {
        return new Column(name, ord, type, null, len, p, s, nullable, def, comment, null);
    }

    /** B 스키마 1개, A 스키마 2개 — 정렬(R19)을 보려고 입력 순서를 뒤섞는다 */
    static List<Schema> fixture() {
        LocalDateTime c = LocalDateTime.of(2024, 1, 2, 9, 0);
        Table child = Table.of("A", "ORD", "TABLE", "주문").withColumns(List.of(
                col("CUST_ID", 2, "NUMBER", null, 10, 0, false, null, null),
                col("ORD_NO", 1, "VARCHAR2", 20L, null, null, false, null, "주문번호"),
                col("AMT", 3, "NUMBER", null, 12, 2, true, "0", "금액")))
                .withConstraints(new PrimaryKey("PK_ORD", List.of("ORD_NO")),
                        List.of(new ForeignKey("FK_ORD_CUST", List.of("CUST_ID"), null, "CUST", List.of("CUST_ID"))),
                        List.of(new UniqueKey("UQ_ORD", List.of("ORD_NO", "CUST_ID"))))
                .withIndexes(List.of(new Index("IX_ORD_CUST", false, List.of("CUST_ID"))))
                .withStats(0L, c, LocalDateTime.of(2024, 3, 1, 0, 0));
        Table parent = Table.of("A", "CUST", "TABLE", null).withColumns(List.of(
                col("CUST_ID", 1, "NUMBER", null, 10, 0, false, null, "고객ID")))
                .withConstraints(new PrimaryKey("PK_CUST", List.of("CUST_ID")), List.of(), List.of())
                .withStats(null, c, null);
        Table b = Table.of("B", "Z", "TABLE", "제트").withColumns(List.of(col("X", 1, "CHAR", 1L, null, null, true, null, null)))
                .withStats(5L, null, null);
        return List.of(new Schema("B", "Oracle Database 19c", List.of(b)), new Schema("A", "Oracle Database 19c", List.of(child, parent)));
    }

    static Doc doc(List<Doc> docs, String no) {
        return docs.stream().filter(d -> d.no().equals(no)).findFirst().orElseThrow();
    }

    @Test
    void goldenOnPostgresVendorSnapshot() throws Exception {
        List<Doc> docs = Definitions.build(GoldenFiles.schemas("meta/postgres-vendor.json"), OPT);
        for (Doc d : docs) {
            GoldenFiles.assertJson("deliverable/pg-" + d.no() + ".json", d);
        }
    }

    @Test
    void r1SequenceAndR19Order() {
        List<Doc> docs = Definitions.build(fixture(), OPT);
        Doc d02 = doc(docs, "02");
        assertEquals(List.of(1, 2, 3), d02.rows().stream().map(r -> r.get(0)).toList(), "R1 순번");
        assertEquals(List.of("CUST", "ORD", "Z"), d02.rows().stream().map(r -> r.get(2)).toList(), "R19 스키마 A 먼저, 이름순");
        Doc d03 = doc(docs, "03");
        assertEquals(List.of("CUST_ID", "ORD_NO", "CUST_ID", "AMT", "X"), d03.rows().stream().map(r -> r.get(3)).toList(), "R19 컬럼 순번");
        assertEquals(5, d03.cell(4, "순번"), "R1 문서별 연번");
    }

    @Test
    void r2DbNameIsSchema() {
        Doc d10 = doc(Definitions.build(fixture(), OPT), "10");
        assertEquals("A", d10.cell(0, "DB명"));
        assertEquals("A/B", doc(Definitions.build(fixture(), OPT), "01").cell(0, "물리DB명"), "스키마 이름순 — 옵션 dbName 이 있으면 그것");
        assertEquals("MINWON", doc(Definitions.build(fixture(), new Definitions.Options(null, null, null, null, null, "MINWON", null, null)), "01")
                .cell(0, "물리DB명"));
    }

    @Test
    void r3r4r5TailSameIn02And03() {
        List<Doc> docs = Definitions.build(fixture(), new Definitions.Options(" ", null, null, null, null, null, null, null));
        Doc d02 = doc(docs, "02");
        Doc d03 = doc(docs, "03");
        for (String col : List.of("최초등록일", "최종수정일", "최종수정자", "변경구분")) {
            assertEquals(d02.cell(1, col), d03.cell(1, col), "R3 ORD — " + col);
        }
        assertEquals(Definitions.NO_AUTHOR, d02.cell(0, "최종수정자"), "R4 작성자 비면 표시");
        assertEquals("2024-01-02", d02.cell(1, "최초등록일"));
        assertEquals("2024-03-01", d02.cell(1, "최종수정일"));
        assertEquals("수정", d02.cell(1, "변경구분"), "R5 lastDdlAt 이 등록일 뒤");
        assertEquals("2024-01-02", d02.cell(0, "최종수정일"), "R5 lastDdlAt 없으면 등록일");
        assertEquals("신규", d02.cell(0, "변경구분"));
        assertEquals("", d02.cell(2, "최초등록일"), "R5 createdAt 없으면 빈칸");
        assertEquals("홍길동", doc(Definitions.build(fixture(), OPT), "03").cell(0, "최종수정자"));
    }

    @Test
    void r6MarkMissingCommentButKeepRow() {
        List<Doc> docs = Definitions.build(fixture(), OPT);
        assertEquals(Definitions.NO_COMMENT, doc(docs, "02").cell(0, "테이블명(한글)"));
        assertEquals(Definitions.NO_COMMENT, doc(docs, "03").cell(2, "컬럼명(한글)"), "ORD.CUST_ID");
        assertEquals("", doc(docs, "02").cell(0, "관련엔터티명"), "재사용 칸엔 표시를 안 넣는다");
    }

    @Test
    void r7NotNullDirection() {
        Doc d03 = doc(Definitions.build(fixture(), OPT), "03");
        assertEquals("Y", d03.cell(1, "Not Null"), "ORD_NO nullable=false → Y");
        assertEquals("N", d03.cell(3, "Not Null"), "AMT nullable=true → N");
    }

    @Test
    void r8ReuseKoreanNames() {
        Doc d03 = doc(Definitions.build(fixture(), OPT), "03");
        assertEquals("주문", d03.cell(1, "연관엔터티명"));
        assertEquals("주문번호", d03.cell(1, "연관속성명"));
    }

    @Test
    void r9r10r11LengthPkDefault() {
        Doc d03 = doc(Definitions.build(fixture(), OPT), "03");
        assertEquals("20", d03.cell(1, "데이터길이"), "문자 length");
        assertEquals("10", d03.cell(2, "데이터길이"), "수 precision");
        assertEquals("12,2", d03.cell(3, "데이터길이"), "소수점 있으면 p,s");
        assertEquals("Y", d03.cell(1, "PK정보"));
        assertEquals("N", d03.cell(3, "PK정보"));
        assertEquals("0", d03.cell(3, "기본값"), "R11 원문");
    }

    @Test
    void r12Volume() {
        Doc d02 = doc(Definitions.build(fixture(), OPT), "02");
        assertEquals("", d02.cell(0, "테이블볼륨(건)"), "null → 빈칸");
        assertEquals(0L, d02.cell(1, "테이블볼륨(건)"), "0 → 0");
        assertEquals(5L, d02.cell(2, "테이블볼륨(건)"));
    }

    @Test
    void r13r14Relations() {
        Doc d04 = doc(Definitions.build(fixture(), OPT), "04");
        assertEquals(1, d04.rows().size());
        assertEquals("CUST", d04.cell(0, "부모 영문테이블명"));
        assertEquals("", d04.cell(0, "부모 한글테이블명"), "부모 코멘트 없음");
        assertEquals("고객ID", d04.cell(0, "부모 한글컬럼명"), "R14 03 한글명 재사용");
        assertEquals("주문", d04.cell(0, "자식 한글테이블명"));
        assertEquals("A", d04.cell(0, "부모 영문DB명"), "refSchema 없으면 자식 스키마");
        assertEquals("", d04.cell(0, "삭제규칙"), "R13 스냅샷에 없다");
        assertEquals("NO ACTION", d04.cell(0, "갱신규칙"), "R13 Oracle 은 ON UPDATE 미지원");
    }

    @Test
    void r15Indexes() {
        Doc d10 = doc(Definitions.build(fixture(), OPT), "10");
        List<Object> kinds = d10.rows().stream().filter(r -> r.get(2).equals("ORD")).map(r -> r.get(4)).toList();
        assertEquals(List.of("PK", "UNIQUE", "UNIQUE", "일반"), kinds, "PK·UNIQUE(2열)·일반");
        assertEquals(2, d10.cell(3, "컬럼순서"), "UQ_ORD 둘째 열");
        assertEquals("ASC", d10.cell(0, "정렬"));
        assertEquals("N", d10.cell(4, "유니크여부"));
    }

    @Test
    void r16Constraints() {
        Doc d11 = doc(Definitions.build(fixture(), OPT), "11");
        assertEquals(List.of("PK", "PK", "UNIQUE"), d11.rows().stream().map(r -> r.get(4)).toList(), "CHECK 는 없다");
        assertEquals("ORD_NO, CUST_ID", d11.cell(2, "제약내용"));
    }

    @Test
    void r17r18Doc01() {
        Doc d01 = doc(Definitions.build(fixture(), OPT), "01");
        assertEquals(3, d01.cell(0, "테이블수"), "R17 02 행 수");
        assertEquals("", d01.cell(0, "데이터용량"));
        assertEquals("행정기관", d01.cell(0, "기관명"), "R18");
        assertEquals("", doc(Definitions.build(fixture(), Definitions.Options.empty()), "01").cell(0, "기관명"));
        assertEquals("Oracle", d01.cell(0, "DBMS명"));
        assertEquals("Oracle Database 19c", d01.cell(0, "DBMS버전"));
    }

    @Test
    void r20NoOrdinalColumnIn03() {
        Doc d03 = doc(Definitions.build(fixture(), OPT), "03");
        assertEquals(false, d03.columns().contains("컬럼순서"));
        assertEquals(19, d03.columns().size(), "13 + 꼬리 6");
    }

    @Test
    void rowWidthMustMatchColumns() {
        assertThrows(IllegalArgumentException.class, () -> new Doc("x", "x", List.of("a", "b"), List.of(List.of(1))));
    }
}
