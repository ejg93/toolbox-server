package kr.ejg.toolbox.core.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 4-2 — 상태 넷·크기 같고 내용 다름·glob·제외 */
class FolderDiffTest {

    @TempDir
    Path tmp;

    void put(Path f, String s) throws IOException {
        Files.createDirectories(f.getParent());
        Files.writeString(f, s);
    }

    @Test
    void fourStatuses() throws IOException {
        Path a = tmp.resolve("a");
        Path b = tmp.resolve("b");
        put(a.resolve("same.jsp"), "같다");
        put(b.resolve("same.jsp"), "같다");
        put(a.resolve("sub/diff.jsp"), "abc");
        put(b.resolve("sub/diff.jsp"), "abd"); // 크기 같고 내용 다름 — 해시가 가른다
        put(a.resolve("size.jsp"), "짧다");
        put(b.resolve("size.jsp"), "조금 더 길다");
        put(a.resolve("onlyA.jsp"), "a");
        put(b.resolve("onlyB.jsp"), "b");
        put(a.resolve("skip.txt"), "glob 밖");
        put(b.resolve("target/t.jsp"), "제외 폴더");

        FolderDiff.Result r = FolderDiff.compare(new LocalFiles(tmp.resolve("data")), a.toString(), b.toString(), List.of("*.jsp"));
        assertEquals(List.of(
                new FolderDiff.Item("onlyA.jsp", FolderDiff.Status.ONLY_A, 1L, null),
                new FolderDiff.Item("onlyB.jsp", FolderDiff.Status.ONLY_B, null, 1L),
                new FolderDiff.Item("same.jsp", FolderDiff.Status.SAME, 6L, 6L),
                new FolderDiff.Item("size.jsp", FolderDiff.Status.DIFF, 6L, 17L),
                new FolderDiff.Item("sub/diff.jsp", FolderDiff.Status.DIFF, 3L, 3L)), r.items());
    }
}
