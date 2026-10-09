package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 6-11 — 분석 실행(코드 표 COMTNBBS·COMVNUSERMASTER) × 스냅샷(comtnbbs 소문자·COMTNUNUSED 뷰) */
class ConsistencyTest {

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

    static Table table(String name, String type) {
        return new Table("APP", name, type, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void report() throws Exception {
        AnalyzeStore analyze = new AnalyzeStore(db);
        SnapshotStore snapshots = new SnapshotStore(db);
        long run = analyze.save("t", "C:/p", AnalyzeStoreTest.result());
        long snap = snapshots.save("t", "dev", null, List.of(new Schema("APP", null, List.of(table("comtnbbs", "TABLE"),
                table("COMTNUNUSED", "VIEW")))));

        Consistency.Report r = Consistency.of(analyze, snapshots, run, snap).orElseThrow();
        assertEquals(List.of(new Consistency.Missing("COMVNUSERMASTER", 1, "없음")), r.missingInDb(), "코드가 쓰는데 DB 에 없다 — scope 없는 옛 꼴");
        assertEquals(null, r.scopeSummary());
        assertEquals(List.of(new Consistency.Unused("APP", "COMTNUNUSED", "VIEW")), r.unusedInCode(), "소문자 comtnbbs 는 쓴다");
        assertEquals(List.of("Board.unused"), r.deadStatements());
        assertEquals(List.of("bbs/Other.jsp"), r.orphanJsps());

        Consistency.Report none = Consistency.of(analyze, snapshots, run, null).orElseThrow();
        assertTrue(none.missingInDb().isEmpty() && none.unusedInCode().isEmpty(), "스냅샷 없이 — 표 목록은 빈다");
        assertEquals(List.of("Board.unused"), none.deadStatements());
        assertTrue(Consistency.of(analyze, snapshots, run, 999L).isEmpty(), "없는 스냅샷");
    }

    /** 6-26 — view 가 가리키는데 폴더에 없는 JSP. 스냅샷 없이도 나오고, 파일 수를 안 남긴 옛 실행은 빈다 */
    @Test
    void missingJsps() throws Exception {
        AnalyzeStore analyze = new AnalyzeStore(db);
        SnapshotStore snapshots = new SnapshotStore(db);
        AnalyzeRunner.Result base = AnalyzeStoreTest.result();
        JavaGraph.Program gone = new JavaGraph.Program("BoardController", "gone", "web/BoardController.java", 60, "GET", "/bbs/gone.do", "",
                "view", List.of(new JavaGraph.View("view", "/bbs/Gone")), "", List.of());
        List<AnalyzeRunner.Row> rows = new java.util.ArrayList<>(base.rows());
        rows.add(new AnalyzeRunner.Row(gone, java.util.Map.of()));
        long run = analyze.save("t", "C:/p", new AnalyzeRunner.Result(rows, base.tables(), base.unresolved(), base.files(), base.skipped(),
                base.truncated(), base.statements(), base.jspLinks(), base.jsps(), base.orphans(), null,
                java.util.Map.of("bbs/BoardList", 1, "bbs/Gone", 0), java.util.Map.of()));
        assertEquals(List.of(new Consistency.MissingJsp("bbs/Gone", 1)), Consistency.of(analyze, snapshots, run, null).orElseThrow().missingJsps(),
                "앞 / 는 떼고 맞춘다 · 있는 JSP(BoardList)는 아니다");

        long old = analyze.save("t", "C:/p", new AnalyzeRunner.Result(base.rows(), base.tables(), base.unresolved(), base.files(),
                base.skipped(), base.truncated(), base.statements(), base.jspLinks(), base.jsps(), base.orphans()));
        assertEquals(List.of(), Consistency.of(analyze, snapshots, old, null).orElseThrow().missingJsps(), "옛 꼴(파일 수 없음) — 모름이라 빔");
    }

    /** 6-23 — 스냅샷 범위에 걸린 표는 「없음」 이 아니라 「범위 밖 — 규칙」. 스키마·빈 표는 코드 쪽에서 모르니 「…일 수 있음」 */
    @Test
    void reasonFollowsSnapshotScope() throws Exception {
        AnalyzeStore analyze = new AnalyzeStore(db);
        SnapshotStore snapshots = new SnapshotStore(db);
        long run = analyze.save("t", "C:/p", AnalyzeStoreTest.result());
        Scope scope = new Scope(List.of("APP"), new Scope.Exclude(List.of("COMVN"), null, null, null), null, true);
        long snap = snapshots.save("t", "dev", null, List.of(new Schema("APP", null, List.of(table("comtnbbs", "TABLE")))), scope, List.of());

        Consistency.Report r = Consistency.of(analyze, snapshots, run, snap).orElseThrow();
        assertEquals(List.of(new Consistency.Missing("COMVNUSERMASTER", 1, "범위 밖 — 제외 접두 COMVN")), r.missingInDb());
        assertEquals("스키마 APP · 제외 접두 COMVN · 빈 표 제외", r.scopeSummary());

        assertEquals("스키마 APP 밖일 수 있음", Consistency.reasonFor(new Scope(List.of("APP"), null, null, null), "X"));
        assertEquals("빈 표라 빠졌을 수 있음", Consistency.reasonFor(new Scope(null, null, null, true), "X"));
        assertEquals("없음", Consistency.reasonFor(Scope.all(), "X"));
        assertEquals("없음", Consistency.reasonFor(null, "X"));
    }
}
