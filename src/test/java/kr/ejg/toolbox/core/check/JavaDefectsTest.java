package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 설계 49 — 예외·동시성 다섯. 픽스처 줄을 직접 단언한다(fixturesGolden 의 「안 걸린 규칙」 은 kind java 줄을 안 댄다) */
class JavaDefectsTest {

    static final Path DIR = Path.of("src/test/resources/fixtures/check/java");
    static final Set<String> ON = Set.of(JavaDefects.FINALLY_RETURN, JavaDefects.FINALLY_THROW, JavaDefects.LOST_CAUSE,
            JavaDefects.THREAD_LOCAL, JavaDefects.STATIC_DATE_FORMAT);
    static final String RET = " finallyReturn";
    static final String THROW = " finallyThrow";
    static final String LOST = " lostCause";
    static final String SDF = " staticDateFormat";

    static List<String> hits(String code, Set<String> on) {
        CompilationUnit cu = new JavaSource().parse(code).getResult().orElseThrow();
        return JavaDefects.apply(cu, on).stream()
                .map(h -> h.node().getBegin().map(p -> p.line).orElse(0) + " " + h.rule().replace("java.", "")).toList();
    }

    static List<String> file(String name) throws Exception {
        return hits(Files.readString(DIR.resolve(name), StandardCharsets.UTF_8), ON);
    }

    @Test
    void positiveFixture() throws Exception {
        assertEquals(List.of("9" + SDF, "10" + SDF, "11 threadLocalRemove", "17" + RET, "26" + RET, "38" + RET,
                "47" + THROW, "59" + THROW, "68" + LOST, "76" + LOST), file("PosDefects.java"));
    }

    @Test
    void negativeFixture() throws Exception {
        assertEquals(List.of(), file("NegDefects.java"));
    }

    @Test
    void loopInsideFinallyIsNotEscape() {
        assertEquals(List.of("3" + RET), hits("class A { void f(int[] a) { for (int x : a) { try { g(); } finally {\n"
                + " for (int y : a) { if (y > 0) { break; } }\n"
                + " break; } } } }", ON));
    }

    @Test
    void throwInNestedTryBlockIsCaught() {
        assertEquals(List.of("3" + THROW), hits("class A { void f() { try { g(); } finally {\n"
                + " try { throw new IllegalStateException(\"a\"); } catch (RuntimeException e) { h(e); }\n"
                + " try { g(); } finally { throw new IllegalStateException(\"b\"); } } } }", ON));
    }

    @Test
    void anonymousAndLocalClassFieldsAreSeen() {
        assertEquals(List.of("1" + SDF), hits("class A { Object o = new Object() { static java.text.DateFormat D; };\n"
                + " static java.time.format.DateTimeFormatter F; }", ON));
    }

    @Test
    void offRulesAreSilent() {
        assertEquals(List.of(), hits("class A { static SimpleDateFormat F; void f() { try { g(); } finally { return; } } }",
                Set.of(JavaDefects.LOST_CAUSE)));
    }
}
