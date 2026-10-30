package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;

/** 5-54b(설계 43 §3-2~8) — 메서드 사이·파일 사이 오염. security 묶음만 켠 검사에 글 여러 개를 넣고 지적의 파일:줄 규칙을 본다 */
class TaintLinkTest {

    static final String CLOSE = "}";
    static final String END = "  }";
    static final String CTL = "  void f(@RequestParam String p) {";
    static final String FILE_X = "    new java.io.File(x);";
    static final String A = "A.java";
    static final String B = "B.java";
    static final String CLASS_A = "class A {";
    static final String CLASS_B = "class B {";
    static final String CIPHER = "    javax.crypto.Cipher.getInstance(props.getProperty(\"alg\", \"AES/GCM/NoPadding\"));";

    static RuleSet rules() {
        Map<String, Boolean> groups = new HashMap<>(JavaRulesTest.ONLY_JAVA);
        groups.put("java", false);
        groups.put("metric", false);
        groups.put("security", true);
        Profile p = new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null, null,
                new Profile.CodeCheck(groups, null, null), "spring", null, null, null);
        return RuleSet.load(p, null);
    }

    static Source src(String rel, String... lines) {
        return new Source(rel, String.join("\n", lines) + "\n", null, null, null);
    }

    static List<String> labels(List<Finding> found) {
        return found.stream().filter(f -> Taint.knows(f.rule()))
                .map(f -> f.file() + ":" + f.line() + " " + f.rule().replace("security.", "")).sorted().toList();
    }

    /** 전부 검사(apply) 한 뒤 finish — 직접 지적 + 이은 지적 */
    static List<String> check(Source... files) {
        RuleSet.Run run = rules().run();
        List<Finding> out = new ArrayList<>();
        for (Source s : files) {
            out.addAll(run.apply(s));
        }
        out.addAll(run.finish());
        return labels(out);
    }

    @Test
    void privateHelperInSameClass() {
        assertEquals(List.of("A.java:6 pathTraversal"), check(src(A, CLASS_A, CTL, "    helper(p);", END,
                "  private void helper(String x) {", FILE_X, END, CLOSE)));
    }

    @Test
    void methodReturningSource() {
        assertEquals(List.of("A.java:4 pathTraversal"), check(src(A, CLASS_A,
                "  void f(javax.servlet.http.HttpServletRequest request) {", "    String d = src(request);", "    new java.io.File(d);", END,
                "  String src(javax.servlet.http.HttpServletRequest r) {", "    return r.getParameter(\"n\");", END, CLOSE)));
    }

    @Test
    void otherFile() {
        assertEquals(List.of("B.java:3 osCommand"), check(src(A, CLASS_A, CTL, "    new B().sink(p, 1);", END, CLOSE),
                src(B, CLASS_B, "  void sink(String v, int n) throws Exception {", "    Runtime.getRuntime().exec(v);", END,
                        CLOSE)));
    }

    @Test
    void chainOfThree() {
        assertEquals(List.of("C.java:3 pathTraversal"), check(src(A, CLASS_A, CTL, "    new B().pass(p);", END, CLOSE),
                src(B, CLASS_B, "  void pass(String v) {", "    new C().sink(\"/d/\" + v);", END, CLOSE),
                src("C.java", "class C {", "  void sink(String x) {", FILE_X, END, CLOSE)));
    }

    @Test
    void interfaceToImplementation() {
        assertEquals(List.of("FileServiceImpl.java:3 pathTraversal"), check(
                src("Ctl.java", "class Ctl {", "  FileService svc;", "  void f(@RequestParam String name) {", "    svc.download(name);", END,
                        CLOSE),
                src("FileService.java", "interface FileService {", "  void download(String n);", CLOSE),
                src("FileServiceImpl.java", "class FileServiceImpl implements FileService {", "  public void download(String n) {",
                        "    new java.io.File(\"/d/\" + n);", END, CLOSE)));
    }

    @Test
    void abstractBaseTakesCreatedType() {
        assertEquals(List.of("Bad.java:3 pathTraversal"), check(
                src(A, CLASS_A, CTL, "    Base b = new Bad();", "    b.action(p);", "    Base g = new Good();", "    g.action(\"x\");",
                        END, CLOSE),
                src("Base.java", "abstract class Base {", "  abstract void action(String d);", CLOSE),
                src("Bad.java", "class Bad extends Base {", "  void action(String x) {", FILE_X, END, CLOSE),
                src("Good.java", "class Good extends Base {", "  void action(String x) {", FILE_X, END, CLOSE)));
    }

    @Test
    void staticFieldAcrossFiles() {
        assertEquals(List.of("B.java:3 pathTraversal"), check(src("H.java", "class H {", "  static String data;", CLOSE),
                src(A, CLASS_A, "  void f(javax.servlet.http.HttpServletRequest r) {", "    H.data = r.getParameter(\"x\");", END, CLOSE),
                src(B, CLASS_B, "  void g() {", "    new java.io.File(H.data);", END, CLOSE)));
    }

    @Test
    void privateFieldBetweenMethods() {
        assertEquals(List.of("A.java:10 pathTraversal"), check(src(A, CLASS_A, "  private String dataBad;",
                "  void bad(javax.servlet.http.HttpServletRequest r) {", "    String data = r.getParameter(\"n\");", "    dataBad = data;",
                "    sink();", END, "  private void sink() {", "    String data = dataBad;", "    new java.io.File(data);", END, CLOSE)));
    }

    @Test
    void containerField() {
        assertEquals(List.of("A.java:8 pathTraversal"), check(src(A, CLASS_A, CTL, "    Box box = new Box();", "    box.v = p;",
                "    use(box);", END, "  void use(Box b) {", "    new java.io.File(b.v);", END, CLOSE)));
    }

    @Test
    void collectionsAndArrays() {
        assertEquals(List.of("A.java:10 pathTraversal", "A.java:12 pathTraversal"), check(src(A, CLASS_A, CTL,
                "    java.util.Vector<String> v = new java.util.Vector<>();", "    v.add(0, p);", "    use(v);",
                "    String[] a = new String[5];", "    a[2] = p;", "    arr(a);", "  } void use(java.util.Vector<String> v) { String d = v.remove(2);",
                "    new java.io.File(d);", "  } void arr(String[] a) {", "    new java.io.File(a[2]);", END, CLOSE)));
    }

    @Test
    void ambiguousNameIsSkipped() {
        TaintLink.counts(true);
        List<String> got = check(src("p1/Svc.java", "package p1;", "class Svc {", "  void run(String v) {", "    new java.io.File(v);", END,
                        CLOSE),
                src("p2/Svc.java", "package p2;", "class Svc {", "  void run(String v) {", "    new java.io.File(v);", END, CLOSE),
                src("p3/A.java", "package p3;", CLASS_A, "  Svc s;", CTL, "    s.run(p);", END, CLOSE));
        assertEquals(List.of(), got);
        assertTrue(TaintLink.counts(true)[0] >= 1, "모호 해석 수");
    }

    /** 마무리 리뷰 — 한 파일에 같은 단순 이름 중첩 클래스 둘은 안 겹치고, 못 가르니 건너뛴다 */
    @Test
    void sameNameNestedInOneFile() {
        TaintLink.counts(true);
        assertEquals(List.of(), check(src(A, CLASS_A, CTL, "    new In().go(p);", END, "  class In {", "    void go(String x) { }", END, CLOSE,
                CLASS_B, "  class In {", "    void go(String x) {", "      new java.io.File(x);", "    }", END, CLOSE)));
        assertTrue(TaintLink.counts(true)[0] >= 1, "모호 해석 수");
    }

    /** 마무리 리뷰 — HashMap put → 다른 메서드의 get(Juliet 74 꼴) */
    @Test
    void hashMapAcrossMethods() {
        assertEquals(List.of("A.java:8 pathTraversal"), check(src(A, CLASS_A, CTL,
                "    java.util.HashMap<Integer, String> m = new java.util.HashMap<>();", "    m.put(2, p);", "    use(m);", END,
                "  void use(java.util.HashMap<Integer, String> m) {", "    new java.io.File(m.get(2));", END, CLOSE)));
    }

    @Test
    void sameFileClassWins() {
        assertEquals(List.of("A.java:7 pathTraversal"), check(src(A, CLASS_A, CTL, "    new Test().go(p);", END, "  class Test {",
                "    void go(String x) {", "      new java.io.File(x);", "    }", END, CLOSE),
                src(B, CLASS_B, "  class Test {", "    void go(String x) {", "    }", END, CLOSE)));
    }

    @Test
    void calleeNeverWashes() {
        assertEquals(List.of("A.java:4 pathTraversal"), check(src(A, CLASS_A, CTL, "    String r = f(p);", "    new java.io.File(r);",
                END, "  String f(String x) {", "    return \"c\";", END, CLOSE)));
    }

    @Test
    void unchangedCallerStillFeeds() {
        RuleSet.Run run = rules().run();
        run.observe(src(A, CLASS_A, CTL, "    new B().sink(p);", END, CLOSE));
        List<Finding> out = new ArrayList<>(run.apply(src(B, CLASS_B, "  void sink(String x) {", FILE_X, END, CLOSE)));
        out.addAll(run.finish());
        assertEquals(List.of("B.java:3 pathTraversal"), labels(out));
    }

    @Test
    void directAndLinkedAreOne() {
        assertEquals(List.of("A.java:6 pathTraversal"), check(src(A, CLASS_A, CTL, "    helper(p);", END,
                "  void helper(@RequestParam String x) {", FILE_X, END, CLOSE)));
    }

    @Test
    void pastedTextLinksMethods() {
        CheckRunner.RunResult r = CheckRunner.runText(String.join("\n", "class C {",
                "  void a(javax.servlet.http.HttpServletRequest r) { b(r.getParameter(\"f\")); }",
                "  void b(String p) throws Exception { new java.io.FileInputStream(p); }", CLOSE), "java", rules());
        assertEquals(List.of("(붙여넣기):3 pathTraversal"), labels(r.findings()));
    }

    @Test
    void lambdaArgumentIsNotSite() {
        assertEquals(List.of(), check(src(A, CLASS_A, CTL, "    java.util.List.of(1).forEach(i -> helper(p));", END,
                "  void helper(String x) {", FILE_X, END, CLOSE)));
    }

    @Test
    void constantCallersAreClean() {
        assertEquals(List.of(), check(src(A, CLASS_A, CTL, "    helper(\"x\");", END, "  void helper(String x) {", FILE_X, END,
                CLOSE)));
    }

    @Test
    void seedBudget() {
        TaintLink.counts(true);
        String params = IntStream.range(0, 40).mapToObj(i -> "String a" + i).collect(Collectors.joining(", "));
        check(src(A, CLASS_A, "  void many(" + params + ") {", "    new java.io.File(a0);", END, CLOSE));
        assertEquals(40 - TaintLink.SEEDS_MAX, TaintLink.counts(true)[1]);
    }

    @Test
    void parameterNamesAcrossMethods() {
        assertEquals(List.of("A.java:8 pathTraversal"), check(src(A, CLASS_A,
                "  void doPost(javax.servlet.http.HttpServletRequest request) {",
                "    java.util.Enumeration<String> names = request.getParameterNames();", "    String p = names.nextElement();", "    go(p);",
                END, "  void go(String v) {", "    new java.io.File(v);", END, CLOSE)));
    }

    // ---------------------------------------------------------------- 5-55c(설계 44 §3-6·7) 설정 값

    static Source javaWith(String line) {
        return src(A, CLASS_A, "  void f(java.util.Properties props) throws Exception {", line, END, CLOSE);
    }

    @Test
    void propertiesReadAfterJavaStillDecide() {
        assertEquals(List.of("A.java:3 weakCipher"), check(javaWith(CIPHER), src("conf/app.properties", "alg=DES/ECB/PKCS5Padding")));
    }

    @Test
    void missingKeyFallsBackToDefault() {
        assertEquals(List.of("A.java:3 weakCipher"), check(javaWith(
                "    javax.crypto.Cipher.getInstance(props.getProperty(\"none\", \"DES/ECB/PKCS5Padding\"));")));
        assertEquals(List.of(), check(javaWith(CIPHER)));
    }

    @Test
    void unchangedPropertiesAreRead() {
        RuleSet.Run run = rules().run();
        run.observe(src("app.properties", "alg = DES/ECB/PKCS5Padding"));
        List<Finding> out = new ArrayList<>(run.apply(javaWith(CIPHER)));
        out.addAll(run.finish());
        assertEquals(List.of("A.java:3 weakCipher"), labels(out));
    }

    @Test
    void passwordDefaultIsJudgedAtOnce() {
        assertEquals(List.of("A.java:3 hardcodedPassword"), check(javaWith("    String password = props.getProperty(\"db.password\", \"s3cret\");"),
                src("app.properties", "db.password=")));
    }
}
