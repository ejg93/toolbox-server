package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-34c — 폴더 미리보기·적용. 미리보기는 안 쓰고, 적용은 백업 뒤 인코딩·줄바꿈 그대로, 미리보기 뒤 바뀐 파일은 안 쓰고, 결과 글에 코드가 없다.
 */
class CleanupRunnerTest {

    static final Charset MS949 = Charset.forName("MS949");
    static final List<String> NO_EOL = Cleanup.RULES.stream().filter(r -> !r.equals("file.lineEnding")).toList();

    @TempDir
    Path tmp;

    Path root;
    LocalFiles files;
    RuleSet rules;
    byte[] msOrig;

    @BeforeEach
    void up() throws Exception {
        root = tmp.resolve("proj");
        Files.createDirectories(root.resolve("web"));
        Files.copy(CleanupTest.FIXTURES.resolve("javaimport/Pos.java"), root.resolve("Pos.java"));
        Files.copy(CleanupTest.FIXTURES.resolve("debugger/pos.jsp"), root.resolve("web/pos.jsp"));
        msOrig = "package p;\r\n\r\nimport java.util.List;\r\n\r\n/** 한글 주석 */\r\npublic class Ms {\r\n}\r\n".getBytes(MS949);
        Files.write(root.resolve("Ms.java"), msOrig);
        Files.writeString(root.resolve("Crlf.java"), "package p;\r\n\r\npublic class Crlf {\r\n}\r\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("c.txt"), "debugger;\n", StandardCharsets.UTF_8);
        files = new LocalFiles(tmp.resolve("data"));
        rules = RuleSet.load(CleanupTest.profile(null), null);
    }

    Map<String, byte[]> bytes() throws Exception {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                out.put(root.relativize(p).toString(), Files.readAllBytes(p));
            }
        }
        return out;
    }

    static List<CleanupRunner.FileRef> refs(CleanupRunner.Preview pv) {
        List<CleanupRunner.FileRef> out = new ArrayList<>();
        pv.rows().stream().filter(CleanupRunner.FileRow::changed).forEach(r -> out.add(new CleanupRunner.FileRef(r.rel(), r.hash(), r.outHash())));
        return out;
    }

    @Test
    void previewWritesNothing() throws Exception {
        Map<String, byte[]> before = bytes();
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, Cleanup.RULES, "LF", files, null, null, List.of());
        Map<String, byte[]> after = bytes();
        assertEquals(before.keySet(), after.keySet());
        before.forEach((k, v) -> assertArrayEquals(v, after.get(k), k + " 미리보기는 안 쓴다"));
        List<String> changed = pv.rows().stream().filter(CleanupRunner.FileRow::changed).map(CleanupRunner.FileRow::rel).toList();
        assertEquals(List.of("Crlf.java", "Ms.java", "Pos.java", "web/pos.jsp"), changed);
        assertEquals(4, pv.changed());
        assertEquals(4, pv.files(), "c.txt 는 glob 밖");
        pv.rows().forEach(r -> assertTrue(r.hash().matches("[0-9a-f]{64}") && r.outHash().matches("[0-9a-f]{64}"), r.rel()));
    }

    @Test
    void applyBacksUpAndKeepsEncoding() throws Exception {
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, NO_EOL, "LF", files, null, null, List.of());
        Path out = tmp.resolve("out/t/20261022-120000");
        CleanupRunner.Applied ap = CleanupRunner.apply(root.toString(), rules, NO_EOL, "LF", files, null, refs(pv), List.of(), out.resolve("backup"), out);
        assertEquals(3, ap.written(), ap.rows().toString());
        assertEquals(0, ap.failed());
        byte[] ms = Files.readAllBytes(root.resolve("Ms.java"));
        String msText = new String(ms, MS949);
        assertFalse(msText.contains("import java.util.List"), msText);
        assertTrue(msText.contains("/** 한글 주석 */\r\n"), "MS949·CRLF 그대로 — " + msText);
        assertArrayEquals(msOrig, Files.readAllBytes(out.resolve("backup/Ms.java")), "백업은 원본 바이트");
        String report = Files.readString(ap.report(), StandardCharsets.UTF_8);
        assertTrue(report.startsWith("코드 정리 20261022-120000 — 폴더 " + root + " · 덮어씀 3 · 실패 0 · 낡음 0"), report);
        assertFalse(report.contains("import") || report.contains("debugger;"), "결과 글에 코드 본문 없음 — " + report);
        assertTrue(report.contains("Ms.java · delete · 전 1 → 후 0"), report);
    }

    @Test
    void staleHashIsSkipped() throws Exception {
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, Cleanup.RULES, "LF", files, null, null, List.of());
        Files.writeString(root.resolve("Pos.java"), Files.readString(root.resolve("Pos.java")) + "// 미리보기 뒤에 고침\n");
        byte[] edited = Files.readAllBytes(root.resolve("Pos.java"));
        List<CleanupRunner.FileRef> r = new ArrayList<>();
        for (CleanupRunner.FileRef x : refs(pv)) {
            r.add(x.rel().equals("Crlf.java") ? new CleanupRunner.FileRef(x.rel(), x.hash(), "0") : x); // 결과가 미리보기와 다르다고 꾸민다
        }
        Path out = tmp.resolve("out/t/20261022-120001");
        CleanupRunner.Applied ap = CleanupRunner.apply(root.toString(), rules, Cleanup.RULES, "LF", files, null, r, List.of(), out.resolve("backup"), out);
        assertEquals(2, ap.stale(), ap.rows().toString());
        assertArrayEquals(edited, Files.readAllBytes(root.resolve("Pos.java")), "미리보기 뒤 바뀐 파일은 안 쓴다");
        assertTrue(ap.rows().stream().anyMatch(w -> w.rel().equals("Crlf.java") && "미리보기와 결과가 다름".equals(w.reason())), ap.rows().toString());
    }

    /** 마무리 AI 리뷰 — 제외 경로·규칙 파일 종류 밖은 hash 가 맞아도 안 쓴다 */
    @Test
    void outOfScopeIsRefused() throws Exception {
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, Cleanup.RULES, "LF", files, null, null, List.of());
        byte[] before = Files.readAllBytes(root.resolve("Pos.java"));
        Path out = tmp.resolve("out/t/20261022-120003");
        CleanupRunner.Applied ap = CleanupRunner.apply(root.toString(), rules, Cleanup.RULES, "LF", files, null, refs(pv), List.of("Pos.java"),
                out.resolve("backup"), out);
        assertTrue(ap.rows().stream().anyMatch(w -> w.rel().equals("Pos.java") && !w.ok() && w.reason().startsWith("대상 밖")), ap.rows().toString());
        assertArrayEquals(before, Files.readAllBytes(root.resolve("Pos.java")), "제외 경로는 안 쓴다");
        assertEquals(3, ap.written());
    }

    @Test
    void changedOnlyPredicate() throws Exception {
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, Cleanup.RULES, "LF", files, null, "Pos.java"::equals, List.of());
        assertEquals(1, pv.files());
        assertEquals(List.of("Pos.java"), pv.rows().stream().map(CleanupRunner.FileRow::rel).toList());
    }

    @Test
    void eolOnlyChangesLineEnding() throws Exception {
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, Set.of("file.lineEnding"), "LF", files, null,
                "Crlf.java"::equals, List.of());
        CleanupRunner.FileRow row = pv.rows().get(0);
        assertEquals("LF", row.lineEnding());
        Path out = tmp.resolve("out/t/20261022-120002");
        CleanupRunner.apply(root.toString(), rules, Set.of("file.lineEnding"), "LF", files, null, refs(pv), List.of(), out.resolve("backup"), out);
        assertEquals("package p;\n\npublic class Crlf {\n}\n", Files.readString(root.resolve("Crlf.java"), StandardCharsets.UTF_8));
    }
}
