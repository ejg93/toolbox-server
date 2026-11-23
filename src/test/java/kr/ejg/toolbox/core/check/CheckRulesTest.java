package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-1 — 내장 정규식·파일 규칙을 {@code fixtures/check/<묶음>/} 양성(pos*)·음성(neg*)에 돌려 파일·줄·규칙을 골든으로 든다.
 * 음성은 어느 규칙에도 안 걸려야 하고, 켜진 규칙은 양성에서 한 번 이상 걸려야 한다.
 */
class CheckRulesTest {

    static final Path FIXTURES = Path.of("src/test/resources/fixtures/check");

    static Profile profile(Profile.CodeCheck cc) {
        return new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null,
                new Profile.Naming(null, null, null, null, null, true), cc, "egov35", null, null, null);
    }

    static List<Source> fixtureSources(Path dir, Path data) throws Exception {
        LocalFiles files = new LocalFiles(data);
        List<Source> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                LocalFiles.Text t = files.read(p.toAbsolutePath().toString());
                out.add(new Source(dir.relativize(p).toString().replace('\\', '/'), t.text(), t.encoding(), t.lineEnding(), null));
            }
        }
        return out;
    }

    /** 5-22 — 주석만 든 스크립틀릿은 공백(줄 수 그대로), 코드가 든 블록은 그대로 */
    @Test
    void commentOnlyScriptletsAreBlanked() {
        String head = "<%\n /**\n  * @Class Name : X.jsp\n  */\n%>\n<%@ page x %>\n<% // 한 줄 주석\n%>";
        String out = RegexRule.blankCommentScriptlets(head);
        assertEquals(head.split("\n", -1).length, out.split("\n", -1).length, "줄 수");
        assertTrue(!out.contains("<%\n") && !out.contains("Class Name") && out.contains("<%@ page x %>"), out);
        String code = "<% /* c */ int a = 1; %>";
        assertEquals(code, RegexRule.blankCommentScriptlets(code));
    }

    /**
     * 5-22 — eGov JSP 머리처럼 주석만 든 스크립틀릿은 jsp.scriptlets 가 안 센다. 순수본 f48c4d6 도 안 세서(0-58) 공용 픽스처
     * neg.jsp 에도 한 줄 있다 — PureCodeCheckTest 가 순수본과 같은 픽스처로 맞댄다
     */
    @Test
    void commentOnlyScriptletIsNotCounted() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(Map.of("java", false, "mybatis", false), null, null)), null);
        String head = "<%\n /**\n  * @Class Name : X.jsp\n  */\n%>\n<%@ page contentType=\"text/html; charset=UTF-8\" %>\n<p>x</p>\n";
        assertTrue(rules.apply(new Source("x.jsp", head, null, null, null)).stream().noneMatch(f -> f.rule().equals("jsp.scriptlets")));
        List<Finding> code = rules.apply(new Source("y.jsp", head + "<% int a = 1; %>\n", null, null, null));
        assertEquals(List.of(8), code.stream().filter(f -> f.rule().equals("jsp.scriptlets")).map(Finding::line).toList(),
                "코드가 든 블록은 센다 — 줄은 그 블록");
    }

    @Test
    void fixturesGolden(@TempDir Path data) throws Exception {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(Map.of("java", false, "mybatis", false), null, null)), null);
        List<Map<String, Object>> golden = new ArrayList<>();
        Set<String> hitRules = new TreeSet<>();
        List<String> negativeHits = new ArrayList<>();
        for (Source s : fixtureSources(FIXTURES, data)) {
            if (s.rel().startsWith("java/") || s.rel().startsWith("mybatis/") || s.rel().startsWith("ext/")) {
                continue; // 5-2·5-3 테스트가 따로 잰다
            }
            RuleSet.Run run = rules.run(); // 5-29b — 쓸모없는 표식(common.unusedIgnore)은 finish 가 낸다
            List<Finding> found = new ArrayList<>(run.apply(s));
            found.addAll(run.finish());
            found.sort(java.util.Comparator.comparingInt(Finding::line).thenComparing(Finding::rule));
            for (Finding f : found) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("file", f.file());
                row.put("line", f.line());
                row.put("rule", f.rule());
                golden.add(row);
                assertTrue(f.excerpt().length() <= Finding.EXCERPT, f.toString());
                if (s.fileName().startsWith("neg")) {
                    negativeHits.add(f.file() + ":" + f.line() + " " + f.rule());
                } else {
                    hitRules.add(f.rule());
                }
            }
        }
        assertEquals(List.of(), negativeHits, "음성 픽스처 히트");
        Set<String> all = rules.enabled().stream().map(r -> r.def().id()).collect(Collectors.toCollection(TreeSet::new));
        all.removeAll(hitRules);
        assertEquals(Set.of(), all, "양성 픽스처에서 한 번도 안 걸린 규칙");
        GoldenFiles.assertJson("check/fixtures.json", golden);
    }

    /** 5-36 — 규칙 지문: 같은 설정이면 같고, 기준값 덮어쓰기·묶음 끔이면 달라진다. 12 hex */
    @Test
    void fingerprintFollowsEffectiveRules() {
        String base = RuleSet.load(profile(new Profile.CodeCheck(null, null, null)), null).fingerprint();
        assertTrue(base.matches("[0-9a-f]{12}"), base);
        assertEquals(base, RuleSet.load(profile(new Profile.CodeCheck(null, null, null)), null).fingerprint());
        assertNotEquals(base, RuleSet.load(profile(new Profile.CodeCheck(null,
                Map.of("metric.complexity", Map.of("params", Map.of("max", 10))), null)), null).fingerprint(), "기준값");
        assertNotEquals(base, RuleSet.load(profile(new Profile.CodeCheck(Map.of("metric", false), null, null)), null).fingerprint(), "묶음 끔");
        assertNotEquals(base, RuleSet.load(profile(new Profile.CodeCheck(null, Map.of("common.todo", Map.of("severity", "error")), null)), null)
                .fingerprint(), "등급");
    }

    @Test
    void groupsAndOverrides() {
        Map<String, Object> over = new LinkedHashMap<>();
        over.put("common.todo", false);
        over.put("common.sysout", Map.of("severity", "error", "regex", "\\bSystem\\.out\\b"));
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(Map.of("jsp", false, "file", false, "java", false), over, null)), null);
        Map<String, Rule.Def> byId = rules.defs().stream().collect(Collectors.toMap(Rule.Def::id, d -> d));
        assertFalse(byId.get("jsp.expression").on(), "묶음 끔");
        assertFalse(byId.get("common.todo").on(), "규칙 끔");
        assertTrue(byId.get("security.sqlDollar").on(), "프로필에 없는 묶음은 켠다");
        assertEquals("error", byId.get("common.sysout").severity());
        List<Finding> f = rules.apply(new Source("a/A.java", "// TODO\nSystem.out.flush();\n", null, null, null));
        assertEquals(List.of("2 common.sysout error"), f.stream().map(x -> x.line() + " " + x.rule() + " " + x.severity()).toList());
    }

    @Test
    void customRulesFileAddsAndReplaces(@TempDir Path base) throws Exception {
        Files.writeString(base.resolve("my.yaml"), """
                rules:
                  - id: custom.vo
                    group: common
                    severity: warn
                    globs: ['*.java']
                    regex: 'HashMap<String, *Object>'
                    message: VO 대신 Map
                  - id: common.todo
                    group: common
                    severity: warn
                    globs: ['*.java']
                    regex: '\\bHACK\\b'
                    message: HACK
                """, StandardCharsets.UTF_8);
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(Map.of("file", false, "java", false), null, "my.yaml")), base);
        List<Finding> f = rules.apply(new Source("A.java", "// TODO\n// HACK\nMap m = new HashMap<String, Object>();\n", null, null, null));
        assertEquals(List.of("2 common.todo", "3 custom.vo"), f.stream().map(x -> x.line() + " " + x.rule()).toList());
        assertEquals(rules.defs().size() - 1, RuleSet.load(profile(new Profile.CodeCheck(null, null, "none.yaml")), base).defs().size(), "없는 파일은 무시");
    }

    /** 5-25b — 보안 묶음은 규칙마다 권장 수정 한 줄(60자 안) · 예시 사업 규칙 파일은 읽히고 꺼져 있다. 근거(ref)는 사람이 채우니 단언하지 않는다 */
    @Test
    void builtinRulesCarryFixAndExampleCustomFileLoadsOff() {
        // 5-28 — 내장 규칙 전부 권장 수정 한 줄. 비운 것은 고칠 코드가 없는 「못 읽었다」 알림 둘뿐
        List<Rule.Def> builtin = RuleSet.load(profile(null), null).defs();
        assertEquals(147, builtin.size()); // 설계 65 printStackTraceStream·fallThrough·piiCardPlain · 설계 64 문장 꼴 다섯 · 설계 61 jsp.blankNoopener · 설계 60 unusedLocal·defaultCharset · 설계 58 넷 · 설계 50 셋 · 설계 49 Java 결함 열하나 · 설계 48 파서·TLS 둘 · 5-57c 매퍼 SQL 다섯 · 5-38 ㅅ 외부 분석기 둘 · 5-40 React 다섯·JSX 접근성 다섯 · 5-45 단언 없는 시험 · 설계 42 오염 싱크 여섯
        assertEquals(46, builtin.stream().filter(d -> d.group().equals("security")).count()); // 설계 65 piiCardPlain · 설계 50 resourceLeak · 설계 42 — 오염 싱크 여섯 · 설계 48 xxe·trustAll
        java.util.Set<String> noFix = java.util.Set.of("java.parseError", "mybatis.parseError");
        for (Rule.Def d : builtin) {
            if (noFix.contains(d.id())) {
                assertTrue(d.fix() == null || d.fix().isBlank(), d.id() + " 은 허용 목록에서 빼고 시험한다");
                continue;
            }
            assertTrue(d.fix() != null && !d.fix().isBlank() && d.fix().length() <= 60, d.id() + " fix: " + d.fix());
        }
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(null, null, "rules/example.yaml")), Path.of(""));
        Rule.Def ex = rules.defs().stream().filter(d -> d.id().equals("common.systemExit")).findFirst().orElseThrow();
        assertTrue(!ex.on(), "예시 규칙은 꺼 둔다");
        assertEquals("예외를 던지거나 오류 응답으로 끝낸다", ex.fix());
    }

    /** 5-29 — tb-ignore 표식: 같은 줄·다음 줄·전부·여럿·모르는 토큰·파일 사이 규칙·붙여넣기 */
    /** 5-119 — SQL 주석 안 ${} 도 security.sqlDollar 다(MyBatis 가 치환한다). XML 주석만 뺀다 */
    @Test
    void sqlDollarSeesSqlComments() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(null, null, null)), null);
        String xml = "<?xml version=\"1.0\"?>\n<mapper namespace=\"a\">\n<select id=\"s\">\nSELECT id FROM t\n-- AND a = ${x}\n/* ${y} */\n"
                + "ORDER BY ${sort}\n<!-- ${z} -->\nUNION SELECT /*+ INDEX(t ${ix}) */ 1 FROM t\n</select>\n</mapper>\n";
        assertEquals(List.of(5, 6, 7, 9), CheckRunner.runText(xml, "xml", rules).findings().stream().filter(f -> f.rule().equals("security.sqlDollar"))
                .map(Finding::line).toList());
    }

    /** D-textBlock — 텍스트 블록 안 /* 는 주석을 안 연다(뒤 코드의 지적이 산다, 백엔드만) */
    @Test
    void textBlockDoesNotOpenAComment() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(null, null, null)), null);
        String java = "class A {\n String s = \"\"\"\n   /* 안 닫는 글\n   \"\"\";\n void m() { System.out.println(1); }\n}\n";
        assertEquals(List.of(5), CheckRunner.runText(java, "java", rules).findings().stream().filter(f -> f.rule().equals("common.sysout"))
                .map(Finding::line).toList());
        assertEquals(java.split("\n", -1).length, RegexRule.stripComments(java, "java").split("\n", -1).length);
    }

    /** D-jspScriptComment — jsp·html 의 script 안 주석 처리한 줄은 지적이 아니다(백엔드만) */
    @Test
    void commentedScriptLinesAreNotFlagged() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(null, null, null)), null);
        String jsp = "<html><body><script>\n// alert(1)\n/* console.log(x) */\n// debugger\nalert(2);\n</script></body></html>";
        List<String> hits = CheckRunner.runText(jsp, "jsp", rules).findings().stream()
                .filter(f -> List.of("common.alert", "common.consoleLog", "common.debugger").contains(f.rule())).map(f -> f.line() + " " + f.rule()).toList();
        assertEquals(List.of("5 common.alert"), hits);
    }

    /** 5-128(D-01) — <script> 몸을 <!-- … //--> 로 감싼 옛 꼴도 스크립트 안 지적이 나온다(뺀 쪽 + 1줄). 주석 처리한 <script> 는 0 */
    @Test
    void wrappedScriptIsStillChecked() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(null, null, null)), null);
        List<String> pick = List.of("common.alert", "common.consoleLog", "common.debugger");
        String wrapped = "<%@ page contentType=\"text/html; charset=UTF-8\" %>\n<script type=\"text/javascript\">\n<!--\nfunction go() {\n"
                + "  alert('x');\n  console.log('y');\n  debugger;\n}\n//-->\n</script>\n";
        String bare = wrapped.replace("<!--\n", "").replace("//-->\n", "");
        for (String lang : List.of("jsp", "html")) {
            List<String> w = CheckRunner.runText(wrapped, lang, rules).findings().stream().filter(f -> pick.contains(f.rule()))
                    .map(f -> f.line() + " " + f.rule()).toList();
            List<String> b = CheckRunner.runText(bare, lang, rules).findings().stream().filter(f -> pick.contains(f.rule()))
                    .map(f -> (f.line() + 1) + " " + f.rule()).toList();
            assertEquals(List.of("5 common.alert", "6 common.consoleLog", "7 common.debugger"), w, lang);
            assertEquals(b, w, lang);
        }
        String commented = "<html><body>\n<!-- <script>alert(1)</script> -->\n</body></html>\n";
        assertEquals(List.of(), CheckRunner.runText(commented, "jsp", rules).findings().stream().filter(f -> pick.contains(f.rule())).toList());
    }

    /** 5-146b(D-1a·1b·1c·D4-09) — 비 JS type · 자기 닫는 태그 · 주석·스크립틀릿 안 여는 태그는 script 몸을 안 연다 · 따옴표 값 안 > */
    @Test
    void scriptBodiesSkipNonJsAndCommentedTags() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(null, null, null)), null);
        Set<String> pick = Set.of("common.alert", "security.jsEval", "common.consoleLog", "tsx.console");
        java.util.function.Function<String, List<String>> hits = text -> CheckRunner.runText(text, "jsp", rules).findings().stream()
                .filter(f -> pick.contains(f.rule())).map(f -> f.line() + " " + f.rule()).toList();
        String dead = "<!-- old\n  alert(1); eval(x);\n-->\n";
        List<String> none = List.of("<script type=\"text/template\" id=\"tpl\">\n<!-- row\n  alert(1); eval(x);\n-->\n<div>a</div>\n</script>\n",
                "<script src=\"a.js\"/>\n" + dead + "<script>var a = 1;</script>\n",
                "<%-- <script src=\"old.js\"> --%>\n" + dead + "<p>x</p>\n<script>\nvar a = 1;\n</script>\n",
                "<!-- <script src=\"old.js\"> -->\n" + dead + "<p>x</p>\n<script>\nvar a = 1;\n</script>\n",
                "<% out.println(\"<script>\"); %>\n" + dead + "<p>x</p>\n<script>\nvar a = 1;\n</script>\n");
        for (String t : none) {
            assertEquals(List.of(), hits.apply(t), t);
        }
        assertEquals(List.of("3 security.jsEval"), hits.apply("<script src=\"<c:url value='/js/a.js'/>\"></script>\n<script>\neval(x);\n</script>\n"));
        for (String type : List.of(" type=\"text/javascript\"", " type=\"module\"", "")) {
            String w = "<script" + type + ">\n<!--\nfunction go() {\n  alert('x');\n}\n//-->\n</script>\n";
            assertEquals(List.of("4 common.alert"), hits.apply(w), type);
        }
        StringBuilder many = new StringBuilder("<script>\n");
        many.append("<!--\n".repeat(200_000)).append("</script>\n");
        String big = many.toString();
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(1), () -> RegexRule.scriptWrappers(big));
    }

    /** 5-150(D4-03) — script 안 주석뿐인 스크립틀릿은 jsp.scriptlets 에 안 센다(script 밖과 같다) · 코드가 든 것은 센다 */
    @Test
    void commentOnlyScriptletsInsideScriptAreNotCounted() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(null, null, null)), null);
        java.util.function.Function<String, List<String>> hits = text -> CheckRunner.runText(text, "jsp", rules).findings().stream()
                .filter(f -> f.rule().equals("jsp.scriptlets")).map(f -> f.line() + " " + f.rule()).toList();
        assertEquals(List.of(), hits.apply("<script>\n<% //if (admin) { %>\n  show();\n<% //} %>\n</script>\n"));
        assertEquals(List.of(), hits.apply("<% //if (admin) { %>\n  show();\n<% //} %>\n"), "script 밖");
        assertEquals(1, hits.apply("<script>\n<% if (admin) { %>\n  show();\n<% } %>\n</script>\n").size(), "코드가 든 스크립틀릿");
    }

    /** 5-146c — 감싼 script 안 카드번호 · -- 없이 끝난 감싸개 뒤 마크업도 Luhn·BlankRel 이 본다(검사와 같은 글) */
    @Test
    void wrappedScriptFeedsLuhnAndBlankRel() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(null, null, null)), null);
        Set<String> pick = Set.of("common.alert", "security.piiCardPlain", "jsp.blankNoopener");
        java.util.function.Function<String, List<String>> hits = text -> CheckRunner.runText(text, "jsp", rules).findings().stream()
                .filter(f -> pick.contains(f.rule())).map(f -> f.line() + " " + f.rule()).sorted().toList();
        assertEquals(List.of("3 security.piiCardPlain", "4 common.alert"),
                hits.apply("<script>\n<!--\nvar c = 4111111111111111;\nalert(1);\n//-->\n</script>\n"));
        assertEquals(List.of("3 common.alert", "5 jsp.blankNoopener", "6 security.piiCardPlain"),
                hits.apply("<script>\n<!--\nalert(1);\n</script>\n<a href=\"x.do\" target=\"_blank\">x</a>\n<p>4111111111111111</p>\n"));
        assertEquals(List.of(), hits.apply("<!--\n<script>\nvar c = 4111111111111111;\n</script>\n-->\n"), "주석 처리한 script 블록");
    }

    /** 5-146a(D4-01) — 끊긴 따옴표 줄 자르기는 그 줄 안 · 문자열 밖 첫 // 부터: 주석 뗀 글의 줄 수·길이가 같고 URL 뒤 코드가 남는다 */
    @Test
    void brokenQuoteKeepsLineCountAndCode() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(null, null, null)), null);
        Set<String> pick = Set.of("common.consoleLog", "tsx.console", "security.jsEval", "common.alert");
        java.util.function.BiFunction<String, String, List<String>> hits = (text, lang) -> CheckRunner.runText(text, lang, rules).findings().stream()
                .filter(f -> pick.contains(f.rule())).map(f -> f.line() + " " + f.rule()).toList();
        String two = "var q = s.replace(/'/g, \"\"); var h = \"<a href=\\\"http://a.b\\\">\\\nx</a>\";\nfoo();\nconsole.log(1);\nconsole.log(2);\nbar();\n";
        assertEquals(List.of("4", "5"), hits.apply(two, "js").stream().map(h -> h.split(" ")[0]).toList(), hits.apply(two, "js").toString());
        assertEquals(List.of("4", "5"), hits.apply(two, "tsx").stream().map(h -> h.split(" ")[0]).toList(), hits.apply(two, "tsx").toString());
        for (String lang : List.of("js", "ts", "tsx", "jsx", "java", "jsp", "html", "css")) {
            for (String text : List.of("\"//\\\n\n", two)) {
                String b = RegexRule.bare(text, lang);
                String[] tl = text.split("\n", -1);
                String[] bl = b.split("\n", -1);
                assertEquals(tl.length, bl.length, lang + " 줄 수");
                for (int i = 0; i < tl.length; i++) {
                    assertEquals(tl[i].length(), bl[i].length(), lang + " 줄 " + (i + 1) + " 길이");
                }
            }
        }
        assertEquals(List.of("1 security.jsEval", "2 common.alert"), hits.apply("var re = /'/; var u = \"http://a.b/c\"; eval(x);\nalert(1);\n", "js"));
        assertEquals(List.of("1 security.jsEval"), hits.apply("const a = <p>Don't</p>; const u = \"http://a.b\"; eval(x);\n", "tsx"));
        assertEquals(List.of(), hits.apply("var re = /'/; // eval(x)\n", "js"), "끊긴 따옴표 뒤 진짜 주석");
        String slow = "'" + "\\'".repeat(30_000) + " // x\n";
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(2), () -> RegexRule.bare(slow, "js"));
    }

    /** 5-130(D-02·D-12·D-13·D-14) — 손으로 고친 규칙 정의의 오류는 전부 BadRule(화면 400 + 사유) · params.max 공백은 받는다 */
    @Test
    void ruleDefinitionErrorsAreBadRule(@TempDir Path tmp) throws Exception {
        java.util.function.Function<Object, Profile> over = v -> profile(new Profile.CodeCheck(null, Map.of("common.alert", v), null));
        Map<Object, String> bad = new java.util.LinkedHashMap<>();
        bad.put(Map.of("flags", List.of("x")), "common.alert");
        bad.put(Map.of("params", Map.of("when", "(")), "common.alert");
        bad.put(Map.of("enable", false), "common.alert");
        bad.put(Map.of("regex", List.of("a", "b")), "common.alert");
        for (Map.Entry<Object, String> e : bad.entrySet()) {
            RuleSet.BadRule x = assertThrows(RuleSet.BadRule.class, () -> RuleSet.load(over.apply(e.getKey()), null), e.getKey().toString());
            assertTrue(x.getMessage().contains(e.getValue()), x.getMessage());
        }
        Files.writeString(tmp.resolve("bad.yaml"), "- a\n- b\n");
        RuleSet.BadRule cr = assertThrows(RuleSet.BadRule.class,
                () -> RuleSet.load(profile(new Profile.CodeCheck(null, null, "bad.yaml")), tmp));
        assertTrue(cr.getMessage().contains("customRules"), cr.getMessage());
        Profile naming = new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null,
                new Profile.Naming("^[A-Z", null, null, null, null, true), new Profile.CodeCheck(null, null, null), "egov35", null, null, null);
        RuleSet.BadRule nm = assertThrows(RuleSet.BadRule.class, () -> RuleSet.load(naming, null));
        assertTrue(nm.getMessage().contains("naming.controller"), nm.getMessage());
        RuleSet spaced = RuleSet.load(profile(new Profile.CodeCheck(null, Map.of("jsp.scriptlets", Map.of("params", Map.of("max", "5 "))), null)), null);
        assertDoesNotThrow(() -> CheckRunner.runText("<% int a = 1; %>\n<% int b = 2; %>\n", "jsp", spaced));
    }

    /** 5-128(D-19) — 닫는 태그 없는 <script> 가 되풀이돼도 선형 */
    @Test
    void unclosedScriptsAreLinear() {
        String many = "<script>\n".repeat(20_000);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> RegexRule.scriptComments(many));
    }

    /** 5-128(D-17) — 텍스트 블록이 \\ 로 끝나도 닫는다(닫는 따옴표 앞 역슬래시가 짝수) */
    @Test
    void textBlockEndingWithBackslashes() {
        String java = "class A {\n    String s = \"\"\"\n        a\\\\\"\"\";\n    int x;\n    // comment line\n}\n";
        String out = RegexRule.stripComments(java, "java");
        String[] lines = out.split("\n", -1);
        assertEquals(java.split("\n", -1).length, lines.length);
        assertEquals("", lines[4].strip(), "5행 주석이 빈칸");
    }

    @Test
    void ignoreMarkers() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(Map.of("java", true, "mybatis", false), null, null)), null);
        java.util.function.Function<String, List<String>> hits = text -> rules.apply(new Source("a/A.java", text, null, null, null)).stream()
                .filter(f -> f.rule().startsWith("common.")).map(f -> f.line() + " " + f.rule()).toList();
        assertEquals(List.of(), hits.apply("// 수정일\nclass A { void f() { System.out.println(1); // tb-ignore common.sysout 이유\n} }\n"),
                "같은 줄 규칙 지정");
        // D-ignoreInString — 문자열 안 tb-ignore 는 표식이 아니다(C 꼴 언어)
        assertEquals(List.of("1 common.sysout"), hits.apply("System.out.println(\"// tb-ignore\");\n"), "문자열 안 // tb-ignore");
        assertEquals(List.of(), hits.apply("System.out.println(1); // tb-ignore common.sysout 사유\n"), "주석 안 표식은 그대로");
        assertEquals(List.of("1 common.sysout"), hits.apply("String s = \"-- tb-ignore\"; System.out.println(s);\n"), "문자열 안 -- tb-ignore");
        assertEquals(List.of("3 common.sysout"), hits.apply("// tb-ignore-next\nSystem.out.println(1);\nSystem.out.println(1);\n"),
                "다음 줄만");
        // 5-44b — 표식은 주석의 첫 낱말. 한 줄에 TODO 와 표식을 같이 두려면 주석을 나눈다
        assertEquals(List.of(), hits.apply("System.out.println(1); /* TODO */ // tb-ignore *\n"), "전부");
        assertEquals(List.of(), hits.apply("System.out.println(1); /* TODO */ // tb-ignore common.todo,common.sysout\n"), "여럿");
        assertEquals(List.of("1 common.sysout"), hits.apply("System.out.println(1); /* TODO */ // tb-ignore common.todo\n"), "지정한 것만");
        assertEquals(List.of("1 common.sysout", "1 common.todo"), hits.apply("System.out.println(1); // TODO  tb-ignore *\n"),
                "주석 가운데 낱말은 표식이 아니다(5-44b)");
        assertEquals(List.of("1 common.sysout"), hits.apply("System.out.println(\"tb-ignore *\");\n"), "문자열 속 낱말은 표식이 아니다(5-44b)");
        assertEquals(List.of("3 common.sysout"), hits.apply("/**\n * 쓰는 법 tb-ignore *\n */ System.out.println(1);\n"),
                "javadoc 설명 속 낱말도 아니다(5-44b)");
        assertEquals(List.of(), hits.apply("/**\n * tb-ignore-next common.sysout 설명\n */ System.out.println(1);\n"),
                "javadoc 연속 줄 * 바로 뒤면 표식");
        assertEquals(List.of("1 common.sysout"), hits.apply("System.out.println(1); // tb-ignore commn.sysout\n"), "모르는 토큰은 아무것도 안 뺀다");
        assertEquals(List.of("1 common.sysout"), hits.apply("System.out.println(1); // tb-ignored\n"), "토큰 경계");
        assertEquals(List.of("1 common.sysout"), hits.apply("System.out.println(1); // tb-ignore-file\n"), "-file 같은 꼴은 표식이 아니다(마무리 리뷰)");
        assertEquals(List.of("2 common.sysout"), hits.apply("// tb-ignore-nxt\nSystem.out.println(1);\n"), "-next 오타는 다음 줄에 안 먹는다");
        assertEquals(List.of(), hits.apply("System.out.println(1); /* TODO */ // tb-ignore common.todo,,common.sysout,\n"), "빈 id 조각은 버린다");

        String ctl = "package a;\n@Controller\npublic class %s {\n    @RequestMapping(\"/dup.do\")%s\n    public String f() { return \"x\"; }\n}\n";
        RuleSet.Run run = rules.run();
        run.apply(new Source("a/AController.java", ctl.formatted("AController", " // tb-ignore java.dupMapping 옛 주소"), null, null, null));
        run.apply(new Source("a/BController.java", ctl.formatted("BController", ""), null, null, null));
        List<Finding> dup = run.finish().stream().filter(f -> f.rule().equals("java.dupMapping")).toList();
        assertEquals(List.of("a/BController.java:4"), dup.stream().map(f -> f.file() + ":" + f.line()).toList(), "파일 사이 지적도 표식에 먹는다");
        assertEquals(1, run.suppressed());
        // 변경분 — 표식 파일을 observe 만 하면(안 바뀜) 그 파일 지적은 결과에 없으니 억제로 세지 않는다(마무리 리뷰)
        RuleSet.Run changedOnly = rules.run();
        changedOnly.observe(new Source("a/AController.java", ctl.formatted("AController", " // tb-ignore java.dupMapping 옛 주소"), null, null, null));
        changedOnly.apply(new Source("a/BController.java", ctl.formatted("BController", ""), null, null, null));
        assertTrue(changedOnly.finish().stream().noneMatch(f -> f.rule().equals("common.unusedIgnore")));
        assertEquals(0, changedOnly.suppressed());

        // 5-128(D-10) — tsx JSX 글 아포스트로피가 줄 끝까지 문자열로 읽혀도 그 뒤 // 표식은 주석이다
        java.util.function.Function<String, List<String>> tsx = text -> rules.apply(new Source("a/A.tsx", text, null, null, null)).stream()
                .filter(f -> f.rule().equals("common.alert")).map(f -> f.line() + " " + f.rule()).toList();
        assertEquals(List.of(), tsx.apply("const a = <p onClick={() => alert(1)}>Don't</p>; // tb-ignore common.alert\n"), "끊긴 따옴표 뒤 표식");

        CheckRunner.RunResult pasted = CheckRunner.runText("class A { void f() { System.out.println(1); // tb-ignore common.sysout\n"
                + " System.out.println(2); } }\n", "java", rules);
        assertEquals(List.of("2 common.sysout"), pasted.findings().stream().filter(f -> f.rule().equals("common.sysout"))
                .map(f -> f.line() + " " + f.rule()).toList(), "붙여넣기");
        assertEquals(1, pasted.suppressed());

        // 5-29b — 쓸모없는 표식: 빼는 지적 없음 · 다음 줄 없는 -next · 규칙 id 오타. 프로필로 끈다
        String unused = "int a = 1; // tb-ignore common.sysout,common.todo\nSystem.out.println(1); // tb-ignore commn.sysout\n// tb-ignore-next\n";
        java.util.function.Function<RuleSet, List<String>> marks = rs -> CheckRunner.runText(unused, "java", rs).findings().stream()
                .filter(f -> f.rule().equals("common.unusedIgnore")).map(f -> f.line() + " " + f.excerpt()).toList();
        assertEquals(List.of("1 tb-ignore common.sysout,common.todo", "2 tb-ignore commn.sysout", "3 tb-ignore-next"), marks.apply(rules),
                "발췌는 표식만 — 이유 글 없이, 규칙 id 는 적은 순서");
        assertEquals(List.of(), marks.apply(RuleSet.load(profile(new Profile.CodeCheck(Map.of("java", true, "mybatis", false),
                Map.of("common.unusedIgnore", false), null)), null)));
        assertTrue(RuleSet.builtin().globs().stream().noneMatch("*"::equals), "marker 글롭은 폴더 목록에 안 들어간다");
    }

    @Test
    void badRegexOverrideIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RuleSet.load(
                profile(new Profile.CodeCheck(null, Map.of("common.todo", Map.of("regex", "(")), null)), null));
        assertTrue(e.getMessage().startsWith("common.todo"), e.getMessage());
    }

    /** D-overrideValidate — 덮어쓰기 꼴이 틀리면(수 아닌 max · Boolean·Map 아님 · 글자 enabled · 목록 아닌 globs) 적재가 거절한다 */
    @Test
    void malformedOverridesAreRefused() {
        List<Object> bad = List.of(Map.of("params", Map.of("max", "abc")), "off", Map.of("enabled", "false"), Map.of("globs", "*.java"));
        for (Object o : bad) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RuleSet.load(
                    profile(new Profile.CodeCheck(null, Map.of("common.todo", o), null)), null), String.valueOf(o));
            assertTrue(e.getMessage().startsWith("common.todo"), e.getMessage());
        }
    }

    /** 붙여넣기는 lang 으로 글롭을 맞추고, 인코딩·줄바꿈을 몰라 파일 묶음 두 규칙이 안 걸린다 */
    @Test
    void pastedSource() {
        RuleSet rules = RuleSet.load(profile(null), null);
        List<Finding> f = rules.apply(Source.pasted("package a;\nclass A { void f() { System.out.println(1); } }\n", "java"));
        assertEquals(List.of("1 file.header", "2 common.sysout"), f.stream().map(x -> x.line() + " " + x.rule()).toList());
        assertEquals("(붙여넣기)", f.get(0).file());
    }

    @Test
    void commentStripKeepsStringsAndLines() {
        String java = "String u = \"http://x\"; // a\n/* b\n c */ int d;\nchar q = '\"'; // e\n";
        String s = RegexRule.stripComments(java, "java");
        assertEquals(java.split("\n", -1).length, s.split("\n", -1).length);
        assertTrue(s.contains("\"http://x\""), s);
        assertFalse(s.contains("a") && s.contains("// a"), s);
        assertTrue(s.contains("int d;") && !s.contains("b"), s);
        assertFalse(s.contains("// e"), s);
        assertEquals("a\n   \nb", RegexRule.stripComments("a\n# c\nb", "properties"));
        assertEquals("x            y", RegexRule.stripComments("x<%-- z --%> y", "jsp").replace("\n", ""));
    }

    /** 번들 78 리뷰 I2 — a11y.thScope 의 when: Thymeleaf <th:block> 은 th 가 아니다(corpus 골든 밖 빠른 레인 단언) */
    @Test
    void thScopeWhenSkipsThymeleafBlock() {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(Map.of("a11y", true), null, null)), null);
        String block = "<table>\n<th:block th:each=\"r : ${rows}\"><tr><td>x</td></tr></th:block>\n</table>\n";
        assertTrue(rules.apply(new Source("b.html", block, null, null, null)).stream().noneMatch(f -> f.rule().equals("a11y.thScope")));
        String th = "<table>\n<tr><th>제목</th></tr>\n</table>\n";
        assertEquals(1, rules.apply(new Source("t.html", th, null, null, null)).stream().filter(f -> f.rule().equals("a11y.thScope")).count());
    }

    @Test
    void jsImportNames() {
        assertEquals(List.of("React", "a", "c", "E"), JsImportRule.names("React, { a, b as c, type E }"));
        assertEquals(List.of("ns"), JsImportRule.names("* as ns"));
    }

    /** 번들 78 bot ③ — rules.yaml a11y.thScope 의 when 과 ThScopeFix.TOKEN 은 <th 경계 판정이 같다(줄 끝 · 공백 · / · > · <th:block · <thead · 줄 끝 \n(줄마다 vs 글 전체)). 대소문자 둘 다(5-85 flags i · TOKEN 은 (?i)) */
    @Test
    void thScopeWhenMatchesFixToken() {
        Rule.Def d = RuleSet.load(profile(new Profile.CodeCheck(Map.of("a11y", true), null, null)), null).defs().stream()
                .filter(x -> x.id().equals("a11y.thScope")).findFirst().orElseThrow();
        Pattern when = AbsentRule.when(d);
        for (String s : List.of("<th>", "<th ", "<th/>", "<th", "<th\tclass", "<th:block", "<thead>", "<the", "<td>", "<tr><th>x", "<th\n", "<th\n>", "<TH>", "<TH ", "<Th\n", "<THEAD>")) {
            Matcher m = ThScopeFix.TOKEN.matcher(s);
            boolean token = false;
            while (m.find() && !token) {
                token = m.group(2).equalsIgnoreCase("th");
            }
            assertEquals(when.matcher(s).find(), token, s);
        }
        RuleSet rs = RuleSet.load(profile(new Profile.CodeCheck(Map.of("a11y", true), null, null)), null);
        assertEquals(List.of("a11y.thScope"), rs.apply(new Source("x.jsp", "<TABLE><TR><TH>a</TH></TR></TABLE>\n", "UTF-8", "LF", null)).stream()
                .filter(f -> f.rule().startsWith("a11y.th")).map(Finding::rule).toList(), "대문자 TH 도 잡는다");
        assertTrue(rs.apply(new Source("y.jsp", "<TABLE><TR><TH SCOPE=\"col\">a</TH></TR></TABLE>\n", "UTF-8", "LF", null)).stream()
                .noneMatch(f -> f.rule().equals("a11y.thScope")), "대문자 SCOPE= 도 있음으로");
    }
}
