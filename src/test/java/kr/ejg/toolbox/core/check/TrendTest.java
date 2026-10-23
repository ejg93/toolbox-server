package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.db.Db;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 5-36 — 추이: 같은 프로필·폴더의 전체 검사만, 지문이 다르면 비교 안 함, (파일, 규칙) 다중집합 차, 폴더 묶음 */
class TrendTest {

    @TempDir
    Path tmp;
    Db db;
    CheckStore store;
    String proj;

    @BeforeEach
    void open() {
        db = Db.open(tmp.resolve("data"));
        store = new CheckStore(db);
        proj = tmp.resolve("proj").toAbsolutePath().toString();
    }

    @AfterEach
    void close() {
        db.close();
    }

    static Finding f(String file, String group, String rule, String sev) {
        return new Finding(file, 1, group, rule, sev, null);
    }

    long save(String path, boolean changedOnly, String hash, List<Finding> fs) throws Exception {
        return store.save("t", path, changedOnly, List.of("common"), fs, 1, 10, 1000, hash);
    }

    @Test
    void samePathFullRunsOnly() throws Exception {
        save(proj, false, "h1", List.of());
        save(tmp.resolve("other").toString(), false, "h1", List.of());
        save(proj, true, "h1", List.of());
        save(proj, false, "h1", List.of());
        save(proj, false, "h1", List.of());
        Trend.Report r = Trend.of(store, "t", proj, 2, 20);
        assertEquals(3, r.points().size());
        assertEquals(1, r.skippedChangedOnly());
        assertEquals(List.of(), Trend.of(store, "다른 프로필", proj, 2, 20).points());
        assertEquals(2, Trend.of(store, "t", proj, 2, 2).points().size(), "limit — 최근 둘");
    }

    @Test
    void fixedAndAddedAreMultisetDifferences() throws Exception {
        save(proj, false, "h1", List.of(f("A.java", "common", "common.sysout", "warn"), f("A.java", "common", "common.sysout", "warn"),
                f("B.java", "common", "common.todo", "info")));
        save(proj, false, "h1", List.of(f("A.java", "common", "common.sysout", "warn"), f("C.java", "common", "common.todo", "info")));
        List<Trend.Point> ps = Trend.of(store, "t", proj, 2, 20).points();
        Trend.Point first = ps.get(0);
        assertNull(first.fixed());
        assertFalse(first.rulesChanged());
        assertEquals(3, first.total());
        assertEquals(Map.of("warn", 2, "info", 1), first.bySeverity());
        assertEquals(Map.of("common", 3), first.byGroup());
        Trend.Point second = ps.get(1);
        assertEquals(2, second.fixed(), "sysout 하나 + B 의 todo");
        assertEquals(1, second.added(), "C 의 todo");
        assertEquals(List.of(10, 1000, 1), List.of(second.files(), second.lines(), second.suppressed()));
    }

    @Test
    void differentRulesAreMarkedNotCompared() throws Exception {
        save(proj, false, "h1", List.of(f("A.java", "common", "common.todo", "info")));
        save(proj, false, "h2", List.of());
        save(proj, false, null, List.of());
        List<Trend.Point> ps = Trend.of(store, "t", proj, 2, 20).points();
        assertTrue(ps.get(1).rulesChanged());
        assertNull(ps.get(1).fixed());
        assertNull(ps.get(1).added());
        assertTrue(ps.get(2).rulesChanged(), "지문 없음(옛 실행)도 다름");
    }

    @Test
    void foldersGroupLastTwoRuns() throws Exception {
        save(proj, false, "h1", List.of(f("src/a/A.java", "common", "common.todo", "info")));
        save(proj, false, "h1", List.of(f("src/a/A.java", "common", "common.todo", "info"), f("src/a/x/B.java", "common", "common.todo", "info"),
                f("src/b/C.java", "common", "common.todo", "info"), f("Root.java", "common", "common.todo", "info")));
        Trend.Report two = Trend.of(store, "t", proj, 2, 20);
        assertEquals(List.of(new Trend.Folder("src/a", 2, 1), new Trend.Folder("(루트)", 1, 0), new Trend.Folder("src/b", 1, 0)), two.folders());
        Trend.Report one = Trend.of(store, "t", proj, 1, 20);
        assertEquals(List.of(new Trend.Folder("src", 3, 1), new Trend.Folder("(루트)", 1, 0)), one.folders());
    }

    @Test
    void pathMatchIgnoresCaseSlashAndTrailingSeparator() throws Exception {
        save(proj, false, "h1", List.of());
        String variant = proj.toUpperCase(java.util.Locale.ROOT) + File.separator;
        if (File.separatorChar == '\\') {
            variant = variant.replace('\\', '/');
        }
        assertEquals(1, Trend.of(store, "t", variant, 2, 20).points().size(), variant);
    }
}
