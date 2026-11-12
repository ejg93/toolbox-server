package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 설계 64 — 문장 꼴 다섯. 픽스처 줄을 직접 단언한다 */
class JavaShapeTest {

    static final Set<String> ON = Set.of(JavaShape.NEED_BRACES, JavaShape.EMPTY_STATEMENT, JavaShape.UNNEEDED_BLOCK, JavaShape.DEFAULT_LAST,
            JavaShape.INTERRUPTED);
    static final String NB = " needBraces";
    static final String ES = " emptyStatement";
    static final String UB = " unneededBlock";
    static final String DL = " defaultLast";
    static final String IE = " interruptedIgnored";

    static List<String> hits(String code) {
        CompilationUnit cu = new JavaSource().parse(code).getResult().orElseThrow();
        return JavaShape.apply(cu, ON).stream()
                .map(h -> h.node().getBegin().map(p -> p.line).orElse(0) + " " + h.rule().replace("java.", "")).toList();
    }

    static List<String> file(String name) throws Exception {
        return hits(Files.readString(JavaDefectsTest.DIR.resolve(name), StandardCharsets.UTF_8));
    }

    @Test
    void positiveFixture() throws Exception {
        assertEquals(List.of("7" + NB, "8" + NB, "10" + NB, "17" + NB, "19" + NB, "21" + NB, "23" + NB, "29" + ES, "30" + ES,
                "31" + UB, "34" + UB, "44" + DL, "53" + DL, "65" + IE, "70" + IE), file("PosShape.java"));
    }

    @Test
    void negativeFixture() throws Exception {
        assertEquals(List.of(), file("NegShape.java"));
    }

    /** 람다·안쪽 catch 의 interrupt() 는 바깥 catch 를 복원하지 않는다 · 꺼진 규칙은 안 돈다 */
    @Test
    void interruptContext() {
        assertEquals(List.of("1" + IE), hits("class A { void f() { try { g(); } catch (InterruptedException e) {"
                + " Runnable r = () -> Thread.currentThread().interrupt(); } } }"));
        assertEquals(List.of("1" + IE), hits("class A { void f() { try { g(); } catch (InterruptedException e) {"
                + " try { g(); } catch (RuntimeException x) { throw x; } } } }"));
        CompilationUnit cu = new JavaSource().parse("class A { void f() { if (a) g(); } }").getResult().orElseThrow();
        assertEquals(List.of(), JavaShape.apply(cu, Set.of(JavaShape.DEFAULT_LAST)));
    }
}
