package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-34a — 코드 정리 파이프라인. {@code fixtures/cleanup} 을 고쳐 글 골든·단계 골든, 지킴 장치(주석·문자열·JSX·파일 사이 규칙·꺼진 규칙),
 * 되돌림 판정 단위.
 */
class CleanupTest {

    static final Path FIXTURES = Path.of("src/test/resources/fixtures/cleanup");

    static Profile profile(Map<String, Boolean> groups) {
        return new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null, null,
                new Profile.CodeCheck(groups, null, null), "egov35", null, null, null);
    }

    static Cleanup cleanup(Map<String, Boolean> groups) {
        return new Cleanup(RuleSet.load(profile(groups), null), Cleanup.RULES, "LF");
    }

    static Map<String, Object> row(Cleanup.Result r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("changed", r.changed());
        row.put("lineEnding", r.lineEnding());
        row.put("before", r.before().size());
        row.put("after", r.after().size());
        row.put("steps", r.steps());
        row.put("skips", r.skips());
        return row;
    }

    static long parseErrors(List<Finding> fs) {
        return fs.stream().filter(f -> f.rule().endsWith(".parseError")).count();
    }

    @Test
    void fixturesGolden(@TempDir Path data) throws Exception {
        Cleanup c = cleanup(null);
        Map<String, Object> steps = new TreeMap<>();
        Map<String, Cleanup.Result> by = new TreeMap<>();
        for (Source s : CheckRulesTest.fixtureSources(FIXTURES, data)) {
            if (s.rel().startsWith("order/")) {
                continue; // 5-34b
            }
            Cleanup.Result r = c.clean(s);
            by.put(s.rel(), r);
            GoldenFiles.assertText("cleanup/" + s.rel().replace('/', '-') + ".txt", r.text());
            steps.put(s.rel(), row(r));
            assertTrue(parseErrors(r.after()) <= parseErrors(r.before()), s.rel() + " 구문이 깨짐");
            assertTrue(r.after().size() <= r.before().size(), s.rel() + " 지적이 늘었다");
            assertEquals(r.changed(), !r.text().equals(s.text()) || !r.lineEnding().equals(s.lineEnding()), s.rel());
        }
        GoldenFiles.assertJson("cleanup/steps.json", steps);

        String jsp = by.get("debugger/pos.jsp").text();
        assertTrue(jsp.contains("var x = 1; debugger;") && jsp.contains("<%-- debugger --%>"), jsp);
        assertFalse(jsp.contains("    debugger;\n") || jsp.contains("debugger ;"), jsp);
        String ts = by.get("debugger/pos.ts").text();
        assertTrue(ts.contains("if (a) { debugger; }") && ts.contains("\ndebugger;\n`"), "한 줄 코드·템플릿 리터럴 안은 남는다 — " + ts);
        assertFalse(ts.contains("  debugger\n"), ts);

        String tsImport = by.get("tsximport/pos.ts").text();
        assertTrue(tsImport.startsWith("import A, { c as d } from 'x';\nimport {\n  f,\n  g\n} from 'z';\nimport './a.css';\n"), tsImport);

        Cleanup.Result eol = by.get("eol/Pos.java");
        assertEquals("LF", eol.lineEnding());
        assertTrue(eol.changed());

        assertFalse(by.get("guards/Broken.java").changed(), "구문을 못 읽는 Java 는 안 고친다");
        assertFalse(by.get("guards/Escape.java").changed(), "유니코드 이스케이프는 줄 매핑을 못 믿는다");
        assertTrue(by.get("guards/Escape.java").skips().stream().anyMatch(k -> k.reason().contains("유니코드")));
        assertTrue(by.get("guards/react.tsx").text().startsWith("import React from 'react';\nimport { Foo } from './foo';"), "JSX 팩터리·주석 이름");
        assertFalse(by.get("guards/regex.js").changed(), "정규식 리터럴 안 // 는 주석으로 못 믿는다");
        assertTrue(by.get("guards/polyfill.js").text().startsWith("import './polyfill';\n  import \"lib\" // 꼬리\nimport { used } from './used';"),
                "JS 는 이름을 다 지워도 모듈 실행은 남긴다 — " + by.get("guards/polyfill.js").text());
        assertFalse(by.get("guards/braceless.js").changed(), "중괄호 없는 if 몸체의 debugger 를 지우면 다음 문장이 몸체가 된다");
        assertFalse(by.get("guards/Block.java").changed(), "텍스트 블록 안 // 는 문자열 — 주석 판정을 못 믿는다");
        assertTrue(by.get("guards/Block.java").skips().stream().anyMatch(k -> k.reason().contains("텍스트 블록")), by.get("guards/Block.java").skips().toString());
        Cleanup.Result next = by.get("guards/next.ts");
        // 5-42 — console.log 는 이제 지우는 대상이라 픽스처를 남기는 console.warn 으로 바꿨다(가드 뜻은 같다)
        assertTrue(next.text().contains("  debugger;\n  console.warn(1);"), "debugger 를 지우면 쓸모없던 표식이 console 을 먹는다 — ① 은 되돌림");
        Cleanup.Result con = by.get("console/pos.ts");
        assertTrue(!con.text().contains("\"start\"") && !con.text().contains("\"multi\"") && !con.text().contains("paren in string"), con.text());
        assertTrue(con.text().contains("console.error(\"kept\", a);") && con.text().contains("console.log(\"in template\")")
                && con.text().contains("console.info(\"inline\")") && con.text().contains("console.log(\"braceless\")"), con.text());
        assertEquals(List.of("줄 9: 템플릿 리터럴 안 console", "줄 11: console 호출이 줄 처음에 있지 않다(다른 코드·주석)",
                "줄 13: 중괄호 없는 제어문 몸체일 수 있음"), con.skips().stream().map(Cleanup.Skip::reason).toList());
        assertTrue(next.skips().stream().anyMatch(k -> k.stage().equals(Cleanup.DELETE) && k.reason().startsWith("다른 지적이 사라짐")),
                next.skips().toString());

        String ig = by.get("ignore/Pos.java").text();
        assertTrue(ig.contains("    int x = 1;\n") && ig.contains("    String s = \"http://a\";\n"), ig);
        assertTrue(ig.contains("/* tb-ignore common.todo */ int y;") && ig.contains("// tb-ignore java.dupMapping")
                && ig.contains("// tb-ignore * 전부"), "앞 주석·파일 사이 규칙·전부 표식은 남는다 — " + ig);
        assertFalse(ig.contains("지난번 println") || ig.contains("java.emptyCatch"), ig);
        assertTrue(ig.contains("     * 설명\n"), "javadoc 가운데 줄은 표식만 뗀다 — " + ig);
    }

    /** 묶음을 끄면 그 묶음 표식은 쓸모없어 보인다 — 안 지운다 */
    @Test
    void disabledRuleMarkerStays(@TempDir Path data) throws Exception {
        Cleanup c = cleanup(Map.of("java", false));
        for (Source s : CheckRulesTest.fixtureSources(FIXTURES.resolve("ignore"), data)) {
            if (!s.rel().equals("Pos.java")) {
                continue;
            }
            Cleanup.Result r = c.clean(s);
            assertTrue(r.text().contains("// tb-ignore java.emptyCatch"), r.text());
            assertTrue(r.skips().stream().anyMatch(k -> k.reason().endsWith("꺼졌거나 없는 규칙의 표식")), r.skips().toString());
        }
    }

    static Finding f(int line, String rule, String excerpt) {
        return new Finding("a.ts", line, rule.substring(0, rule.indexOf('.')), rule, "warn", excerpt);
    }

    @Test
    void worseRevertsStage() {
        Set<String> t = Set.of("tsx.debugger");
        List<Finding> before = List.of(f(2, "tsx.debugger", "debugger;"), f(5, "tsx.console", "console.log(1);"));
        assertNull(Cleanup.worse(Cleanup.DELETE, t, before, List.of(f(4, "tsx.console", "console.log(1);")), Set.of(2), Set.of()),
                "같은 발췌가 줄만 밀렸다");
        assertEquals("구문이 깨짐", Cleanup.worse(Cleanup.DELETE, t, before,
                List.of(f(4, "tsx.console", "console.log(1);"), f(1, "java.parseError", "x")), Set.of(2), Set.of()));
        assertEquals("효과 없음", Cleanup.worse(Cleanup.DELETE, t, before, before, Set.of(2), Set.of()));
        assertEquals("다른 지적이 사라짐: tsx.console", Cleanup.worse(Cleanup.DELETE, t, before, List.of(), Set.of(2), Set.of()));
        assertEquals("다른 지적이 생김: tsx.any", Cleanup.worse(Cleanup.DELETE, t, before,
                List.of(f(4, "tsx.console", "console.log(1);"), f(6, "tsx.any", "x: any")), Set.of(2), Set.of()));
        assertNull(Cleanup.worse(Cleanup.DELETE, t, before,
                List.of(f(4, "tsx.console", "console.log(1);"), f(1, "common.unusedIgnore", "tb-ignore-next tsx.x")), Set.of(2), Set.of()),
                "① 뒤 쓸모없어진 표식은 ② 몫");
        List<Finding> withIndent = new ArrayList<>(before);
        withIndent.add(f(1, "file.mixedIndent", "탭 3줄 · 공백 4줄"));
        assertNull(Cleanup.worse(Cleanup.DELETE, t, withIndent,
                List.of(f(4, "tsx.console", "console.log(1);"), f(1, "file.mixedIndent", "탭 2줄 · 공백 4줄")), Set.of(2), Set.of()),
                "file.* 는 있음·없음만");
        assertEquals("다른 지적이 생김: file.header", Cleanup.worse(Cleanup.EOL, Set.of("file.lineEnding"),
                List.of(f(1, "file.lineEnding", "CRLF ≠ LF")), List.of(f(1, "file.header", "")), Set.of(), Set.of()));
        // ② — 지운 주석 줄 위의 지적은 사라져도 된다
        List<Finding> mb = List.of(f(3, "common.unusedIgnore", "tb-ignore common.todo"), f(3, "common.todo", "// TODO tb-ignore common.todo"));
        assertNull(Cleanup.worse(Cleanup.MARKER, Set.of("common.unusedIgnore"), mb, List.of(), Set.of(3), Set.of()));
        assertEquals("다른 지적이 사라짐: tsx.console", Cleanup.worse(Cleanup.MARKER, Set.of("common.unusedIgnore"),
                List.of(f(3, "common.unusedIgnore", "tb-ignore x.y"), f(3, "tsx.console", "console.log(1); // tb-ignore x.y")), List.of(), Set.of(3),
                Set.of()), "② 가 코드에 걸린 지적을 없애면 코드를 자른 것");
        assertEquals("다른 지적이 사라짐: common.todo", Cleanup.worse(Cleanup.DELETE, Set.of("common.unusedIgnore"), mb, List.of(), Set.of(3), Set.of()));
    }

    @Test
    void tsStatements() {
        List<JsImportRule.Stmt> st = JsImportRule.statements(new Source("a.ts",
                "import A, { b } from 'x';\nimport {\n  c\n} from \"y\" ; // 꼬리\nimport d from 'z'\nexport const q = A + d;\n", null, null, null));
        assertEquals(3, st.size());
        assertEquals(List.of(1, 1, 2, 4, 5, 5), List.of(st.get(0).startLine(), st.get(0).endLine(), st.get(1).startLine(), st.get(1).endLine(),
                st.get(2).startLine(), st.get(2).endLine()));
        assertEquals(List.of("b"), st.get(0).unused());
        assertEquals(List.of("c"), st.get(1).unused());
        assertEquals(List.of(), st.get(2).unused());
        String text = "import A, { b } from 'x';\nimport {\n  c\n} from \"y\" ; // 꼬리\n";
        assertEquals("} from \"y\" ;", text.substring(text.lastIndexOf("} from"), st.get(1).stmtEnd()));
        assertEquals("A, { b }", text.substring(st.get(0).clauseStart(), st.get(0).clauseEnd()));
    }

    @Test
    void rebuildClause() {
        assertEquals("A, { c as d }", Cleanup.rebuildClause("A, { b, c as d }", Set.of("b")));
        assertEquals("A", Cleanup.rebuildClause("A, { b }", Set.of("b")));
        assertEquals("{c,}", Cleanup.rebuildClause("{b, c, }", Set.of("b")), "끝 쉼표 꼴 유지");
        assertEquals("{ type T }", Cleanup.rebuildClause("{ type T, U }", Set.of("U")));
        assertEquals("* as ns", Cleanup.rebuildClause("A, * as ns", Set.of("A")));
    }
}
