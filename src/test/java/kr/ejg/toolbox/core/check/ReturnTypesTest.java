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

    static final String STRING = "String";
    static final String PROJ = "proj";
    static final String DATA = "data";
    static final String C_JAVA = "src/main/java/p/C.java";

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
        assertEquals(Optional.of(STRING), ReturnTypes.SAME_FILE.of("A.java", a, "A", "name", 0));
        assertEquals(Optional.of("int"), ReturnTypes.SAME_FILE.of("A.java", a, "A", "size", 0));
    }

    @Test
    void importedVoAndSuper() throws Exception {
        Path proj = tmp.resolve(PROJ);
        write(proj, "src/main/java/p/vo/Base.java", "package p.vo;\npublic class Base { public String getNttSj() { return null; } }\n");
        write(proj, "src/main/java/p/vo/BoardVO.java", "package p.vo;\npublic class BoardVO extends Base { public java.util.Date getDt() { return null; } }\n");
        String c = "package p.web;\nimport p.vo.BoardVO;\nclass C { }\n";
        write(proj, "src/main/java/p/web/C.java", c);
        ReturnTypes.Finder f = ReturnTypes.finder(new LocalFiles(tmp.resolve(DATA)), proj.toString());
        assertEquals(Optional.of(STRING), f.of("src/main/java/p/web/C.java", cu(c), "BoardVO", "getNttSj", 0), "조상에서");
        assertEquals(Optional.of("Date"), f.of("src/main/java/p/web/C.java", cu(c), "BoardVO", "getDt", 0));
    }

    /** 5-136(D-16) — 그 형식에 하나여도 조상의 같은 이름·인자 수 반환 형식을 모은다 — 두 가지면 모름 · 같으면 그 형식 · 조상에 없으면 그 형식 */
    @Test
    void ancestorOverloadsAreCollected() throws Exception {
        Path proj = tmp.resolve(PROJ);
        write(proj, "src/main/java/p/Base.java", "package p;\npublic class Base { public Object code(String k) { return null; } "
                + "public String same(String k) { return null; } }\n");
        write(proj, "src/main/java/p/Sub.java", "package p;\npublic class Sub extends Base { public String code(int k) { return null; } "
                + "public String same(String k) { return null; } public String own(int k) { return null; } }\n");
        String c = "package p;\nclass C { }\n";
        ReturnTypes.Finder f = ReturnTypes.finder(new LocalFiles(tmp.resolve(DATA)), proj.toString());
        assertEquals(Optional.empty(), f.of(C_JAVA, cu(c), "Sub", "code", 1), "조상 오버로드와 반환 형식이 다르다");
        assertEquals(Optional.of(STRING), f.of(C_JAVA, cu(c), "Sub", "same", 1), "재정의 — 같은 형식");
        assertEquals(Optional.of(STRING), f.of(C_JAVA, cu(c), "Sub", "own", 1), "조상에 없다");
    }

    @Test
    void samePackage() throws Exception {
        Path proj = tmp.resolve(PROJ);
        write(proj, "src/main/java/p/T.java", "package p;\npublic class T { public String v() { return null; } }\n");
        String c = "package p;\nclass C { }\n";
        ReturnTypes.Finder f = ReturnTypes.finder(new LocalFiles(tmp.resolve(DATA)), proj.toString());
        assertEquals(Optional.of(STRING), f.of(C_JAVA, cu(c), "T", "v", 0));
    }

    /** 5-126 — 묻는 파일의 enum·record 이름은 「모름」: 같은 이름의 같은 패키지 클래스로 안 넘어간다 */
    @Test
    void nonClassTypeNamesAreUnknown() throws Exception {
        Path proj = tmp.resolve(PROJ);
        write(proj, "src/main/java/p/Status.java", "package p;\npublic class Status { public String code() { return \"\"; } }\n");
        write(proj, "src/main/java/p/R.java", "package p;\npublic class R { public String code() { return \"\"; } }\n");
        String c = "package p;\nclass C { enum Status { A; int code() { return 0; } } record R(int code) { } }\n";
        ReturnTypes.Finder f = ReturnTypes.finder(new LocalFiles(tmp.resolve(DATA)), proj.toString());
        assertEquals(Optional.empty(), f.of(C_JAVA, cu(c), "Status", "code", 0), "같은 파일 enum");
        assertEquals(Optional.empty(), f.of(C_JAVA, cu(c), "R", "code", 0), "같은 파일 record");
        String d = "package p;\nclass D { }\n";
        assertEquals(Optional.of(STRING), f.of("src/main/java/p/D.java", cu(d), "Status", "code", 0), "중첩 형식이 없으면 같은 패키지");
    }

    @Test
    void notUnique() throws Exception {
        Path proj = tmp.resolve(PROJ);
        write(proj, "src/main/java/p/O.java", "package p;\npublic class O { public String pick(int a) { return null; } public Object pick(String a) { return null; } }\n");
        write(proj, "src/main/java/p/Broken.java", "package p;\npublic class Broken { public String v( { }\n");
        String c = "package p;\nimport q.*;\nclass C { }\n";
        ReturnTypes.Finder f = ReturnTypes.finder(new LocalFiles(tmp.resolve(DATA)), proj.toString());
        String rel = C_JAVA;
        assertEquals(Optional.empty(), f.of(rel, cu(c), "O", "pick", 1), "같은 인자 수 오버로드 둘");
        assertEquals(Optional.empty(), f.of(rel, cu(c), "Star", "v", 0), "별 import 로만 오는 형식");
        assertEquals(Optional.empty(), f.of(rel, cu(c), "Nope", "v", 0), "없는 형식");
        assertEquals(Optional.empty(), f.of(rel, cu(c), "Broken", "v", 0), "깨진 파일");
    }

    @Test
    void readsEachTypeOnce() throws Exception {
        Path proj = tmp.resolve(PROJ);
        Path t = write(proj, "src/main/java/p/T.java", "package p;\npublic class T { public String v() { return null; } }\n");
        String c = "package p;\nclass C { }\n";
        ReturnTypes.Finder f = ReturnTypes.finder(new LocalFiles(tmp.resolve(DATA)), proj.toString());
        assertEquals(Optional.of(STRING), f.of(C_JAVA, cu(c), "T", "v", 0));
        Files.delete(t);
        assertEquals(Optional.of(STRING), f.of(C_JAVA, cu(c), "T", "v", 0), "둘째는 다시 안 읽는다");
    }
}
