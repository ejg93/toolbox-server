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
            JavaDefects.THREAD_LOCAL, JavaDefects.STATIC_DATE_FORMAT, "java.catchThrowable", "java.logAndThrow", "java.mappingPrivate",
            "java.floatEquals");
    static final String CT = " catchThrowable";
    static final String LT = " logAndThrow";
    static final String MP = " mappingPrivate";
    static final String FE = " floatEquals";
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
                "47" + THROW, "59" + THROW, "68" + LOST, "76" + LOST,
                "95" + SDF, "96" + SDF, "102" + CT, "107" + CT, "112" + CT, "123" + LT, "129" + LT, "133" + MP, "138" + MP,
                "144" + FE, "146" + FE, "157" + LOST, "174" + FE, "183" + CT), file("PosDefects.java"));
    }

    /** 5-67 — 수신자로만 쓴 매개변수(t.getMessage())는 원인을 안 싣는다 — 다시 던지기가 아니다 */
    @Test
    void receiverOnlyIsNotRethrow() {
        assertEquals(List.of("1" + CT), hits("class A { void f() { try { g(); } catch (Throwable t) { throw new RuntimeException(t.getMessage()); } } }", ON));
        assertEquals(List.of(), hits("class A { void f() { try { g(); } catch (Throwable t) { throw new RuntimeException(t.getMessage(), t); } } }", ON));
        assertEquals(List.of(), hits("class A { void f() { try { g(); } catch (Throwable t) { throw new RuntimeException(\"m: \" + t); } } }", ON));
    }

    /** 5-61b·5-64 ① — throw 식 어디든 catch 매개변수가 나오면 다시 던지기(그대로·감싸서·형 변환) — 아니다 */
    @Test
    void rethrowIsNotCatchThrowable() {
        assertEquals(List.of(), hits("class A { void f() throws Throwable { try { g(); } catch (Throwable t) { throw t; }\n"
                + " try { g(); } catch (Throwable t) { throw new RuntimeException(t); }\n"
                + " try { g(); } catch (Throwable t) { throw (Error) t; } } }", ON));
    }

    /** 5-64 ② — 래퍼끼리 ==·!= 는 refEquals 몫이라 floatEquals 가 아니다. 래퍼 대 기본형은 floatEquals */
    @Test
    void wrapperPairIsRefEqualsOnly() {
        assertEquals(List.of("1" + FE), hits("class A { boolean f(Double w, Double v, double d) { return w != v || w == d; } }", ON));
    }

    /** 5-61b — 로그만·던지기만은 조용, 로그(로거·printStackTrace) + throw 는 첫 throw 자리 */
    @Test
    void logOnlyOrThrowOnlyIsSilent() {
        assertEquals(List.of("3" + LT), hits("class A { void f() throws Exception { try { g(); } catch (Exception e) { log.warn(\"x\", e); }\n"
                + " try { g(); } catch (Exception e) { throw e; }\n"
                + " try { g(); } catch (Exception e) { LOGGER.error(\"x\", e.getMessage()); throw new IllegalStateException(e); }\n"
                + " try { g(); } catch (RuntimeException e) { if (retry) { throw e; } LOG.info(\"다시\"); } } }", ON));
    }

    /** 5-61b — 같은 글끼리(x != x — NaN 검사)·null 비교·정수는 floatEquals 가 아니다 */
    @Test
    void nanCheckIsNotFloatEquals() {
        assertEquals(List.of("2" + FE), hits("class A { boolean f(double d, Double w, int i, int j) { boolean a = d != d || w == null || i == j;\n"
                + " return a && d == 1; } }", ON));
    }

    /** 5-61b — 중첩 catch 의 throw 는 안쪽 catch 가 판정(바깥이 매개변수를 안 써도 안쪽이 쓰면 아니다) */
    @Test
    void nestedCatchJudgedInside() {
        assertEquals(List.of("3" + LOST), hits("class A { void f() { try { g(); } catch (Exception e) {\n"
                + " try { g(); } catch (RuntimeException io) { throw new IllegalStateException(\"m\", io); } }\n"
                + " try { g(); } catch (Exception e) { try { g(); } catch (RuntimeException io) { throw new IllegalStateException(\"m\"); } } } }", ON));
    }

    /** 5-61b — ThreadLocal 필드를 인자로 넘기면(넘겨 지우는 꼴) remove 로 친다 */
    @Test
    void threadLocalPassedToCleaner() {
        assertEquals(List.of("1 threadLocalRemove"), hits("class A { static ThreadLocal<String> T = new ThreadLocal<>(); static ThreadLocal<String> U = new ThreadLocal<>();\n"
                + " void f() { T.set(\"a\"); U.set(\"b\"); clear(U); }\n"
                + " static void clear(ThreadLocal<?> t) { t.remove(); } }", ON));
    }

    /** claude[bot] ① — logAndThrow 의 로거 판정은 JavaRules 사본과 같아야 한다(가시성을 못 열어 복사했다 — DeclarationOrder) */
    @Test
    void loggerPatternsMatchJavaRules() throws Exception {
        Path main = Path.of("src/main/java/kr/ejg/toolbox/core/check");
        List<String> defects = Files.readAllLines(main.resolve("JavaDefects.java"), StandardCharsets.UTF_8);
        List<String> rules = Files.readAllLines(main.resolve("JavaRules.java"), StandardCharsets.UTF_8);
        for (String name : List.of(" LOG_METHODS = ", " LOGGER = ")) {
            List<String> a = defects.stream().filter(l -> l.contains(name)).map(l -> l.substring(l.indexOf(name))).toList();
            List<String> b = rules.stream().filter(l -> l.contains(name)).map(l -> l.substring(l.indexOf(name))).toList();
            assertEquals(1, b.size(), name);
            assertEquals(b, a, name);
        }
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
