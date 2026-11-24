package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.JobContext;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 0-85f — 규칙 사이 취소 · 파일 하나의 스택 넘침은 그 파일만 「못 읽음」 에 센다(검사 전체가 FAILED 가 되지 않게) */
class CheckRunnerTest {


    static final String JAVA = "java";

    private static final String RULES_YAML = """
            rules:
              - id: t.deep
                group: common
                severity: warn
                globs: ['*.txt']
                regex: '(?:a|b)*c'
                message: deep
              - id: t.hit
                group: common
                severity: warn
                globs: ['*.txt']
                regex: 'HIT'
                message: hit
            """;

    private static RuleSet rules(Path base) throws Exception {
        Files.writeString(base.resolve("rules.yaml"), RULES_YAML, StandardCharsets.UTF_8);
        return RuleSet.load(CheckRulesTest.profile(new Profile.CodeCheck(Map.of(JAVA, false, "mybatis", false), null, "rules.yaml")), base);
    }

    @Test
    void stackOverflowFileIsSkipped(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("deep.txt"), "ab".repeat(200_000) + "\n", StandardCharsets.UTF_8);
        Files.writeString(src.resolve("ok.txt"), "HIT\n", StandardCharsets.UTF_8);
        CheckRunner.RunResult r = CheckRunner.run(src.toString(), rules(tmp), new LocalFiles(tmp.resolve("data")), null);
        assertEquals(1, r.skipped(), "스택 넘친 파일 하나");
        assertEquals(List.of("ok.txt t.hit"),
                r.findings().stream().filter(f -> f.rule().startsWith("t.")).map(f -> f.file() + " " + f.rule()).toList());
    }

    /** D-parseErrCount — 파싱 실패 수는 java.parseError 규칙 켬·끔과 tb-ignore 표식과 무관하다 */
    @Test
    void parseErrorCountIgnoresRuleSwitchAndMarkers() {
        String broken = "class A { void m( { }";
        RuleSet base = RuleSet.load(CheckRulesTest.profile(new Profile.CodeCheck(null, null, null)), null);
        RuleSet off = RuleSet.load(CheckRulesTest.profile(new Profile.CodeCheck(null, Map.of("java.parseError", false), null)), null);
        assertEquals(1, CheckRunner.runText(broken, JAVA, base).parseErrors(), "기본");
        assertEquals(1, CheckRunner.runText(broken, JAVA, off).parseErrors(), "규칙 끔");
        assertEquals(1, CheckRunner.runText("// tb-ignore-next java.parseError x\n" + broken, JAVA, base).parseErrors(), "표식");
    }

    /** 0-92a(설계 69 §3-9) — 붙여넣기 검사도 폴더 길과 같게: 스택 넘침은 작업 실패가 아니라 「못 읽음 1」 */
    @Test
    void pastedStackOverflowIsSkipped(@TempDir Path tmp) throws Exception {
        CheckRunner.RunResult r = CheckRunner.runText("ab".repeat(200_000) + "\n", "txt", rules(tmp));
        assertEquals(1, r.skipped());
        assertEquals(List.of(), r.findings());
    }

    /** 번들 83 마무리 리뷰 I1 — 스택 넘쳐 건너뛴 Java 파일도 매퍼 대조의 형식으로는 본다(거짓 mybatis.namespace 가 안 나게) */
    @Test
    void skippedJavaStillSeenByMapperCheck(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("deep.yaml"), RULES_YAML.replace("'*.txt'", "'*.java'"), StandardCharsets.UTF_8);
        Path src = tmp.resolve("src/com/ex");
        Files.createDirectories(src);
        Files.writeString(src.resolve("UserMapper.java"), "package com.ex;\n\npublic interface UserMapper {\n    String K = \"" + "ab".repeat(200_000)
                + "\";\n}\n", StandardCharsets.UTF_8);
        Files.writeString(src.resolve("Other.java"), "package com.ex;\n\npublic class Other {\n}\n", StandardCharsets.UTF_8);
        Files.writeString(src.resolve("UserMapper.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mapper namespace=\"com.ex.UserMapper\">\n"
                + "  <select id=\"one\" resultType=\"int\">SELECT 1 FROM DUAL WHERE 1 = 1</select>\n</mapper>\n", StandardCharsets.UTF_8);
        RuleSet rules = RuleSet.load(CheckRulesTest.profile(new Profile.CodeCheck(null, null, "deep.yaml")), tmp);
        CheckRunner.RunResult r = CheckRunner.run(tmp.resolve("src").toString(), rules, new LocalFiles(tmp.resolve("data")), null);
        assertEquals(1, r.skipped());
        assertEquals(List.of(), r.findings().stream().filter(f -> "mybatis.namespace".equals(f.rule())).map(Finding::file).toList());
    }

    @Test
    void cancelBetweenRules(@TempDir Path tmp) throws Exception {
        RuleSet.Run run = rules(tmp).run().onRule(() -> {
            throw new JobContext.JobCancelledException();
        });
        Source s = new Source("a.txt", "HIT\n", null, null, null);
        assertThrows(JobContext.JobCancelledException.class, () -> run.apply(s));
    }

    /** 5-149(D4-04·D4-05·D-11) — 붙여넣기 언어 추정: 긴 한 줄 선형 · record·enum·abstract 로 시작하는 JS·TS · 메서드·필드만 붙인 자바 */
    @Test
    void guessLangPastedShapes() {
        String line = "var s = \"" + "a class b ".repeat(40_000) + "\";";
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(2), () -> CheckRunner.guessLang(line));
        for (String js : List.of("var rows = grid.rows;\nrecord = rows[0];\nalert(record.name);\neval(x);\n", "callback(\n    record\n);\n",
                "var o = { record : 1 };\n", "enum Color { Red, Green }\n", "abstract class A {\n  x = 1;\n}\n")) {
            assertEquals(false, JAVA.equals(CheckRunner.guessLang(js)), js);
        }
        for (String java : List.of("@RequestMapping(\"/a.do\")\npublic String list(@RequestParam String id, ModelMap model) throws Exception {\n  return \"a\";\n}\n",
                "private static final Logger LOG = LoggerFactory.getLogger(A.class);\npublic void f() { e.printStackTrace(); }\n",
                "public List<Map<String, Object>> rows(String id) {\n  return null;\n}\n", "public void run() {\n  go();\n}\n",
                "protected String[] names() {\n  return new String[0];\n}\n")) {
            assertEquals(JAVA, CheckRunner.guessLang(java), java);
        }
        for (String ts : List.of("public name: string;\n", "private async load() {\n}\n", "public get x() {\n  return 1;\n}\n",
                "protected readonly a = 1;\n", "public static create(): Foo {\n  return new Foo();\n}\n", "private items: Item[] = [];\n",
                "public set y(v) {\n}\n", "public override run() {\n}\n", "private declare z;\n", "public constructor(private a: A) {\n}\n",
                "private handle = (e) => {\n};\n")) {
            assertEquals(false, JAVA.equals(CheckRunner.guessLang(ts)), ts);
        }
        assertEquals(JAVA, CheckRunner.guessLang("public record Point(int x, int y) {}\n"));
        assertEquals(JAVA, CheckRunner.guessLang("package a;\nenum Color { RED }\n"));
    }
}
