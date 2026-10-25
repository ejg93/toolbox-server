package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 8-17 — 키 꼴·같은 키 번호·파일 왕복·차이 셋 */
class BaselineTest {

    @Test
    void keysNumberRepeats() {
        Finding a = new Finding("a/B.java", 3, "java", "java.unusedImport", "warn", "import x.Y;");
        Finding b = new Finding("a/B.java", 9, "java", "java.unusedImport", "warn", "import x.Y;");
        assertEquals(List.of("a/B.java|java.unusedImport|import x.Y;", "a/B.java|java.unusedImport|import x.Y; #2"),
                Baseline.keys(List.of(a, b)));
    }

    @Test
    void writeReadRoundTrip(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("sub/b.txt");
        Baseline.write(f, List.of("z|r|e", "a|r|e"));
        assertEquals("a|r|e\nz|r|e\n", Files.readString(f, StandardCharsets.UTF_8));
        Files.writeString(f, "a|r|e\n\n  z|r|e  \n", StandardCharsets.UTF_8);
        assertEquals(Set.of("a|r|e", "z|r|e"), Baseline.read(f));
        Baseline.write(f, List.of());
        assertEquals("", Files.readString(f, StandardCharsets.UTF_8));
    }

    @Test
    void diffCountsFreshHiddenStale() {
        Baseline.Diff d = Baseline.diff(List.of("a|r|1", "b|r|2", "c|r|3"), Set.of("a|r|1", "b|r|2", "gone|r|9"));
        assertEquals(List.of("c|r|3"), d.fresh());
        assertEquals(2, d.hidden());
        assertEquals(1, d.stale());
    }
}
