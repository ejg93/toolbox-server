package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 5-35b — 파일 사이 중복 덩어리. 정규화(주석·빈 줄·import·홑 괄호), 자기 겹침 제외, 결과 밖 파일은 상대만 */
class DuplicatesTest {

    static Rule.Def def(int minLines) {
        return new Rule.Def("metric.duplicate", "metric", "warn", "dup", List.of("*.java"), null, null, null, "m",
                Map.of("minLines", minLines), true);
    }

    /** 서로 다른 문장 n 줄 — 같은 tag 면 같은 글 */
    static String body(String tag, int from, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < from + n; i++) {
            sb.append("        call(\"").append(tag).append(i).append("\");\n");
        }
        return sb.toString();
    }

    static Source java(String rel, String text) {
        return new Source(rel, text, "UTF-8", "LF", "java");
    }

    static List<String> run(Duplicates d, Object... sourcesAndFlags) {
        for (int i = 0; i < sourcesAndFlags.length; i += 2) {
            d.see((Source) sourcesAndFlags[i], (Boolean) sourcesAndFlags[i + 1]);
        }
        List<String> out = new ArrayList<>();
        for (Finding f : d.finish()) {
            out.add(f.file() + ":" + f.line() + " " + f.excerpt());
        }
        return out;
    }

    @Test
    void twoFilesSameBlockHitsLaterFile() {
        String a = "package p;\nimport x.Y;\n\nclass A {\n    void f() {\n" + body("s", 0, 32) + "    }\n}\n";
        String b = "package p;\n\n// 다른 주석\nclass B {\n    void g() {\n\n" + body("s", 0, 16) + "        // 가운데\n\n" + body("s", 16, 16)
                + "    }\n}\n";
        assertEquals(List.of("B.java:7 A.java:6 과 같음 — 정규화 32줄"), run(new Duplicates(def(30)), java("A.java", a), true, java("B.java", b), true));
    }

    @Test
    void twentyNineLinesIsBelowThreshold() {
        String a = "class A {\n" + body("s", 0, 29) + "}\n";
        String b = "class B {\n" + body("s", 0, 29) + "}\n";
        assertEquals(List.of(), run(new Duplicates(def(30)), java("A.java", a), true, java("B.java", b), true));
    }

    @Test
    void selfOverlapInOneFileIsNotADuplicate() {
        StringBuilder same = new StringBuilder("class A {\n");
        for (int i = 0; i < 50; i++) {
            same.append("        x++;\n");
        }
        assertEquals(List.of(), run(new Duplicates(def(30)), java("A.java", same.append("}\n").toString()), true));
    }

    @Test
    void minLinesOverrideCatchesShorterBlocks() {
        String a = "class A {\n" + body("s", 0, 29) + "}\n";
        String b = "class B {\n" + body("s", 0, 29) + "}\n";
        assertEquals(List.of("B.java:2 A.java:2 과 같음 — 정규화 29줄"), run(new Duplicates(def(10)), java("A.java", a), true, java("B.java", b), true));
    }

    /** 변경분 검사 — 안 바뀐 파일(observe)은 상대만 된다. 바뀐 쪽이 앞이어도 뒤여도 바뀐 쪽에 한 건 */
    @Test
    void observedFileIsOnlyACounterpart() {
        String a = "class A {\n" + body("s", 0, 30) + "}\n";
        String b = "class B {\n" + body("s", 0, 30) + "}\n";
        assertEquals(List.of("B.java:2 A.java:2 과 같음 — 정규화 30줄"), run(new Duplicates(def(30)), java("A.java", a), false, java("B.java", b), true));
        assertEquals(List.of("A.java:2 B.java:2 과 같음 — 정규화 30줄"), run(new Duplicates(def(30)), java("A.java", a), true, java("B.java", b), false));
        assertEquals(List.of(), run(new Duplicates(def(30)), java("A.java", a), false, java("B.java", b), false));
    }

    @Test
    void threeCopiesHitSecondAndThird() {
        String t = "class X {\n" + body("s", 0, 30) + "}\n";
        assertEquals(List.of("B.java:1 A.java:1 과 같음 — 정규화 31줄", "C.java:1 A.java:1 과 같음 — 정규화 31줄"), // 클래스 줄까지 같다
                run(new Duplicates(def(30)), java("A.java", t), true, java("B.java", t), true, java("C.java", t), true));
    }

    /** RuleSet 으로 — 끄면 안 돌고, 켜면 finish 에서 나온다 */
    @Test
    void wiredThroughRuleSet() {
        String t = "class X {\n" + body("s", 0, 30) + "}\n";
        RuleSet rules = RuleSet.builtin();
        RuleSet.Run run = rules.run();
        run.apply(java("A.java", t));
        run.apply(java("B.java", t));
        assertEquals(1, run.finish().stream().filter(f -> f.rule().equals("metric.duplicate")).count());
        assertEquals(true, rules.globs().contains("*.java"));
    }
}
