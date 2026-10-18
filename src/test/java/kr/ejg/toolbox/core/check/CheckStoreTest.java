package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 5-4 — 검사 이력 저장·조회·비교. 발췌는 저장하지 않는다(절대 규칙 3) */
class CheckStoreTest {

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

    static Finding f(String file, int line, String rule) {
        return new Finding(file, line, "common", rule, "warn", "원문 " + line);
    }

    @Test
    void saveList() throws Exception {
        CheckStore store = new CheckStore(db);
        long a = store.save("t", "C:/p", false, List.of("common", "java"), List.of(f("A.java", 3, "common.todo"), f("A.java", 9, "common.sysout")));
        long b = store.save("t", "C:/p", false, List.of("common"), List.of(f("A.java", 4, "common.todo"), f("A.java", 9, "common.sysout"),
                f("B.java", 1, "file.header")));
        long other = store.save("t", "C:/q", false, List.of("common"), List.of());
        assertEquals(List.of(other, b, a), store.runs().stream().map(CheckStore.RunInfo::id).toList());
        CheckStore.RunInfo info = store.run(b).orElseThrow();
        assertEquals(3, info.findings());
        assertEquals("common", info.groups());
        List<Finding> got = store.findings(b);
        assertEquals(3, got.size());
        assertNull(got.get(0).excerpt(), "발췌는 저장 안 함");
        assertEquals(List.of("A.java:3 common.todo", "A.java:9 common.sysout"),
                store.findings(a).stream().map(x -> x.file() + ":" + x.line() + " " + x.rule()).toList(), "파일·줄·규칙 순");
    }

    /** 픽스처 폴더 검사가 RunResult 를 채운다 — 진행 손잡이 없이 */
    @Test
    void runnerOverFixtureFolder() throws Exception {
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        CheckRunner.RunResult r = CheckRunner.run(CheckRulesTest.FIXTURES.toAbsolutePath().toString(), RuleSet.builtin(), files, null);
        assertTrue(r.files() > 20, r.toString());
        assertEquals(0, r.skipped());
        assertEquals(2, r.parseErrors(), "java/PosBroken.java · mybatis/PosBroken.xml");
        assertTrue(r.findings().stream().anyMatch(f -> f.rule().equals("java.dupMapping")), "파일 사이 규칙");
        assertEquals(6, r.suppressed(), "5-29 — ignore/neg.java·neg.jsp 의 tb-ignore 표식이 뺀 지적");
        assertTrue(r.findings().stream().noneMatch(f -> f.rule().equals("mybatis.namespace") && f.file().endsWith("NegMapper.xml")),
                "namespace 는 같은 실행의 Java 와 — UserDAO 는 mybatis/src 에 있다");
    }

    /** 5-26 — codecheck.exclude: 폴더 글롭은 아래를 통째로, 파일 글롭은 그 파일만, 맨 앞 ** / 는 루트 바로 아래도 맞는다 */
    @Test
    void runnerSkipsExcludedPaths() throws Exception {
        Path root = tmp.resolve("proj");
        String sysout = "class A { void f() { System.out.println(1); } }\n";
        for (String rel : List.of("a/A.java", "lib/B.java", "src/web/lib/C.java", "D.min.java", "x/E.min.java")) {
            Path f = root.resolve(rel);
            java.nio.file.Files.createDirectories(f.getParent());
            java.nio.file.Files.writeString(f, sysout);
        }
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        java.util.function.Function<List<String>, List<String>> filesWithSysout = ex -> {
            try {
                return CheckRunner.run(root.toString(), RuleSet.builtin(), files, null, null, ex).findings().stream()
                        .filter(f -> f.rule().equals("common.sysout")).map(Finding::file).distinct().sorted().toList();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        };
        assertEquals(List.of("D.min.java", "a/A.java", "lib/B.java", "src/web/lib/C.java", "x/E.min.java"), filesWithSysout.apply(List.of()));
        assertEquals(List.of("D.min.java", "a/A.java", "src/web/lib/C.java", "x/E.min.java"), filesWithSysout.apply(List.of("lib/**")),
                "루트 기준 — src/web/lib 는 안 맞는다");
        assertEquals(List.of("a/A.java"), filesWithSysout.apply(List.of("**/LIB/**", "**/*.min.java")), "대소문자 무시 · 맨 앞 **/ 는 비어도");
        assertEquals(filesWithSysout.apply(List.of()), filesWithSysout.apply(null), "null 은 안 뺀다");
        assertEquals(List.of("D.min.java", "a/A.java", "lib/B.java", "x/E.min.java"), filesWithSysout.apply(List.of("src/**/web/**")),
                "가운데 /**/ 는 0단계 폴더도(src/web)");
    }

    @Test
    void guessLang() {
        assertEquals("xml", CheckRunner.guessLang("<?xml version=\"1.0\"?><mapper/>"));
        assertEquals("jsp", CheckRunner.guessLang("<%@ page %>"));
        assertEquals("java", CheckRunner.guessLang("package a;\nclass A {}"));
        assertEquals("ts", CheckRunner.guessLang("import { a } from 'b';"));
        assertEquals("js", CheckRunner.guessLang("var a = 1;"));
    }
}
