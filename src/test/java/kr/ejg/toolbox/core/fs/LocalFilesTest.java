package kr.ejg.toolbox.core.fs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 4-1 — 목록·제외·인코딩 셋 왕복·줄바꿈 보존·백업·거절 */
class LocalFilesTest {

    static final Charset MS949 = Charset.forName("MS949");
    static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tmp;

    LocalFiles files() {
        return new LocalFiles(tmp.resolve("data"));
    }

    static void put(Path f, byte[] b) throws IOException {
        Files.createDirectories(f.getParent());
        Files.write(f, b);
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    @Test
    void listSortsFiltersAndSkips() throws IOException {
        Path root = tmp.resolve("proj");
        for (String rel : List.of("z.jsp", "a.jsp", "sub/b.java", "sub/c.txt", ".hidden/x.jsp", ".h.jsp", "target/t.jsp",
                "node_modules/m.jsp", ".git/g.java")) {
            put(root.resolve(rel), "x".getBytes(StandardCharsets.UTF_8));
        }
        LocalFiles.Listing l = files().list(root.toString(), List.of("*.jsp", " *.java"), LocalFiles.MAX_FILES);
        assertEquals(List.of("a.jsp", "sub/b.java", "z.jsp"), l.files().stream().map(LocalFiles.Entry::rel).toList());
        assertFalse(l.truncated());
        assertEquals(4, files().list(root.toString(), List.of(), LocalFiles.MAX_FILES).files().size(), "glob 이 없으면 전부");
        assertTrue(files().list(root.toString(), List.of(), 2).truncated());
    }

    @Test
    void readDetectsEncodingAndLineEnding() throws IOException {
        Path root = tmp.resolve("enc");
        put(root.resolve("bom.jsp"), concat(BOM, "가\r\n나\r\n".getBytes(StandardCharsets.UTF_8)));
        put(root.resolve("u8.jsp"), "가\n나\n".getBytes(StandardCharsets.UTF_8));
        put(root.resolve("949.jsp"), "가\r\n나\r\n".getBytes(MS949));
        LocalFiles fs = files();
        assertEquals(new LocalFiles.Text("가\n나\n", "UTF-8-BOM", "CRLF"), fs.read(root.resolve("bom.jsp").toString()));
        assertEquals(new LocalFiles.Text("가\n나\n", "UTF-8", "LF"), fs.read(root.resolve("u8.jsp").toString()));
        assertEquals(new LocalFiles.Text("가\n나\n", "MS949", "CRLF"), fs.read(root.resolve("949.jsp").toString()));
    }

    @Test
    void writeKeepsEncodingLineEndingAndBacksUpFirst() throws IOException {
        Path root = tmp.resolve("w");
        Path bom = root.resolve("a/bom.jsp");
        Path ms = root.resolve("949.jsp");
        byte[] bomOrig = concat(BOM, "가\r\n".getBytes(StandardCharsets.UTF_8));
        byte[] msOrig = "가\r\n".getBytes(MS949);
        put(bom, bomOrig);
        put(ms, msOrig);
        LocalFiles fs = files();
        Path backupRoot = tmp.resolve("out/p/20260927-120000/backup");

        for (Path f : List.of(bom, ms)) {
            LocalFiles.Text t = fs.read(f.toString());
            Path b = fs.write(f.toString(), root.toString(), t.text() + "다\n", t.encoding(), t.lineEnding(), backupRoot);
            assertEquals(backupRoot.resolve(root.relativize(f).toString()), b);
        }
        assertArrayEquals(concat(BOM, "가\r\n다\r\n".getBytes(StandardCharsets.UTF_8)), Files.readAllBytes(bom));
        assertArrayEquals("가\r\n다\r\n".getBytes(MS949), Files.readAllBytes(ms));
        assertArrayEquals(bomOrig, Files.readAllBytes(backupRoot.resolve("a/bom.jsp")));
        assertArrayEquals(msOrig, Files.readAllBytes(backupRoot.resolve("949.jsp")));

        fs.write(ms.toString(), root.toString(), "라\n", "MS949", "CRLF", backupRoot);
        assertArrayEquals(msOrig, Files.readAllBytes(backupRoot.resolve("949.jsp")), "같은 stamp 에서 두 번 쓰면 첫 원본을 둔다");
    }

    @Test
    void noWriteWhenBackupFails() throws IOException {
        Path root = tmp.resolve("bf");
        Path f = root.resolve("x.jsp");
        put(f, "원본\n".getBytes(StandardCharsets.UTF_8));
        Path blocker = tmp.resolve("blocker");
        Files.writeString(blocker, "파일이라 폴더를 못 만든다");
        assertThrows(IOException.class, () -> files().write(f.toString(), root.toString(), "새것\n", "UTF-8", "LF",
                blocker.resolve("backup")));
        assertEquals("원본\n", Files.readString(f));
    }

    @Test
    void refusals() throws IOException {
        Path root = tmp.resolve("r");
        Path f = root.resolve("x.jsp");
        put(f, "가\n".getBytes(MS949));
        put(tmp.resolve("data/toolbox.mv.db"), new byte[] {1});
        put(tmp.resolve("other/y.jsp"), "y".getBytes(StandardCharsets.UTF_8));
        LocalFiles fs = files();
        Path backupRoot = tmp.resolve("bk");

        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.read("r/x.jsp")).status(), "상대 경로");
        assertEquals(400, assertThrows(LocalFiles.Refused.class,
                () -> fs.read(tmp.resolve("data/toolbox.mv.db").toString())).status(), "서버 data/");
        assertEquals(400, assertThrows(LocalFiles.Refused.class,
                () -> fs.list(tmp.resolve("data").toString(), List.of(), 10)).status(), "서버 data/ 목록");
        assertEquals(404, assertThrows(LocalFiles.Refused.class,
                () -> fs.list(tmp.resolve("없는폴더").toString(), List.of(), 10)).status());
        assertEquals(404, assertThrows(LocalFiles.Refused.class,
                () -> fs.write(root.resolve("new.jsp").toString(), root.toString(), "", "UTF-8", "LF", backupRoot)).status(),
                "없는 파일은 안 만든다");
        assertEquals(400, assertThrows(LocalFiles.Refused.class,
                () -> fs.write(tmp.resolve("other/y.jsp").toString(), root.toString(), "", "UTF-8", "LF", backupRoot)).status(),
                "고른 폴더 밖");
        assertEquals(400, assertThrows(LocalFiles.Refused.class,
                () -> fs.write(root.resolve("../other/y.jsp").toString(), root.toString(), "", "UTF-8", "LF", backupRoot)).status(),
                "../ 로 빠져나가기");
        assertEquals(400, assertThrows(LocalFiles.Refused.class,
                () -> fs.write(f.toString(), root.toString(), "😀\n", "MS949", "LF", backupRoot)).status(),
                "MS949 로 못 쓰는 글자");
        assertArrayEquals("가\n".getBytes(MS949), Files.readAllBytes(f), "거절이면 원본 그대로");
        assertFalse(Files.exists(backupRoot), "거절이면 백업도 안 만든다");

        String windir = System.getenv("WINDIR");
        if (windir != null) {
            assertEquals(400, assertThrows(LocalFiles.Refused.class,
                    () -> fs.list(Path.of(windir, "System32").toString(), List.of(), 10)).status(), "Windows 폴더");
        }
    }

    @Test
    void recentKeepsPathsOnlyNewestFirst() throws IOException {
        LocalFiles fs = files();
        fs.remember(tmp.resolve("a"));
        fs.remember(tmp.resolve("b"));
        fs.remember(tmp.resolve("a"));
        assertEquals(List.of(tmp.resolve("a").toString(), tmp.resolve("b").toString()), fs.recent());
    }
}
