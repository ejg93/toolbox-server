package kr.ejg.toolbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

/** V-1 — 실물 표본 도우미. baseline·지문은 표본 없이 재고, 실제 표본 지문 대조만 corpus 태그 */
class CorpusFilesTest {

    @TempDir
    Path tmp;

    @Test
    void baselineCatchesNewBrokenFixedAndOverLimit() throws IOException {
        Path f = tmp.resolve("strip-java.txt");
        CorpusFiles.baseline(f, "t", List.of("b/2.java", "a/1.java"), 100, true);
        assertEquals("a/1.java\nb/2.java\n", Files.readString(f), "정렬해 쓴다");

        assertDoesNotThrow(() -> CorpusFiles.baseline(f, "t", List.of("a/1.java", "b/2.java"), 100, false));
        String added = assertThrows(AssertionFailedError.class,
                () -> CorpusFiles.baseline(f, "t", List.of("a/1.java", "b/2.java", "c/3.java"), 100, false)).getMessage();
        assertTrue(added.contains("새로 깨짐 1 [c/3.java]"), added);
        String fixed = assertThrows(AssertionFailedError.class,
                () -> CorpusFiles.baseline(f, "t", List.of("a/1.java"), 100, false)).getMessage();
        assertTrue(fixed.contains("새로 고쳐짐 1 — baseline 갱신 [b/2.java]"), fixed);
        String over = assertThrows(AssertionFailedError.class,
                () -> CorpusFiles.baseline(f, "t", List.of("a/1.java", "b/2.java"), 50, false)).getMessage();
        assertTrue(over.contains("상한 2% 초과"), over);
        assertThrows(AssertionFailedError.class, () -> CorpusFiles.baseline(tmp.resolve("없음.txt"), "t", List.of(), 10, false),
                "baseline 파일이 없으면 실패");
    }

    @Test
    void gradeAMustBeEmptyAndReportIsCapped() {
        CorpusFiles.none("t", List.of(), 5);
        List<String> many = java.util.stream.IntStream.range(0, 30).mapToObj(i -> "f" + i).toList();
        String m = assertThrows(AssertionFailedError.class, () -> CorpusFiles.none("t", many, 30)).getMessage();
        assertTrue(m.contains("등급 A 30/30") && m.contains("외 10") && !m.contains("f25"), m);
    }

    @Test
    void fingerprintFollowsBytesAndOrder() throws IOException {
        Path d = tmp.resolve("src");
        Files.createDirectories(d.resolve("b"));
        Files.writeString(d.resolve("a.txt"), "x\n");
        Files.writeString(d.resolve("b/c.txt"), "y\n");
        String first = CorpusFiles.fingerprint(d);
        assertTrue(first.startsWith("2 "), first);
        // 목록 = 「sha256  상대경로」 정렬 줄 + 끝 줄바꿈 — corpus-fetch.sh 와 같은 셈
        String list = CorpusFiles.sha256("x\n".getBytes(StandardCharsets.UTF_8)) + "  a.txt\n"
                + CorpusFiles.sha256("y\n".getBytes(StandardCharsets.UTF_8)) + "  b/c.txt\n";
        assertEquals("2 " + CorpusFiles.sha256(list.getBytes(StandardCharsets.UTF_8)), first);
        Files.writeString(d.resolve("b/c.txt"), "y\r\n");
        assertNotEquals(first, CorpusFiles.fingerprint(d), "바이트가 바뀌면 지문이 바뀐다(CRLF 도)");
        assertEquals("없음", CorpusFiles.fingerprint(tmp.resolve("nope")));
    }

    /** 실제 표본이 MANIFEST 와 같다 — corpus-fetch.sh(셸)와 자바가 같은 지문을 낸다 */
    @Test
    @Tag("corpus")
    void realCorpusMatchesManifest() throws IOException {
        CorpusFiles.verify();
    }
}
