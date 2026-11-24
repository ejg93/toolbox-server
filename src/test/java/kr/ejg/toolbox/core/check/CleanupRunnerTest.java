package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    /** 줄바꿈·인코딩 고침을 뺀 목록 — 읽은 인코딩·줄바꿈 그대로 쓰는지 본다(file.encoding 은 설계 62) */
    static final List<String> NO_EOL = Cleanup.RULES.stream().filter(r -> !r.equals("file.lineEnding") && !r.equals("file.encoding")).toList();

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

    /** 0-92a(설계 69 §3-9) — 파일 하나의 스택 넘침은 그 파일만 건너뛴다(사유 내부 오류 — StackOverflowError), 나머지는 처리 */
    @Test
    void stackOverflowFileIsSkipped() throws Exception {
        Files.writeString(root.resolve("Deep.java"), "class Deep { int x = " + "(".repeat(6000) + "1" + ")".repeat(6000) + "; }\n",
                StandardCharsets.UTF_8);
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, Cleanup.RULES, "LF", files, null, null, List.of());
        CleanupRunner.FileRow deep = pv.rows().stream().filter(r -> r.rel().equals("Deep.java")).findFirst().orElseThrow();
        assertEquals(List.of("내부 오류 — StackOverflowError"), deep.skips().stream().map(Cleanup.Skip::reason).toList());
        assertTrue(pv.rows().stream().anyMatch(r -> r.rel().equals("Crlf.java") && r.changed()), "정상 파일은 처리");
    }

    /** 0-94 — 같은 패키지 이웃 파일이 넘쳐도 별표 import 파일은 처리한다(그 패키지만 못 푼다) */
    @Test
    void deepNeighborDoesNotSkipTarget() throws Exception {
        Files.createDirectories(root.resolve("q"));
        Files.writeString(root.resolve("q/Deep.java"), "package q;\nclass Deep { int x = " + "(".repeat(6000) + "1" + ")".repeat(6000) + "; }\n",
                StandardCharsets.UTF_8);
        Files.writeString(root.resolve("q/Other.java"), "package q;\npublic class Other {\n}\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Use.java"), "import q.*;\n\nclass Use {\n    Other a;\n}\n", StandardCharsets.UTF_8);
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, Cleanup.RULES, "LF", files, null, null, List.of());
        CleanupRunner.FileRow use = pv.rows().stream().filter(r -> r.rel().equals("Use.java")).findFirst().orElseThrow();
        assertTrue(use.skips().stream().noneMatch(s -> s.reason().startsWith("내부 오류")), use.toString());
    }

    /** 5-116 ⑤ — 섞인 줄끝이어도 고칠 것이 없으면 미리보기에 줄이 없다 */
    @Test
    void mixedEolOnlyWhenChanged() throws Exception {
        Files.writeString(root.resolve("Clean.java"), "package p;\r\n\r\npublic class Clean {\n}\r\n", StandardCharsets.UTF_8);
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, NO_EOL, "LF", files, null, null, List.of());
        assertTrue(pv.rows().stream().noneMatch(r -> r.rel().equals("Clean.java")), pv.rows().toString());
    }

    /** 5-116 ⑤ — 줄끝 통일을 고르면 섞인 파일은 고치고 통일했다고 알린다 */
    @Test
    void unifyNotesMixed() throws Exception {
        Files.writeString(root.resolve("Mix2.java"), "package p;\r\n\r\npublic class Mix2 {\n}\r\n", StandardCharsets.UTF_8);
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, Cleanup.RULES, "LF", files, null, null, List.of());
        CleanupRunner.FileRow row = pv.rows().stream().filter(r -> r.rel().equals("Mix2.java")).findFirst().orElseThrow();
        assertTrue(row.changed(), row.toString());
        assertTrue(row.skips().stream().anyMatch(k -> k.reason().equals(CleanupRunner.UNIFIED_EOL + "LF")), row.skips().toString());
    }

    /** 5-157b(E-15) — 다수 쪽이 프로필과 같은 섞인 파일도 줄끝 통일이 돌고(바뀜) · 앞 단계 고침을 되돌리지 않는다 */
    @Test
    void mixedMajorityLikeProfileIsUnified() throws Exception {
        Files.writeString(root.resolve("Maj.java"), "package p;\n\npublic class Maj {\r\n}\n", StandardCharsets.UTF_8);
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, List.of("file.lineEnding"), "LF", files, null, null, List.of());
        CleanupRunner.FileRow row = pv.rows().stream().filter(r -> r.rel().equals("Maj.java")).findFirst().orElseThrow();
        assertTrue(row.changed() && "LF".equals(row.lineEnding()), row.toString());
        assertTrue(row.steps().stream().anyMatch(s -> s.stage().equals(Cleanup.EOL)), row.toString());
        Files.writeString(root.resolve("m.js"), "function f() {\n  console.log(1);\r\n  go();\n}\n", StandardCharsets.UTF_8);
        CleanupRunner.Preview both = CleanupRunner.preview(root.toString(), rules, List.of("file.lineEnding", "tsx.console", "common.consoleLog"), "LF",
                files, null, null, List.of());
        CleanupRunner.FileRow m = both.rows().stream().filter(r -> r.rel().equals("m.js")).findFirst().orElseThrow();
        assertTrue(m.changed() && m.steps().stream().anyMatch(s -> s.stage().equals(Cleanup.EOL))
                && m.steps().stream().anyMatch(s -> s.stage().equals(Cleanup.DELETE)), m.toString());
    }

    /** 5-144(E-15) — 섞인 파일이어도 안 바뀌었으면(쓸 것이 없으면) 「통일했다」 를 안 붙인다 */
    @Test
    void unifyNoteOnlyWhenChanged() throws Exception {
        Files.writeString(root.resolve("w.js"), "var a = 1;\nconsole.warn(a);\nvar b = 2;\r\nvar c = 3;\n", StandardCharsets.UTF_8);
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, Cleanup.RULES, "LF", files, null, null, List.of());
        pv.rows().stream().filter(r -> r.rel().equals("w.js")).forEach(r -> assertTrue(r.changed()
                || r.skips().stream().noneMatch(k -> k.reason().startsWith(CleanupRunner.UNIFIED_EOL)), r.toString()));
    }

    /** 5-113(설계 70 §3-13) — 줄끝이 섞인 파일은 줄끝 통일을 안 골랐으면 건너뛴다(쓰면 안 고친 줄의 줄끝도 바뀐다) */
    @Test
    void mixedEolFileIsSkipped() throws Exception {
        Files.writeString(root.resolve("Mixed.java"), "package p;\r\n\r\nimport java.util.List;\nimport java.util.Map;\r\n\r\npublic class Mixed {\r\n"
                + "    Map<String, String> m;\n}\r\n", StandardCharsets.UTF_8);
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, NO_EOL, "LF", files, null, null, List.of());
        CleanupRunner.FileRow mixed = pv.rows().stream().filter(r -> r.rel().equals("Mixed.java")).findFirst().orElseThrow();
        assertFalse(mixed.changed(), mixed.toString());
        assertEquals(List.of(CleanupRunner.MIXED_EOL), mixed.skips().stream().map(Cleanup.Skip::reason).toList());
        CleanupRunner.FileRow ms = pv.rows().stream().filter(r -> r.rel().equals("Ms.java")).findFirst().orElseThrow();
        assertTrue(ms.changed() && "CRLF".equals(ms.lineEnding()), "섞이지 않은 CRLF 는 그대로 고친다 — " + ms);
        Path out = tmp.resolve("out/t/20261115-120000");
        CleanupRunner.Applied ap = CleanupRunner.apply(root.toString(), rules, NO_EOL, "LF", files, null,
                List.of(new CleanupRunner.FileRef("Mixed.java", mixed.hash(), "x")), List.of(), result("20261115-120000"), out);
        assertEquals(1, ap.failed(), ap.rows().toString());
        assertEquals(CleanupRunner.MIXED_EOL, ap.rows().get(0).reason());
        assertFalse(Files.exists(out.resolve("cleanup/Mixed.java")), "결과 폴더에 안 쓴다");

        CleanupRunner.Preview all = CleanupRunner.preview(root.toString(), rules, Cleanup.RULES, "LF", files, null, null, List.of());
        CleanupRunner.FileRow unified = all.rows().stream().filter(r -> r.rel().equals("Mixed.java")).findFirst().orElseThrow();
        assertTrue(unified.changed() && "LF".equals(unified.lineEnding()), "통일을 골랐으면 고친다 — " + unified);
        Path out2 = tmp.resolve("out/t/20261115-120001");
        CleanupRunner.apply(root.toString(), rules, Cleanup.RULES, "LF", files, null,
                List.of(new CleanupRunner.FileRef("Mixed.java", unified.hash(), unified.outHash())), List.of(), result("20261115-120001"), out2);
        String wrote = Files.readString(out2.resolve("cleanup/Mixed.java"), StandardCharsets.UTF_8);
        assertFalse(wrote.contains("\r") || wrote.contains("import java.util.List"), wrote);
    }

    /** 설계 60 — 로거 없는 클래스는 같은 소스 루트의 로거 꼴을 베껴 끼운다. 결과 폴더에만 쓰고 원본은 그대로(규칙 7) */
    @Test
    void loggerShapeFromSameSourceRoot() throws Exception {
        Path proj = tmp.resolve("logproj");
        Path pkg = proj.resolve("src/main/java/p");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("A.java"), "package p;\n\nimport org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;\n\nclass A {\n"
                + "    private static final Logger logger = LoggerFactory.getLogger(A.class);\n}\n", StandardCharsets.UTF_8);
        byte[] bOrig = "package p;\n\nclass B {\n    void f() {\n        System.out.println(\"b\");\n    }\n}\n".getBytes(StandardCharsets.UTF_8);
        Files.write(pkg.resolve("B.java"), bOrig);
        List<String> wanted = List.of("common.sysout");
        CleanupRunner.Preview pv = CleanupRunner.preview(proj.toString(), rules, wanted, "LF", files, null, null, List.of());
        Path out = files.resultRoot(null, tmp.resolve("out/t/20261109-000000/cleanup"), proj, "cleanup", "20261109-000000");
        CleanupRunner.apply(proj.toString(), rules, wanted, "LF", files, null, refs(pv), List.of(), out, tmp.resolve("logreport"));
        String b = Files.readString(out.resolve("src/main/java/p/B.java"), StandardCharsets.UTF_8);
        assertTrue(b.contains("import org.slf4j.Logger;") && b.contains("private static final Logger logger = LoggerFactory.getLogger(B.class);")
                && b.contains("logger.debug(\"b\");"), b);
        assertArrayEquals(bOrig, Files.readAllBytes(pkg.resolve("B.java")), "원본 불변");
    }

    /** 5-114b — 같은 소스 루트 VO 의 getter 반환 형식이 String 이면 equals 를 뒤집고, 아니면 RECEIVER 로 건너뛴다 */
    @Test
    void voGetterFromSameSourceRoot() throws Exception {
        Path proj = tmp.resolve("voproj");
        Path vo = proj.resolve("src/main/java/p/vo");
        Path web = proj.resolve("src/main/java/p/web");
        Files.createDirectories(vo);
        Files.createDirectories(web);
        Files.writeString(vo.resolve("BoardVO.java"), "package p.vo;\n\npublic class BoardVO {\n    public String getNttSj() {\n        return null;\n    }\n\n"
                + "    public Integer getNo() {\n        return null;\n    }\n}\n", StandardCharsets.UTF_8);
        Files.writeString(web.resolve("C.java"), "package p.web;\n\nimport p.vo.BoardVO;\n\nclass C {\n    boolean f(BoardVO vo) {\n"
                + "        if (vo.getNttSj().equals(\"A\")) {\n            return true;\n        }\n        return vo.getNo().equals(\"1\");\n    }\n}\n",
                StandardCharsets.UTF_8);
        CleanupRunner.Preview pv = CleanupRunner.preview(proj.toString(), rules, List.of("java.equalsLiteralFirst"), "LF", files, null, null, List.of());
        CleanupRunner.FileRow row = pv.rows().stream().filter(x -> x.rel().endsWith("C.java")).findFirst().orElseThrow(() -> new AssertionError(pv.rows().toString()));
        assertTrue(row.changed(), row.toString());
        Path out = files.resultRoot(null, tmp.resolve("out/t/20261121-000000/cleanup"), proj, "cleanup", "20261121-000000");
        CleanupRunner.apply(proj.toString(), rules, List.of("java.equalsLiteralFirst"), "LF", files, null, refs(pv), List.of(), out, tmp.resolve("voreport"));
        String c = Files.readString(out.resolve("src/main/java/p/web/C.java"), StandardCharsets.UTF_8);
        assertTrue(c.contains("if (\"A\".equals(vo.getNttSj())) {") && c.contains("return vo.getNo().equals(\"1\");"), c);
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

    /** 결과 폴더(9-3a·9-3b) — 테스트마다 out/t/<stamp>/cleanup */
    Path result(String stamp) throws Exception {
        return files.resultRoot(null, tmp.resolve("out/t/" + stamp + "/cleanup"), root, "cleanup", stamp);
    }

    @Test
    void applyWritesResultAndKeepsOriginal() throws Exception {
        Map<String, byte[]> before = bytes();
        CleanupRunner.Preview pv = CleanupRunner.preview(root.toString(), rules, NO_EOL, "LF", files, null, null, List.of());
        Path out = tmp.resolve("out/t/20261022-120000");
        CleanupRunner.Applied ap = CleanupRunner.apply(root.toString(), rules, NO_EOL, "LF", files, null, refs(pv), List.of(), result("20261022-120000"), out);
        assertEquals(3, ap.written(), ap.rows().toString());
        assertEquals(0, ap.failed());
        Map<String, byte[]> after = bytes();
        assertEquals(before.keySet(), after.keySet());
        before.forEach((k, v) -> assertArrayEquals(v, after.get(k), k + " 원본 그대로"));
        byte[] ms = Files.readAllBytes(out.resolve("cleanup/Ms.java"));
        String msText = new String(ms, MS949);
        assertFalse(msText.contains("import java.util.List"), msText);
        assertTrue(msText.contains("/** 한글 주석 */\r\n"), "MS949·CRLF 그대로 — " + msText);
        assertTrue(Files.exists(out.resolve("cleanup/web/pos.jsp")), "같은 상대 경로");
        assertTrue(Files.exists(out.resolve("cleanup/" + LocalFiles.MARK)), "표식");
        assertEquals(out.resolve("cleanup/Ms.java").toString(),
                ap.rows().stream().filter(w -> w.rel().equals("Ms.java")).findFirst().orElseThrow().path());
        String report = Files.readString(ap.report(), StandardCharsets.UTF_8);
        assertTrue(report.startsWith("코드 정리 20261022-120000 — 폴더 " + root + " · 썼다 3 · 실패 0 · 낡음 0"), report);
        assertTrue(report.contains("결과 폴더 " + out.resolve("cleanup") + " — 원본은 바뀌지 않았다"), report);
        assertFalse(report.contains("import java") || report.contains("debugger;"), "결과 글에 코드 본문 없음 — " + report); // 설계 64 — 단계 이름 imports 는 글이 아니다
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
        CleanupRunner.Applied ap = CleanupRunner.apply(root.toString(), rules, Cleanup.RULES, "LF", files, null, r, List.of(), result("20261022-120001"), out);
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
                result("20261022-120003"), out);
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
        CleanupRunner.apply(root.toString(), rules, Set.of("file.lineEnding"), "LF", files, null, refs(pv), List.of(), result("20261022-120002"), out);
        assertEquals("package p;\n\npublic class Crlf {\n}\n", Files.readString(out.resolve("cleanup/Crlf.java"), StandardCharsets.UTF_8));
        assertEquals("package p;\r\n\r\npublic class Crlf {\r\n}\r\n", Files.readString(root.resolve("Crlf.java"), StandardCharsets.UTF_8), "원본 그대로");
    }

    /** 5-81 — outHash 는 쓸 인코딩을 센다: 글·줄바꿈이 같고 인코딩만 달라도 다른 해시(적용이 낡은 미리보기를 거절하는 근거) */
    @Test
    void outHashCountsEncoding() {
        Cleanup.Result utf = new Cleanup.Result("x\n", "UTF-8", "LF", true, List.of(), List.of(), List.of(), List.of());
        Cleanup.Result ms = new Cleanup.Result("x\n", "MS949", "LF", true, List.of(), List.of(), List.of(), List.of());
        assertNotEquals(CleanupRunner.outHash(utf), CleanupRunner.outHash(ms));
        assertEquals(CleanupRunner.outHash(utf),
                CleanupRunner.outHash(new Cleanup.Result("x\n", "UTF-8", "LF", false, List.of(), List.of(), List.of(), List.of())), "changed 는 해시 밖");
    }
}
