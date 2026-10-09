package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 6-28 — 화면 전수: 행 포함·제외 · JSP 파일 판정 · 부르는 화면(꼴 묶음) · 순서 */
class ScreensTest {

    static AnalyzeStore.ProgramRow row(String method, String url, String kind, List<JavaGraph.View> views) {
        return new AnalyzeStore.ProgramRow(1, "BoardController", method, "web/BoardController.java", url.length(), "GET", url, "", kind, "",
                views, List.of(), Map.of());
    }

    static JavaGraph.View view(String name) {
        return new JavaGraph.View("view", name);
    }

    /** A 없음 · B 있음·여럿 · C json · D redirect 만 · E 동적(뷰 없음) · F 파일 수 모름 · G 팝업으로 불림 */
    static List<AnalyzeStore.ProgramRow> programs() {
        return List.of(
                row("list", "/bbs/list.do", "view", List.of(view("sample/bbs/BoardList"))),
                row("detail", "/bbs/detail.do", "view", List.of(view("sample/bbs/BoardDetail"), view("/sample/bbs/Other"))),
                row("json", "/bbs/json.do", "json", List.of()),
                row("go", "/bbs/go.do", "view", List.of(new JavaGraph.View("redirect", "/x.do"))),
                row("dyn", "/bbs/dyn.do", "view", List.of()),
                row("other", "/other", "view", List.of(view("sample/other/List"))),
                row("pop", "/bbs/pop.do", "view", List.of(view("sample/bbs/Pop"))));
    }

    static final Map<String, Integer> FILES = Map.of("sample/bbs/BoardList", 0, "sample/bbs/BoardDetail", 1, "sample/bbs/Other", 2,
            "sample/bbs/Pop", 1);

    static final List<AnalyzeStore.JspLink> LINKS = List.of(
            new AnalyzeStore.JspLink("jsp/bbs/List.jsp", "/bbs/detail.do", "link"),
            new AnalyzeStore.JspLink("jsp/bbs/List.jsp", "/bbs/detail.do", "popup"),
            new AnalyzeStore.JspLink("jsp/bbs/List.jsp", "/bbs/list.do", null),
            new AnalyzeStore.JspLink("jsp/sample/bbs/BoardList.jsp", "/bbs/pop.do", "popup"));

    @Test
    void rowsAndExcluded() {
        Screens.Report r = Screens.of(programs(), FILES, LINKS, List.of());
        assertEquals(List.of("/other", "/bbs/detail.do", "/bbs/dyn.do", "/bbs/list.do", "/bbs/pop.do"), r.rows().stream().map(Screens.Row::url).toList(),
                "모듈(/ → bbs, CRUD 모듈 열과 같은 순) → URL 순, json·redirect 만은 뺀다");
        assertEquals(List.of(1, 2, 3, 4, 5), r.rows().stream().map(Screens.Row::no).toList());
        Map<String, Screens.Row> by = new java.util.HashMap<>();
        r.rows().forEach(x -> by.put(x.url(), x));
        assertEquals(Screens.FILE_NO, by.get("/bbs/list.do").jspFile());
        assertEquals("있음·여럿", by.get("/bbs/detail.do").jspFile(), "뷰 둘 — 판정이 다르면 잇는다(앞 / 뗌)");
        assertEquals(Screens.FILE_UNKNOWN, by.get("/bbs/dyn.do").jspFile(), "동적 뷰 — 모름");
        assertEquals(Screens.FILE_UNKNOWN, by.get("/other").jspFile(), "파일 수가 없는 뷰(옛 실행) — 모름");
        assertEquals("/", by.get("/other").module());
        assertEquals(List.of(new Screens.Caller("jsp/bbs/List.jsp", List.of("link", "popup"))), by.get("/bbs/detail.do").callers());
        assertEquals(List.of(new Screens.Caller("jsp/bbs/List.jsp", List.of("모름"))), by.get("/bbs/list.do").callers(), "옛 행 꼴 null — 모름");
        assertEquals(List.of(new Screens.Excluded("json", 1), new Screens.Excluded("redirect", 1)), r.excluded());
        assertFalse(r.menuLoaded());
        assertEquals("BoardController.list", by.get("/bbs/list.do").program());
    }
}
