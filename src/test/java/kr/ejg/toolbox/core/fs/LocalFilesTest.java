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
import java.util.Set;
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

    /** 0-121(G-01) — 같은 폴더 판정은 파일 키로: 대소문자 바꾼 표기 · 아래 파일 · 아직 없는 아래 경로가 참, 형제 폴더는 거짓 */
    @Test
    void sameOrInsideUsesFileKey() throws IOException {
        Path dir = tmp.resolve("Prof");
        Path sib = tmp.resolve("ProfX");
        Files.createDirectories(dir);
        Files.createDirectories(sib);
        Files.writeString(dir.resolve("a.yaml"), "x");
        // 대소문자를 안 가리는 것은 Windows 파일 시스템만 — 그 밖은 「.」 조각 표기(CI Linux)
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        Path upper = windows ? Path.of(dir.toString().toUpperCase(java.util.Locale.ROOT)) : dir.resolve(".");
        assertTrue(PathRules.sameOrInside(upper, dir), "다른 표기(Windows 는 대소문자)");
        assertTrue(PathRules.sameOrInside(upper.resolve("a.yaml"), dir), "아래 파일");
        assertTrue(PathRules.sameOrInside(dir.resolve("new/b.yaml"), dir), "아직 없는 아래 경로");
        assertFalse(PathRules.sameOrInside(sib, dir), "형제 폴더");
        assertFalse(PathRules.sameOrInside(dir, tmp.resolve("nope")), "없는 폴더");
    }

    /** 0-125a(G-10) — 드라이브 종류는 OS 답으로: 4(DRIVE_REMOTE)만 원격 · 시스템 드라이브는 3 · 없는 드라이브는 원격 아님 */
    @Test
    void driveTypeJudgesRemoteDrives() {
        for (int t : new int[] {0, 1, 2, 3, 5, 6}) {
            assertFalse(PathRules.remote(t), String.valueOf(t));
        }
        assertTrue(PathRules.remote(4));
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        if (!windows) {
            assertTrue(PathRules.driveType("C:\\").isEmpty(), "Windows 가 아니면 빈 값");
            return;
        }
        String sys = System.getenv().getOrDefault("SystemDrive", "C:") + "\\";
        assertEquals(java.util.OptionalInt.of(3), PathRules.driveType(sys), sys);
        org.junit.jupiter.api.Assumptions.assumeFalse(Files.exists(Path.of("Q:/")), "Q: 드라이브가 있다");
        assertFalse(PathRules.remoteDrive("Q:/x"));
        assertFalse(LocalFiles.network(tmp.toString()), "지킴이 — 로컬 경로");
        assertTrue(LocalFiles.network("\\\\srv\\share\\x"), "지킴이 — UNC");
    }

    /** 0-121(G-18) — 결과 폴더 쓰기 실패(없는 드라이브)는 500 대신 400 */
    @Test
    void resultWriteFailureIsRefused() throws IOException {
        Path blocker = tmp.resolve("blocker"); // 폴더 자리에 파일 — 어느 OS 에서나 폴더를 못 만든다
        Files.writeString(blocker, "x");
        LocalFiles.Refused e = assertThrows(LocalFiles.Refused.class, () -> files().writeResult(blocker.resolve("out"), "a.txt", "1\n", "UTF-8", "LF"));
        assertEquals(400, e.status());
        assertTrue(e.getMessage().startsWith("결과 폴더에 못 썼다"), e.getMessage());
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name", "").startsWith("Windows") && !Files.exists(Path.of("Q:/")), "없는 드라이브는 Windows 만");
        LocalFiles.Refused q = assertThrows(LocalFiles.Refused.class, () -> files().writeResult(Path.of("Q:/out"), "a.txt", "1\n", "UTF-8", "LF"));
        assertEquals(400, q.status());
    }

    /** G1-K04·K13 — 글롭 문법 오류·별 묶음 넷 넘음은 400(JDK 글롭 정규식 역추적이 별 수에 지수) */
    @Test
    void badGlobIsRefused() throws IOException {
        Path root = tmp.resolve("g");
        put(root.resolve("a.java"), "x".getBytes(StandardCharsets.UTF_8));
        LocalFiles fs = files();
        for (String g : List.of("[", "*.{jsp", "*a*a*a*a*b")) {
            LocalFiles.Refused e = org.junit.jupiter.api.Assertions.assertThrows(LocalFiles.Refused.class, () -> fs.list(root.toString(), List.of(g), 100), g);
            assertEquals(400, e.status(), g);
        }
        assertEquals(1, fs.list(root.toString(), List.of("*.java"), 100).files().size());
        fs.list(root.toString(), List.of("*Test*Impl*.java"), 100);
    }

    /** G1-K12 — 다른 폴더를 훑다 만난 금지 폴더(data)·정션 폴더는 안 들어간다 */
    @Test
    void listSkipsForbiddenAndJunctions() throws Exception {
        Path proj = tmp.resolve("proj");
        put(proj.resolve("a.txt"), "a".getBytes(StandardCharsets.UTF_8));
        put(proj.resolve("data/x.db"), "x".getBytes(StandardCharsets.UTF_8));
        link(proj.resolve("loop"), proj);
        LocalFiles fs = new LocalFiles(proj.resolve("data"));
        assertEquals(List.of("a.txt"), fs.list(proj.toString(), List.of(), 100).files().stream().map(LocalFiles.Entry::rel).toList());
    }

    /** 링크, 안 되면 정션(MakeTemplatesTest 와 같은 도우미) — 둘 다 안 되면 시험을 건너뛴다 */
    static void link(Path link, Path target) throws IOException, InterruptedException {
        try {
            Files.createSymbolicLink(link, target);
            return;
        } catch (IOException | UnsupportedOperationException e) {
            // tb-ignore java.emptyCatch 정션으로 다시 해 본다
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        if (windows && new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0 && Files.exists(link)) {
            return;
        }
        org.junit.jupiter.api.Assumptions.abort("링크·정션을 못 만든다");
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

    /** 0-83b — 10MB 넘는 파일(소스 트리 안 DB 덤프)은 읽지 않는다. 전엔 readAllBytes 가 통째로 올려 OOM */
    @Test
    void readRefusesOverMaxRead() throws IOException {
        Path root = tmp.resolve("big");
        Files.createDirectories(root);
        Path at = root.resolve("at.sql");
        Path over = root.resolve("over.sql");
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(at.toFile(), "rw")) {
            f.setLength(LocalFiles.MAX_READ);
        }
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(over.toFile(), "rw")) {
            f.setLength(LocalFiles.MAX_READ + 1);
        }
        LocalFiles fs = files();
        assertEquals(10L * 1024 * 1024, LocalFiles.MAX_READ);
        assertEquals(LocalFiles.MAX_READ, fs.read(at.toString()).text().length(), "상한과 같으면 읽는다");
        assertEquals(413, assertThrows(LocalFiles.Refused.class, () -> fs.read(over.toString())).status());
    }

    /** G1-K21 — UTF-16 BOM 파일은 못 읽는다(깨진 글 대신 400) */
    @Test
    void utf16IsRefused() throws IOException {
        Path f = tmp.resolve("u16/a.jsp");
        put(f, concat(new byte[] {(byte) 0xFF, (byte) 0xFE}, "가나".getBytes(StandardCharsets.UTF_16LE)));
        LocalFiles.Refused e = org.junit.jupiter.api.Assertions.assertThrows(LocalFiles.Refused.class, () -> files().read(f.toString()));
        assertEquals(400, e.status());
        assertTrue(e.getMessage().contains("UTF-16"), e.getMessage());
    }

    /** G1-K19 — 다른 프로그램이 잠근 파일은 IOException 500 대신 Refused 400(Windows 만 — 리눅스 잠금은 읽기를 안 막는다) */
    @Test
    void lockedFileIsRefused() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"), "Windows 잠금만");
        Path f = tmp.resolve("lock/a.jsp");
        put(f, "a".getBytes(StandardCharsets.UTF_8));
        try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(f, java.nio.file.StandardOpenOption.READ, java.nio.file.StandardOpenOption.WRITE);
                java.nio.channels.FileLock lock = ch.lock()) {
            org.junit.jupiter.api.Assertions.assertTrue(lock.isValid(), "잠금을 쥐었다");
            LocalFiles.Refused e = org.junit.jupiter.api.Assertions.assertThrows(LocalFiles.Refused.class, () -> files().read(f.toString()));
            assertEquals(400, e.status());
        }
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
        // 5-113(설계 70 §3-13) — CRLF 와 LF 단독이 같이 있으면 mixed, lineEnding 은 다수 쪽
        put(root.resolve("mix.jsp"), "a\r\nb\nc\r\n".getBytes(StandardCharsets.UTF_8));
        LocalFiles.Text mix = fs.read(root.resolve("mix.jsp").toString());
        assertTrue(mix.mixed(), mix.toString());
        assertEquals("CRLF", mix.lineEnding());
        assertFalse(LocalFiles.mixedEol("a\r\nb\r\n"), "CRLF 만");
        assertFalse(LocalFiles.mixedEol("a\nb\n"), "LF 만");
        assertFalse(LocalFiles.mixedEol("ab"), "줄바꿈 없음");
        assertTrue(LocalFiles.mixedEol("a\nb\r\n"), "LF 먼저");
        // 5-144(E-17) — \r\r\n 줄끝은 섞인 파일(읽으면 줄마다 \r 이 남는다)
        put(root.resolve("crcr.js"), "x\r\r\ny\r\r\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(fs.read(root.resolve("crcr.js").toString()).mixed());
        assertFalse(LocalFiles.mixedEol("a\r\nb\r\n") || LocalFiles.mixedEol("a\nb\n"), "순수 CRLF·LF");
    }

    @Test
    void refusals() throws IOException {
        Path root = tmp.resolve("r");
        Path f = root.resolve("x.jsp");
        put(f, "가\n".getBytes(MS949));
        put(tmp.resolve("data/toolbox.mv.db"), new byte[] {1});
        put(tmp.resolve("other/y.jsp"), "y".getBytes(StandardCharsets.UTF_8));
        LocalFiles fs = files();

        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.read("r/x.jsp")).status(), "상대 경로");
        assertEquals(400, assertThrows(LocalFiles.Refused.class,
                () -> fs.read(tmp.resolve("data/toolbox.mv.db").toString())).status(), "서버 data/");
        assertEquals(400, assertThrows(LocalFiles.Refused.class,
                () -> fs.list(tmp.resolve("data").toString(), List.of(), 10)).status(), "서버 data/ 목록");
        assertEquals(404, assertThrows(LocalFiles.Refused.class,
                () -> fs.list(tmp.resolve("없는폴더").toString(), List.of(), 10)).status());
        assertArrayEquals("가\n".getBytes(MS949), Files.readAllBytes(f), "읽기·목록은 원본을 안 바꾼다");
        // G1-K07 — 아직 없는 경로도 가장 가까운 있는 조상의 실제 경로로(끝 점 data. · 정션)
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.check(tmp.resolve("data.").resolve("new").toString())).status(), "data.");
        }
        try {
            link(tmp.resolve("jdata"), tmp.resolve("data"));
        } catch (IOException | InterruptedException e) {
            org.junit.jupiter.api.Assumptions.abort("정션을 못 만든다: " + e);
        }
        try {
            assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.check(tmp.resolve("jdata").resolve("new").toString())).status(), "정션");
        } finally {
            Files.deleteIfExists(tmp.resolve("jdata")); // 정션만 지운다 — TempDir 정리가 data 를 두 번 밟지 않게
        }

        String windir = System.getenv("WINDIR");
        if (windir != null) {
            assertEquals(400, assertThrows(LocalFiles.Refused.class,
                    () -> fs.list(Path.of(windir, "System32").toString(), List.of(), 10)).status(), "Windows 폴더");
        }
    }

    @Test
    void networkPathsRefusedBeforeTouching() {
        LocalFiles fs = files();
        for (String p : List.of("\\\\server\\share\\a.jsp", "//server/share/a.jsp", "\\\\?\\C:\\a.jsp", "\\\\.\\C:\\a.jsp", " \\\\srv\\share")) {
            assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.read(p)).status(), p);
        }
    }

    /** 0-87 — UNC·장치 경로 판정 한 자리 */
    @Test
    void networkTable() {
        assertFalse(LocalFiles.network(null));
        assertFalse(LocalFiles.network("a/b"));
        assertFalse(LocalFiles.network("C:\\a"));
        assertTrue(LocalFiles.network("//s/x"));
        assertTrue(LocalFiles.network("\\\\?\\C:\\a"));
        assertTrue(LocalFiles.network(" \\\\srv\\share"));
    }

    /** 9-3a — 결과 폴더에 같은 상대 경로로, 인코딩·줄바꿈 그대로. 원본은 그대로, 표식이 생긴다 */
    @Test
    void writeResultMirrorsRelAndKeepsEncoding() throws IOException {
        Path src = tmp.resolve("src");
        byte[] bomOrig = concat(BOM, "가\r\n".getBytes(StandardCharsets.UTF_8));
        byte[] msOrig = "가\r\n".getBytes(MS949);
        put(src.resolve("a/bom.jsp"), bomOrig);
        put(src.resolve("949.jsp"), msOrig);
        LocalFiles fs = files();
        Path out = fs.resultRoot(null, tmp.resolve("out/p/20261023-120000/jsp"), src, "jsp", "20261023-120000");
        for (String rel : List.of("a/bom.jsp", "949.jsp")) {
            LocalFiles.Text t = fs.read(src.resolve(rel).toString());
            assertEquals(out.resolve(rel), fs.writeResult(out, rel, t.text() + "다\n", t.encoding(), t.lineEnding()));
        }
        assertArrayEquals(concat(BOM, "가\r\n다\r\n".getBytes(StandardCharsets.UTF_8)), Files.readAllBytes(out.resolve("a/bom.jsp")));
        assertArrayEquals("가\r\n다\r\n".getBytes(MS949), Files.readAllBytes(out.resolve("949.jsp")));
        assertArrayEquals(bomOrig, Files.readAllBytes(src.resolve("a/bom.jsp")), "원본 그대로");
        assertArrayEquals(msOrig, Files.readAllBytes(src.resolve("949.jsp")), "원본 그대로");
        String mark = Files.readString(out.resolve(LocalFiles.MARK), StandardCharsets.UTF_8);
        assertTrue(mark.contains("기능 jsp") && mark.contains("원본 " + src.toAbsolutePath().normalize()) && mark.contains("원본은 바뀌지 않았다"), mark);
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.writeResult(out, "x.jsp", "😀\n", "MS949", "LF")).status(),
                "MS949 로 못 쓰는 글자");
    }

    /** 9-3a — 원본 안·원본 자체·원본을 품는 폴더는 결과 폴더가 못 된다 */
    @Test
    void resultRootRefusesInsideEqualOrAround() throws IOException {
        Path src = tmp.resolve("proj/src");
        Files.createDirectories(src);
        LocalFiles fs = files();
        for (Path bad : List.of(src.resolve("out"), src, tmp.resolve("proj"))) {
            assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.resultRoot(bad.toString(), tmp.resolve("x"), src, "jsp", "s")).status(),
                    bad.toString());
        }
        assertFalse(Files.exists(src.resolve("out")), "거절이면 폴더도 안 만든다");
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.resultRoot("rel/out", tmp.resolve("x"), src, "jsp", "s")).status(),
                "상대 경로");
    }

    /** 9-3a — 남의 비어 있지 않은 폴더는 거절, 빈 폴더는 표식을 쓰고 통과, 표식 있는 폴더는 다시 써도 표식을 안 덮는다 */
    @Test
    void resultRootRefusesNonEmptyForeign() throws IOException {
        Path src = tmp.resolve("s2");
        Files.createDirectories(src);
        LocalFiles fs = files();
        Path foreign = tmp.resolve("foreign");
        put(foreign.resolve("keep.txt"), "남의 것".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.resultRoot(foreign.toString(), tmp.resolve("x"), src, "jsp", "s")).status());
        assertFalse(Files.exists(foreign.resolve(LocalFiles.MARK)));
        Path empty = Files.createDirectories(tmp.resolve("empty"));
        assertEquals(empty, fs.resultRoot(empty.toString(), tmp.resolve("x"), src, "cleanup", "s1"));
        assertTrue(Files.readString(empty.resolve(LocalFiles.MARK), StandardCharsets.UTF_8).contains("시각 s1"));
        fs.writeResult(empty, "a.txt", "1\n", "UTF-8", "LF");
        assertEquals(empty, fs.resultRoot(empty.toString(), tmp.resolve("x"), src, "cleanup", "s2"), "표식 있는 폴더는 다시 쓴다");
        assertTrue(Files.readString(empty.resolve(LocalFiles.MARK), StandardCharsets.UTF_8).contains("시각 s1"), "첫 출처를 안 덮는다");
        put(tmp.resolve("afile"), new byte[] {1});
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.resultRoot(tmp.resolve("afile").toString(), tmp.resolve("x"), src, "jsp", "s"))
                .status(), "파일 자리");
    }

    /** 9-3a — 결과 폴더 밖·표식 자리·절대 경로는 안 쓴다 */
    @Test
    void writeResultRefusesEscape() throws IOException {
        LocalFiles fs = files();
        Path out = fs.resultRoot(null, tmp.resolve("o"), null, "jsp", "s");
        String mark = Files.readAllLines(out.resolve(LocalFiles.MARK), StandardCharsets.UTF_8).get(0);
        // G1-K06·K08 — 금지 글자·끝 점·끝 공백(Windows 가 떼어 다른 이름이 된다 — 표식 파일을 덮는다)
        for (String bad : List.of("../x.txt", "a/../../x.txt", "/x.txt", "C:/x.txt", "", LocalFiles.MARK, "a?.jsp", "a/b<c.txt", "...", "a /b.txt",
                "x.txt.", LocalFiles.MARK + ".")) {
            assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.writeResult(out, bad, "z", "UTF-8", "LF")).status(), bad);
        }
        assertFalse(Files.exists(tmp.resolve("x.txt")));
        assertEquals(mark, Files.readAllLines(out.resolve(LocalFiles.MARK), StandardCharsets.UTF_8).get(0), "표식 파일 첫 줄 그대로");
    }

    /** 9-3a — 결과 폴더가 링크로 원본 안을 가리키면 거절 */
    @Test
    void resultRootRefusesLinkIntoSource() throws IOException {
        Path src = tmp.resolve("s3");
        Files.createDirectories(src.resolve("inner"));
        try {
            Files.createSymbolicLink(tmp.resolve("lnk"), src.resolve("inner"));
        } catch (IOException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "심볼릭 링크를 못 만든다(Windows 권한) — CI 리눅스에서 돈다");
        }
        LocalFiles fs = files();
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.resultRoot(tmp.resolve("lnk").toString(), tmp.resolve("x"), src, "jsp", "s"))
                .status());
        assertFalse(Files.exists(src.resolve("inner").resolve(LocalFiles.MARK)));
    }

    @Test
    void recentKeepsPathsOnlyNewestFirst() throws IOException {
        LocalFiles fs = files();
        fs.remember(tmp.resolve("a"));
        fs.remember(tmp.resolve("b"));
        fs.remember(tmp.resolve("a"));
        assertEquals(List.of(tmp.resolve("a").toString(), tmp.resolve("b").toString()), fs.recent());
    }

    /** 5-84 — WRITABLE 과 encode 가 한 셋: 목록 것은 다 써지고 밖 것(EUC-KR 이름 그대로·windows-1252)은 400 */
    @Test
    void writableNamesMatchEncode() {
        assertEquals(Set.of("UTF-8", "UTF-8-BOM", "MS949"), LocalFiles.WRITABLE);
        for (String enc : LocalFiles.WRITABLE) {
            assertTrue(LocalFiles.encode("가\n", enc, "LF").length > 0, enc);
        }
        for (String enc : List.of("EUC-KR", "windows-1252", "", "utf-8")) {
            assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> LocalFiles.encode("가\n", enc, "LF")).status(), enc);
        }
    }

    /** 0-88(설계 69 §3-1) — 2-인자 생성자로 더한 폴더는 그 아래 전부 거절, 형제 폴더는 통과 */
    @Test
    void extraForbiddenFolderIsRefused() throws IOException {
        Path secret = tmp.resolve("profiles");
        put(secret.resolve("p.yaml"), "x".getBytes(StandardCharsets.UTF_8));
        put(tmp.resolve("profiles2/q.yaml"), "x".getBytes(StandardCharsets.UTF_8));
        LocalFiles fs = new LocalFiles(tmp.resolve("data"), List.of(secret));
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.check(secret.toString())).status());
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.check(secret.resolve("p.yaml").toString())).status());
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.check(secret.resolve("sub/../p.yaml.bak").toString())).status());
        assertEquals(tmp.resolve("profiles2/q.yaml").toAbsolutePath().normalize(), fs.check(tmp.resolve("profiles2/q.yaml").toString()));
    }

    /** 0-88 리뷰 I2 — 거절 폴더가 링크로 잡혀 있어도 그 실제 경로로 돌아 들어가면 거절. 링크를 못 만드는 판(권한)은 건너뛴다 */
    @Test
    void forbiddenFolderBehindLinkIsRefusedByRealPath() throws IOException {
        Path real = tmp.resolve("real-profiles");
        put(real.resolve("p.yaml"), "x".getBytes(StandardCharsets.UTF_8));
        Path link = tmp.resolve("profiles-link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (IOException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.abort("심볼릭 링크를 못 만든다 — " + e.getClass().getSimpleName());
        }
        LocalFiles fs = new LocalFiles(tmp.resolve("data"), List.of(link));
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.check(link.resolve("p.yaml").toString())).status());
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.check(real.resolve("p.yaml").toString())).status());
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.check(real.toString())).status());
    }

    /** 0-127(설계 76 후보) — 마디가 Windows 예약 장치 이름(확장자가 붙어도)이면 결과 상대 경로가 아니다(파일 대신 장치에 쓴다) */
    @Test
    void relRefusesDeviceNames() {
        for (String r : List.of("a/NUL", "a/nul.txt", "COM1", "x/lpt9.log", "Aux.java")) {
            assertThrows(LocalFiles.Refused.class, () -> LocalFiles.checkRel(r), r);
        }
        for (String r : List.of("a/NULL.java", "a/console.js", "COM10.txt", "a/com.java", "nul_x.txt")) {
            assertEquals(r, LocalFiles.checkRel(r));
        }
    }

    /** 0-129(G4-01) — 확인한 폴더를 잠깐 기억해 파일마다 조상 전부를 금지 폴더와 다시 견주지 않는다 · 판정은 그대로 */
    @Test
    void checkIsCheapAfterTheFirst(@TempDir Path tmp) throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("p.yaml"), "name: p" + (char) 10);
        LocalFiles fs = new LocalFiles(tmp.resolve("data"), List.of(profiles));
        Path deep = tmp.resolve("a/b/c/d/e/f/g/h");
        Files.createDirectories(deep);
        Path f = deep.resolve("x.txt");
        Files.writeString(f, "x");
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(10), () -> {
            for (int i = 0; i < 2000; i++) {
                fs.check(f.toString());
            }
        });
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.check(profiles.resolve("p.yaml").toString())).status(), "기억이 금지 폴더를 안 지나친다");
        Files.createDirectories(tmp.resolve("data"));
        assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> fs.check(tmp.resolve("data").resolve("q").toString())).status());
    }
}
