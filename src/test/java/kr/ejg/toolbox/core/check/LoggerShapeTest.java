package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 설계 60 — 같은 소스 루트의 static 로거 선언 꼴 */
class LoggerShapeTest {

    static final String A_JAVA = "a/src/main/java/p/A.java";
    static final String SLF4J = "package p;\n\nimport org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;\n\nclass A {\n"
            + "    private static final Logger logger = LoggerFactory.getLogger(A.class);\n}\n";
    static final String COMMONS = "package p;\n\nimport org.apache.commons.logging.Log;\nimport org.apache.commons.logging.LogFactory;\n\nclass C {\n"
            + "    private static final Log log = LogFactory.getLog(C.class);\n}\n";

    @Test
    void ofSlf4j() {
        LoggerShape s = LoggerShape.of(SLF4J).orElseThrow();
        assertEquals(List.of("org.slf4j.Logger", "org.slf4j.LoggerFactory"), s.imports());
        assertEquals("logger", s.name());
        assertEquals("private static final Logger logger = LoggerFactory.getLogger(B.class);", s.decl("B"));
    }

    @Test
    void ofLog4jOneImport() {
        LoggerShape s = LoggerShape.of("import org.apache.log4j.Logger;\nclass A {\n    static Logger log = Logger.getLogger(A.class);\n}\n").orElseThrow();
        assertEquals(List.of("org.apache.log4j.Logger"), s.imports());
        assertEquals("log", s.name());
    }

    @Test
    void ofCommonsLogging() {
        LoggerShape s = LoggerShape.of(COMMONS).orElseThrow();
        assertEquals(List.of("org.apache.commons.logging.Log", "org.apache.commons.logging.LogFactory"), s.imports());
        assertEquals("private static final Log log = LogFactory.getLog(B.class);", s.decl("B"));
    }

    /** static 아님 · getClass() · 와일드카드 import 만 · 다른 클래스 이름 인자 — 꼴이 아니다 */
    @Test
    void ofRejects() {
        assertEquals(Optional.empty(), LoggerShape.of(SLF4J.replace("private static final", "private final")));
        assertEquals(Optional.empty(), LoggerShape.of(SLF4J.replace("getLogger(A.class)", "getLogger(getClass())")));
        assertEquals(Optional.empty(), LoggerShape.of(SLF4J.replace("import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;", "import org.slf4j.*;")));
        assertEquals(Optional.empty(), LoggerShape.of(SLF4J.replace("getLogger(A.class)", "getLogger(Other.class)")));
    }

    @Test
    void sourceRoot() {
        assertEquals("a/src/main/java/", LoggerShape.sourceRoot(A_JAVA));
        assertEquals("src/test/java/", LoggerShape.sourceRoot("src/test/java/p/A.java"));
        assertEquals("src/", LoggerShape.sourceRoot("src/p/A.java"));
        assertEquals("", LoggerShape.sourceRoot("p/A.java"));
    }

    /** 같은 루트의 가장 흔한 꼴 · 다른 소스 루트는 안 섞임 · 꼴 없는 루트는 빈 값 · 원본은 읽기만 */
    @Test
    void finder(@TempDir Path tmp) throws Exception {
        Path proj = tmp.resolve("proj");
        write(proj.resolve(A_JAVA), SLF4J);
        write(proj.resolve("a/src/main/java/p/B.java"), SLF4J.replace("class A", "class B").replace("(A.class)", "(B.class)"));
        write(proj.resolve("a/src/main/java/p/C.java"), COMMONS);
        write(proj.resolve("b/src/main/java/p/C.java"), COMMONS);
        write(proj.resolve("c/src/main/java/p/D.java"), "package p;\nclass D {\n}\n");
        long before = Files.getLastModifiedTime(proj.resolve(A_JAVA)).toMillis();
        Function<String, Optional<LoggerShape>> f = LoggerShape.finder(new LocalFiles(tmp.resolve("data")), proj.toString());
        assertEquals("logger", f.apply("a/src/main/java/p/X.java").orElseThrow().name());
        assertEquals("log", f.apply("b/src/main/java/q/Y.java").orElseThrow().name());
        assertEquals(Optional.empty(), f.apply("c/src/main/java/p/D.java"));
        assertEquals(before, Files.getLastModifiedTime(proj.resolve(A_JAVA)).toMillis(), "원본 불변");
    }

    static void write(Path p, String text) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }
}
