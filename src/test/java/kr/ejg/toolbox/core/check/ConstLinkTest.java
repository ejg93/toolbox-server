package kr.ejg.toolbox.core.check;

import static kr.ejg.toolbox.core.check.TaintLinkTest.check;
import static kr.ejg.toolbox.core.check.TaintLinkTest.src;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 5-56a(설계 45 §3-3~5) — 비밀번호·키 싱크 인자를 메서드·파일 사이 상수로 푼다 */
class ConstLinkTest {

    static final String CONN = "    java.sql.DriverManager.getConnection(u, s, pw);";
    static final String OPEN = "  void open(String pw) throws Exception {";
    static final String B = "B.java";
    static final String CLASS_B = "class B {";
    static final String END = "  }";
    static final String CLOSE = "}";
    static final String HIT_B = "B.java:3 hardcodedPassword";
    static final String A = "A.java";
    static final String CLASS_A = "class A {";

    @Test
    void staticFieldOfOtherClass() {
        assertEquals(List.of("A.java:3 hardcodedPassword"), check(
                src("Config.java", "class Config {", "  static final String DB_PW = \"s3cret\";", CLOSE),
                src(A, CLASS_A, "  void f() throws Exception {", "    java.sql.DriverManager.getConnection(u, s, Config.DB_PW);",
                        END, CLOSE)));
    }

    @Test
    void fieldWrittenInOtherFile() {
        assertEquals(List.of("T.java:4 hardcodedPassword"), check(
                src("S.java", "class S {", "  static String data;", "  void bad() { data = \"pw1\"; }", CLOSE),
                src("T.java", "class T {", "  void sink() throws Exception {", "    String d = S.data;",
                        "    java.sql.DriverManager.getConnection(u, s, d);", END, CLOSE)));
    }

    @Test
    void constantReturnOfOtherClass() {
        assertEquals(List.of("A.java:3 hardcodedKey"), check(
                src("K.java", "class K {", "  static String key() { return \"0123456789abcdef\"; }", CLOSE),
                src(A, CLASS_A, "  void f() {", "    new javax.crypto.spec.SecretKeySpec(K.key().getBytes(), \"AES\");", END,
                        CLOSE)));
    }

    @Test
    void callerPassesConstant() {
        assertEquals(List.of(HIT_B), check(
                src(A, CLASS_A, "  void f() throws Exception { new B().open(\"hunter2\"); }", CLOSE),
                src(B, CLASS_B, OPEN, CONN, END, CLOSE)));
    }

    @Test
    void twoHopChain() {
        assertEquals(List.of("C.java:3 hardcodedPassword"), check(
                src(A, CLASS_A, "  void f() throws Exception { new B().go(\"x9\"); }", CLOSE),
                src(B, CLASS_B, "  void go(String p) throws Exception { new C().open(p); }", CLOSE),
                src("C.java", "class C {", OPEN, CONN, END, CLOSE)));
    }

    @Test
    void castThroughObject() {
        assertEquals(List.of("B.java:4 hardcodedPassword"), check(
                src(A, CLASS_A, "  void f() throws Exception { new B().open((Object) \"x9\"); }", CLOSE),
                src(B, CLASS_B, "  void open(Object o) throws Exception {", "    String pw = (String) o;", CONN, END, CLOSE)));
    }

    @Test
    void requestValueOrEmptyIsNotConstant() {
        assertEquals(List.of(), check(
                src(A, CLASS_A, "  void f(@RequestParam String p) throws Exception { new B().open(p); new B().open(\"\"); }",
                        CLOSE),
                src(B, CLASS_B, OPEN, CONN, END, CLOSE)));
    }

    @Test
    void ambiguousClassIsSkipped() {
        TaintLink.counts(true);
        assertEquals(List.of(), check(
                src("x/Config.java", "package x;", "class Config {", "  static final String DB_PW = \"s3cret\";", CLOSE),
                src("y/Config.java", "package y;", "class Config {", "  static final String DB_PW = \"other\";", CLOSE),
                src("z/A.java", "package z;", CLASS_A, "  void f() throws Exception {",
                        "    java.sql.DriverManager.getConnection(u, s, Config.DB_PW);", END, CLOSE)));
        assertTrue(TaintLink.counts(true)[0] >= 1, "모호 수");
    }
}
