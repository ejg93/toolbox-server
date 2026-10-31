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
    void offRulesAreSilent() {
        assertEquals(List.of(), hits("class A { boolean m(String a) { return a == \"x\"; } }", Set.of(ValueAst.WEEK_YEAR)));
    }
}
