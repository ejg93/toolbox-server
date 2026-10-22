package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.JavaToken;
import com.github.javaparser.ast.CompilationUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-34b — 선언 순서 수정기. 고쳐지는 몸체·건너뛰는 몸체 일곱·ignoreModifiers 키·우리 코드 무변경·멱등.
 * 고친 글은 시험이 따로 다시 잰다 — 줄 수·줄 다중집합·토큰 다중집합 같음, 고친 파일의 남은 위반은 건너뛴 몸체 것만.
 */
class CleanupOrderTest {

    static final Path ORDER_FIXTURES = CleanupTest.FIXTURES.resolve("order");
    static final Path CHECK_JAVA = CheckRulesTest.FIXTURES.resolve("java");

    static Cleanup order(Map<String, Object> overrides) {
        Profile p = new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null, null,
                new Profile.CodeCheck(null, overrides, null), "egov35", null, null, null);
        return new Cleanup(RuleSet.load(p, null), Set.of(JavaRules.ORDER), "LF");
    }

    static List<Source> inputs(Path data) throws Exception {
        List<Source> out = new ArrayList<>();
        for (Source s : CheckRulesTest.fixtureSources(ORDER_FIXTURES, data)) {
            out.add(s);
        }
        for (Source s : CheckRulesTest.fixtureSources(CHECK_JAVA, data)) {
            if (s.rel().equals("PosOrder.java") || s.rel().equals("PosOrderEdge.java")) {
                out.add(new Source("check-" + s.rel(), s.text(), s.encoding(), s.lineEnding(), s.lang()));
            }
        }
        return out;
    }

    static Map<String, Integer> multiset(List<String> xs) {
        Map<String, Integer> m = new TreeMap<>();
        xs.forEach(x -> m.merge(x, 1, Integer::sum));
        return m;
    }

    static List<String> tokens(String text) {
        CompilationUnit cu = new JavaSource().parse(text).getResult().orElseThrow();
        List<String> out = new ArrayList<>();
        for (JavaToken t : cu.getTokenRange().orElseThrow()) {
            if (!t.getCategory().isWhitespaceOrComment()) {
                out.add(t.getKind() + ":" + t.getText());
            }
        }
        return out;
    }

    /** 시험이 따로 잰다 — 옮기기만 했나 */
    static void assertOnlyMoved(String rel, String before, String after) {
        List<String> lb = List.of(before.split("\n", -1));
        List<String> la = List.of(after.split("\n", -1));
        assertEquals(lb.size(), la.size(), rel + " 줄 수");
        assertEquals(multiset(lb), multiset(la), rel + " 줄 다중집합");
        assertEquals(multiset(tokens(before)), multiset(tokens(after)), rel + " 토큰 다중집합");
    }

    @Test
    void fixturesGolden(@TempDir Path data) throws Exception {
        Cleanup c = order(null);
        Map<String, Object> steps = new TreeMap<>();
        Map<String, Cleanup.Result> by = new TreeMap<>();
        for (Source s : inputs(data)) {
            Cleanup.Result r = c.clean(s);
            by.put(s.rel(), r);
            GoldenFiles.assertText("cleanup/order-" + s.rel() + ".txt", r.text());
            steps.put(s.rel(), CleanupTest.row(r));
            assertOnlyMoved(s.rel(), s.text(), r.text());
            assertFalse(r.skips().stream().anyMatch(k -> k.reason().equals(OrderFix.RECHECK_FAILED) || k.reason().equals("구문이 깨짐")),
                    s.rel() + " " + r.skips());
            if (r.changed()) {
                assertEquals(idempotent(c, s.rel(), r.text()), r.text(), s.rel() + " 멱등");
            }
        }
        GoldenFiles.assertJson("cleanup/order-steps.json", steps);
        for (String fixed : List.of("Pos.java", "PosEdge.java")) {
            assertTrue(by.get(fixed).changed(), fixed);
            assertEquals(0, by.get(fixed).after().stream().filter(f -> f.rule().equals(JavaRules.ORDER)).count(), fixed + " 위반 0");
        }
        String edge = by.get("PosEdge.java").text();
        assertTrue(edge.contains("    /** 빈 줄 너머 javadoc 은 선언을 따라간다 */\n\n    private int max;"), "javadoc 은 빈 줄째 따라간다 — " + edge);
        assertTrue(edge.contains("\n    // ---- 메서드\n    void m() {\n    } // end m\n"), "구획 줄은 제자리 — " + edge);
        String pos = by.get("Pos.java").text();
        assertTrue(pos.contains("    /** 상수 설명 */\n    @Deprecated\n    public static final int C = 1;"), pos);
        assertTrue(pos.contains("public int b; // 꼬리 주석은 제 선언과"), pos);
        assertTrue(by.get("Mixed.java").changed() && by.get("Mixed.java").text().contains("class MixedInit {\n    static {\n    }\n    private int a;"),
                "둘 중 하나만 — " + by.get("Mixed.java").text());
    }

    private static String idempotent(Cleanup c, String rel, String text) {
        return c.clean(new Source(rel, text, "UTF-8", "LF", "java")).text();
    }

    /** 건너뜀 일곱 — 파일마다 제 사유로만 */
    @Test
    void skipsAreNamed(@TempDir Path data) throws Exception {
        Map<String, String> want = new LinkedHashMap<>();
        want.put("SkipInit.java", OrderFix.SKIP_INIT);
        want.put("SkipSameLine.java", OrderFix.SKIP_SAME_LINE);
        want.put("SkipStraddle.java", OrderFix.SKIP_STRADDLE);
        want.put("SkipRead.java", OrderFix.SKIP_READ);
        want.put("SkipImpure.java", OrderFix.SKIP_IMPURE);
        want.put("SkipLombok.java", OrderFix.SKIP_LOMBOK);
        want.put("SkipSuppressed.java", OrderFix.SKIP_SUPPRESSED);
        want.put("SkipSameOrg.java", OrderFix.SKIP_IMPURE); // 같은 조직 타입 호출 — opaque
        Cleanup c = order(null);
        for (Source s : CheckRulesTest.fixtureSources(ORDER_FIXTURES, data)) {
            String reason = want.get(s.rel());
            if (reason == null) {
                continue;
            }
            Cleanup.Result r = c.clean(s);
            assertFalse(r.changed(), s.rel());
            List<String> got = r.skips().stream().map(Cleanup.Skip::reason).map(x -> x.substring(x.indexOf(": ") + 2)).distinct().toList();
            assertEquals(List.of(reason), got, s.rel() + " " + r.skips());
        }
    }

    @Test
    void ignoreModifiersKey(@TempDir Path data) throws Exception {
        Cleanup c = order(Map.of(JavaRules.ORDER, Map.of("params", Map.of("ignoreModifiers", true))));
        for (Source s : CheckRulesTest.fixtureSources(ORDER_FIXTURES, data)) {
            if (s.rel().equals("IgnoreMods.java")) {
                Cleanup.Result r = c.clean(s);
                GoldenFiles.assertText("cleanup/order-IgnoreMods-ignore.txt", r.text());
                assertTrue(r.text().contains("    public int x;\n    private static int y;\n    IgnoreMods() {"), r.text());
            }
        }
    }

    /** 마무리 AI 리뷰 — 확인이 틀리면 -1(③ 전체 되돌림). 줄 수·토큰·몸체 못 찾음 각각 */
    @Test
    void verifyRejects() {
        JavaSource parser = new JavaSource();
        String before = "class A {\n    private int a;\n    public int b;\n}\n";
        String good = "class A {\n    public int b;\n    private int a;\n}\n";
        String key = OrderFix.bodies(parser.parse(before).getResult().orElseThrow()).get(0).key();
        assertEquals(0, OrderFix.verify(before, good, key, true, parser), "옮기기만 — 통과, 남은 위반 0");
        assertEquals(-1, OrderFix.verify(before, "class A {\n    public int b;\n    private int a;\n\n}\n", key, true, parser), "줄 수");
        assertEquals(-1, OrderFix.verify(before, "class A {\n    public int b;\n    private int c;\n}\n", key, true, parser), "줄·토큰이 바뀜");
        assertEquals(-1, OrderFix.verify(before, "class A {\n    private int a;\n    public int b;\n}\n", key, true, parser), "몸체 위반이 남음");
        assertEquals(-1, OrderFix.verify(before, good, "ClassOrInterfaceDeclaration@9:9", true, parser), "고친 몸체를 못 찾음");
    }

    /** 우리 코드(0-61e 가 위반 0 을 보증)는 그대로 */
    @Test
    void ownSourcesUnchanged() throws Exception {
        Rule.Def def = RuleSet.builtin().defs().stream().filter(d -> d.id().equals(JavaRules.ORDER)).findFirst().orElseThrow();
        JavaSource parser = new JavaSource();
        List<String> changed = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"))) {
            try (Stream<Path> s = Files.walk(root)) {
                for (Path p : s.filter(x -> x.toString().endsWith(".java")).sorted().toList()) {
                    String text = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
                    List<Cleanup.Skip> skips = new ArrayList<>();
                    if (!OrderFix.apply(text, true, def, parser, p.toString(), skips).equals(text)) {
                        changed.add(root.relativize(p).toString());
                    }
                }
            }
        }
        assertEquals(List.of(), changed);
    }
}
