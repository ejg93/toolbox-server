package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.ast.CompilationUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 5-73b — 단일 import 끼우기 공용(Imports) 직접 시험. 판정 표: has · clash · used · line · text · simple */
class ImportsTest {

    private static final String OBJECTS = "java.util.Objects";
    private static final String ACME = "com.acme.Objects";
    private static final String CLASS_A = "class A {}\n";

    private static CompilationUnit cu(String text) {
        return new JavaSource().parse(text).getResult().orElseThrow();
    }

    @Test
    void has() {
        assertTrue(Imports.has(cu("import java.util.Objects;\n" + CLASS_A), OBJECTS));
        assertFalse(Imports.has(cu(CLASS_A), OBJECTS));
        assertFalse(Imports.has(cu("import java.util.*;\n" + CLASS_A), OBJECTS), "별표 import 는 단일 import 가 아니다");
        assertFalse(Imports.has(cu("import java.util.Objects;\nimport com.acme.Objects;\n" + CLASS_A), OBJECTS), "같은 단순 이름이 겹친다");
    }

    @Test
    void clash() {
        assertTrue(Imports.clash(cu("import com.acme.Objects;\n" + CLASS_A), OBJECTS));
        assertFalse(Imports.clash(cu("import java.util.Objects;\n" + CLASS_A), OBJECTS), "같은 fqn 만");
        assertFalse(Imports.clash(cu("import static com.acme.Objects;\n" + CLASS_A), OBJECTS), "static import 는 안 센다");
        assertTrue(Imports.clash(cu("import com.acme.Objects;\n" + CLASS_A), OBJECTS) && !Imports.clash(cu(CLASS_A), ACME));
    }

    @Test
    void used() {
        assertTrue(Imports.used(cu("class A { int f() { return Objects.hash(1); } }\n"), "Objects"), "식의 이름");
        assertTrue(Imports.used(cu("class A { Objects o; }\n"), "Objects"), "형식");
        assertFalse(Imports.used(cu("class A { java.util.Objects o; }\n"), "Objects"), "scope 가 있는 형식");
    }

    @Test
    void line() {
        assertEquals(4, Imports.line(cu("package p;\n\nimport a.B;\nimport c.D;\n\n" + CLASS_A)));
        assertEquals(1, Imports.line(cu("package p;\n\n" + CLASS_A)));
        assertEquals(0, Imports.line(cu(CLASS_A)));
    }

    @Test
    void text() {
        assertEquals("import c.D;", Imports.text(cu("import a.B;\n" + CLASS_A), List.of("a.B", "c.D")));
        assertEquals("import a.B;\nimport c.D;", Imports.text(cu(CLASS_A), List.of("a.B", "c.D")));
        assertEquals("", Imports.text(cu("import a.B;\nimport c.D;\n" + CLASS_A), List.of("a.B", "c.D")));
    }

    @Test
    void simple() {
        assertEquals("C", Imports.simple("a.b.C"));
    }
}
