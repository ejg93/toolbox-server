package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-3 — MyBatis XML 묶음 ㄷ 을 {@code fixtures/check/mybatis} 양성(Pos*)·음성(Neg*)에 돌린다. 음성에는 주석 안 {@code ${}}·
 * 페이징 래퍼·iBATIS sqlMap·FQCN namespace 가 있다. namespace 는 같은 폴더 {@code src/} 의 Java 와 견준다.
 */
class MyBatisRulesTest {

    static final Map<String, Boolean> ONLY_MYBATIS = Map.of("common", false, "jsp", false, "tsx", false, "file", false,
            "security", false, "java", false, "mybatis", true);

    static List<Finding> run(RuleSet rules, Path data) throws Exception {
        RuleSet.Run run = rules.run();
        List<Finding> out = new ArrayList<>();
        for (Source s : CheckRulesTest.fixtureSources(CheckRulesTest.FIXTURES.resolve("mybatis"), data)) {
            out.addAll(run.apply(s));
        }
        out.addAll(run.finish());
        return out;
    }

    static RuleSet rules() {
        return RuleSet.load(new Profile("t", null, null, null, null, null, null, new Profile.CodeCheck(ONLY_MYBATIS, null, null),
                "egov35", null, null, null), null);
    }

    @Test
    void fixturesGolden(@TempDir Path data) throws Exception {
        RuleSet rules = rules();
        List<Finding> found = run(rules, data);
        List<Map<String, Object>> golden = new ArrayList<>();
        for (Finding f : found) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("file", f.file());
            row.put("line", f.line());
            row.put("rule", f.rule());
            golden.add(row);
        }
        assertEquals(List.of(), found.stream().filter(f -> f.file().replaceAll(".*/", "").startsWith("Neg")).map(Finding::toString)
                .toList(), "음성 히트");
        Set<String> missing = new TreeSet<>();
        rules.defs().stream().filter(d -> d.on() && d.group().equals("mybatis")).forEach(d -> missing.add(d.id()));
        missing.removeAll(found.stream().map(Finding::rule).collect(Collectors.toSet()));
        assertEquals(Set.of(), missing, "양성에서 한 번도 안 걸린 규칙");
        GoldenFiles.assertJson("check/mybatis.json", golden);
    }

    /** 5-133(D-06·D-07·D-08·D-18) — 여러 줄 시작 태그는 머리 줄 · 서브쿼리 안 where 태그는 WHERE 가 아니다 · sql 조각은 안 본다 · package 정규식 선형 */
    @Test
    void statementLinesWhereAndFragments() {
        Map<String, Boolean> groups = new java.util.HashMap<>(ONLY_MYBATIS);
        groups.put("common", true);
        RuleSet rs = RuleSet.load(new Profile("t", null, null, null, null, null, null, new Profile.CodeCheck(groups, null, null),
                "egov35", null, null, null), null);
        RuleSet.Run run = rs.run();
        String multi = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mapper namespace=\"a.B\">\n<!-- tb-ignore-next mybatis.noWhere 손으로 확인 -->\n"
                + "<delete id=\"d\"\n    parameterType=\"map\">\nDELETE FROM T1\n</delete>\n</mapper>\n";
        List<Finding> f1 = new ArrayList<>(run.apply(new Source("a/B.xml", multi, null, null, "xml")));
        f1.addAll(run.finish());
        assertEquals(List.of(), f1.stream().filter(f -> f.rule().equals("mybatis.noWhere") || f.rule().equals("common.unusedIgnore")).toList());
        assertEquals(1, run.suppressed());
        java.util.function.Function<String, List<String>> nw = body -> {
            RuleSet.Run r = rules().run();
            List<Finding> out = new ArrayList<>(r.apply(new Source("a/C.xml", "<mapper namespace=\"a.C\">\n" + body + "\n</mapper>\n", null, null, "xml")));
            out.addAll(r.finish());
            return out.stream().filter(f -> f.rule().equals("mybatis.noWhere")).map(f -> f.line() + " " + f.rule()).toList();
        };
        assertEquals(List.of("2 mybatis.noWhere"), nw.apply("<update id=\"u\">UPDATE T1 SET N = (SELECT N FROM T2 <where> ID = #{id} </where>)</update>"));
        assertEquals(List.of(), nw.apply("<update id=\"u2\">UPDATE T1 SET A = 1 <where> ID = #{id} </where></update>"));
        assertEquals(List.of(), nw.apply("<sql id=\"del\">DELETE FROM T1</sql>"));
        // 5-151(D4-06) — 글의 괄호가 안 맞는 동적 SQL(속성·갈래 괄호) 뒤 최상위 <where> 는 태그를 믿는다
        assertEquals(List.of(), nw.apply("<update id=\"u3\">UPDATE T1 SET A = (SELECT MAX(X) FROM T2 WHERE K IN (\n"
                + "<foreach collection=\"ks\" item=\"k\" separator=\",\" close=\"))\">#{k}</foreach>\n<where> ID = #{id} </where></update>"));
        assertEquals(List.of(), nw.apply("<update id=\"u4\">UPDATE T1 SET A =\n<choose><when test=\"x\">NVL(#{a},</when><otherwise>NVL(#{b},</otherwise></choose>\n"
                + "0)\n<where> ID = #{id} </where></update>"));
        assertEquals(List.of(), nw.apply("<update id=\"u5\">UPDATE T1 SET A = 1 <trim prefix=\"WHERE (\" suffix=\")\"> ID = #{id} </trim></update>"));
        assertEquals(List.of(), nw.apply("<update id=\"u6\">UPDATE T1 SET A = '(' /* ( */ <where> ID = #{id} </where></update>"));
        String blank = "class A {}" + "\n".repeat(40_000);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
                () -> rules().run().apply(new Source("a/A.java", blank, null, null, "java")));
    }

    /** Java 를 하나도 못 본 실행(붙여넣기)은 namespace 를 안 잰다 */
    @Test
    void namespaceNeedsJavaSources() {
        RuleSet.Run run = rules().run();
        run.apply(Source.pasted("<mapper namespace=\"X\"><select id=\"a\">SELECT 1</select></mapper>", "xml"));
        assertEquals(List.of(), run.finish());
    }
}
