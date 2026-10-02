package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void fixturesGolden(@TempDir Path data) throws Exception {
        RuleSet rules = RuleSet.load(profile(new Profile.CodeCheck(Map.of("java", false, "mybatis", false), null, null)), null);
        List<Map<String, Object>> golden = new ArrayList<>();
        Set<String> hitRules = new TreeSet<>();
        List<String> negativeHits = new ArrayList<>();
        for (Source s : fixtureSources(FIXTURES, data)) {
            if (s.rel().startsWith("java/") || s.rel().startsWith("mybatis/")) {
                continue; // 5-2·5-3 테스트가 따로 잰다
            }
            for (Finding f : rules.apply(s)) {
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

    @Test
    void badRegexOverrideIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RuleSet.load(
                profile(new Profile.CodeCheck(null, Map.of("common.todo", Map.of("regex", "(")), null)), null));
        assertTrue(e.getMessage().startsWith("common.todo"), e.getMessage());
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

    @Test
    void jsImportNames() {
        assertEquals(List.of("React", "a", "c", "E"), JsImportRule.names("React, { a, b as c, type E }"));
        assertEquals(List.of("ns"), JsImportRule.names("* as ns"));
    }
}
