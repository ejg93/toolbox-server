package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.meta.Schema;
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
        assertEquals(List.of(new Consistency.Missing("COMVNUSERMASTER", 1)), r.missingInDb(), "코드가 쓰는데 DB 에 없다");
        assertEquals(List.of(new Consistency.Unused("APP", "COMTNUNUSED", "VIEW")), r.unusedInCode(), "소문자 comtnbbs 는 쓴다");
        assertEquals(List.of("Board.unused"), r.deadStatements());
        assertEquals(List.of("bbs/Other.jsp"), r.orphanJsps());

        Consistency.Report none = Consistency.of(analyze, snapshots, run, null).orElseThrow();
        assertTrue(none.missingInDb().isEmpty() && none.unusedInCode().isEmpty(), "스냅샷 없이 — 표 목록은 빈다");
        assertEquals(List.of("Board.unused"), none.deadStatements());
        assertTrue(Consistency.of(analyze, snapshots, run, 999L).isEmpty(), "없는 스냅샷");
    }
}
