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
                        List.of(new ForeignKey("FK_ORD_CUST", List.of("CUST_ID"), null, "CUST", List.of("CUST_ID"), "CASCADE", null)),
                        List.of(new UniqueKey("UQ_ORD", List.of("ORD_NO", "CUST_ID"))))
                .withIndexes(List.of(new Index("IX_ORD_CUST", false, List.of("CUST_ID"), List.of("DESC"))))
                .withStats(0L, c, LocalDateTime.of(2024, 3, 1, 0, 0))
                .withChecks(List.of(new kr.ejg.toolbox.core.meta.Check("CK_ORD_AMT", "AMT >= 0")));
        Table parent = Table.of("A", "CUST", "TABLE", null).withColumns(List.of(
                col("CUST_ID", 1, "NUMBER", null, 10, 0, false, null, "고객ID")))
                .withConstraints(new PrimaryKey("PK_CUST", List.of("CUST_ID")), List.of(), List.of())
                .withStats(null, c, null);
        Table b = Table.of("B", "Z", "TABLE", "제트").withColumns(List.of(col("X", 1, "CHAR", 1L, null, null, true, null, null)))
                .withStats(5L, null, null);
        return List.of(new Schema("B", "Oracle Database 19c", List.of(b), 3145728L), new Schema("A", "Oracle Database 19c", List.of(child, parent)));
    }

    static List<Object> column(Doc d, String name) {
        return java.util.stream.IntStream.range(0, d.rows().size()).mapToObj(i -> d.cell(i, name)).toList();
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
        assertEquals(List.of(1, 2, 3), column(d02, "순번"), "R1 순번(확장 열로 뒤에 — 2-10)");
        assertEquals(List.of("CUST", "ORD", "Z"), column(d02, "영문 테이블명"), "R19 스키마 A 먼저, 이름순");
        Doc d03 = doc(docs, "03");
        assertEquals(List.of("CUST_ID", "ORD_NO", "CUST_ID", "AMT", "X"), column(d03, "영문 컬럼명"), "R19 컬럼 순번");
        assertEquals(5, d03.cell(4, "순번"), "R1 문서별 연번");
    }

    @Test
    void r2DbNameIsSchema() {
        Doc d10 = doc(Definitions.build(fixture(), OPT), "10");
        assertEquals("A", d10.cell(0, "영문 DB명"));
        assertEquals("A/B", doc(Definitions.build(fixture(), OPT), "01").cell(0, "영문 DB명"), "스키마 이름순 — 옵션 dbName 이 있으면 그것");
        assertEquals("MINWON", doc(Definitions.build(fixture(), new Definitions.Options(null, null, null, null, null, "MINWON", null, null)), "01")
                .cell(0, "영문 DB명"));
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
        assertEquals("", d02.cell(1, "최종수정일"), "R5 최종수정일은 빈칸 — lastDdlAt 이 있어도(사용자 2026-10-04, 2-13)");
        assertEquals("", d02.cell(1, "변경구분"), "R5 변경구분 빈칸");
        assertEquals("", d02.cell(0, "최종수정일"));
        assertEquals("", d02.cell(0, "변경구분"));
        assertEquals("", d02.cell(2, "최초등록일"), "R5 createdAt 없으면 빈칸");
        assertEquals("홍길동", doc(Definitions.build(fixture(), OPT), "03").cell(0, "최종수정자"));
    }

    @Test
    void r6MarkMissingCommentButKeepRow() {
        List<Doc> docs = Definitions.build(fixture(), OPT);
        assertEquals(Definitions.NO_COMMENT, doc(docs, "02").cell(0, "한글 테이블명"));
        assertEquals(Definitions.NO_COMMENT, doc(docs, "03").cell(2, "한글 컬럼명"), "ORD.CUST_ID");
        assertEquals("", doc(docs, "02").cell(0, "관련 엔터티명"), "재사용 칸엔 표시를 안 넣는다");
    }

    @Test
    void r7NotNullDirection() {
        Doc d03 = doc(Definitions.build(fixture(), OPT), "03");
        assertEquals("N", d03.cell(1, "Not Null 여부"), "R7 표준 표기 — ORD_NO nullable=false → N(사용자 2026-10-04, 2-10)");
        assertEquals("Y", d03.cell(3, "Not Null 여부"), "R7 AMT nullable=true → Y = Nullable");
    }

    @Test
    void r8ReuseKoreanNames() {
        Doc d03 = doc(Definitions.build(fixture(), OPT), "03");
        assertEquals("주문", d03.cell(1, "연관 엔터티명"));
        assertEquals("주문번호", d03.cell(1, "연관 속성명"));
    }

    @Test
    void r9r10r11LengthPkDefault() {
        Doc d03 = doc(Definitions.build(fixture(), OPT), "03");
        assertEquals("20", d03.cell(1, "데이터 길이"), "문자 length");
        assertEquals("10", d03.cell(2, "데이터 길이"), "수 precision");
        assertEquals("12,2", d03.cell(3, "데이터 길이"), "소수점 있으면 p,s");
        assertEquals("PK01", d03.cell(1, "PK정보"), "R10 PK + 참여 순서 두 자리(2-10)");
        assertEquals("", d03.cell(3, "PK정보"), "R10 PK 아니면 빈칸");
        assertEquals("AK_1-01", d03.cell(1, "AK정보"), "ORD_NO 는 UQ_ORD 첫째");
        assertEquals("AK_1-02", d03.cell(2, "AK정보"), "CUST_ID 는 UQ_ORD 둘째");
        assertEquals("CUST.CUST_ID", d03.cell(2, "FK정보"), "같은 스키마면 스키마 생략");
        assertEquals("", d03.cell(1, "FK정보"));
        assertEquals("DEFAULT 0; CHECK (AMT >= 0)", d03.cell(3, "제약조건"), "기본값 + 이 컬럼이 든 CHECK");
        assertEquals("", d03.cell(1, "제약조건"));
        assertEquals("0", d03.cell(3, "기본값"), "R11 원문");
    }

    @Test
    void r12Volume() {
        Doc d02 = doc(Definitions.build(fixture(), OPT), "02");
        assertEquals("", d02.cell(0, "테이블 볼륨"), "null → 빈칸");
        assertEquals(0L, d02.cell(1, "테이블 볼륨"), "0 → 0");
        assertEquals(5L, d02.cell(2, "테이블 볼륨"));
    }

    @Test
    void r13r14Relations() {
        Doc d04 = doc(Definitions.build(fixture(), OPT), "04");
        assertEquals(1, d04.rows().size());
        assertEquals("CUST", d04.cell(0, "부모 영문 테이블명"));
        assertEquals("", d04.cell(0, "부모 한글 테이블명"), "부모 코멘트 없음");
        assertEquals("고객ID", d04.cell(0, "부모 한글 컬럼명"), "R14 03 한글명 재사용");
        assertEquals("주문", d04.cell(0, "자식 한글 테이블명"));
        assertEquals("A", d04.cell(0, "부모 영문 DB명"), "refSchema 없으면 자식 스키마");
        assertEquals("CASCADE", d04.cell(0, "삭제규칙"), "R13 수집값(1-19)");
        assertEquals("", d04.cell(0, "갱신규칙"), "R13 모르면 빈칸 — Oracle 드라이버는 UPDATE_RULE 을 안 준다(1-19 실측)");
    }

    @Test
    void r15Indexes() {
        Doc d10 = doc(Definitions.build(fixture(), OPT), "10");
        List<Object> kinds = d10.rows().stream().filter(r -> r.get(2).equals("ORD")).map(r -> r.get(4)).toList();
        assertEquals(List.of("PK", "UNIQUE", "UNIQUE", "일반"), kinds, "PK·UNIQUE(2열)·일반");
        assertEquals(2, d10.cell(3, "컬럼순서"), "UQ_ORD 둘째 열");
        assertEquals("", d10.cell(0, "정렬"), "R15 PK 는 빈칸 — PK 인덱스는 안 모은다(1-20)");
        assertEquals("DESC", d10.cell(4, "정렬"), "R15 수집값(1-20)");
        assertEquals("", d10.cell(2, "정렬"), "R15 같은 이름 인덱스가 없는 UNIQUE 는 빈칸");
        assertEquals("N", d10.cell(4, "유니크여부"));
    }

    @Test
    void r16Constraints() {
        Doc d11 = doc(Definitions.build(fixture(), OPT), "11");
        assertEquals(List.of("PK", "PK", "UNIQUE", "CHECK"), d11.rows().stream().map(r -> r.get(4)).toList(), "CHECK 행(1-21)");
        assertEquals("ORD_NO, CUST_ID", d11.cell(2, "제약내용"));
        assertEquals("CK_ORD_AMT", d11.cell(3, "제약조건명"));
        assertEquals("AMT >= 0", d11.cell(3, "제약내용"), "R16 조건 글 그대로");
    }

    @Test
    void r17r18Doc01() {
        Doc d01 = doc(Definitions.build(fixture(), OPT), "01");
        assertEquals(3, d01.cell(0, "테이블 수"), "R17 02 행 수");
        assertEquals("3.0 MB", d01.cell(0, "데이터 용량"), "R17 아는 스키마 용량만 더한다(1-23)");
        assertEquals("", Definitions.size(List.of(new Schema("X", "v", List.of()))), "R17 전부 모르면 빈칸");
        assertEquals("2.0 GB", Definitions.size(List.of(new Schema("X", "v", List.of(), 2147483648L))));
        assertEquals("행정기관", d01.cell(0, "기관명"), "R18");
        assertEquals("", doc(Definitions.build(fixture(), Definitions.Options.empty()), "01").cell(0, "기관명"));
        assertEquals("Oracle Database 19c", d01.cell(0, "DBMS 정보"), "DBMS명 + 버전 한 칸 — 버전이 이름으로 시작하면 버전만(2-10)");
        assertEquals("정형", d01.cell(0, "DB 형태"));
        assertEquals("", d01.cell(0, "관련법령"), "사람이 채운다");
    }

    @Test
    void r20NoOrdinalColumnIn03() {
        Doc d03 = doc(Definitions.build(fixture(), OPT), "03");
        assertEquals(false, d03.columns().contains("컬럼순서"));
        assertEquals(25, d03.columns().size(), "표준 16 + 확장 3 + 꼬리 6(2-10)");
    }

    @Test
    void rowWidthMustMatchColumns() {
        assertThrows(IllegalArgumentException.class, () -> new Doc("x", "x", List.of("a", "b"), List.of(List.of(1))));
    }
}
