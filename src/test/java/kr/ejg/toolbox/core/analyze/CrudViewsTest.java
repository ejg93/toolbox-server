package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 6-21 — 모듈 자르기 · 세로 목록 · 모듈 매트릭스 합집합 */
class CrudViewsTest {

    static AnalyzeStore.ProgramRow row(String method, String url, String params, Map<String, String> crud) {
        return new AnalyzeStore.ProgramRow(1, "A", method, "A.java", 1, "GET", url, params, "view", "", List.of(), List.of(), crud);
    }

    @Test
    void module() {
        assertEquals("sec/gmt", CrudViews.module("/sec/gmt/EgovGroupList.do"));
        assertEquals("bbs", CrudViews.module("/bbs/list.do"));
        assertEquals("a/b", CrudViews.module("/a/b/c/d.do"));
        assertEquals("/", CrudViews.module("/other"));
        assertEquals("x", CrudViews.module("/x/y.do?z=1"));
        assertEquals("/", CrudViews.module(null));
        assertEquals("/", CrudViews.module(""));
        assertEquals("/", CrudViews.module("/"));
    }

    @Test
    void longRowsAndModuleMatrix() {
        List<AnalyzeStore.ProgramRow> rows = List.of(
                row("list", "/bbs/list.do", "", Map.of("COMTNBBS", "R", "T2", "C")),
                row("add", "/bbs/add.do", "cmd=Regist", Map.of("COMTNBBS", "CU")),
                row("other", "/other", null, Map.of("T2", "D")),
                row("none", "/bbs/none.do", "", Map.of()));
        List<CrudViews.LongRow> longs = CrudViews.longRows(rows);
        assertEquals(4, longs.size(), "CRUD 쌍 수 — 표 없는 프로그램은 줄이 없다");
        assertEquals(new CrudViews.LongRow("A.list", "/bbs/list.do", "bbs", "COMTNBBS", "R"), longs.get(0));
        assertEquals(new CrudViews.LongRow("A.add", "/bbs/add.do cmd=Regist", "bbs", "COMTNBBS", "CU"), longs.get(2));

        CrudViews.ModuleMatrix mm = CrudViews.moduleMatrix(rows);
        assertEquals(List.of("/", "bbs"), mm.modules());
        assertEquals(List.of(new CrudViews.ModuleRow("COMTNBBS", Map.of("bbs", "CRU")), new CrudViews.ModuleRow("T2", Map.of("/", "D", "bbs", "C"))),
                mm.rows());
    }

    @Test
    void union() {
        assertEquals("CRU", CrudViews.union("UR", "C"));
        assertEquals("", CrudViews.union("", ""));
        assertEquals("CRUD", CrudViews.union("DC", "UR"));
    }
}
