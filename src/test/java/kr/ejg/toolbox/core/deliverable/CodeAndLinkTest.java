package kr.ejg.toolbox.core.deliverable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.sqlrun.ResultTable;
import org.junit.jupiter.api.Test;

/** 2-3 — 08 공통코드 후보 판정(PG 골든)·코드값 조회(H2)·09 연계 단서 */
class CodeAndLinkTest {

    @Test
    void codeCandidatesFromPostgresSnapshot() throws Exception {
        List<CodeAndLink.CodeTable> c = CodeAndLink.codeCandidates(GoldenFiles.schemas("meta/postgres-vendor.json"));
        assertEquals(List.of("code_groups", "codes"), c.stream().map(CodeAndLink.CodeTable::table).toList(),
                "products 는 code·name 컬럼이 있어도 표 이름·코멘트가 코드가 아니다");
        CodeAndLink.CodeTable groups = c.get(0);
        assertNull(groups.groupCol());
        assertEquals("group_code", groups.codeCol());
        assertEquals("group_name", groups.nameCol());
        CodeAndLink.CodeTable codes = c.get(1);
        assertEquals("group_code", codes.groupCol(), "코드 컬럼이 둘이면 GROUP 이 든 쪽이 그룹");
        assertEquals("code", codes.codeCol());
        assertEquals("code_name", codes.nameCol());
        assertEquals("sort_order", codes.sortCol());
        assertEquals("SELECT group_code, code, code_name FROM public.codes ORDER BY group_code, sort_order", CodeAndLink.codeSql(codes));
    }

    @Test
    void codeSqlRejectsNonIdentifiers() {
        CodeAndLink.CodeTable bad = new CodeAndLink.CodeTable("S", "T; DROP TABLE X", null, null, "CD", "NM", null, null, null);
        assertThrows(IllegalArgumentException.class, () -> CodeAndLink.codeSql(bad));
        CodeAndLink.CodeTable badCol = new CodeAndLink.CodeTable("S", "T", null, null, "CD", "NM /* x */", null, null, null);
        assertThrows(IllegalArgumentException.class, () -> CodeAndLink.codeSql(badCol));
    }

    @Test
    void codeDocReadsValues() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:codedoc;DB_CLOSE_DELAY=-1")) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE CMM_CD (GRP_CD VARCHAR(10), CD VARCHAR(10), CD_NM VARCHAR(50), CD_DESC VARCHAR(100), USE_YN CHAR(1), "
                        + "SORT_ORD INT)");
                st.execute("INSERT INTO CMM_CD VALUES ('SEX','M','남','남자','Y',1), ('SEX','F','여','여자','Y',2), ('YN','Y','예',NULL,'N',1)");
            }
            Table t = Table.of("PUBLIC", "CMM_CD", "TABLE", "공통코드").withColumns(List.of(
                    col("GRP_CD", 1), col("CD", 2), col("CD_NM", 3), col("CD_DESC", 4), col("USE_YN", 5), col("SORT_ORD", 6)));
            List<CodeAndLink.CodeTable> cands = CodeAndLink.codeCandidates(List.of(new Schema("PUBLIC", null, List.of(t))));
            assertEquals(1, cands.size());
            assertEquals("CD_DESC", cands.get(0).descCol());
            assertEquals("USE_YN", cands.get(0).useCol());
            Doc d = CodeAndLink.codeDoc(c, cands, new CodeAndLink.Options("기관", "팀"));
            assertEquals(3, d.rows().size());
            assertEquals(List.of("M", "F", "Y"), java.util.stream.IntStream.range(0, 3).mapToObj(i -> d.cell(i, "코드값")).toList(),
                    "그룹·정렬 컬럼 순");
            assertEquals("SEX", d.cell(0, "영문코드명"), "그룹 값");
            assertEquals("공통코드", d.cell(0, "한글코드명"), "표 코멘트");
            assertEquals("", d.cell(0, "코드설명"), "코드설명은 사람이 채운다(2-12)");
            assertEquals("남", d.cell(0, "코드값 의미"));
            assertEquals("남자", d.cell(0, "코드값설명"));
            assertEquals("", d.cell(2, "코드값설명"), "NULL → 빈칸");
            assertEquals("N", d.cell(2, "사용여부"));
            assertEquals("기관", d.cell(0, "기관명"));
            assertEquals("PUBLIC", d.cell(0, "DB명"));
        }
    }

    static Column col(String name, int ord) {
        return new Column(name, ord, "VARCHAR", 12, 10L, null, null, true, null, null, null);
    }

    @Test
    void linkCandidatesAndDoc() {
        Table rcv = Table.of("S", "IF_ORDER_RCV", "TABLE", null).withColumns(List.of(col("ORD_NO", 1)));
        Table snd = Table.of("S", "T_SND_LOG", "TABLE", null).withColumns(List.of(col("LOG_ID", 1), col("SENT_AT", 2)));
        Table byComment = Table.of("S", "TB_X", "TABLE", "외부 연계 테이블").withColumns(List.of(
                new Column("X_NO", 1, "NUMBER", 2, null, 10, 0, false, null, "연계번호", null)));
        Table view = Table.of("S", "V_OPEN", "VIEW", null).withColumns(List.of(col("OPEN_CD", 1)));
        Table plain = Table.of("S", "TB_CUST", "TABLE", "고객");
        List<CodeAndLink.LinkCandidate> c = CodeAndLink.linkCandidates(List.of(new Schema("S", null, List.of(rcv, snd, byComment, view, plain))));
        assertEquals(List.of("IF_ORDER_RCV", "TB_X", "T_SND_LOG", "V_OPEN"), c.stream().map(CodeAndLink.LinkCandidate::name).toList());
        assertEquals("뷰", c.get(3).kind());
        assertTrue(c.get(0).reason().contains("이름 IF") && c.get(0).reason().contains("이름 RCV"), c.get(0).reason());
        assertEquals("ORD_NO(VARCHAR 10)", c.get(0).items());
        Doc d = CodeAndLink.linkDoc(c);
        assertEquals(5, d.rows().size(), "09 행 수 = 후보 표 컬럼 합(1 + 1 + 2 + 1, 2-12)");
        assertEquals("ORD_NO", d.cell(0, "연계 항목명"), "코멘트 없으면 영문 컬럼명");
        assertEquals("연계번호", d.cell(1, "연계 항목명"), "한글 컬럼명 = 컬럼 코멘트");
        assertEquals("연계번호", d.cell(1, "연계 항목 설명"));
        assertEquals("NUMBER", d.cell(1, "데이터 타입"));
        assertEquals("S", d.cell(2, "출처 DB명"));
        assertEquals("T_SND_LOG", d.cell(3, "출처 테이블명"));
        assertEquals("SENT_AT", d.cell(3, "출처 컬럼명"));
        assertEquals("", d.cell(0, "연계정보 구분"), "구분·정보명·주기·기관은 사람이 채운다");
        assertEquals("", d.cell(0, "연계 주기"));
        assertEquals("뷰", d.cell(4, "방식"));
    }

    @Test
    void dbLinks() {
        assertTrue(CodeAndLink.dbLinkSql("oracle").contains("ALL_DB_LINKS"));
        assertTrue(CodeAndLink.dbLinkSql("mssql").contains("sys.servers"));
        assertTrue(CodeAndLink.dbLinkSql("postgresql").contains("pg_foreign_server"));
        assertNull(CodeAndLink.dbLinkSql("h2"));
        ResultTable r = new ResultTable(List.of(new ResultTable.Col("OWNER", "VARCHAR"), new ResultTable.Col("DB_LINK", "VARCHAR")),
                List.of(List.of("PUBLIC", "HR.EXAMPLE.COM")), false, -1, 0);
        List<CodeAndLink.LinkCandidate> l = CodeAndLink.dbLinks(r);
        assertEquals("DB링크", l.get(0).kind());
        assertEquals("DB링크", CodeAndLink.linkDoc(l).cell(0, "방식"));
    }
}
