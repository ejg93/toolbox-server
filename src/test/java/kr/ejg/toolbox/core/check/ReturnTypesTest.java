package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 5-114a — 같은 소스 루트에 선언된 메서드의 반환 형식(단순 이름). 하나로 못 정하면 빈 값 */
class ReturnTypesTest {

    @TempDir
    Path tmp;

    private static CompilationUnit cu(String text) {
        return new JavaSource().parse(text).getResult().orElseThrow();
    }

    private Path write(Path proj, String rel, String text) throws Exception {
        Path p = proj.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void sameFile() {
        CompilationUnit a = cu("class A { String name() { return \"\"; } int size() { return 0; } }");
        assertEquals(Optional.of("String"), ReturnTypes.SAME_FILE.of("A.java", a, "A", "name", 0));
        assertEquals(Optional.of("int"), ReturnTypes.SAME_FILE.of("A.java", a, "A", "size", 0));
    }

    @Test
    void importedVoAndSuper() throws Exception {
        Path proj = tmp.resolve("proj");
        write(proj, "src/main/java/p/vo/Base.java", "package p.vo;\npublic class Base { public String getNttSj() { return null; } }\n");
        write(proj, "src/main/java/p/vo/BoardVO.java", "package p.vo;\npublic class BoardVO extends Base { public java.util.Date getDt() { return null; } }\n");
        String c = "package p.web;\nimport p.vo.BoardVO;\nclass C { }\n";
        write(proj, "src/main/java/p/web/C.java", c);
        ReturnTypes.Finder f = ReturnTypes.finder(new LocalFiles(tmp.resolve("data")), proj.toString());
        assertEquals(Optional.of("String"), f.of("src/main/java/p/web/C.java", cu(c), "BoardVO", "getNttSj", 0), "조상에서");
        assertEquals(Optional.of("Date"), f.of("src/main/java/p/web/C.java", cu(c), "BoardVO", "getDt", 0));
    }

    @Test
    void samePackage() throws Exception {
        Path proj = tmp.resolve("proj");
        write(proj, "src/main/java/p/T.java", "package p;\npublic class T { public String v() { return null; } }\n");
        String c = "package p;\nclass C { }\n";
        ReturnTypes.Finder f = ReturnTypes.finder(new LocalFiles(tmp.resolve("data")), proj.toString());
        assertEquals(Optional.of("String"), f.of("src/main/java/p/C.java", cu(c), "T", "v", 0));
    }

    @Test
    void notUnique() throws Exception {
        Path proj = tmp.resolve("proj");
        write(proj, "src/main/java/p/O.java", "package p;\npublic class O { public String pick(int a) { return null; } public Object pick(String a) { return null; } }\n");
        write(proj, "src/main/java/p/Broken.java", "package p;\npublic class Broken { public String v( { }\n");
        String c = "package p;\nimport q.*;\nclass C { }\n";
        ReturnTypes.Finder f = ReturnTypes.finder(new LocalFiles(tmp.resolve("data")), proj.toString());
        String rel = "src/main/java/p/C.java";
        assertEquals(Optional.empty(), f.of(rel, cu(c), "O", "pick", 1), "같은 인자 수 오버로드 둘");
        assertEquals(Optional.empty(), f.of(rel, cu(c), "Star", "v", 0), "별 import 로만 오는 형식");
        assertEquals(Optional.empty(), f.of(rel, cu(c), "Nope", "v", 0), "없는 형식");
        assertEquals(Optional.empty(), f.of(rel, cu(c), "Broken", "v", 0), "깨진 파일");
    }

    @Test
    void readsEachTypeOnce() throws Exception {
        Path proj = tmp.resolve("proj");
        Path t = write(proj, "src/main/java/p/T.java", "package p;\npublic class T { public String v() { return null; } }\n");
        String c = "package p;\nclass C { }\n";
        ReturnTypes.Finder f = ReturnTypes.finder(new LocalFiles(tmp.resolve("data")), proj.toString());
        assertEquals(Optional.of("String"), f.of("src/main/java/p/C.java", cu(c), "T", "v", 0));
        Files.delete(t);
        assertEquals(Optional.of("String"), f.of("src/main/java/p/C.java", cu(c), "T", "v", 0), "둘째는 다시 안 읽는다");
    }
}
