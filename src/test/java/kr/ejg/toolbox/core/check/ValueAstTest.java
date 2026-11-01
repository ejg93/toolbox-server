package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 설계 49 — 값·비교 셋. 픽스처 줄을 직접 단언한다 */
class ValueAstTest {

    static final Path DIR = Path.of("src/test/resources/fixtures/check/java");
    static final Set<String> ON = Set.of(ValueAst.REF_EQUALS, ValueAst.BIG_DECIMAL, ValueAst.WEEK_YEAR);
    static final String REF = " refEquals";
    static final String BIG = " bigDecimalDouble";
    static final String WEEK = " weekYear";

    static List<String> hits(String code, Set<String> on) {
        CompilationUnit cu = new JavaSource().parse(code).getResult().orElseThrow();
        return ValueAst.apply(cu, on).stream()
                .map(h -> h.node().getBegin().map(p -> p.line).orElse(0) + " " + h.rule().replace("java.", "")).toList();
    }

    static List<String> file(String name) throws Exception {
        return hits(Files.readString(DIR.resolve(name), StandardCharsets.UTF_8), ON);
    }

    @Test
    void positiveFixture() throws Exception {
        assertEquals(List.of("11" + WEEK, "14" + REF, "15" + REF, "16" + REF, "17" + REF, "18" + REF, "21" + REF,
                "26" + BIG, "27" + BIG, "28" + BIG, "29" + BIG, "30" + BIG, "31" + BIG, "32" + BIG,
                "37" + WEEK, "38" + WEEK, "40" + WEEK), file("PosValue.java"));
    }

    @Test
    void negativeFixture() throws Exception {
        assertEquals(List.of(), file("NegValue.java"));
    }

    @Test
    void boxedAgainstPrimitiveIsSilent() {
        assertEquals(List.of("3" + REF), hits("class A { Integer f; void m(Integer a, int b) {\n"
                + " boolean x = a == b || a == 1;\n"
                + " boolean y = this.f == a; } }", ON));
    }

    @Test
    void constantHopReportsFieldLineOnce() {
        assertEquals(List.of("1" + WEEK), hits("class A { static final String P = \"YYYY.MM.dd\";\n"
                + " Object f() { return new java.text.SimpleDateFormat(P); }\n"
                + " Object g() { return java.time.format.DateTimeFormatter.ofPattern(P); } }", ON));
    }

    @Test
    void localScopesResolveDeclaredType() {
        assertEquals(List.of("2" + BIG, "3" + REF), hits("class A { void m(java.util.List<Double> xs) { for (Double x : xs) {\n"
                + " Object o = new java.math.BigDecimal(-x);\n"
                + " java.util.function.BiPredicate<Long, Long> p = (Long a, Long b) -> a == b; } } }", ON));
    }

    @Test
    void identityShortcutKeepsStringLiteral() {
        assertEquals(List.of("2" + REF), hits("class A { int f(String a, String b) { if (a == b) { return 0; }\n"
                + " if (a == \"x\") { return 1; }\n"
                + " return (a == b) || b.equals(a) ? 2 : 3; } }", ON));
    }

    @Test
    void identityShortcutNeedsSameNames() {
        assertEquals(List.of("1" + REF), hits("class A { boolean m(String a, String b, String c, String d) {"
                + " return (a == b || c.equals(d)) && (a == b || java.util.Objects.equals(b, a)); } }", ON));
    }

    @Test
    void arrayIdentityIsSilent() {
        assertEquals(List.of(), hits("class A { String[] f; boolean m(String[] a, Long... b) { Long[] c = b; String g[] = a;\n"
                + " return a == this.f || b == c || g == a; } }", ON));
    }

    @Test
    void singleYIsNotWeekYear() {
        assertEquals(List.of(), hits("class A { void f(Object v) { update(\"dao.updateIndvdlYrycManage\", v); } }", ON));
    }

    /** 설계 50 — 리터럴을 뒤에 둔 equals */
    @Test
    void literalFirst() throws Exception {
        Set<String> on = Set.of(ValueAst.EQUALS_FIRST);
        assertEquals(List.of("6 equalsLiteralFirst", "10 equalsLiteralFirst", "14 equalsLiteralFirst"),
                hits(Files.readString(DIR.resolve("PosLiteral.java"), StandardCharsets.UTF_8), on));
        assertEquals(List.of(), hits(Files.readString(DIR.resolve("NegLiteral.java"), StandardCharsets.UTF_8), on));
    }

    @Test
    void literalFirstSkipsSuperAndNoScope() {
        Set<String> on = Set.of(ValueAst.EQUALS_FIRST);
        assertEquals(List.of(), hits("class A { boolean m() { return equals(\"a\") || super.equals(\"b\") || A.class.equals(\"c\")"
                + " || \"d\".equals(\"e\") || java.util.Objects.equals(this, \"f\"); } }", on));
        assertEquals(List.of("1 equalsLiteralFirst"), hits("class A { boolean m(A o) { return o.name().equals(\"a\"); } }", on));
        // 마무리 리뷰 — equalsIgnoreCase 는 String 만 받는다. 선언이 String 아닌 이름이면 뒤집으면 컴파일이 깨진다
        assertEquals(List.of("1 equalsLiteralFirst"), hits("class A { boolean m(Code c, String s) { return c.equalsIgnoreCase(\"a\")"
                + " || s.equalsIgnoreCase(\"b\"); } }", on));
        assertEquals(List.of("1 equalsLiteralFirst", "1 equalsLiteralFirst"), hits("class A { boolean m(Code c, String s) {"
                + " return c.equals(\"a\") || s.equalsIgnoreCase(\"b\") || c.equalsIgnoreCase(\"c\"); } }", on));
    }

    /** 설계 50 — 코드 정리가 valueOf 를 건너뛰는 float 인자 */
    @Test
    void isFloat() {
        CompilationUnit cu = new JavaSource().parse("class A { float g; void m(float f, double d, Float w, String s) {"
                + " new BigDecimal(0.1f); new BigDecimal(-1.5F); new BigDecimal((float) d); new BigDecimal(f); new BigDecimal(w);"
                + " new BigDecimal(Float.parseFloat(s)); new BigDecimal(0.1); new BigDecimal(d); new BigDecimal((double) f); } }")
                .getResult().orElseThrow();
        assertEquals(List.of(true, true, true, true, true, true, false, false, false),
                cu.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class).stream().map(ValueAst::isFloat).toList());
    }

    @Test
    void offRulesAreSilent() {
        assertEquals(List.of(), hits("class A { boolean m(String a) { return a == \"x\"; } }", Set.of(ValueAst.WEEK_YEAR)));
    }
}
