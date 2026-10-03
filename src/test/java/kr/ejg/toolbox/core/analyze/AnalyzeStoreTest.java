package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.db.Db;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 6-4 — 분석 이력 저장·조회. 식별자·글자·설명만, 지우면 자식 표까지 */
class AnalyzeStoreTest {

    @TempDir
    Path tmp;
    Db db;

    @BeforeEach
    void open() {
        db = Db.open(tmp.resolve("data"));
    }

    @AfterEach
    void close() {
        db.close();
    }

    static AnalyzeRunner.Result result() {
        JavaGraph.Program list = new JavaGraph.Program("BoardController", "list", "web/BoardController.java", 27, "ANY", "/bbs/list.do", "",
                "view", List.of(new JavaGraph.View("view", "bbs/BoardList"), new JavaGraph.View("redirect", "/login.do")), "목록을 조회한다.",
                List.of(new JavaGraph.Stmt("Board.selectList", "literal")));
        JavaGraph.Program add = new JavaGraph.Program("BoardController", "add", "web/BoardController.java", 40, "GET,POST", "/bbs/add.do",
                "cmd=Regist", "view", List.of(), "x".repeat(150), List.of(new JavaGraph.Stmt("Login.updateIncorrect", "prefix")));
        return new AnalyzeRunner.Result(List.of(new AnalyzeRunner.Row(list, Map.of("COMTNBBS", "R", "COMVNUSERMASTER", "R")),
                new AnalyzeRunner.Row(add, Map.of("COMTNBBS", "CR"))), List.of("COMTNBBS", "COMVNUSERMASTER"),
                List.of(new Unresolved("prefix", "service/impl/BoardDAO.java", 27, "Login.updateIncorrect")), 12, 0, false, 9,
                Map.of("bbs/BoardList.jsp", List.of("/bbs/add.do", "/bbs/list.do"), "bbs/Other.jsp", List.of("/bbs/list.do")), 2,
                List.of(new AnalyzeRunner.Orphan("jsp", "bbs/Other.jsp"), new AnalyzeRunner.Orphan("statement", "Board.unused")));
    }

    @Test
    void saveAndRead() throws Exception {
        AnalyzeStore store = new AnalyzeStore(db);
        long a = store.save("t", "C:/p", result());
        long b = store.save("t", "C:/q", result());
        assertEquals(List.of(b, a), store.runs().stream().map(AnalyzeStore.RunInfo::id).toList());
        AnalyzeStore.RunInfo info = store.run(a).orElseThrow();
        assertEquals(2, info.programs());
        assertEquals(9, info.statements());
        assertEquals(2, info.tables());
        assertEquals(1, info.unresolved());

        List<AnalyzeStore.ProgramRow> ps = store.programs(a);
        assertEquals(List.of("list", "add"), ps.stream().map(AnalyzeStore.ProgramRow::method).toList());
        assertEquals(List.of("view:bbs/BoardList", "redirect:/login.do"), ps.get(0).views().stream().map(v -> v.kind() + ":" + v.name()).toList());
        assertEquals("Board.selectList", ps.get(0).statements().get(0).id());
        assertEquals(Map.of("COMTNBBS", "R", "COMVNUSERMASTER", "R"), ps.get(0).crud());
        assertEquals(100, ps.get(1).description().length(), "설명은 100자");
        assertEquals("cmd=Regist", ps.get(1).params());
        assertEquals("GET,POST", ps.get(1).verb(), "배열 method 는 정렬한 이름 — 자르지 않는다(6-8)");

        AnalyzeStore.Matrix m = store.crud(a);
        assertEquals(List.of("COMTNBBS", "COMVNUSERMASTER"), m.tables());
        assertEquals("CR", m.rows().get(1).crud().get("COMTNBBS"));
        assertEquals(List.of(new Unresolved("prefix", "service/impl/BoardDAO.java", 27, "Login.updateIncorrect")), store.unresolved(a));

        // 6-11 고아 — 종류·이름 순
        assertEquals(List.of(new AnalyzeRunner.Orphan("jsp", "bbs/Other.jsp"), new AnalyzeRunner.Orphan("statement", "Board.unused")),
                store.orphans(a));

        // 6-6 영향도 — 표 → 프로그램 → JSP. 소문자 표 이름도 맞춘다
        AnalyzeStore.Impact im = store.impact(a, "comtnbbs");
        assertEquals("COMTNBBS", im.table());
        assertEquals(List.of("list", "add"), im.rows().stream().map(r -> r.program().method()).toList());
        assertEquals(List.of("bbs/BoardList.jsp", "bbs/Other.jsp"), im.rows().get(0).jsps(), "/bbs/list.do 를 부르는 JSP 둘");
        assertEquals(List.of("bbs/BoardList.jsp"), im.rows().get(1).jsps(), "/bbs/add.do 는 한 JSP 만 부른다");
        assertEquals(List.of("bbs/BoardList.jsp", "bbs/Other.jsp"), im.jsps());
        assertEquals(List.of("list"), store.impact(a, "COMVNUSERMASTER").rows().stream().map(r -> r.program().method()).toList());
        assertEquals(List.of(), store.impact(a, "NOPE").rows(), "없는 표는 빈 목록");

        try (Connection c = db.connect(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM analyze_run WHERE id = " + a);
            for (String t : new String[] {"analyze_program", "analyze_view", "analyze_stmt", "analyze_crud", "analyze_unresolved", "analyze_jsp_link", "analyze_orphan"}) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t)) {
                    rs.next();
                    assertTrue(rs.getInt(1) > 0, t + " — 다른 실행 b 의 행은 남는다");
                }
            }
        }
        assertTrue(store.programs(a).isEmpty(), "지운 실행의 프로그램은 같이 지워진다");
        assertEquals(2, store.programs(b).size());
    }
}
