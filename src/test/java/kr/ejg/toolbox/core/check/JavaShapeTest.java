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
            JavaShape.INTERRUPTED, JavaShape.STACK_STREAM, JavaShape.FALL_THROUGH);
    static final String NB = " needBraces";
    static final String ES = " emptyStatement";
    static final String UB = " unneededBlock";
    static final String DL = " defaultLast";
    static final String IE = " interruptedIgnored";
    static final String PS = " printStackTraceStream";
    static final String FT = " fallThrough";

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
                "31" + UB, "34" + UB, "44" + DL, "53" + DL, "65" + IE, "70" + IE, "80" + IE, "89" + PS, "95" + FT), file("PosShape.java"));
    }

    @Test
    void negativeFixture() throws Exception {
        assertEquals(List.of(), file("NegShape.java"));
    }

    /** R-28 — try 와 catch 가 다 탈출하면 안 떨어진다(egov EgovBndtManageServiceImpl) · 빈 블록뿐인 entry 는 묶인 label(jeesite FileUtils) */
    @Test
    void fallThroughExits() {
        assertEquals(List.of(), hits("class A { int f(int k) { switch (k) { case 1: try { return g(); } catch (RuntimeException e) { return 0; }"
                + " case 2: return 2; default: return 3; } } }"));
        assertEquals(List.of(), hits("class A { void f(int k) { switch (k) { case 0: { } case 1: { g(); break; } default: g(); } } }"));
        assertEquals(List.of("1" + FT), hits("class A { int f(int k) { switch (k) { case 1: try { return g(); } catch (RuntimeException e) { g(); }"
                + " case 2: return 2; default: return 3; } } }"));
        assertEquals(List.of(), hits("class A { int f(int k) { switch (k) { case 1: try { g(); } finally { return 1; }"
                + " case 2: return 2; default: return 3; } } }"));
    }

    /** R-28 — switch 머리(줄·바로 윗줄·첫 case 앞)의 drop through·fall through 주석은 그 switch 전체의 의도(commons Flat3Map) */
    @Test
    void fallThroughHeadNote() {
        assertEquals(List.of(), hits("class A { void f(int k) {\n switch (k) { // drop through\n case 3: g();\n case 2: g();\n case 1: g();\n } } }"));
        assertEquals(List.of(), hits("class A { void f(int k) {\n switch (k) {\n // drop through\n case 3: g();\n case 2: g();\n } } }"));
        assertEquals(List.of(), hits("class A { void f(int k) {\n if (k > 0) { // drop through\n switch (k) {\n case 3: g();\n case 2: g();\n } } } }"));
        assertEquals(List.of(), hits("class A { void f(int k) {\n switch (k) {\n case 3: g();\n // drops through\n case 2: g();\n } } }"));
        assertEquals(List.of("3" + FT), hits("class A { void f(int k) {\n switch (k) {\n case 3: g(); // count\n case 2: g();\n } } }"));
        // 번들 81 마무리 리뷰 I4 — 첫 case 줄의 꼬리 주석은 switch 머리가 아니다(묶인 label 의 주석)
        assertEquals(List.of("4" + FT), hits("class A { void f(int k) {\n switch (k) {\n case 1: // fall through\n case 2:\n a();\n case 3:\n b(); break;\n } } }"));
        assertEquals(List.of("5" + FT), hits("class A { void f(int k) {\n // drop through\n\n switch (k) {\n case 3: g();\n case 2: g();\n } } }"));
        // 5-99a — 바로 윗줄이 앞 형제 문장의 끝이면 그 꼬리 주석은 앞 문장 것 · no break 는 낱말 경계
        assertEquals(List.of("4" + FT), hits("class A { void f(int k) {\n x(); // fall through\n switch (k) {\n case 3: g();\n case 2: g();\n } } }"));
        String sw = "class A { void f(int k) {\n switch (k) {\n case 1: g(); // ";
        String tail = "\n case 2: g();\n } } }";
        assertEquals(List.of("3" + FT), hits(sw + "no breaking change" + tail));
        assertEquals(List.of("3" + FT), hits(sw + "no breakpoint here" + tail));
        assertEquals(List.of(), hits(sw + "no break" + tail));
        assertEquals(List.of(), hits(sw + "no break here" + tail));
    }

    /** 람다·안쪽 catch 의 interrupt() 는 바깥 catch 를 복원하지 않는다 · 꺼진 규칙은 안 돈다 */
    @Test
    void interruptContext() {
        assertEquals(List.of("1" + IE), hits("class A { void f() { try { g(); } catch (InterruptedException e) {"
                + " Runnable r = () -> Thread.currentThread().interrupt(); } } }"));
        assertEquals(List.of("1" + IE), hits("class A { void f() { try { g(); } catch (InterruptedException e) {"
                + " try { g(); } catch (RuntimeException x) { throw x; } } } }"));
        // 5-94 — union catch 도 본다(InterruptedException 이 든 것만)
        assertEquals(List.of("1" + IE), hits("class A { void f() { try { g(); } catch (IllegalStateException | InterruptedException e) { g(); } } }"));
        assertEquals(List.of(), hits("class A { void f() { try { g(); } catch (IllegalStateException | InterruptedException e) {"
                + " if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); } } } }"));
        assertEquals(List.of(), hits("class A { void f() { try { g(); } catch (IllegalStateException | ArithmeticException e) { g(); } } }"));
        CompilationUnit cu = new JavaSource().parse("class A { void f() { if (a) g(); } }").getResult().orElseThrow();
        assertEquals(List.of(), JavaShape.apply(cu, Set.of(JavaShape.DEFAULT_LAST)));
    }
}
