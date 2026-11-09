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
    static final String FIRST = " equalsLiteralFirst";

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
                "37" + WEEK, "38" + WEEK, "40" + WEEK, "51" + REF, "57" + REF), file("PosValue.java"));
    }

    @Test
    void negativeFixture() throws Exception {
        assertEquals(List.of(), file("NegValue.java"));
    }

    /** 설계 60 — String 수신자 무인자 getBytes() · byte[] 한 인자 new String */
    @Test
    void defaultCharset() throws Exception {
        Set<String> on = Set.of(ValueAst.DEFAULT_CHARSET);
        String cs = " defaultCharset";
        assertEquals(List.of("8" + cs, "12" + cs, "16" + cs, "20" + cs, "24" + cs),
                hits(Files.readString(DIR.resolve("PosCharset.java"), StandardCharsets.UTF_8), on));
        assertEquals(List.of(), hits(Files.readString(DIR.resolve("NegCharset.java"), StandardCharsets.UTF_8), on));
    }

    /** 설계 60 — 수신자 형식을 못 풀면 지적 안 함(MultipartFile.getBytes() 꼴) · String 필드·valueOf·+ 는 String */
    @Test
    void defaultCharsetNeedsStringReceiver() {
        Set<String> on = Set.of(ValueAst.DEFAULT_CHARSET);
        assertEquals(List.of(), hits("class A { byte[] f(Object o, MultipartFile mf) { o.getBytes(); g().getBytes(); return mf.getBytes(); } }", on));
        assertEquals(List.of("1 defaultCharset", "2 defaultCharset", "3 defaultCharset"), hits("class A { String n; byte[] f(int i) { this.n.getBytes();\n"
                + " String.valueOf(i).getBytes();\n"
                + " return (\"a\" + i).getBytes(); } }", on));
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
        assertEquals(List.of("6" + FIRST, "10" + FIRST, "14" + FIRST, "23" + FIRST),
                hits(Files.readString(DIR.resolve("PosLiteral.java"), StandardCharsets.UTF_8), on));
        assertEquals(List.of(), hits(Files.readString(DIR.resolve("NegLiteral.java"), StandardCharsets.UTF_8), on));
    }

    /** 5-61c — if (a == b) return CONST; 도 빠른 길(commons-lang BooleanUtils 꼴) */
    @Test
    void constantReturnIsShortcut() {
        assertEquals(List.of("3" + REF), hits("class A { static final Boolean T = Boolean.TRUE; Boolean x;\n"
                + " Boolean f(Integer a, Integer b) { if (a == b) { return T; } return a.equals(b); }\n"
                + " Boolean g(Integer a, Integer b) { if (a == b) { return x; } return a.equals(b); } }", ON));
    }

    /** 5-61c — OR 이 셋 이상이어도 equals 쪽을 찾는다 */
    @Test
    void threeTermOrIsShortcut() {
        assertEquals(List.of("2" + REF), hits("class A { boolean f(Integer a, Integer b, boolean x) { return a == b || x || a.equals(b); }\n"
                + " boolean g(Integer a, Integer b, boolean x) { return a == b || x || x; } }", ON));
    }

    /** 5-61c — static final 필드를 new 로 만든 표지 객체는 동일성 비교가 맞다(==·!= 둘 다) */
    @Test
    void sentinelObjectIsSilent() {
        assertEquals(List.of("2" + REF), hits("class A { static final String MARK = new String(\"\"); static final String LIT = \"\";\n"
                + " boolean f(String s) { return s == MARK || s != this.MARK || s == LIT; } }", ON));
    }

    /** 5-61c — String.format·String.valueOf 는 날짜 형식 자리가 아니다 */
    @Test
    void stringFormatIsNotDateSlot() {
        assertEquals(List.of("2" + WEEK), hits("class A { String f(Object v) { return String.format(\"YYYY-MM\", v); }\n"
                + " String g(java.util.Date d) { return new java.text.SimpleDateFormat(\"YYYY-MM\").format(d); } }", ON));
    }

    /** 5-61c — enum·record 필드·record 구성요소의 선언 형식 */
    @Test
    void enumAndRecordFieldsResolve() {
        assertEquals(List.of("1" + REF, "2" + REF), hits("enum E { A; Integer n; boolean f(Integer o) { return n == o; } }\n"
                + "record R(Long n) { boolean f(Long o) { return n == o; } }", ON));
    }

    /** 5-61c — 같은 파일 static final String 리터럴 상수를 뒤에 둔 equals 도 지적(다른 형식·지역 변수는 아님) */
    @Test
    void constantArgIsLiteralFirst() {
        Set<String> on = Set.of(ValueAst.EQUALS_FIRST);
        assertEquals(List.of("2" + FIRST), hits("class A { static final String C = \"a\"; static final Object O = new Object(); static String NF = \"b\";\n"
                + " boolean f(String x) { String l = \"c\"; return x.equals(C) || x.equals(O) || x.equals(l) || x.equals(NF) || C.equals(x); } }", on));
    }

    @Test
    void literalFirstSkipsSuperAndNoScope() {
        Set<String> on = Set.of(ValueAst.EQUALS_FIRST);
        assertEquals(List.of(), hits("class A { boolean m() { return equals(\"a\") || super.equals(\"b\") || A.class.equals(\"c\")"
                + " || \"d\".equals(\"e\") || java.util.Objects.equals(this, \"f\"); } }", on));
        assertEquals(List.of("1" + FIRST), hits("class A { boolean m(A o) { return o.name().equals(\"a\"); } }", on));
        // 마무리 리뷰 — equalsIgnoreCase 는 String 만 받는다. 선언이 String 아닌 이름이면 뒤집으면 컴파일이 깨진다
        assertEquals(List.of("1" + FIRST), hits("class A { boolean m(Code c, String s) { return c.equalsIgnoreCase(\"a\")"
                + " || s.equalsIgnoreCase(\"b\"); } }", on));
        assertEquals(List.of("1" + FIRST, "1" + FIRST), hits("class A { boolean m(Code c, String s) {"
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
