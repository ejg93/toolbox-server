package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
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
    /**
     * 5-93b — 도우미의 파일은 혼자다: 자기 패키지(픽스처 cleanup.* · 인라인 한 마디 패키지) 형식 목록은 빈 것으로 안다. 그 밖(별표로 들여오는 javax.servlet 등)은 모른다.
     * 자기 패키지를 모르는 길은 셋짜리 생성자로 본다
     */
    static final java.util.function.BiFunction<String, String, java.util.Optional<Set<String>>> OWN =
            (rel, pkg) -> pkg.startsWith("cleanup") || pkg.indexOf('.') < 0 ? java.util.Optional.of(Set.of()) : java.util.Optional.empty();

    static Profile profile(Map<String, Boolean> groups) {
        return new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null, null,
                new Profile.CodeCheck(groups, null, null), "egov35", null, null, null);
    }

    static Cleanup cleanup(Map<String, Boolean> groups) {
        return new Cleanup(RuleSet.load(profile(groups), null), Cleanup.RULES, "LF", rel -> java.util.Optional.empty(), OWN);
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
            assertEquals(r.changed(), !r.text().equals(s.text()) || !r.lineEnding().equals(s.lineEnding())
                    || !r.encoding().equals(s.encoding()), s.rel());
        }
        GoldenFiles.assertJson("cleanup/steps.json", steps);

        String jsp = by.get("debugger/pos.jsp").text();
        assertTrue(jsp.contains("var x = 1; debugger;") && jsp.contains("<%-- debugger --%>"), jsp);
        assertFalse(jsp.contains("    debugger;\n") || jsp.contains("debugger ;"), jsp);
        String ts = by.get("debugger/pos.ts").text();
        assertTrue(ts.contains("if (a) { debugger; }") && ts.contains("\ndebugger;\n`"), "한 줄 코드·템플릿 리터럴 안은 남는다 — " + ts);
        assertFalse(ts.contains("  debugger\n"), ts);

        String tsImport = by.get("tsximport/pos.ts").text();
        assertTrue(tsImport.contains("import { s } from 's';"), "5-49 — 스프레드로만 쓰는 import 는 안 지운다 — " + tsImport);
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
        Cleanup.Result con = by.get("console/pos.tsx");
        assertTrue(!con.text().contains("\"start\"") && !con.text().contains("\"multi\"") && !con.text().contains("paren in string"), con.text());
        assertTrue(con.text().contains("console.error(\"kept\", a);") && con.text().contains("console.log(\"in template\")")
                && con.text().contains("console.info(\"inline\")") && con.text().contains("console.log(\"braceless\")"), con.text());
        assertEquals(List.of("줄 9: 템플릿 리터럴 안 console", "줄 11: console 호출이 줄 처음에 있지 않다(다른 코드·주석)",
                "줄 13: 중괄호 없는 제어문 몸체일 수 있음", "줄 18: 앞 줄이 문장 끝이 아니다(이어지는 식일 수 있음)", // 마무리 리뷰 둘
                "줄 20: 여러 줄 console 끝을 못 찾음"), con.skips().stream().map(Cleanup.Skip::reason).toList());
        assertTrue(con.text().contains("console.log(\"arrow body\")") && con.text().contains("don't"), con.text());
        assertTrue(next.skips().stream().anyMatch(k -> k.stage().equals(Cleanup.DELETE) && k.reason().startsWith("다른 지적이 사라짐")),
                next.skips().toString());

        String ig = by.get("ignore/Pos.java").text();
        assertTrue(ig.contains("    int x = 1;\n") && ig.contains("    String s = \"http://a\";\n"), ig);
        assertTrue(ig.contains("/* tb-ignore common.todo */ int y;") && ig.contains("// tb-ignore java.dupMapping")
                && ig.contains("// tb-ignore * 전부") && ig.contains("// tb-ignore-next metric.duplicate"), "앞 주석·파일 사이 규칙·전부 표식은 남는다 — " + ig);
        assertFalse(ig.contains("지난번 println") || ig.contains("java.emptyCatch"), ig);
        // 5-44b — 설명 글 속 낱말은 표식이 아니라 그대로, * 바로 뒤 표식 줄만 쓸모없는 표식으로 뗀다
        assertTrue(ig.contains("     * 설명 tb-ignore common.sysout\n") && !ig.contains("이유 — 5-44b"), "javadoc — " + ig);
    }

    /** 설계 50 — ③ 값·비교: 지적 노드만 한 줄 안에서. 못 바꾸는 꼴은 사유 */
    @Test
    void valueStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("value", "Pos.java", data);
        String t = r.text();
        assertTrue(t.contains("return \"a\".equals(x) || !\"b\".equals(y);") && t.contains("return Objects.equals(a, b);"), t);
        assertTrue(t.contains("\"yyyy-MM-dd HH:mm\"") && t.contains("new SimpleDateFormat(\"yyyy/MM/dd\")"), t);
        assertTrue(t.contains("BigDecimal.valueOf(0.1);") && t.contains("BigDecimal.valueOf(d);"), t);
        assertTrue(t.contains("return \"a\".equals(x) && \"B\".equalsIgnoreCase(y);"), "리터럴 앞세우기 — 한 줄에 둘 · 괄호 scope — " + t);
        assertTrue(t.contains("new BigDecimal(0.1f)") && t.contains("new BigDecimal(d, mc)") && t.contains("== \"c\";")
                && t.contains("\"a\" == \"a\""), "건너뛴 꼴은 그대로 — " + t);
        assertEquals(List.of("줄 22: 양쪽이 리터럴", "줄 26: 여러 줄 식", "줄 41: float 는 valueOf 가 double 로 넓힌다", "줄 42: MathContext 인자"),
                r.skips().stream().filter(k -> k.stage().equals(Cleanup.VALUE)).map(Cleanup.Skip::reason).toList());
    }

    /** 설계 50 — ④ 자원 해제: 선언·대입·finally 줄을 지우고 try 머리에. 줄 꼴이 다르면·중첩 안쪽은 사유 */
    @Test
    void resourceStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("resource", "Pos.java", data);
        String t = r.text();
        assertTrue(t.contains("        try (InputStream is = new FileInputStream(path)) {\n            return is.read();\n        }\n    }"), t);
        assertTrue(t.contains("try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {"), t);
        assertTrue(t.contains("try (FileInputStream in = new FileInputStream(path)) {"), t);
        assertTrue(t.contains("        InputStream is = null;\n        try {\n            is = new FileInputStream(path);\n            return is.read();\n"
                + "        } catch (IOException e) {\n            return -1;\n        } finally {"), "5-109 — catch + finally 안 try-catch close 는 원문 그대로 — " + t);
        assertTrue(t.contains("try { // 주석") && t.contains("try (InputStream x = new FileInputStream(a)) {")
                && t.contains("            InputStream y = new FileInputStream(b);\n            try {"), "줄 꼴 틀림·중첩 안쪽은 그대로 — " + t);
        assertTrue(t.contains("e.printStackTrace()"), "삼키는 finally 는 남는다 — " + t);
        assertEquals(List.of("줄 52: close 예외를 삼키며 닫는다(closeQuietly·try-catch) — 예외 경로가 바뀌어 손으로", "줄 70: 줄 꼴이 달라 못 고침(한 줄에 다른 코드·여러 줄 식·여러 줄 close)", "줄 84: 겹친 try",
                        "줄 137: 줄 꼴이 달라 못 고침(한 줄에 다른 코드·여러 줄 식·여러 줄 close)", // 마무리 리뷰 — 꼴 A' 블록 null 지킴 close 는 SHAPE
                        "줄 145: close 뒤에서 몸 안 이름을 다시 쓴다 — 줄을 옮겨야 해 손으로"), // 5-68
                r.skips().stream().filter(k -> k.stage().equals(Cleanup.RESOURCE)).map(Cleanup.Skip::reason).toList());
        assertTrue(t.contains("        InputStream is = new FileInputStream(path);\n        int n = is.read();\n        is.close();\n        return n;"),
                "close 뒤 몸 안 변수 꼴은 원문 그대로 — " + t);
    }

    /** 설계 60 — 안 쓰는 지역 변수: 순수한 초기화식의 한 줄 선언만 줄째 지운다. import 의 마지막 사용을 지우면 unusedImport 가 남는다(다음 실행이 지운다) */
    @Test
    void localStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("local", "Pos.java", data);
        String t = r.text();
        assertFalse(t.contains("int count = 0;") || t.contains("String label") || t.contains("int z = 3;") || t.contains("Date d = null;"), t);
        assertTrue(t.contains("int m = k;") && t.contains("int a = 1, b = 2;")
                && t.contains("int only = 0;") && t.contains("String s =\n"), "건너뛴 꼴은 그대로 — " + t);
        assertFalse(t.contains("int n = xs.size();"), t);
        assertEquals(2, t.split("        xs\\.size\\(\\);\n", -1).length - 1, "호출만 남김(설계 61) — 20줄·58줄 — " + t);
        assertTrue(t.contains("        xs.get(0).trim(); // 메모\n") && t.contains("        now();\n") && t.contains("            e.getMessage();\n"),
                "꼬리 주석·객체·블록에 홀로 — " + t);
        assertTrue(t.contains("Object made = new Object();") && t.contains("String cast = (String) o;") && t.contains("String pick =")
                && t.contains("sup = wrap(") && t.contains("String multi = String.valueOf(\n"), "new·캐스트·삼항·람다·여러 줄은 그대로 — " + t);
        assertEquals(List.of(new Cleanup.Step(Cleanup.LOCAL, LocalsAst.UNUSED_LOCAL, 18, 9)),
                r.steps().stream().filter(k -> k.stage().equals(Cleanup.LOCAL)).toList());
        String impure = "초기화식이 리터럴도 호출 하나도 아니다 — 지우면 동작이 바뀔 수 있다";
        assertEquals(List.of("줄 21: " + impure, "줄 26: 한 문장에 선언 둘", "줄 31: 여러 줄 선언", "줄 40: 블록에 이 선언뿐 — 지우면 빈 블록",
                "줄 73: " + impure, "줄 74: " + impure, "줄 75: " + impure, "줄 76: 초기화식에 람다·메서드 참조 — 호출만 남기면 형식 추론이 달라질 수 있다",
                "줄 77: 여러 줄 선언"),
                r.skips().stream().filter(k -> k.stage().equals(Cleanup.LOCAL)).map(Cleanup.Skip::reason).toList());
        assertEquals(1, r.after().stream().filter(x -> x.rule().equals("java.unusedImport")).count(), "Date import 가 남는다");
    }

    /** 설계 60 — 문자셋 고침: 프로필 UTF-8 → StandardCharsets.UTF_8. 한 파일에 StandardCharsets·Objects import 가 둘 다 끼워진다 */
    @Test
    void charsetStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("charset", "Pos.java", data);
        String t = r.text();
        assertTrue(t.contains("return s.getBytes(StandardCharsets.UTF_8);") && t.contains("return \"가\".getBytes(StandardCharsets.UTF_8);")
                && t.contains("return new String(b, StandardCharsets.UTF_8);") && t.contains("return Objects.equals(x, y);"), t);
        assertTrue(t.contains("import java.nio.charset.StandardCharsets;\n") && t.contains("import java.util.Objects;\n"), "import 둘 — " + t);
        Cleanup.Result c = clean("charset", "Clash.java", data);
        assertTrue(c.text().contains("return s.getBytes();"), c.text());
        assertEquals(List.of("줄 7: 다른 StandardCharsets·Charset 과 이름 충돌"),
                c.skips().stream().filter(k -> k.stage().equals(Cleanup.VALUE)).map(Cleanup.Skip::reason).toList());
    }

    static Cleanup cleanupIn(String encoding) {
        Profile p = new Profile("t", new Profile.Project(null, encoding, "LF", null), null, null, null, null, null,
                new Profile.CodeCheck(null, null, null), "egov35", null, null, null);
        return new Cleanup(RuleSet.load(p, null), Cleanup.RULES, "LF", rel -> java.util.Optional.empty(), OWN);
    }

    /** 설계 60 — 프로필 인코딩: EUC-KR → Charset.forName · UTF-8-BOM → UTF_8 · 모르는 이름 → 건너뜀 */
    @Test
    void charsetFromProfile() {
        String src = "package e;\n\nclass E {\n    byte[] f(String s) {\n        return s.getBytes();\n    }\n}\n";
        Source in = new Source("E.java", src, "UTF-8", "LF", "java");
        String euc = cleanupIn("EUC-KR").clean(in).text();
        assertTrue(euc.contains("return s.getBytes(Charset.forName(\"EUC-KR\"));") && euc.contains("import java.nio.charset.Charset;\n"), euc);
        assertTrue(cleanupIn("UTF-8-BOM").clean(in).text().contains("s.getBytes(StandardCharsets.UTF_8)"));
        Cleanup.Result bad = cleanupIn("NO-SUCH-CS").clean(in);
        assertEquals(src, bad.text());
        assertEquals(List.of("줄 5: 프로필 project.encoding 꼴을 못 읽음"),
                bad.skips().stream().filter(k -> k.stage().equals(Cleanup.VALUE)).map(Cleanup.Skip::reason).toList());
    }

    /** 설계 64 §3-5 — 중괄호 끼우기(같은 줄·여러 줄·else if 사슬·do while) · 빈 문장 · 홀로 블록 내어쓰기. 머리 끝 주석·같은 줄 중첩·선언 블록은 건너뜀 */
    @Test
    void syntaxStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("syntax", "Pos.java", data);
        String t = r.text();
        assertTrue(t.contains("        if (a) { return 1; }\n        if (a) {\n            return 2;\n        } else if (b) {\n            return 3;\n"
                + "        } else {\n            return 4;\n        }\n    }"), t);
        assertTrue(t.contains("        for (int x : xs) {\n            use(x);\n        }\n        while (xs.length > 9) {\n            use(1);\n        }\n"
                + "        do {\n            use(2);\n        } while (xs.length > 9);\n"), t);
        assertTrue(t.contains("        use(1);\n        use(2);\n        {\n            int y = 3;"), t);
        assertTrue(t.contains("        if (a) // 주석\n            use(1);\n        if (a) if (b) use(2);\n"), t);
        assertEquals(List.of("줄 32: 블록 안 선언 — 범위가 바뀐다", "줄 39: 머리 줄 끝에 다른 글", "줄 41: 중첩된 중괄호 없는 문장", "줄 41: 중첩된 중괄호 없는 문장",
                "줄 48: 블록 안 선언 — 범위가 바뀐다"), skipsOf(r, Cleanup.SYNTAX)); // 5-93c ⑦ — instanceof 패턴 변수도 범위가 흐른다
        assertEquals(List.of("java.emptyStatement 2→0", "java.needBraces 9→3", "java.unneededBlock 3→2"), r.steps().stream()
                .filter(s -> s.stage().equals(Cleanup.SYNTAX)).map(s -> s.rule() + " " + s.before() + "→" + s.after()).sorted().toList());
    }

    /** 설계 64 §3-6 — 가운데 default 를 끝으로(break·return·->·주석 같이). default·앞 case·마지막 case 가 떨어지거나 default 가 묶이면 건너뜀 */
    @Test
    void switchStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("switch", "Pos.java", data);
        String t = r.text();
        assertTrue(t.contains("            case 1:\n                return 1;\n            case 2:\n                return 2;\n            default:\n                return 0;\n        }"), t);
        assertTrue(t.contains("            case 1:\n                use(1);\n                break;\n            // 기본\n            default:\n                use(0);\n                break;\n        }"), t);
        assertEquals(List.of("줄 38: default 가 다음 case 로 떨어진다", "줄 50: 앞 case 가 default 로 떨어진다", "줄 61: 마지막 case 가 떨어진다",
                "줄 71: default 가 다음 case 와 묶임"), skipsOf(r, Cleanup.SWITCH));
        assertTrue(t.contains("            case 1 -> use(1);\n            default -> use(0);\n"), t);
        // 5-93c ⑥ — default 위 주석 두 줄이 같이 옮겨진다
        assertTrue(t.contains("            case 1:\n                use(1);\n                break;\n            case 2:\n                use(2);\n                break;\n"
                + "            // 기본 첫 줄\n            // 기본 둘째 줄\n            default:\n                use(0);\n                break;\n        }"), t);
        assertEquals(List.of("java.defaultLast 8→4"), r.steps().stream().filter(s -> s.stage().equals(Cleanup.SWITCH))
                .map(s -> s.rule() + " " + s.before() + "→" + s.after()).toList());
        // 5-99c — default 줄보다 깊이 들여쓴 줄 주석은 앞 case 것 — 그 자리에 남는다
        String head = "package cleanup.t;\n\nclass A {\n    void f(int k) {\n        switch (k) {\n            case 1:\n                a();\n                break;\n"
                + "                // case 1 뒤 메모\n";
        String dflt = "            default:\n                b();\n                break;\n";
        String two = "            case 2:\n                c();\n                break;\n";
        String foot = "        }\n    }\n}\n";
        String own = cleanup(null).clean(new Source("t/A.java", head + dflt + two + foot, null, "LF", "java")).text();
        assertEquals(head + two + dflt + foot, own);
        // 번들 83 마무리 리뷰 C1 — 블록 주석 안의 // 줄로는 자르지 않는다(자르면 뒤 case 가 주석 안으로 들어간다)
        String blockHead = "package cleanup.t;\n\nclass A {\n    void f(int k) {\n        switch (k) {\n            case 1:\n                a();\n                break;\n";
        String block = "            /*\n            case 9:\n                // legacy\n                old();\n                break;\n            */\n";
        String moved = cleanup(null).clean(new Source("t/A.java", blockHead + block + dflt + two + foot, null, "LF", "java")).text();
        assertEquals(blockHead + two + block + dflt + foot, moved);
    }

    /** 설계 64 §3-7 — InterruptedException catch 의 { 줄 뒤에 interrupt() 를 끼운다. 빈 catch 가 사라지는 것은 의도(되돌리지 않음) · 한 줄 {} 은 건너뜀 */
    @Test
    void interruptStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("interrupt", "Pos.java", data);
        String t = r.text();
        String add = "Thread.currentThread().interrupt();";
        assertTrue(t.contains("        } catch (InterruptedException e) {\n            " + add + "\n        }\n    }\n\n    void commentOnly"), t);
        assertTrue(t.contains("        } catch (InterruptedException e) {\n            " + add + "\n            // 무시\n"), t);
        assertTrue(t.contains("        } catch (InterruptedException e) {\n            " + add + "\n            log(e);\n        }\n    }\n\n    void tabs"), t);
        assertTrue(t.contains("\t\t} catch (InterruptedException e) {\n\t\t\t" + add + "\n\t\t}\n"), t);
        assertTrue(t.contains("        } catch (IOException | InterruptedException e) {\n            if (e instanceof InterruptedException) {\n                " + add
                + "\n            }\n            log(e);\n"), "5-94 union 은 instanceof 세 줄 — " + t);
        // 5-93a — Allman 빈 catch · 루프 안(탈출 있음) · { 뒤 줄 주석은 고친다, 루프 안(탈출 없음)은 건너뜀
        assertTrue(t.contains("        } catch (InterruptedException e)\n        {\n            " + add + "\n        }\n"), t);
        assertTrue(t.contains("            } catch (InterruptedException e) {\n                " + add + "\n                log(e);\n                break;\n"), t);
        assertTrue(t.contains("            } catch (InterruptedException e) {\n                log(e);\n            }\n        }\n    }\n\n    void loopBreak"), t);
        assertTrue(t.contains("        } catch (InterruptedException e) { // 무시\n            " + add + "\n        }\n"), t);
        assertEquals(9, t.split(java.util.regex.Pattern.quote(add), -1).length - 1, "이미 있는 것 하나 + 끼운 여덟(union 하나)");
        assertEquals(List.of("줄 41: catch 몸이 한 줄", "줄 79: " + InterruptFix.IN_LOOP), skipsOf(r, Cleanup.INTERRUPT));
        assertEquals(List.of("java.interruptedIgnored 10→2"), r.steps().stream().filter(s -> s.stage().equals(Cleanup.INTERRUPT))
                .map(s -> s.rule() + " " + s.before() + "→" + s.after()).toList());

        String other = "package p;\n\nimport q.Thread;\n\nclass A {\n    void a() {\n        try {\n            java.lang.Thread.sleep(1);\n"
                + "        } catch (InterruptedException e) {\n        }\n    }\n}\n";
        Edits e = new Edits(other);
        List<Cleanup.Skip> skips = new java.util.ArrayList<>();
        InterruptFix.apply(other, List.of(new Finding("A.java", 9, "java", JavaShape.INTERRUPTED, "warn", "")), new JavaSource(), e, skips);
        assertTrue(e.isEmpty(), "다른 Thread import 면 안 고친다");
        assertEquals(List.of("줄 9: Thread 이름이 다른 형식"), skips.stream().map(Cleanup.Skip::reason).toList());

        String after = "class A {\n    void a() {\n        try {\n            Thread.sleep(1);\n        } catch (InterruptedException e) { log(e);\n        }\n    }\n\n"
                + "    void log(Exception e) {\n    }\n}\n";
        Edits ea = new Edits(after);
        List<Cleanup.Skip> sa = new java.util.ArrayList<>();
        InterruptFix.apply(after, List.of(new Finding("A.java", 5, "java", JavaShape.INTERRUPTED, "warn", "")), new JavaSource(), ea, sa);
        assertTrue(ea.isEmpty(), "{ 뒤 문장이면 안 고친다");
        assertEquals(List.of("줄 5: " + InterruptFix.AFTER_BRACE), sa.stream().map(Cleanup.Skip::reason).toList());

        // 5-99d ⑥ — 탭 들여쓰기 union catch 도 instanceof 세 줄을 탭으로
        String tabs = "package p;\n\nimport java.io.IOException;\n\nclass A {\n\tvoid a() {\n\t\ttry {\n\t\t\tThread.sleep(1);\n\t\t\tio();\n"
                + "\t\t} catch (IOException | InterruptedException e) {\n\t\t\tlog(e);\n\t\t}\n\t}\n}\n";
        String tt = cleanup(null).clean(new Source("p/A.java", tabs, null, "LF", "java")).text();
        assertTrue(tt.contains("\t\t} catch (IOException | InterruptedException e) {\n\t\t\tif (e instanceof InterruptedException) {\n\t\t\t\t" + add
                + "\n\t\t\t}\n\t\t\tlog(e);\n"), tt);
    }

    /** 설계 64 §3-8 — 카드번호 꼴을 끝 넷만 남겨 가린다(언어 무관 줄 치환). 주석 속 번호·자리 넘는 숫자는 그대로 */
    @Test
    void piiStage(@TempDir Path data) throws Exception {
        String j = clean("pii", "Pos.java", data).text();
        assertTrue(j.contains("    static final String CARD = \"****-****-****-3456\";\n"), j);
        assertTrue(j.contains("return \"****-****-****-7654\" + \" / \" + \"****-****-****-0003\";"), j);
        assertTrue(j.contains("// 예: 1111-2222-3333-4444 — 주석"), j);
        assertTrue(j.contains("\"1234-5678-9012-34567\""), j);
        // 설계 65 5-97 — 대시 없는 번호는 Luhn 통과만 앞 열둘을 가린다
        assertTrue(j.contains("    static final String PLAIN = \"************1234\";\n"), j);
        assertTrue(j.contains("    static final String SERIAL = \"4030000010001235\";\n"), j);
        Cleanup.Result p = clean("pii", "pos.properties", data);
        assertEquals("# 설계 64 — 코드 정리 pii(카드번호 가리기)\n# 시험 1111-2222-3333-4444 은 주석이라 그대로\ncard.test=****-****-****-9010\n"
                + "card.pair=****-****-****-0004,****-****-****-0002\ncard.plain=****-****-****-3456 ************2000\n"
                + "card.serial=4030000010001235\n", p.text());
        assertEquals(List.of("security.piiCard 3→0", "security.piiCardPlain 1→0"), p.steps().stream().filter(s -> s.stage().equals(Cleanup.PII))
                .map(s -> s.rule() + " " + s.before() + "→" + s.after()).sorted().toList());
        assertEquals(List.of(), skipsOf(p, Cleanup.PII));
        // 5-99d ⑥ — 가리기 규칙을 하나만 고르면 그 꼴만 가린다(targets.contains 길)
        String props = java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/fixtures/cleanup/pii/pos.properties")).replace("\r\n", "\n");
        RuleSet all = RuleSet.load(profile(null), null);
        String dash = new Cleanup(all, Set.of("security.piiCard"), "LF").clean(new Source("pos.properties", props, null, "LF", null)).text();
        assertTrue(dash.contains("card.test=****-****-****-9010") && dash.contains("card.plain=****-****-****-3456 5100000020002000"), dash);
        String plain = new Cleanup(all, Set.of("security.piiCardPlain"), "LF").clean(new Source("pos.properties", props, null, "LF", null)).text();
        assertTrue(plain.contains("card.test=4000-1234-5678-9010") && plain.contains("card.plain=1234-5678-9012-3456 ************2000"), plain);
    }

    /** 설계 64 §3-9 — catch 매개변수의 e.printStackTrace() 문장만 로거 error(메시지, 예외)로. catch 밖·람다 안·main 은 건너뜀 · 로거가 없으면 끼운다 */
    @Test
    void stackStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("log", "Stack.java", data);
        String t = r.text();
        assertTrue(t.contains("        } catch (RuntimeException e) {\n            logger.error(e.getMessage(), e);\n        }"), t);
        assertTrue(t.contains("            logger.error(ex.getMessage(), ex);\n            throw ex;"), t);
        assertTrue(t.contains("        t.printStackTrace();") && t.contains("() -> e.printStackTrace();"), t);
        assertEquals(List.of("줄 28: catch 매개변수의 printStackTrace 가 아니다 — 손으로", "줄 35: catch 매개변수의 printStackTrace 가 아니다 — 손으로",
                "줄 44: main 의 콘솔 출력"), skipsOf(r, Cleanup.LOG));
        // 설계 65 5-95 — printStackTrace(System.out) 도 로거 error 로 · PrintWriter 변수 인자는 지적 밖
        assertTrue(t.contains("        } catch (RuntimeException e) {\n            logger.error(e.getMessage(), e);\n        }\n    }\n\n    void f("), t);
        assertTrue(t.contains("            e.printStackTrace(pw);\n"), t);
        assertEquals(List.of("common.printStackTrace 5→3", "java.printStackTraceStream 1→0"), r.steps().stream().filter(s -> s.stage().equals(Cleanup.LOG))
                .map(s -> s.rule() + " " + s.before() + "→" + s.after()).sorted().toList());
        assertEquals(List.of("줄 11: 로거가 없고 같은 소스 루트에 베낄 로거 꼴도 없다"), skipsOf(clean("log", "StackNoLogger.java", data), Cleanup.LOG));

        LoggerShape sh = new LoggerShape(List.of("org.slf4j.Logger", "org.slf4j.LoggerFactory"), "logger",
                "private static final Logger logger = LoggerFactory.getLogger({C}.class);");
        Cleanup c = new Cleanup(RuleSet.load(profile(null), null), Cleanup.RULES, "LF", rel -> java.util.Optional.of(sh));
        Cleanup.Result in = c.clean(source("log", "StackNoLogger.java", data));
        GoldenFiles.assertText("cleanup/log-StackNoLogger.inserted.java.txt", in.text());
        assertTrue(in.text().contains("    private static final Logger logger = LoggerFactory.getLogger(StackNoLogger.class);\n\n    private int c;"), in.text());
        assertTrue(in.text().contains("            logger.error(e.getMessage(), e);\n"), in.text());
        String cp = System.getProperty("java.class.path");
        assertEquals(List.of(), compile(t, data.resolve("stack-after"), cp), "고친 글이 컴파일된다");
        assertEquals(List.of(), compile(in.text(), data.resolve("stacknologger-after"), cp), "끼운 로거가 컴파일된다");

        // R-27 — roller RollerException:105 super.printStackTrace() · 수신자가 이름 하나가 아니면 그 사유(print·println 사유가 아니다)
        String sup = "package log;\n\nclass E extends Exception {\n    @Override\n    public void printStackTrace() {\n        super.printStackTrace();\n"
                + "        getCause().printStackTrace();\n    }\n}\n";
        assertEquals(List.of("줄 6: printStackTrace 수신자가 이름 하나가 아니다 — 손으로", "줄 7: printStackTrace 수신자가 이름 하나가 아니다 — 손으로"),
                skipsOf(c.clean(new Source("log/E.java", sup, null, "LF", "java")), Cleanup.LOG));
        // 5-93d ⑧ — 문자열 속 글 · 호출이 앞 줄에서 시작하는 꼴은 그 사유
        String odd = "package log;\n\nclass S {\n    String s = \".printStackTrace()\";\n\n    void a(Exception e) {\n        e\n            .printStackTrace();\n"
                + "    }\n}\n";
        assertEquals(List.of("줄 4: printStackTrace 호출이 아니다(문자열 속 글)", "줄 8: 여러 줄 식"),
                skipsOf(c.clean(new Source("log/S.java", odd, null, "LF", "java")), Cleanup.LOG));
        // R-27 — zheng BaseServiceImpl:30 · ServiceImpl 의 catch 줄 java.swallow 가 로거를 얻어 사라지는 것은 의도(catch 머리 줄도 고친 줄)
        String svc = "package log;\n\nimport org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;\n\nclass FooServiceImpl {\n"
                + "    private static final Logger logger = LoggerFactory.getLogger(FooServiceImpl.class);\n\n    void a() {\n        try {\n            a();\n"
                + "        } catch (RuntimeException e) {\n            e.printStackTrace();\n        }\n    }\n}\n";
        Cleanup.Result sr = c.clean(new Source("log/FooServiceImpl.java", svc, null, "LF", "java"));
        assertEquals(List.of(), skipsOf(sr, Cleanup.LOG));
        assertEquals(svc.replace("e.printStackTrace();", "logger.error(e.getMessage(), e);"), sr.text());
    }

    /** 설계 64 §3-10 — 별표 import 를 쓰는 형식의 단일 import 로(JDK·자기 소스만). 안 쓰는 별표는 삭제 · 정적 별표·못 푸는 패키지·두 별표에 겹친 이름은 건너뜀 */
    @Test
    void starImportStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("starimport", "Pos.java", data);
        assertTrue(r.text().startsWith("package cleanup.starimport;\n\nimport java.util.Collections;\nimport java.util.List;\nimport java.util.Map;\n"
                + "import static java.util.Collections.*;\n"), r.text());
        assertEquals(List.of("줄 5: 정적 별표 import — 손으로"), skipsOf(r, Cleanup.IMPORTS));
        assertEquals(List.of("java.starImport 3→1"), r.steps().stream().filter(s -> s.stage().equals(Cleanup.IMPORTS))
                .map(s -> s.rule() + " " + s.before() + "→" + s.after()).toList());
        Cleanup.Result third = clean("starimport", "Third.java", data);
        assertTrue(third.text().contains("import java.util.*;\nimport javax.servlet.*;\n"), third.text());
        assertEquals(List.of("줄 4: 별표 패키지를 못 푼다(JDK·소스 루트 밖): javax.servlet"), skipsOf(third, Cleanup.IMPORTS));
        assertFalse(clean("javaimport", "Pos.java", data).text().contains("import java.util.*;"), "안 쓰는 별표는 지운다");

        Cleanup own = new Cleanup(RuleSet.load(profile(null), null), Cleanup.RULES, "LF", rel -> java.util.Optional.empty(),
                (rel, pkg) -> pkg.equals("a.b") ? java.util.Optional.of(java.util.Set.of("Foo"))
                        : pkg.equals("p") ? java.util.Optional.of(java.util.Set.of("B")) : java.util.Optional.empty());
        String src = "package p;\n\nimport a.b.*;\n\nclass A {\n    Foo f;\n    String s;\n}\n";
        assertEquals(src.replace("import a.b.*;", "import a.b.Foo;"), own.clean(new Source("p/A.java", src, null, "LF", "java")).text(),
                "자기 소스 패키지 — 그 폴더의 형식만(String 은 java.lang)");
        // 마무리 리뷰 C1 — egov-msa EgovBbsMasterServiceImpl:5·31 · 별표 폴더에 없는 생성 클래스(QFoo)·출처 모를 형식(Bar)을 쓰면 풀면 import 가 사라진다 → 파일 건너뜀
        String gen = "package p;\n\nimport a.b.*;\n\nclass A {\n    Foo f;\n    Bar g;\n    Object q = QFoo.foo;\n}\n";
        Cleanup.Result genR = own.clean(new Source("p/A.java", gen, null, "LF", "java"));
        assertEquals(gen, genR.text());
        assertEquals(List.of("줄 3: 출처를 모르는 형식 이름 — 손으로: Bar"), skipsOf(genR, Cleanup.IMPORTS));
        String two = "package p;\n\nimport java.awt.*;\nimport java.util.*;\n\nclass A {\n    List<String> a;\n}\n";
        Cleanup.Result amb = own.clean(new Source("p/A.java", two, null, "LF", "java"));
        assertEquals(two, amb.text());
        assertEquals(List.of("줄 4: 이름이 두 별표 패키지에 — 손으로: List"), skipsOf(amb, Cleanup.IMPORTS));

        // 5-93b ⑤ — 줄 주석 낱말은 쓰인 이름이 아니다 · javadoc 참조 태그 이름은 쓰인 이름이다
        String note = "package cleanup.t;\n\nimport java.util.*;\n\nclass A {\n    // Set 은 안 쓴다\n    List<String> a;\n}\n";
        assertEquals(note.replace("import java.util.*;", "import java.util.List;"), cleanup(null).clean(new Source("t/A.java", note, null, "LF", "java")).text());
        String doc = "package cleanup.t;\n\nimport java.util.*;\n\n/** {@link Set} 을 본다 */\nclass A {\n    List<String> a;\n}\n";
        assertEquals(doc.replace("import java.util.*;", "import java.util.List;\nimport java.util.Set;"),
                cleanup(null).clean(new Source("t/A.java", doc, null, "LF", "java")).text());
        // 5-93b — 자기 패키지 형식 목록을 모르면 같은 패키지 형식이 별표 형식에 가려질 수 있다 → 건너뜀
        String blind = "package p;\n\nimport java.util.*;\n\nclass A {\n    List<String> a;\n}\n";
        Cleanup.Result br = new Cleanup(RuleSet.load(profile(null), null), Cleanup.RULES, "LF").clean(new Source("p/A.java", blind, null, "LF", "java"));
        assertEquals(blind, br.text());
        assertEquals(List.of("줄 3: " + StarImportFix.OWN_UNKNOWN), skipsOf(br, Cleanup.IMPORTS));
        // 5-93b — 밑줄 든 대문자 수신자(물려받은 상수)는 형식 자리가 아니다(jspwiki PROPERTY_APPROVER_PREFIX)
        String cons = "package p;\n\nimport java.util.*;\n\nclass A extends B {\n    int n = LIMIT_MAX.length();\n    List<String> a;\n}\n";
        assertEquals(cons.replace("import java.util.*;", "import java.util.List;"), own.clean(new Source("p/A.java", cons, null, "LF", "java")).text());
        // 5-99b ⑤ — 물려받을 길(상위 형식·익명 클래스·static import)이 없으면 선언 안 된 밑줄 대문자 수신자는 형식이다 → 출처 모름
        String stray = "package p;\n\nimport a.b.*;\n\nclass A {\n    Foo f;\n    int n = GEN_CODES.size();\n}\n";
        Cleanup.Result sr = own.clean(new Source("p/A.java", stray, null, "LF", "java"));
        assertEquals(stray, sr.text());
        assertEquals(List.of("줄 3: " + StarImportFix.STRAY + "GEN_CODES"), skipsOf(sr, Cleanup.IMPORTS));
        // 5-99b ③ — javadoc 괄호 형식의 인자도 쓰인 이름(정식 이름 인자는 안 센다) · 여러 줄
        String javaUtil = "import java.util.*;";
        String listSet = "import java.util.List;\nimport java.util.Set;";
        String args = "package cleanup.t;\n\nimport java.util.*;\n\n/** {@link A#f(Set)} */\nclass A {\n    List<String> a;\n\n    void f(Object s) {\n    }\n}\n";
        assertEquals(args.replace(javaUtil, listSet), cleanup(null).clean(new Source("t/A.java", args, null, "LF", "java")).text());
        String fq = args.replace("A#f(Set)", "A#f(java.util.Set)");
        assertEquals(fq.replace(javaUtil, "import java.util.List;"), cleanup(null).clean(new Source("t/A.java", fq, null, "LF", "java")).text());
        String varargs = args.replace("A#f(Set)", "A#f(Set...)");
        assertEquals(varargs.replace(javaUtil, listSet), cleanup(null).clean(new Source("t/A.java", varargs, null, "LF", "java")).text(), "가변 인자");
        String multi = args.replace("/** {@link A#f(Set)} */", "/**\n * {@link A#f(List,\n * Set)}\n */");
        assertEquals(multi.replace(javaUtil, listSet), cleanup(null).clean(new Source("t/A.java", multi, null, "LF", "java")).text());
        // 5-99b ⑦ — 정식 이름으로만 쓴 형식은 쓰인 이름이 아니다(jeecg CgformEnhanceJsEntity)
        String ser = "package cleanup.t;\n\nimport java.io.*;\n\nclass A implements java.io.Serializable {\n}\n";
        String serOut = cleanup(null).clean(new Source("t/A.java", ser, null, "LF", "java")).text();
        assertFalse(serOut.contains("import "), serOut);
        assertTrue(serOut.contains("class A implements java.io.Serializable {"), serOut);
        String serFile = ser.replace("{\n}", "{\n    File f;\n}");
        assertEquals(serFile.replace("import java.io.*;", "import java.io.File;"),
                cleanup(null).clean(new Source("t/A.java", serFile, null, "LF", "java")).text());
        String ann = "package cleanup.t;\n\nimport java.util.*;\n\n@java.lang.Deprecated\nclass A {\n    List<String> a;\n}\n";
        assertEquals(ann.replace(javaUtil, "import java.util.List;"), cleanup(null).clean(new Source("t/A.java", ann, null, "LF", "java")).text());
        // 5-93b ④ — 패키지 폴더 목록이 잘리면 모른다
        java.nio.file.Path pk = data.resolve("proj/src/main/java/q");
        java.nio.file.Files.createDirectories(pk);
        java.nio.file.Files.writeString(pk.resolve("A.java"), "package q;\n\nclass A {\n}\n");
        java.nio.file.Files.writeString(pk.resolve("B.java"), "package q;\n\nclass B {\n}\n");
        kr.ejg.toolbox.core.fs.LocalFiles files = new kr.ejg.toolbox.core.fs.LocalFiles(data.resolve("d"));
        assertEquals(java.util.Optional.of(Set.of("A", "B")), PackageTypes.scan(files, data.resolve("proj").toString(), "src/main/java/q", new JavaSource(), 2));
        assertEquals(java.util.Optional.empty(), PackageTypes.scan(files, data.resolve("proj").toString(), "src/main/java/q", new JavaSource(), 1));
    }

    /** 5-99d ⑧ — PiiFix.FREE(맨 숫자도 가리는 글)는 규칙 globs 안 · globs 의 나머지 언어는 따옴표 밖 맨 숫자를 손으로 넘긴다 */
    @Test
    void piiFreeLangsAreInGlobs() {
        List<String> exts = RuleSet.builtin().defs().stream().filter(d -> LuhnRule.CARD_PLAIN.equals(d.id())).findFirst().orElseThrow().globs().stream()
                .map(g -> g.substring(g.lastIndexOf('.') + 1)).toList();
        assertTrue(exts.containsAll(PiiFix.FREE), exts + " ⊇ " + PiiFix.FREE);
        String pan = "4030000010001234";
        for (String ext : exts) {
            if (PiiFix.FREE.contains(ext)) {
                continue;
            }
            String text = "java".equals(ext) ? "class A {\n    Object n = " + pan + ";\n}\n" : "n = " + pan + ";\n";
            Cleanup.Result r = cleanup(null).clean(new Source("a." + ext, text, null, "LF", null));
            assertEquals(text, r.text(), ext);
            assertTrue(skipsOf(r, Cleanup.PII).stream().allMatch(k -> k.endsWith(PiiFix.UNQUOTED)) && !skipsOf(r, Cleanup.PII).isEmpty(), ext + " " + r.skips());
        }
    }

    /** 번들 81 마무리 리뷰 — I1 따옴표 밖 16 자리는 안 가린다 · I2 시험 루트 파일은 짝 main 루트의 같은 패키지 형식도 자기 형식 · I3 안쪽 루프·switch 의 break 는 바깥 루프 탈출이 아니다 */
    @Test
    void finalReview81(@TempDir Path data) throws Exception {
        String js = "const pan = 4030000010001234;\nconst s = \"4030000010001234\";\nconst q = n/4030000010001234;\n";
        Cleanup.Result jr = cleanup(null).clean(new Source("a.js", js, null, "LF", "js"));
        assertEquals("const pan = 4030000010001234;\nconst s = \"************1234\";\nconst q = n/4030000010001234;\n", jr.text());
        assertEquals(List.of("줄 1: " + PiiFix.UNQUOTED, "줄 3: " + PiiFix.UNQUOTED), skipsOf(jr, Cleanup.PII));
        String sql = "INSERT INTO t VALUES (4030000010001234, 'a');\nINSERT INTO t VALUES (1, '4030000010001234');\n"
                + "INSERT INTO t VALUES (4030000010001234, '4030000010001234');\n";
        Cleanup.Result sr = cleanup(null).clean(new Source("a.sql", sql, null, "LF", "sql"));
        assertEquals(sql.replace("(1, '4030000010001234')", "(1, '************1234')"), sr.text(), "맨 숫자가 섞인 줄은 통째로 둔다");
        assertEquals(List.of("줄 1: " + PiiFix.UNQUOTED, "줄 3: " + PiiFix.UNQUOTED), skipsOf(sr, Cleanup.PII));
        // 5-99d ⑧ — 따옴표 밖: html·jsp·ts 는 손으로, xml 은 값이라 가린다 · ts 따옴표 안은 가린다
        String pan = "4030000010001234";
        for (String[] c : new String[][] {{"a.html", "<td>" + pan + "</td>\n"}, {"a.jsp", "<td>" + pan + "</td>\n"}, {"a.ts", "const n = " + pan + ";\n"}}) {
            Cleanup.Result u = cleanup(null).clean(new Source(c[0], c[1], null, "LF", null));
            assertEquals(c[1], u.text(), c[0]);
            assertEquals(List.of("줄 1: " + PiiFix.UNQUOTED), skipsOf(u, Cleanup.PII), c[0]);
        }
        assertEquals("<pan>************1234</pan>\n", cleanup(null).clean(new Source("a.xml", "<pan>" + pan + "</pan>\n", null, "LF", null)).text());
        assertEquals("const s = '************1234';\n", cleanup(null).clean(new Source("a.ts", "const s = '" + pan + "';\n", null, "LF", null)).text());

        java.nio.file.Path main = data.resolve("proj/src/main/java/app");
        java.nio.file.Path test = data.resolve("proj/src/test/java/app");
        java.nio.file.Files.createDirectories(main);
        java.nio.file.Files.createDirectories(test);
        java.nio.file.Files.writeString(main.resolve("Stack.java"), "package app;\n\nclass Stack {\n}\n");
        java.nio.file.Files.writeString(test.resolve("StackTest.java"), "package app;\n\nclass StackTest {\n}\n");
        kr.ejg.toolbox.core.fs.LocalFiles files = new kr.ejg.toolbox.core.fs.LocalFiles(data.resolve("d"));
        java.util.function.BiFunction<String, String, java.util.Optional<Set<String>>> find = PackageTypes.finder(files, data.resolve("proj").toString());
        assertEquals(java.util.Optional.of(Set.of("Stack", "StackTest")), find.apply("src/test/java/app/StackTest.java", "app"));
        assertEquals(java.util.Optional.of(Set.of("Stack")), find.apply("src/main/java/app/Stack.java", "app"), "main 은 시험 형식을 못 본다");

        String loop = "class A {\n    void a(java.util.List<Object> ls) {\n        while (run) {\n            try {\n                Thread.sleep(10);\n"
                + "            } catch (InterruptedException e) {\n                for (Object l : ls) {\n                    if (l == null) {\n                        break;\n"
                + "                    }\n                }\n                switch (ls.size()) {\n                    case 0:\n                        break;\n"
                + "                    default:\n                }\n            }\n        }\n    }\n}\n";
        Edits el = new Edits(loop);
        List<Cleanup.Skip> sl = new java.util.ArrayList<>();
        InterruptFix.apply(loop, List.of(new Finding("A.java", 6, "java", JavaShape.INTERRUPTED, "warn", "")), new JavaSource(), el, sl);
        assertTrue(el.isEmpty(), "안쪽 for·switch 의 break 는 바깥 while 을 안 벗어난다");
        assertEquals(List.of("줄 6: " + InterruptFix.IN_LOOP), sl.stream().map(Cleanup.Skip::reason).toList());
    }

    /** 마무리 리뷰 I1·I2·I3 — 지적(cur) 줄만 고친다 · default 안 지역 선언은 안 옮긴다 · 머리와 몸 사이 주석은 건너뜀 */
    @Test
    void reviewShapeFixes(@TempDir Path data) throws Exception {
        String braces = "package p;\n\nclass A {\n    void f(int x) {\n        if (x > 0)\n            x--;\n    }\n}\n";
        String sw = "package p;\n\nclass A {\n    int f(int k) {\n        switch (k) {\n            default:\n                return 0;\n            case 1:\n"
                + "                return 1;\n        }\n    }\n}\n";
        String in = "package p;\n\nclass A {\n    void f() {\n        try {\n            Thread.sleep(1);\n        } catch (InterruptedException e) {\n"
                + "            f();\n        }\n    }\n}\n";
        JavaSource parser = new JavaSource();
        for (String t : List.of(braces, sw, in)) { // I1 — 지적이 없으면(tb-ignore 로 가린 줄 꼴) 안 고친다
            Edits e = new Edits(t);
            List<Cleanup.Skip> skips = new java.util.ArrayList<>();
            SyntaxFix.apply(t, List.of(), java.util.Set.of(JavaShape.NEED_BRACES), parser, e, skips);
            SwitchFix.apply(t, List.of(), parser, e, skips);
            InterruptFix.apply(t, List.of(), parser, e, skips);
            assertTrue(e.isEmpty() && skips.isEmpty(), t);
        }
        // I2 — 옛 꼴 switch 는 블록 하나: default 의 선언을 뒤 case 가 쓰면 옮기면 컴파일이 깨진다
        String decl = "package p;\n\nclass A {\n    int f(int k) {\n        switch (k) {\n            default:\n                int y = 0;\n                return y;\n"
                + "            case 1:\n                y = 2;\n                return y;\n        }\n    }\n}\n";
        Cleanup.Result d = cleanup(null).clean(new Source("p/A.java", decl, null, "LF", "java"));
        assertEquals(decl, d.text());
        assertEquals(List.of("줄 6: default 안 지역 선언 — 범위가 바뀐다"), skipsOf(d, Cleanup.SWITCH));
        // I3 — 머리와 몸 사이 주석 줄 · 머리 줄 끝 주석은 머리 줄 꼴이 아니다(되돌림 대신 건너뜀)
        String cm = "package p;\n\nclass A {\n    void f(int x) {\n        if (x == 0)\n            // nothing to do\n            return;\n"
                + "        if (x == 1) // see (1)\n            return;\n    }\n}\n";
        Cleanup.Result c = cleanup(null).clean(new Source("p/A.java", cm, null, "LF", "java"));
        assertEquals(cm, c.text());
        assertEquals(List.of("줄 6: 머리 줄 끝에 다른 글", "줄 8: 머리 줄 끝에 다른 글"), skipsOf(c, Cleanup.SYNTAX)); // 머리 = 몸 앞 마지막 글자의 줄(주석 줄)
    }

    static List<String> skipsOf(Cleanup.Result r, String stage) {
        return r.skips().stream().filter(k -> k.stage().equals(stage)).map(Cleanup.Skip::reason).toList();
    }

    /** 설계 60 — System.out/err → 있는 로거의 debug·error. 인자가 String 이 아니면 String.valueOf. main·catch·초기화·여러 줄 등은 건너뜀 */
    @Test
    void logStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("log", "Pos.java", data);
        String t = r.text();
        assertTrue(t.contains("        logger.debug(\"a\");\n        logger.error(String.valueOf(n));\n        logger.debug(\"x\" + n);\n"), t);
        assertEquals(List.of("줄 11: 초기화 블록·필드 초기화식 안", "줄 18: 인자 없는 출력", "줄 19: print·println 호출이 아니다", "줄 23: 여러 줄 식",
                "줄 28: catch 안 출력 — 로그·던지기 판정이 바뀌어 손으로", "줄 33: main 의 콘솔 출력"), skipsOf(r, Cleanup.LOG));
        Cleanup.Result in = clean("log", "Instance.java", data);
        assertTrue(in.text().contains("        logger.debug(\"a\");\n") && in.text().contains("System.out.println(\"b\");"), in.text());
        assertEquals(List.of("줄 15: static 문맥 — 인스턴스 로거를 못 쓴다"), skipsOf(in, Cleanup.LOG));
        assertEquals(List.of("줄 15: 로거가 없고 같은 소스 루트에 베낄 로거 꼴도 없다"), skipsOf(clean("log", "NoLogger.java", data), Cleanup.LOG));
        assertEquals(List.of("줄 10: java.util.logging 로거 — debug·error 가 없다"), skipsOf(clean("log", "Jul.java", data), Cleanup.LOG));
        assertEquals(List.of("줄 11: 로거 이름과 같은 지역 변수·매개변수가 있다"), skipsOf(clean("log", "Shadow.java", data), Cleanup.LOG));
    }

    /** 설계 60 — 로거가 없으면 베낀 꼴을 마지막 static 필드 뒤에 끼우고 import 를 더한다(선언 순서 지적 없음 · 컴파일된다) */
    @Test
    void loggerInserted(@TempDir Path data) throws Exception {
        LoggerShape sh = new LoggerShape(List.of("org.slf4j.Logger", "org.slf4j.LoggerFactory"), "logger",
                "private static final Logger logger = LoggerFactory.getLogger({C}.class);");
        Cleanup c = new Cleanup(RuleSet.load(profile(null), null), Cleanup.RULES, "LF", rel -> java.util.Optional.of(sh));
        Source src = source("log", "NoLogger.java", data);
        Cleanup.Result r = c.clean(src);
        GoldenFiles.assertText("cleanup/log-NoLogger.inserted.java.txt", r.text());
        assertTrue(r.text().contains("    protected static int B = 1;\n    private static final Logger logger = LoggerFactory.getLogger(NoLogger.class);\n"), r.text());
        assertTrue(r.text().contains("package log;\nimport org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;\n"), r.text());
        assertTrue(r.text().contains("logger.debug(A + c);"), r.text());
        assertEquals(List.of(), r.after().stream().filter(x -> x.rule().equals("java.declarationOrder")).toList());
        String cp = System.getProperty("java.class.path");
        for (String name : List.of("Pos.java", "Instance.java")) {
            Source s0 = source("log", name, data);
            assertEquals(List.of(), compile(s0.text(), data.resolve(name + "-before"), cp), name + " 원문");
            assertEquals(List.of(), compile(c.clean(s0).text(), data.resolve(name + "-after"), cp), name + " 고친 글");
        }
        assertEquals(List.of(), compile(r.text(), data.resolve("nologger-after"), cp), "끼운 로거가 컴파일된다");
    }

    /** 설계 60 — 마크업: 줄 머리 <html> 에 lang · target=_blank 인 <a> 에 rel. script 안·줄 머리 아님·여러 줄 태그는 건너뜀 */
    @Test
    void markupStage(@TempDir Path data) throws Exception {
        Cleanup.Result jsp = clean("markup", "pos.jsp", data);
        assertTrue(jsp.text().contains("<!DOCTYPE html>\n<html lang=\"ko\">\n<head>"), jsp.text());
        assertEquals(List.of("줄 8: script 안 <html>", "줄 13: 줄 머리가 <html> 이 아니다"), skipsOf(jsp, Cleanup.MARKUP_STAGE));
        assertTrue(clean("markup", "attrs.html", data).text().contains("<html lang=\"ko\" xmlns=\"http://www.w3.org/1999/xhtml\">"));
        Cleanup.Result struts = clean("markup", "struts.jsp", data);
        assertEquals(List.of("줄 2: 줄 머리가 <html> 이 아니다"), skipsOf(struts, Cleanup.MARKUP_STAGE));
        Cleanup.Result tsx = clean("markup", "pos.tsx", data);
        String t = tsx.text();
        String rel = " rel=\"noopener noreferrer\"";
        assertTrue(t.contains("<a href=\"/o\" target=\"_blank\"" + rel + ">") && t.contains("target={'_blank'}" + rel + ">")
                && t.contains("<a href=\"/r\" target=\"_blank\"" + rel + ">하나</a> <a href=\"/s\" target=\"_blank\"" + rel + ">둘</a>")
                && t.contains("target=\"_blank\" rel=\"noreferrer\">있음"), t);
        assertEquals(List.of("줄 8: 여러 줄 태그"), skipsOf(tsx, Cleanup.MARKUP_STAGE));
    }

    /** 번들 76 마무리 리뷰 — 첫 멤버가 class 줄에 있으면 끼울 자리가 없다 · System.Logger 에는 debug·error 가 없다(둘 다 건너뜀, 글 불변) */
    @Test
    void logSlotAndSystemLogger() {
        LoggerShape sh = new LoggerShape(List.of("org.slf4j.Logger", "org.slf4j.LoggerFactory"), "logger",
                "private static final Logger logger = LoggerFactory.getLogger({C}.class);");
        Cleanup c = new Cleanup(RuleSet.load(profile(null), null), Cleanup.RULES, "LF", rel -> java.util.Optional.of(sh));
        String one = "package q;\n\nclass Q { void f() { System.out.println(\"a\"); } }\n";
        Cleanup.Result r = c.clean(new Source("Q.java", one, "UTF-8", "LF", "java"));
        assertEquals(one, r.text());
        assertEquals(List.of("줄 3: 로거를 끼울 자리의 줄 꼴이 다르다"), skipsOf(r, Cleanup.LOG));
        String sys = "package q;\n\nclass S {\n    private static final System.Logger LOG = System.getLogger(\"s\");\n\n    void f() {\n"
                + "        System.out.println(\"a\");\n    }\n}\n";
        Cleanup.Result t = c.clean(new Source("S.java", sys, "UTF-8", "LF", "java"));
        assertEquals(sys, t.text());
        assertEquals(List.of("줄 7: System.Logger — debug·error 가 없다"), skipsOf(t, Cleanup.LOG));
    }

    /** 설계 62 — a11y.thScope 고침: thead·첫 행 col · 상세 표 row · 중첩 표 · 대문자 TH · 여러 줄 th · 같은 줄의 rel 과 scope · 섞인 표·가운데 th·이웃 th(5-81)·조각은 건너뜀 */
    @Test
    void thScopeStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("markup", "th.jsp", data);
        String t = r.text();
        assertTrue(t.contains("<thead><tr><th scope=\"col\">이름</th><th scope=\"col\"\n  class=\"w\">값</th>")
                && t.contains("<tr><th scope=\"col\">a</th><th scope=\"col\">b</th></tr>\n<tr><td>1</td><td>2</td></tr>\n</table>\n<table><caption>상세")
                && t.contains("<tr><th scope=\"row\">제목</th><td>글</td><th scope=\"row\">작성자</th><td>홍길동</td></tr>")
                && t.contains("<tr><th scope=\"row\"><a href=\"/x.do\" target=\"_blank\" rel=\"noopener noreferrer\">링크</a></th>")
                && t.contains("<tr><TH scope=\"col\">안쪽</TH><th scope=\"col\">둘</th></tr>"), t);
        assertTrue(t.contains("<table><caption>섞임</caption>\n<tr><th>a</th><th>b</th></tr>") && t.contains("<tr><td>1</td><th>h</th></tr>")
                && t.contains("\n<th>조각</th>\n") && t.contains("<script>var t = \"<table><tr><th>글</th></tr></table>\";</script>")
                && t.contains("<%-- <table><tr><th>주석</th></tr></table> --%>")
                && t.contains("<table><caption>이웃</caption>\n<tr><th>a</th><th>b</th><td>1</td></tr>"), "그대로 — " + t);
        assertEquals(List.of(new Cleanup.Step(Cleanup.MARKUP_STAGE, "a11y.thScope", 1, 0), new Cleanup.Step(Cleanup.MARKUP_STAGE, "jsp.blankNoopener", 1, 0)),
                r.steps().stream().filter(k -> k.stage().equals(Cleanup.MARKUP_STAGE)).toList());
        assertEquals(List.of("줄 18: " + ThScopeFix.NOT_FIRST, "줄 23: " + ThScopeFix.MID_ROW, "줄 26: " + ThScopeFix.TH_PAIR, "줄 29: " + ThScopeFix.OUTSIDE),
                skipsOf(r, Cleanup.MARKUP_STAGE));
    }

    /** 5-80 — MS949 로 읽혔지만 GBK·EUC-JP·Latin-1 일 수 있는 글은 거절: 한자 · KS X 1001 밖 한글(UHC 확장) · 사용자 정의 영역. 픽스처 셋은 U+FFFD 없이 MS949 로 읽힌다(기존 가드가 못 잡는 꼴). 한자 주석의 진짜 한국어 파일도 거절(사용자 2026-11-11 — 받아들인 비용) */
    @Test
    void encodingSuspect(@TempDir Path data) throws Exception {
        for (String name : List.of("Gbk.java", "EucJp.java", "Latin1.java")) {
            Source s = source("encoding", name, data);
            assertEquals("MS949", s.encoding(), name);
            assertTrue(s.text().indexOf('�') < 0, name + " — U+FFFD 가 없어야 5-80 이 잡는 꼴(바이트가 틀렸다)");
            Cleanup.Result r = clean("encoding", name, data);
            assertEquals("MS949", r.encoding(), name);
            assertEquals(List.of(EncodingFix.MISREAD), skipsOf(r, Cleanup.ENCODING), name);
        }
        assertFalse(EncodingFix.suspect("가나다 ①㈜〔 ascii"), "KS X 1001 한글·기호");
        assertTrue(EncodingFix.suspect("똠방각하"), "UHC 확장 음절");
        assertTrue(EncodingFix.suspect("漢字 주석"), "한자 — 받아들인 비용");
        assertTrue(EncodingFix.suspect(""), "사용자 정의 영역");
        Cleanup.Result utf = cleanup(null).clean(new Source("U.java", "package q;\n\n/** 漢字 */\nclass U {\n}\n", "UTF-8", "LF", null));
        assertTrue(skipsOf(utf, Cleanup.ENCODING).isEmpty(), "UTF-8 로 읽힌 글은 오판 판정 밖");
        Cleanup.Result bom = cleanup(null).clean(new Source("B.java", "package q;\n\n/** 漢字 */\nclass B {\n}\n", "UTF-8-BOM", "LF", null));
        assertEquals("UTF-8", bom.encoding(), "BOM 도 오판 판정 밖");
    }

    /** 설계 62 — 인코딩 고침: BOM·MS949 → 프로필 UTF-8(글 불변) · 거절 넷(쓸 수 없는 프로필 인코딩 · U+FFFD · MS949 오판 5-80 · MS949 로 못 적는 글자) · EUC-KR 프로필은 MS949 로 */
    @Test
    void encodingStage(@TempDir Path data) throws Exception {
        for (String name : List.of("Bom.java", "Ms949.java")) {
            Source s = source("encoding", name, data);
            Cleanup.Result r = clean("encoding", name, data);
            assertEquals(name.startsWith("Bom") ? "UTF-8-BOM" : "MS949", s.encoding(), name);
            assertEquals("UTF-8", r.encoding(), name);
            assertTrue(r.changed() && r.text().equals(s.text()), name + " — 글은 그대로");
            assertEquals(List.of(new Cleanup.Step(Cleanup.ENCODING, "file.encoding", 1, 0)),
                    r.steps().stream().filter(k -> k.stage().equals(Cleanup.ENCODING)).toList(), name);
        }
        String ko = "package q;\n\n/** 가 */\nclass K {\n}\n";
        Cleanup.Result jp = cleanupIn("EUC-JP").clean(new Source("K.java", ko, "UTF-8", "LF", null));
        assertEquals("UTF-8", jp.encoding());
        assertEquals(List.of("프로필 project.encoding EUC-JP 로는 못 적는다(UTF-8·UTF-8-BOM·MS949)"), skipsOf(jp, Cleanup.ENCODING));
        Cleanup.Result bad = cleanup(null).clean(new Source("B.java", "package q;\n\n/** 가� */\nclass B {\n}\n", "MS949", "LF", null));
        assertEquals("MS949", bad.encoding());
        assertEquals(List.of("MS949 로 읽은 글에 깨진 글자(U+FFFD) — 인코딩 판별을 못 믿는다"), skipsOf(bad, Cleanup.ENCODING));
        Cleanup.Result emoji = cleanupIn("MS949").clean(new Source("E.java", "package q;\n\n/** 😀 */\nclass E {\n}\n", "UTF-8", "LF", null));
        assertEquals("UTF-8", emoji.encoding());
        assertEquals(List.of("MS949 로 못 적는 글자가 있다"), skipsOf(emoji, Cleanup.ENCODING));
        Cleanup.Result toMs = cleanupIn("EUC-KR").clean(new Source("U.java", ko, "UTF-8", "LF", null));
        assertEquals("MS949", toMs.encoding());
        assertTrue(toMs.changed());
        Cleanup.Result same = cleanupIn("EUC-KR").clean(new Source("S.java", ko, "MS949", "LF", null));
        assertFalse(same.changed(), "EUC-KR 과 MS949 는 같은 기준 — 지적 없음");
    }

    /** 번들 78 리뷰 C1 — 파일 안 인코딩 선언(pageEncoding·charset·xml encoding·@charset)이 목표와 다르면 · 선언 없는 BOM JSP 는 거절. java·js 의 charset 글은 선언이 아니다 */
    @Test
    void encodingDeclaration() {
        String declared = EncodingFix.DECLARED;
        Cleanup utf8 = cleanupIn("UTF-8");
        String jsp = "<%@ page contentType=\"text/html; charset=euc-kr\" %>\n<p>가</p>\n";
        Cleanup.Result ms = utf8.clean(new Source("a.jsp", jsp, "MS949", "LF", null));
        assertEquals("MS949", ms.encoding());
        assertEquals(List.of(declared + "EUC-KR"), skipsOf(ms, Cleanup.ENCODING));
        String xml = "<?xml version=\"1.0\" encoding=\"EUC-KR\"?>\n<a>가</a>\n";
        assertEquals(List.of(declared + "EUC-KR"), skipsOf(utf8.clean(new Source("a.xml", xml, "MS949", "LF", null)), Cleanup.ENCODING));
        String css = "@charset \"euc-kr\";\n/* 가 */\n";
        assertEquals(List.of(declared + "EUC-KR"), skipsOf(utf8.clean(new Source("a.css", css, "MS949", "LF", null)), Cleanup.ENCODING));
        String utfJsp = "<%@ page pageEncoding=\"UTF-8\" %>\n<p>가</p>\n";
        Cleanup.Result toMs = cleanupIn("EUC-KR").clean(new Source("u.jsp", utfJsp, "UTF-8", "LF", null));
        assertEquals("UTF-8", toMs.encoding());
        assertEquals(List.of(declared + "UTF-8"), skipsOf(toMs, Cleanup.ENCODING));
        Cleanup.Result bomBare = utf8.clean(new Source("b.jsp", "<p>가</p>\n", "UTF-8-BOM", "LF", null));
        assertEquals("UTF-8-BOM", bomBare.encoding());
        assertEquals(List.of(EncodingFix.BOM_ONLY), skipsOf(bomBare, Cleanup.ENCODING));
        Cleanup.Result bomDeclared = utf8.clean(new Source("c.jsp", utfJsp, "UTF-8-BOM", "LF", null));
        assertEquals("UTF-8", bomDeclared.encoding(), "선언이 UTF-8 이면 BOM 을 떼도 된다");
        assertTrue(skipsOf(bomDeclared, Cleanup.ENCODING).isEmpty());
        String java = "package q;\n\n/** 가 */\nclass J {\n    String t = \"text/html; charset=euc-kr\";\n}\n";
        Cleanup.Result j = utf8.clean(new Source("J.java", java, "MS949", "LF", null));
        assertEquals("UTF-8", j.encoding(), "java 의 charset 글은 응답 머리 — 파일 선언이 아니다");
        Cleanup.Result html = utf8.clean(new Source("h.html", "<meta charset=\"utf-8\">\n<p>가</p>\n", "MS949", "LF", null));
        assertEquals("UTF-8", html.encoding(), "선언이 이미 목표면 바이트를 선언에 맞춘다");
        // 5-145(E-16) — 남의 charset= 속성(script src · accept-charset)은 이 파일의 선언이 아니다
        String f1 = "<script src=\"/js/old.js\" charset=\"euc-kr\"></script>\n<form accept-charset=\"euc-kr\">\n<p>가</p>\n</form>\n";
        assertEquals("UTF-8", utf8.clean(new Source("f1.jsp", f1, "MS949", "LF", null)).encoding(), "f1.jsp 는 변환된다");
        for (String d : List.of("<meta charset=\"euc-kr\">\n<p>가</p>\n", "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=euc-kr\">\n<p>가</p>\n",
                "<% response.setContentType(\"text/html; charset=euc-kr\"); %>\n<p>가</p>\n")) {
            assertEquals(List.of(declared + "EUC-KR"), skipsOf(utf8.clean(new Source("m.jsp", d, "MS949", "LF", null)), Cleanup.ENCODING), d);
        }
    }

    /** 설계 61 — jsp.blankNoopener 고침: target 값 바로 뒤에 rel(값과 같은 따옴표) · 여러 줄 태그는 target 줄 · 한 줄 둘 · 맨 값은 건너뜀 */
    @Test
    void blankRelStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("markup", "blank.jsp", data);
        String t = r.text();
        String rel = " rel=\"noopener noreferrer\"";
        assertTrue(t.contains("target=\"_blank\"" + rel + ">밖") && t.contains("target=\"_blank\"" + rel + " title=\"새 창\">안")
                && t.contains("target='_blank' rel='noopener noreferrer'>값") && t.contains("   target=\"_blank\"" + rel + ">여러 줄")
                && t.contains("target=\"_blank\"" + rel + ">하나</a> <a href=\"/d.do\" target=\"_blank\"" + rel + ">둘"), t);
        assertTrue(t.contains("target=\"_blank\" rel=\"noopener\">있음") && t.contains("target=_blank>맨 값")
                && t.contains("<script>var h = \"<a href='\" + u + \"' target=_blank >x</a>\";</script>")
                && t.contains("<%-- <a href=\"/g.do\" target=\"_blank\">주석</a> --%>"), "그대로 — " + t);
        assertEquals(List.of(new Cleanup.Step(Cleanup.MARKUP_STAGE, "jsp.blankNoopener", 7, 1)),
                r.steps().stream().filter(k -> k.stage().equals(Cleanup.MARKUP_STAGE)).toList());
        assertEquals(List.of("줄 13: 따옴표 없는 target — 손으로"), skipsOf(r, Cleanup.MARKUP_STAGE));
    }

    /** 5-142(E-08·E-14) — 뒤 case 의 지역 이름을 default 가 쓰면 안 옮긴다 · 끝 줄 꼬리 주석은 줄째 따라간다 */
    @Test
    void switchLaterLocalsAndTailComments() {
        String p3 = "class P3 {\n    int n;\n\n    void f(int k) {\n        switch (k) {\n            default:\n                n = 9;\n                break;\n"
                + "            case 1:\n                int n = 1;\n                use(n);\n                break;\n        }\n    }\n\n    void use(int x) {\n    }\n}\n";
        Cleanup.Result r3 = cleanup(null).clean(new Source("t/P3.java", p3, null, "LF", "java"));
        assertEquals(p3, r3.text());
        assertEquals(List.of("줄 6: " + SwitchFix.DECL), skipsOf(r3, Cleanup.SWITCH));
        String pre = "class P4 {\n    int x;\n\n    void f(int k) {\n        switch (k) {\n";
        String dflt = "            default:\n                x = 0;\n                break; // base\n";
        String one = "            case 1:\n                x = 1;\n                break;\n";
        String foot = "        }\n    }\n}\n";
        Cleanup.Result r4 = cleanup(null).clean(new Source("t/P4.java", pre + dflt + one + foot, null, "LF", "java"));
        assertEquals(pre + one + dflt + foot, r4.text(), "꼬리 주석째 옮긴다");
        String arrow = "class P5 {\n    int f(int k) {\n        return switch (k) {\n            default -> 0; // base\n            case 1 -> 1;\n        };\n    }\n}\n";
        assertEquals("class P5 {\n    int f(int k) {\n        return switch (k) {\n            case 1 -> 1;\n            default -> 0; // base\n        };\n    }\n}\n",
                cleanup(null).clean(new Source("t/P5.java", arrow, null, "LF", "java")).text(), "화살표 꼴");
        String block = pre + "            default:\n                x = 0;\n                break; /* base */\n" + one + foot;
        Cleanup.Result rb = cleanup(null).clean(new Source("t/P6.java", block, null, "LF", "java"));
        assertEquals(block, rb.text(), "끝 줄 뒤 블록 주석은 지금처럼 SPLIT");
        assertEquals(List.of("줄 6: " + SwitchFix.SPLIT), skipsOf(rb, Cleanup.SWITCH));
    }

    /** 5-141(E-06) — tsx 의 rel 도 target 값과 같은 따옴표 — 작은따옴표 문자열 안 a 태그가 안 깨진다 */
    @Test
    void tsxRelFollowsTargetQuote() {
        Cleanup c = cleanup(null);
        String b3 = "const h = \"<a href='/x' target='_blank'>open</a>\";\n";
        assertEquals("const h = \"<a href='/x' target='_blank' rel='noopener noreferrer'>open</a>\";\n",
                c.clean(new Source("z/b3.tsx", b3, null, "LF", "tsx")).text());
        String dq = "export const A = () => <a href=\"/x\" target=\"_blank\">open</a>;\n";
        assertTrue(c.clean(new Source("z/b4.tsx", dq, null, "LF", "tsx")).text().contains("target=\"_blank\" rel=\"noopener noreferrer\""));
        String br = "export const A = () => <a href=\"/x\" target={'_blank'}>open</a>;\n";
        assertTrue(c.clean(new Source("z/b5.tsx", br, null, "LF", "tsx")).text().contains("target={'_blank'} rel=\"noopener noreferrer\""));
    }

    /** 5-73a ① — 마크업 고침의 건너뜀 사유: lang 이 이미 있다(직접 부를 때만 닿는다) · target 꼴이 달라 못 고침(여러 줄 태그와 가른다) */
    @Test
    void markupReasons() {
        String html = "<html xml:lang=\"ko\">\n";
        Edits e = new Edits(html);
        List<Cleanup.Skip> skips = new ArrayList<>();
        MarkupFix.apply(new Source("a.html", html, "UTF-8", "LF", "html"),
                List.of(new Finding("a.html", 1, "a11y", MarkupFix.HTML_LANG, "warn", "")), Set.of(MarkupFix.HTML_LANG), e, skips);
        assertTrue(e.isEmpty());
        assertEquals(List.of("줄 1: lang 속성이 이미 있다"), skips.stream().map(Cleanup.Skip::reason).toList());
        String tsx = "<a href=\"/o\" target={blank ? \"_blank\" : \"_self\"}>x</a>\n";
        Edits f = new Edits(tsx);
        List<Cleanup.Skip> other = new ArrayList<>();
        MarkupFix.apply(new Source("a.tsx", tsx, "UTF-8", "LF", "tsx"),
                List.of(new Finding("a.tsx", 1, "tsx", MarkupFix.BLANK, "warn", "")), Set.of(MarkupFix.BLANK), f, other);
        assertTrue(f.isEmpty());
        assertEquals(List.of("줄 1: target 꼴이 달라 못 고침"), other.stream().map(Cleanup.Skip::reason).toList());
    }

    /** 5-73a ②⑤ — 로거를 못 넣는 가지 넷의 사유. 베낄 꼴이 없어도 인터페이스면 그 사유가 먼저(설계 60 §3-9 표 차례) */
    @Test
    void logInsertRefusals() {
        LoggerShape sh = new LoggerShape(List.of("org.slf4j.Logger", "org.slf4j.LoggerFactory"), "logger",
                "private static final Logger logger = LoggerFactory.getLogger({C}.class);");
        Cleanup with = new Cleanup(RuleSet.load(profile(null), null), Cleanup.RULES, "LF", rel -> java.util.Optional.of(sh));
        Cleanup without = new Cleanup(RuleSet.load(profile(null), null), Cleanup.RULES, "LF", rel -> java.util.Optional.empty());
        String notClass = "인터페이스·enum·record — 로거 필드를 안 넣는다";
        String iface = "package q;\n\ninterface I {\n    default void f() {\n        System.out.println(\"a\");\n    }\n}\n";
        assertLogSkip(with, iface, "줄 5: " + notClass);
        assertLogSkip(without, iface, "줄 5: " + notClass);
        assertLogSkip(with, "package q;\n\nclass A extends B {\n    void f() {\n        logger.info(\"x\");\n        System.out.println(\"a\");\n    }\n}\n",
                "줄 6: 물려받은 로거를 쓰는 것으로 보인다 — 손으로");
        assertLogSkip(with, "package q;\n\nimport com.acme.LoggerFactory;\n\nclass C {\n    Object x = LoggerFactory.make();\n\n    void f() {\n        System.out.println(\"a\");\n"
                + "    }\n}\n", "줄 9: 로거 형식이 이 파일의 다른 것과 겹친다");
        assertLogSkip(with, "package q;\n\nclass E {\n    static class In {\n        void f() {\n            System.out.println(\"a\");\n        }\n    }\n}\n",
                "줄 6: 빈 클래스 몸");
    }

    private static void assertLogSkip(Cleanup c, String src, String reason) {
        Cleanup.Result r = c.clean(new Source("X.java", src, "UTF-8", "LF", "java"));
        assertEquals(src, r.text());
        assertEquals(List.of(reason), skipsOf(r, Cleanup.LOG));
    }

    /** 설계 60 — tb-ignore 로 누른 선언은 지적이 없어 안 지운다 */
    @Test
    void localKeepsSuppressed() {
        String src = "package k;\n\nclass K {\n    int f() {\n        // tb-ignore-next java.unusedLocal 자리 맞춤\n        int kept = 0;\n"
                + "        int gone = 0;\n        return 1;\n    }\n}\n";
        Cleanup.Result r = cleanup(null).clean(new Source("K.java", src, "UTF-8", "LF", "java"));
        assertTrue(r.text().contains("int kept = 0;") && !r.text().contains("int gone = 0;"), r.text());
    }

    /** 5-68 — closeQuietly 로 닫는 꼴 A' 는 지적만 — 글 불변, 사유 하나 */
    @Test
    void quietCloseIsSkipped() {
        String src = "package q;\n\nimport java.io.FileInputStream;\nimport java.io.IOException;\nimport java.io.InputStream;\n"
                + "import org.apache.commons.io.IOUtils;\n\nclass Q {\n    void f(String p) throws IOException {\n"
                + "        InputStream is = new FileInputStream(p);\n        is.read();\n        IOUtils.closeQuietly(is);\n    }\n}\n";
        Cleanup.Result r = cleanup(null).clean(new Source("Q.java", src, "UTF-8", "LF", "java"));
        assertEquals(src, r.text());
        assertEquals(List.of("줄 10: closeQuietly 로 닫는다 — close 예외 경로가 바뀌어 손으로"),
                r.skips().stream().filter(k -> k.stage().equals(Cleanup.RESOURCE)).map(Cleanup.Skip::reason).toList());
    }

    /** 5-62c — java.util.Objects import 가 없으면 마지막 import 뒤에 끼우고 Objects.equals 로 고친다(컴파일된다). 다른 Objects import 면 건너뜀 */
    @Test
    void objectsImportInserted(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("value", "Pos2.java", data);
        String t = r.text();
        assertTrue(t.contains("import java.math.BigDecimal;\nimport java.util.Objects;\n") && t.contains("return Objects.equals(a, b);"), t);
        assertEquals(List.of(), r.skips().stream().filter(k -> k.stage().equals(Cleanup.VALUE)).toList());
        assertEquals(List.of(), compile(t, data.resolve("pos2-after")), "끼운 import 가 컴파일된다");
        Cleanup.Result c = cleanup(null).clean(new Source("B.java",
                "package b;\n\nimport com.google.common.base.Objects;\n\nclass B {\n    boolean f(Integer a, Integer b) {\n        return a == b;\n    }\n\n"
                        + "    int h(Object o) {\n        return Objects.hashCode(o);\n    }\n}\n",
                "UTF-8", "LF", "java"));
        assertTrue(c.text().contains("return a == b;"), c.text());
        assertEquals(List.of("줄 7: 이름끼리 비교 — 다른 Objects import 와 충돌"),
                c.skips().stream().filter(k -> k.stage().equals(Cleanup.VALUE)).map(Cleanup.Skip::reason).toList());
        // 마무리 리뷰 — import 없이 쓰는 같은 패키지 Objects 를 끼운 import 가 가린다 · java.util.* 이면 끼우지 않는다
        Cleanup.Result same = cleanup(null).clean(new Source("C.java",
                "package c;\n\nclass C {\n    boolean f(Integer a, Integer b) {\n        return a == b;\n    }\n\n"
                        + "    boolean g(Object o) {\n        return Objects.isEmpty(o);\n    }\n}\n",
                "UTF-8", "LF", "java"));
        assertTrue(same.text().contains("return a == b;") && !same.text().contains("import java.util.Objects"), same.text());
        assertEquals(List.of("줄 5: 이름끼리 비교 — 다른 Objects 사용과 충돌"),
                same.skips().stream().filter(k -> k.stage().equals(Cleanup.VALUE)).map(Cleanup.Skip::reason).toList());
        Cleanup.Result star = cleanup(null).clean(new Source("D.java",
                "package d;\n\nimport java.util.*;\n\nclass D {\n    boolean f(Integer a, Integer b) {\n        return a == b;\n    }\n\n"
                        + "    List<String> l() {\n        return new ArrayList<>();\n    }\n}\n",
                "UTF-8", "LF", "java"));
        // claude[bot] ③ — java.util.* 만으로는 같은 패키지 Objects 가 가린다 → 명시 import 를 끼운다(단일 형식 import 가 이긴다)
        // 설계 64 — 뒤 imports 단계가 별표를 쓰는 형식으로 푼다(Objects 는 단일 import 라 별표 몫에서 빠진다)
        assertTrue(star.text().contains("import java.util.ArrayList;\nimport java.util.List;\nimport java.util.Objects;\n") && star.text().contains("return Objects.equals(a, b);"), star.text());
        Cleanup.Result starUse = cleanup(null).clean(new Source("E.java",
                "package e;\n\nimport java.util.*;\n\nclass E {\n    boolean f(Integer a, Integer b) {\n        return a == b;\n    }\n\n"
                        + "    boolean g(List<Object> l) {\n        return Objects.isEmpty(l);\n    }\n}\n",
                "UTF-8", "LF", "java"));
        // 설계 64 — 값 단계는 import 를 안 끼우고(a == b 그대로), 뒤 imports 단계가 별표를 쓰는 형식(List·Objects)으로 푼다
        assertTrue(starUse.text().contains("return a == b;") && starUse.text().contains("import java.util.List;\nimport java.util.Objects;\n\nclass E"), starUse.text());
    }

    /** 5-62c — 한 줄을 여러 줄 글로 바꾸면 고친 줄(새 번호)은 끼운 줄까지 */
    @Test
    void touchedNewCountsInsertedLines() {
        Edits e = new Edits("a\nb\nc\nd");
        e.replace(2, "x\ny");
        e.replace(4, "z");
        assertEquals("a\nx\ny\nc\nz", e.result());
        assertEquals(java.util.Set.of(2, 3, 5), e.touchedNew());
    }

    /** 마무리 리뷰 — 작은따옴표 안 글자는 형식 문자가 아니라 그대로 */
    @Test
    void weekYearKeepsQuotedText() {
        Cleanup.Result r = cleanup(null).clean(new Source("A.java",
                "class A {\n    String f(java.util.Date d) {\n        return new java.text.SimpleDateFormat(\"YYYY-MM-dd 'Y'\").format(d);\n    }\n}\n",
                "UTF-8", "LF", "java"));
        assertTrue(r.text().contains("\"yyyy-MM-dd 'Y'\""), r.text());
    }

    /** 고친 글도 JDK 만으로 컴파일된다(원문도) */
    @Test
    void valueAndResourceCompile(@TempDir Path data) throws Exception {
        for (String dir : List.of("value", "resource", "local", "charset")) {
            Source s = source(dir, "Pos.java", data);
            Cleanup.Result r = cleanup(null).clean(s);
            assertTrue(r.changed(), dir);
            assertEquals(List.of(), compile(s.text(), data.resolve(dir + "-before")), dir + " 원문");
            assertEquals(List.of(), compile(r.text(), data.resolve(dir + "-after")), dir + " 고친 글");
        }
    }

    static Source source(String dir, String name, Path data) throws Exception {
        return CheckRulesTest.fixtureSources(FIXTURES.resolve(dir), data).stream().filter(x -> x.rel().equals(name)).findFirst().orElseThrow();
    }

    static Cleanup.Result clean(String dir, String name, Path data) throws Exception {
        return cleanup(null).clean(source(dir, name, data));
    }

    /** javac 오류 글 — 파일 하나를 그 폴더에 Pos.java 로 */
    static List<String> compile(String text, Path dir) throws Exception {
        return compile(text, dir, null);
    }

    /** 설계 60 — classpath 를 주면 javac 에 -classpath 로(slf4j — DdlCorpusTest 전례) */
    static List<String> compile(String text, Path dir, String classpath) throws Exception {
        java.nio.file.Files.createDirectories(dir);
        Path f = java.nio.file.Files.writeString(dir.resolve("Pos.java"), text);
        javax.tools.JavaCompiler javac = javax.tools.ToolProvider.getSystemJavaCompiler();
        javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diags = new javax.tools.DiagnosticCollector<>();
        try (javax.tools.StandardJavaFileManager fm = javac.getStandardFileManager(diags, java.util.Locale.ROOT,
                java.nio.charset.StandardCharsets.UTF_8)) {
            List<String> opts = new java.util.ArrayList<>(List.of("-proc:none", "-nowarn", "-encoding", "UTF-8", "-d", dir.toString()));
            if (classpath != null) {
                opts.addAll(List.of("-classpath", classpath));
            }
            javac.getTask(null, fm, diags, opts, null, fm.getJavaFileObjectsFromPaths(List.of(f))).call();
        }
        return diags.getDiagnostics().stream().filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                .map(d -> d.getLineNumber() + " " + d.getMessage(java.util.Locale.ROOT)).toList();
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
        // 5-76 — 이미 있던 생겨도-되는 규칙(unusedImport·unusedIgnore)이 그대로 남은 것은 사라짐이 아니다
        List<Finding> lb = List.of(f(3, "java.unusedImport", "import a.B;"), f(9, "java.unusedLocal", "int n = f();"));
        assertNull(Cleanup.worse(Cleanup.LOCAL, Set.of("java.unusedLocal"), lb, List.of(f(3, "java.unusedImport", "import a.B;")), Set.of(9),
                Set.of(9)), "원래 있던 unusedImport 가 남았다");
        List<Finding> db = List.of(f(2, "tsx.debugger", "debugger;"), f(7, "common.unusedIgnore", "tb-ignore x.y"));
        assertNull(Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db, List.of(f(6, "common.unusedIgnore", "tb-ignore x.y")), Set.of(2),
                Set.of()), "원래 있던 unusedIgnore 가 남았다");
        assertEquals("다른 지적이 사라짐: common.unusedIgnore", Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db, List.of(), Set.of(2),
                Set.of()), "원래 있던 표식이 쓰이게 됐다 — 지운 줄 뒤 다른 줄을 덮는다(guards/next.ts)");
        // 5-81 — 같은 발췌의 생겨도-되는 지적이 하나 더 생긴 것은 새 것(비교 밖) — 지운 줄 뒤 표식 둘이 같은 글일 때 delete 를 되돌리지 않는다
        assertNull(Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db,
                List.of(f(6, "common.unusedIgnore", "tb-ignore x.y"), f(9, "common.unusedIgnore", "tb-ignore x.y")), Set.of(2), Set.of()),
                "같은 발췌 둘째는 새로 생긴 것");
        assertEquals("다른 지적이 생김: common.todo", Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db,
                List.of(f(6, "common.todo", "// TODO"), f(9, "common.todo", "// TODO")), Set.of(2), Set.of()), "생겨도 되는 규칙이 아니면 둘 다 센다");
        // 5-83 ① — 고친 줄 위의 생겨도-되는 지적은 줄 차례와 무관하게 고친 줄 쪽(toa)으로 — before 수 예산은 고친 줄 밖 것만 쓴다
        List<Finding> ab = List.of(f(6, "common.unusedIgnore", "tb-ignore x.y"), f(9, "common.unusedIgnore", "tb-ignore x.y"));
        List<Finding> ba = List.of(ab.get(1), ab.get(0));
        assertEquals("다른 지적이 생김: common.unusedIgnore", Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db, ab, Set.of(2), Set.of(9)));
        assertEquals("다른 지적이 생김: common.unusedIgnore", Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db, ba, Set.of(2), Set.of(9)), "줄 차례 무관");
    }

    /** 5-76 — java.unusedLocal 만 고르면 delete 가 안 돌아 원래 있던 Date import 지적이 남는다. 그래도 local 단계는 적용된다(되돌림 없음, zheng DemoController 꼴) */
    @Test
    void localOnlyKeepsOtherUnusedImport() {
        Cleanup c = new Cleanup(RuleSet.load(profile(null), null), List.of(LocalsAst.UNUSED_LOCAL), "LF");
        String src = "package q;\n\nimport java.util.Date;\nimport java.util.List;\n\nclass Z {\n    void f(List<String> xs) {\n"
                + "        int n = xs.size();\n        System.identityHashCode(xs);\n    }\n}\n";
        Cleanup.Result r = c.clean(new Source("Z.java", src, "UTF-8", "LF", "java"));
        assertTrue(r.changed() && r.text().contains("        xs.size();\n"), r.text());
        assertEquals(List.of(new Cleanup.Step(Cleanup.LOCAL, LocalsAst.UNUSED_LOCAL, 1, 0)), r.steps());
        assertEquals(List.of(), skipsOf(r, Cleanup.LOCAL).stream().filter(k -> k.startsWith("다른 지적이")).toList());
        assertEquals(1, r.before().stream().filter(x -> x.rule().equals("java.unusedImport")).count(), "Date import 지적은 처음부터 있다");
        assertEquals(1, r.after().stream().filter(x -> x.rule().equals("java.unusedImport")).count(), "그대로 남는다");
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

    /** 5-101(설계 70 §3-1) — 수신자 선언 형식이 String 일 때만 "L".equals(S) 로 뒤집는다. 다른 형식·못 찾음은 건너뜀 */
    @Test
    void equalsFirstNeedsStringReceiver() {
        String src = "package v;\n\nclass V {\n    private String name;\n\n"
                + "    boolean a(Object o) {\n        return o.equals(\"a\");\n    }\n\n"
                + "    boolean b() {\n        StringBuilder sb = new StringBuilder();\n        return sb.equals(\"b\");\n    }\n\n"
                + "    boolean c() {\n        return name().equals(\"c\");\n    }\n\n"
                + "    boolean d() {\n        return other.equals(\"d\");\n    }\n\n"
                + "    boolean e(String s) {\n        return s.equals(\"e\");\n    }\n\n"
                + "    boolean f() {\n        String t = name;\n        return t.equals(\"f\");\n    }\n\n"
                + "    boolean g() {\n        return this.name.equals(\"g\");\n    }\n\n"
                + "    String name() {\n        return name;\n    }\n"
                // 5-114b — 같은 파일 메서드 반환 형식: Object · 같은 인자 수 오버로드 둘 · 익명 클래스 몸 안 맨 호출은 그대로 건너뜀
                + "\n    Object o() {\n        return null;\n    }\n\n    boolean h() {\n        return o().equals(\"x\");\n    }\n\n"
                + "    String pick(int i) {\n        return null;\n    }\n\n    Object pick(String s) {\n        return null;\n    }\n\n"
                + "    boolean i() {\n        return pick(1).equals(\"a\");\n    }\n\n"
                + "    Object j() {\n        return new Object() {\n            boolean k() {\n                return name().equals(\"k\");\n            }\n        };\n    }\n}\n";
        Cleanup.Result r = cleanup(null).clean(new Source("v/V.java", src, "UTF-8", "LF", "java"));
        String t = r.text();
        for (String keep : List.of("return o.equals(\"a\");", "return sb.equals(\"b\");", "return other.equals(\"d\");", "return o().equals(\"x\");",
                "return pick(1).equals(\"a\");", "return name().equals(\"k\");")) {
            assertTrue(t.contains(keep), keep + "\n" + t);
        }
        for (String fixed : List.of("return \"c\".equals(name());", "return \"e\".equals(s);", "return \"f\".equals(t);", "return \"g\".equals(this.name);")) {
            assertTrue(t.contains(fixed), fixed + "\n" + t);
        }
        assertEquals(List.of("줄 7: " + ValueFix.RECEIVER, "줄 12: " + ValueFix.RECEIVER, "줄 20: " + ValueFix.RECEIVER, "줄 45: " + ValueFix.RECEIVER,
                "줄 57: " + ValueFix.RECEIVER, "줄 63: " + ValueFix.RECEIVER), skipsOf(r, Cleanup.VALUE));
    }

    /** 5-139(E-03·E-09·E-10) — 표식으로 눌린 줄은 안 고친다 · 패턴 변수는 같은 이름 필드 형식이 아니다 · 가려진 상수 이름은 상수가 아니다 */
    @Test
    void valueFixRespectsMarkersAndShadowing() {
        String p1 = "package v;\n\nclass P1 {\n    boolean f(String s, String t) {\n        boolean a = s.equals(\"a\"); // tb-ignore java.equalsLiteralFirst 손으로\n"
                + "        return a && t.equals(\"b\");\n    }\n}\n";
        Cleanup.Result r1 = cleanup(null).clean(new Source("v/P1.java", p1, "UTF-8", "LF", "java"));
        assertTrue(r1.text().contains("boolean a = s.equals(\"a\"); // tb-ignore") && r1.text().contains("return a && \"b\".equals(t);"), r1.text());
        String c5 = "package v;\n\nclass C5 {\n    Integer a;\n\n    boolean f(Object o, Integer b) {\n        if (o instanceof Integer a) {\n"
                + "            return a == b;\n        }\n        return false;\n    }\n}\n";
        Cleanup.Result r5 = cleanup(null).clean(new Source("v/C5.java", c5, "UTF-8", "LF", "java"));
        assertFalse(r5.text().contains("Objects.equals"), r5.text());
        String field = "package v;\n\nclass C6 {\n    String s;\n\n    boolean f(Object o) {\n        if (o instanceof StringBuilder s) {\n"
                + "            return s.equals(\"a\");\n        }\n        return false;\n    }\n}\n";
        assertEquals(field, cleanup(null).clean(new Source("v/C6.java", field, "UTF-8", "LF", "java")).text());
        String p5 = "package v;\n\nclass P5 {\n    static final String KEY = \"k\";\n\n    boolean f(String s, String KEY) {\n        return s.equals(KEY);\n    }\n}\n";
        assertEquals(p5, cleanup(null).clean(new Source("v/P5.java", p5, "UTF-8", "LF", "java")).text());
        String p6 = "package v;\n\nclass P6 {\n    static final String KEY = \"k\";\n\n    boolean f(String s) {\n        return s.equals(KEY);\n    }\n}\n";
        assertTrue(cleanup(null).clean(new Source("v/P6.java", p6, "UTF-8", "LF", "java")).text().contains("return KEY.equals(s);"), "가려지지 않은 상수");
    }

    /** 5-102(설계 70 §3-2) — default 위 경계(switch 머리 줄·앞 case 끝 줄)에 걸친 블록 주석의 꼬리는 안 옮긴다. 혼자 선 주석은 같이 간다 */
    @Test
    void switchKeepsStraddlingComment() {
        String pre = "package cleanup.t;\n\nclass A {\n    void f(int x) {\n";
        String dflt = "            default:\n                a();\n                break;\n";
        String one = "            case 1:\n                b();\n                break;\n";
        String foot = "        }\n    }\n}\n";
        String head = "        switch (x) { /* 머리\n               꼬리 */\n";
        String t1 = cleanup(null).clean(new Source("t/A.java", pre + head + dflt + one + foot, null, "LF", "java")).text();
        assertEquals(pre + head + one + dflt + foot, t1, "머리 줄에서 열린 주석의 꼬리는 제자리");
        assertEquals(2, entries(t1), t1);
        String caseEnd = "        switch (x) {\n            case 2:\n                c();\n                break; /* a\n                 b */\n";
        Cleanup.Result r2 = cleanup(null).clean(new Source("t/A.java", pre + caseEnd + dflt + one + foot, null, "LF", "java"));
        assertEquals(pre + caseEnd + dflt + one + foot, r2.text(), "앞 case 끝 줄 뒤 주석은 그 case 가 줄을 혼자 안 쓴다 — 통째 건너뜀, 꼬리는 제자리");
        assertEquals(3, entries(r2.text()), r2.text());
        assertEquals(List.of("줄 10: " + SwitchFix.SPLIT), skipsOf(r2, Cleanup.SWITCH));
        String alone = "        switch (x) {\n            case 2:\n                c();\n                break;\n";
        String note = "            /* 설명 */\n";
        String t3 = cleanup(null).clean(new Source("t/A.java", pre + alone + note + dflt + one + foot, null, "LF", "java")).text();
        assertEquals(pre + alone + one + note + dflt + foot, t3, "혼자 선 주석은 default 와 같이 끝으로");
    }

    /** 5-104(설계 70 §3-4) — 인코딩 선언 없는 XML 은 파서가 UTF-8 로 읽는다. 목표가 UTF-8 이 아니면 손으로 */
    @Test
    void xmlWithoutDeclarationStaysUtf8() {
        String body = "<mapper>한글</mapper>\n";
        assertEquals(EncodingFix.XML_NO_DECL, EncodingFix.refusal("m.xml", body, "UTF-8", "MS949", "LF"), "선언 없음");
        assertEquals(EncodingFix.XML_NO_DECL, EncodingFix.refusal("m.xml", "<?xml version=\"1.0\"?>\n" + body, "UTF-8", "MS949", "LF"), "encoding 없는 선언");
        assertEquals(EncodingFix.XML_NO_DECL,
                EncodingFix.refusal("m.xml", "<mapper><property name=\"url\" value=\"jdbc:x?charset=EUC-KR\"/>한글</mapper>\n", "UTF-8", "MS949", "LF"),
                "본문 charset 글은 선언이 아니다");
        assertNull(EncodingFix.refusal("m.xml", "<?xml version=\"1.0\" encoding=\"EUC-KR\"?>\n" + body, "UTF-8", "MS949", "LF"), "선언이 프로필과 같다");
        assertNull(EncodingFix.refusal("m.xml", body, "MS949", "UTF-8", "LF"), "UTF-8 로 올리기는 통과");
        assertNull(EncodingFix.refusal("a.jsp", "<p>한글</p>\n", "UTF-8", "MS949", "LF"), "jsp 는 이 판정 밖");
    }

    /** 5-106(설계 70 §3-6) — debugger·console 지우기는 Zones 로: 문자열 안 백틱·겹친 템플릿·<pre>·<textarea>·짝 안 맞음 */
    /** 5-137(E-01·E-07·E-13) — jsp·html 의 script 주석은 판정과 같은 글(RegexRule.bare)로 · 스크립트 밖 본문 글은 안 지운다 */
    @Test
    void markupScriptCommentsAndBodyText() {
        Cleanup c = cleanup(null);
        String b1 = "<script>\nif (list.length > 100) // big only\ndebugger;\nnext();\n</script>\n";
        Cleanup.Result r1 = c.clean(new Source("z/b1.jsp", b1, null, "LF", "jsp"));
        assertEquals(b1, r1.text(), "꼬리 주석 뒤 debugger — 지우면 다음 문장이 if 몸");
        assertEquals(List.of("줄 3: 중괄호 없는 제어문 몸체일 수 있음"), skipsOf(r1, Cleanup.DELETE));
        for (String head : List.of("} else // stop here", "if (x) /* stop */")) {
            String t = "<script>\nif (y) {\na();\n" + head + "\ndebugger;\nnext();\n</script>\n";
            assertEquals(t, c.clean(new Source("z/b1b.jsp", t, null, "LF", "jsp")).text(), head);
        }
        String html = "<script>\nfor (var i = 0; i < n; i++) // each row\ndebugger;\nnext();\n</script>\n";
        assertEquals(html, c.clean(new Source("z/b1.html", html, null, "LF", "html")).text());
        String was = "<script>\nif (x)\n// was: foo();\nconsole.log(x);\nnext();\n</script>\n";
        assertEquals(was, c.clean(new Source("z/b1c.jsp", was, null, "LF", "jsp")).text(), "주석 줄 너머 제어문");
        String b2 = "<script>\n// note\nconsole.log(1);\nvar a = 1; // tail\nconsole.log(2);\n</script>\n";
        assertEquals("<script>\n// note\nvar a = 1; // tail\n</script>\n", c.clean(new Source("z/b2.jsp", b2, null, "LF", "jsp")).text(),
                "주석 줄·꼬리 주석 뒤 console 은 지운다");
        String b4 = "<div class=\"code\">\ndebugger;\nconsole.log(1);\n</div>\n";
        Cleanup.Result r4 = c.clean(new Source("z/b4.jsp", b4, null, "LF", "jsp"));
        assertEquals(b4, r4.text(), "스크립트 밖 본문 글");
        assertEquals(List.of("줄 2: <script> 밖 글 — 마크업 본문", "줄 3: <script> 밖 글 — 마크업 본문"), skipsOf(r4, Cleanup.DELETE));
        String js = "if (list.length > 100) // big only\ndebugger;\nnext();\n";
        assertEquals(js, c.clean(new Source("z/b1.js", js, null, "LF", "js")).text(), "같은 글의 .js 는 지금과 같다");
        String plain = "<script>\nconsole.log(1);\nnext();\n</script>\n";
        assertEquals("<script>\nnext();\n</script>\n", c.clean(new Source("z/b5.jsp", plain, null, "LF", "jsp")).text());
    }

    /** 5-153(E4-01) — debugger 삭제도 「앞 줄이 문장 끝」 가드 — 끊긴 따옴표 줄 · 정규식 리터럴 · 주석뿐인 스크립틀릿 뒤에서 지우면 다음 문장이 if 몸 */
    @Test
    void debuggerNeedsAStatementEndBefore() {
        Cleanup c = cleanup(null);
        List<String[]> hang = List.of(new String[] {"z/a.js", "js", "if (/'/.test(u) || u == \"http://x\")\ndebugger;\nnext();\n", "줄 2: "},
                new String[] {"z/r1.jsp", "jsp", "<script>\nif (/^https?:\\/\\//.test(u))\ndebugger;\ngo(u);\n</script>\n", "줄 3: "},
                new String[] {"z/g1.jsp", "jsp", "<script>\nif (x)\n<% /* note */ %>\ndebugger;\nnext();\n</script>\n", "줄 4: "},
                new String[] {"z/k1.jsx", "jsx", "const k = (\n<pre>\ndebugger;\n</pre>\n);\n", "줄 3: "});
        for (String[] h : hang) {
            Cleanup.Result r = c.clean(new Source(h[0], h[2], null, "LF", h[1]));
            assertEquals(h[2], r.text(), h[0]);
            List<String> sk = skipsOf(r, Cleanup.DELETE);
            assertTrue(sk.size() == 1 && sk.get(0).startsWith(h[3]), h[0] + " " + sk);
        }
        // 지킴이 — 문장 끝 · 여는 중괄호 · <script> 뒤는 지운다 · 꼬리 주석 뒤 제어문은 지금 사유 그대로
        assertEquals("foo();\nnext();\n", c.clean(new Source("z/k2.js", "foo();\ndebugger;\nnext();\n", null, "LF", "js")).text());
        assertEquals("function f() {\n}\n", c.clean(new Source("z/k3.js", "function f() {\ndebugger;\n}\n", null, "LF", "js")).text());
        assertEquals("<script>\nnext();\n</script>\n", c.clean(new Source("z/k4.jsp", "<script>\ndebugger;\nnext();\n</script>\n", null, "LF", "jsp")).text());
        Cleanup.Result tail = c.clean(new Source("z/k5.js", "if (x) // c\ndebugger;\nnext();\n", null, "LF", "js"));
        assertEquals(List.of("줄 2: 중괄호 없는 제어문 몸체일 수 있음"), skipsOf(tail, Cleanup.DELETE));
    }

    @Test
    void templateAndRawTextAreKept() {
        Cleanup c = cleanup(null);
        String ts = "const h = `<ul>${xs.map(x => `\ndebugger;\nconsole.log(x);\n`).join('')}</ul>`;\ndebugger;\nconsole.log(1);\n";
        Cleanup.Result r1 = c.clean(new Source("z/a.ts", ts, null, "LF", "ts"));
        assertEquals("const h = `<ul>${xs.map(x => `\ndebugger;\nconsole.log(x);\n`).join('')}</ul>`;\n", r1.text(), "겹친 템플릿 안쪽은 남고 밖은 지운다");
        assertTrue(skipsOf(r1, Cleanup.DELETE).containsAll(List.of("줄 2: 템플릿 리터럴 안 debugger", "줄 3: 템플릿 리터럴 안 console")),
                r1.skips().toString());

        String js = "var q = '`';\nvar t = `\ndebugger;\n`;\ndebugger;\n";
        Cleanup.Result r2 = c.clean(new Source("z/b.js", js, null, "LF", "js"));
        assertEquals("var q = '`';\nvar t = `\ndebugger;\n`;\n", r2.text(), "문자열 안 백틱은 안 센다");

        String jsp = "<pre>\nconsole.log(\"예시\");\ndebugger;\n</pre>\n<textarea>\ndebugger;\n</textarea>\n<script>\ndebugger;\n</script>\n";
        Cleanup.Result r3 = c.clean(new Source("z/c.jsp", jsp, null, "LF", "jsp"));
        assertEquals(jsp.replace("<script>\ndebugger;\n", "<script>\n"), r3.text(), "날글 줄은 남고 script 안은 지운다");
        assertTrue(skipsOf(r3, Cleanup.DELETE).containsAll(List.of("줄 3: <pre>·<textarea> 안 debugger", "줄 6: <pre>·<textarea> 안 debugger")),
                r3.skips().toString());

        String odd = "var r = /`/;\ndebugger;\n";
        Cleanup.Result r4 = c.clean(new Source("z/d.js", odd, null, "LF", "js"));
        assertEquals(odd, r4.text(), "백틱 짝이 안 맞으면 다 남긴다");
        assertEquals(List.of("줄 2: " + Zones.UNBALANCED), skipsOf(r4, Cleanup.DELETE));
    }

    /** 5-107(설계 70 §3-7) — console 인자에 / 는 정규식일 수 있고, 호출·증감·대입은 부수 효과 — 지우지 않는다 */
    @Test
    void consoleArgsRisk() {
        Cleanup c = cleanup(null);
        for (String side : List.of("console.log(q.pop());", "console.log(i++);", "console.log(x = 5);", "console.log(await load());", "console.log(save(a));")) {
            Cleanup.Result r = c.clean(new Source("z/a.tsx", side + "\n", null, "LF", "tsx"));
            assertEquals(side + "\n", r.text(), side);
            assertEquals(List.of("줄 1: " + ConsoleArgs.SIDE_EFFECT), skipsOf(r, Cleanup.DELETE), side);
        }
        String regex = "items.forEach((s) => {\n  console.log(s.replace(/\\(/g, ''));\n  next();\n});\n";
        Cleanup.Result rr = c.clean(new Source("z/b.tsx", regex, null, "LF", "tsx"));
        assertEquals(regex, rr.text(), "\\( 가 깊이를 올려 뒤 문장까지 먹던 꼴");
        assertEquals(List.of("줄 2: " + ConsoleArgs.SLASH), skipsOf(rr, Cleanup.DELETE));
        // 마무리 리뷰 M2 — 겹친 템플릿 글의 ( 가 consoleEnd 를 늘려 뒤 문장까지 먹던 꼴: 인자 코드 자리 괄호 짝으로 거른다
        String nested = "items.forEach((s) => {\n  console.log(`${a ? `(` : ''}`);\n  return;\n});\n";
        Cleanup.Result rn = c.clean(new Source("z/e.tsx", nested, null, "LF", "tsx"));
        assertEquals(nested, rn.text(), "괄호 짝이 안 맞는 범위는 안 지운다");
        assertEquals(List.of("줄 2: " + ConsoleArgs.PAREN), skipsOf(rn, Cleanup.DELETE));
        Cleanup.Result div = c.clean(new Source("z/c.tsx", "console.log(a / b);\n", null, "LF", "tsx"));
        assertEquals("console.log(a / b);\n", div.text());
        assertEquals(List.of("줄 1: " + ConsoleArgs.SLASH), skipsOf(div, Cleanup.DELETE));
        // 5-115 ① — 선택 호출·복합 시프트 대입·태그드 템플릿은 부수 효과
        for (String side : List.of("console.log(cb?.(x));", "console.log(a <<= 1);", "console.log(a >>>= 1);", "console.log(tag`x`);")) {
            Cleanup.Result r = c.clean(new Source("z/f.tsx", side + "\n", null, "LF", "tsx"));
            assertEquals(side + "\n", r.text(), side);
            assertEquals(List.of("줄 1: " + ConsoleArgs.SIDE_EFFECT), skipsOf(r, Cleanup.DELETE), side);
        }
        for (String pure : List.of("console.log(\"a\", b);", "console.log(JSON.stringify(o));", "console.log('x: ' + o.p.q);",
                "console.log(a == b, c >= d, e => e);", "console.log(\"f(x)/2\");", "console.log(a <= b, a >> 1, a => a, `t`);")) {
            assertEquals("", c.clean(new Source("z/d.tsx", pure + "\n", null, "LF", "tsx")).text(), pure);
        }
        // 5-138(E-02) — TS 제네릭 호출 f<T>() · non-null 호출 cb!() 는 호출. a > (b) 도 건너뜀(안전한 쪽)
        for (String lang : List.of("ts", "tsx")) {
            String d1 = "function f() {\n  console.log(q.take<number>());\n  console.log(cb!(1));\n  console.log(a > (b));\n}\n";
            Cleanup.Result r = c.clean(new Source("z/d1." + lang, d1, null, "LF", lang));
            assertEquals(d1, r.text(), lang);
            assertEquals(List.of("줄 2: " + ConsoleArgs.SIDE_EFFECT, "줄 3: " + ConsoleArgs.SIDE_EFFECT, "줄 4: " + ConsoleArgs.SIDE_EFFECT),
                    skipsOf(r, Cleanup.DELETE), lang);
        }
        for (String pure : List.of("console.log(a > 1 ? x : y);", "console.log(!flag);")) {
            assertEquals("", c.clean(new Source("z/g.ts", pure + "\n", null, "LF", "ts")).text(), pure);
        }
        assertTrue(ConsoleArgs.scriptOpen("<script>") && ConsoleArgs.scriptOpen("<SCRIPT type=\"text/javascript\">"));
        assertFalse(ConsoleArgs.scriptOpen("<scripts>") || ConsoleArgs.scriptOpen("<script> x") || ConsoleArgs.scriptOpen("a > b"));
    }

    /** 5-107 — 긴 공백·반복 <script 앞 줄에서 정규식 역추적이 제곱으로 안 간다 */
    @Test
    void consoleLongInputsAreLinear() {
        String pad = " ".repeat(200_000);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            List<Cleanup.Skip> skips = new ArrayList<>();
            Cleanup.deleteConsole(new Source("a.ts", "console.log(1)" + pad + "x\n", null, "LF", "ts"), List.of(1), new Edits("x"), skips);
            assertEquals(1, skips.size(), skips.toString());
        });
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            List<Cleanup.Skip> skips = new ArrayList<>();
            Cleanup.deleteDebugger(new Source("a.ts", "debugger" + pad + "x\n", null, "LF", "ts"), List.of(1), new Edits("x"), skips);
            assertEquals(1, skips.size(), skips.toString());
        });
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            List<Cleanup.Skip> skips = new ArrayList<>();
            Cleanup.deleteConsole(new Source("a.ts", "<script ".repeat(20_000) + "\nconsole.log(1);\n", null, "LF", "ts"), List.of(2), new Edits("x"), skips);
            assertEquals(1, skips.size(), skips.toString());
        });
    }

    /** 5-108(설계 70 §3-8) — <pre>·<textarea> 안은 글이라 scope·rel 을 안 끼운다. 밖은 그대로 고친다 */
    @Test
    void markupSkipsRawText() {
        String table = "<table><tr><th>a</th></tr></table>";
        String link = "<a href=\"x\" target=\"_blank\">y</a>";
        String jsp = "<textarea>\n" + table + "\n" + link + "\n</textarea>\n" + table + "\n" + link + "\n";
        Cleanup.Result r = cleanup(null).clean(new Source("z/a.jsp", jsp, null, "LF", "jsp"));
        String[] out = r.text().split("\n", -1);
        assertEquals(table, out[1], "textarea 안 표");
        assertEquals(link, out[2], "textarea 안 링크");
        assertTrue(out[4].contains("scope=\"col\""), r.text());
        assertTrue(out[5].contains("rel=\"noopener noreferrer\""), r.text());
        assertTrue(skipsOf(r, Cleanup.MARKUP_STAGE).containsAll(List.of("줄 2: " + MarkupFix.RAW, "줄 3: " + MarkupFix.RAW)), r.skips().toString());
        assertEquals(1, skipsOf(r, Cleanup.MARKUP_STAGE).stream().filter(k -> k.startsWith("줄 2: ")).count(), "날글 사유는 줄마다 한 번");
    }

    /** 5-116 ④ — pre 안은 마크업이다(textarea 만 날글) */
    @Test
    void markupPreIsMarkup() {
        String jsp = "<pre>\n<table><tr><th>이름</th></tr></table>\n</pre>\n";
        Cleanup.Result r = cleanup(null).clean(new Source("z/p.jsp", jsp, null, "LF", "jsp"));
        assertTrue(r.text().split("\n", -1)[1].contains("scope=\"col\""), r.text());
        assertTrue(skipsOf(r, Cleanup.MARKUP_STAGE).stream().noneMatch(k -> k.endsWith(MarkupFix.RAW)), r.skips().toString());
    }

    /** 5-116 ⑥ — textarea 안 줄의 <html> 글에는 lang 을 안 끼운다 */
    @Test
    void htmlLangSkipsRawLine() {
        String jsp = "<textarea>\n<html>\n</textarea>\n";
        assertEquals("<html>", cleanup(null).clean(new Source("z/t.jsp", jsp, null, "LF", "jsp")).text().split("\n", -1)[1]);
    }

    /** 5-116 ⑥ — 앞 줄이 아주 긴 <script>>>… 꼴이어도 console 삭제가 선형 */
    @Test
    void scriptOpenLongTail() {
        String html = "<script" + ">".repeat(200_000) + "\nconsole.log(x);\n</script>\n";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> cleanup(null).clean(new Source("z/s.html", html, null, "LF", "html")));
    }

    /** 5-108 — th 3만 개(상세 표 — th 만 행은 첫 행 밖이면 표째 건너뛴다): 줄마다 하나 · 한 줄에 다. 끼울 자리마다 앞머리를 다시 세지 않는다 */
    @Test
    void manyInsertsAreLinear() {
        String row = "<tr><th>a</th><td>b</td></tr>";
        for (String sep : List.of("\n", "")) {
            String html = "<table>\n" + String.join(sep, java.util.Collections.nCopies(30_000, row)) + "\n</table>\n";
            Edits e = new Edits(html);
            List<Cleanup.Skip> skips = new ArrayList<>();
            assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> MarkupFix.apply(new Source("a.html", html, null, "LF", "html"), List.of(), Set.of(ThScopeFix.TH_SCOPE), e, skips));
            assertEquals(30_000, e.result().split("scope=", -1).length - 1, "sep=" + sep.length());
        }
    }

    /** 5-110(설계 70 §3-10) — 루프 안 catch 는 몸의 마지막 문장이 탈출일 때만 고친다. 조건부 탈출은 안 벗어난 길에서 다음 대기가 곧바로 깨진다 */
    @Test
    void interruptConditionalLeaveIsLoop() {
        for (String leave : List.of("if (stop) {\n                    return;\n                }", "if (stop) break;")) {
            String src = "class A {\n    boolean run;\n    boolean stop;\n\n    void a() {\n        while (run) {\n            try {\n                Thread.sleep(1);\n"
                    + "            } catch (InterruptedException e) {\n                " + leave + "\n            }\n        }\n    }\n}\n";
            Edits e = new Edits(src);
            List<Cleanup.Skip> skips = new ArrayList<>();
            InterruptFix.apply(src, List.of(new Finding("A.java", 9, "java", JavaShape.INTERRUPTED, "warn", "")), new JavaSource(), e, skips);
            assertTrue(e.isEmpty(), leave);
            assertEquals(List.of("줄 9: " + InterruptFix.IN_LOOP), skips.stream().map(Cleanup.Skip::reason).toList(), leave);
        }
        // 마무리 리뷰 I1 — break 가 벗어나는 것이 안쪽 for·switch 뿐이면 바깥 while 의 다음 대기가 곧바로 깨진다
        for (String[] wrap : List.of(new String[] {"for (int i = 0; i < 3; i++) {\n", "}\n"}, new String[] {"switch (x) {\n            case 1:\n", "}\n"})) {
            String src = "class A {\n    boolean run;\n    int x;\n\n    void a() {\n        while (run) {\n            " + wrap[0] + "            try {\n                Thread.sleep(1);\n"
                    + "            } catch (InterruptedException e) {\n                break;\n            }\n            " + wrap[1] + "        }\n    }\n}\n";
            int no = java.util.Arrays.asList(src.split("\n", -1)).indexOf("            } catch (InterruptedException e) {") + 1;
            Edits e = new Edits(src);
            List<Cleanup.Skip> skips = new ArrayList<>();
            InterruptFix.apply(src, List.of(new Finding("A.java", no, "java", JavaShape.INTERRUPTED, "warn", "")), new JavaSource(), e, skips);
            assertTrue(e.isEmpty(), wrap[0]);
            assertEquals(List.of("줄 " + no + ": " + InterruptFix.IN_LOOP), skips.stream().map(Cleanup.Skip::reason).toList(), wrap[0]);
        }
        for (String leave : List.of("log(e);\n                return;", "log(e);\n                break;")) {
            String src = "class A {\n    boolean run;\n\n    void a() {\n        while (run) {\n            try {\n                Thread.sleep(1);\n"
                    + "            } catch (InterruptedException e) {\n                " + leave + "\n            }\n        }\n    }\n\n    void log(Exception e) {\n    }\n}\n";
            Edits e = new Edits(src);
            List<Cleanup.Skip> skips = new ArrayList<>();
            InterruptFix.apply(src, List.of(new Finding("A.java", 8, "java", JavaShape.INTERRUPTED, "warn", "")), new JavaSource(), e, skips);
            assertTrue(e.result().contains("Thread.currentThread().interrupt();"), leave + " — " + skips);
        }
    }

    /** 5-111(설계 70 §3-11) — 로거보다 먼저 도는 static 초기화가 이 클래스 코드를 부르면 로거가 null 이다 */
    @Test
    void logSkipsEarlyStaticInit() {
        String imports = "import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;\n\n";
        String inst = "    private static final S INSTANCE = new S();\n";
        String logger = "    private static final Logger log = LoggerFactory.getLogger(S.class);\n";
        String body = "\n    private S() {\n        System.out.println(\"init\");\n    }\n\n    void m() {\n        System.out.println(\"m\");\n    }\n}\n";
        String early = imports + "class S {\n" + inst + logger + body;
        Cleanup.Result r = cleanup(null).clean(new Source("log/S.java", early, null, "LF", "java"));
        assertEquals(early, r.text(), "싱글턴 생성자가 로거보다 먼저 돈다");
        assertEquals(2, skipsOf(r, Cleanup.LOG).stream().filter(k -> k.endsWith(LogFix.EARLY_INIT)).count(), r.skips().toString());

        LoggerShape sh = new LoggerShape(List.of("org.slf4j.Logger", "org.slf4j.LoggerFactory"), "logger",
                "private static final Logger logger = LoggerFactory.getLogger({C}.class);");
        Cleanup ins = new Cleanup(RuleSet.load(profile(null), null), Cleanup.RULES, "LF", rel -> java.util.Optional.of(sh));
        String bare = "class S {\n" + inst + body;
        Cleanup.Result ri = ins.clean(new Source("log/S.java", bare, null, "LF", "java"));
        assertEquals(bare, ri.text(), "끼울 자리 앞 static 초기화가 생성자를 부른다");
        assertTrue(skipsOf(ri, Cleanup.LOG).stream().anyMatch(k -> k.endsWith(LogFix.EARLY_INIT)), ri.skips().toString());

        String late = imports + "class S {\n" + logger + inst + body;
        assertTrue(cleanup(null).clean(new Source("log/S.java", late, null, "LF", "java")).text().contains("log.debug(\"init\");"), "로거가 먼저");
        String map = imports + "class S {\n    static final java.util.Map<String, String> M = new java.util.HashMap<>();\n" + logger + body;
        assertTrue(cleanup(null).clean(new Source("log/S.java", map, null, "LF", "java")).text().contains("log.debug(\"m\");"), "다른 형식 초기화는 괜찮다");
        // 5-115 ② — 자기 형식 이름으로 부르는 static 메서드 · 같은 파일 중첩 형식의 new · 생성자 있는 enum
        String selfCall = imports + "class S {\n    private static final S I = S.create();\n" + logger
                + "\n    static S create() {\n        System.out.println(\"c\");\n        return null;\n    }\n}\n";
        assertEquals(selfCall, cleanup(null).clean(new Source("log/S.java", selfCall, null, "LF", "java")).text(), "S.create()");
        String nested = imports + "class S {\n    private static final Inner X = new Inner();\n" + logger
                + "\n    static class Inner {\n        Inner() {\n            System.out.println(\"i\");\n        }\n    }\n}\n";
        assertEquals(nested, cleanup(null).clean(new Source("log/S.java", nested, null, "LF", "java")).text(), "중첩 형식 new");
        String en = imports + "enum S {\n    A;\n\n" + logger + "\n    S() {\n        System.out.println(\"e\");\n    }\n}\n";
        assertEquals(en, cleanup(null).clean(new Source("log/S.java", en, null, "LF", "java")).text(), "생성자 있는 enum");
    }

    /** 5-140(E-04·E-05) — 같은 파일 형식의 static 호출 · 인스턴스 로거 앞 인스턴스 초기화가 부르는 메서드 */
    @Test
    void logSkipsEarlyInitOfOtherShapes() {
        String imports = "import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;\n\n";
        String f4 = imports + "class F4 {\n    static final Object HOLDER = Helper.create();\n"
                + "    private static final Logger log = LoggerFactory.getLogger(F4.class);\n\n    static class Helper {\n"
                + "        static Object create() {\n            System.out.println(\"create\");\n            return new Object();\n        }\n    }\n}\n";
        Cleanup.Result r4 = cleanup(null).clean(new Source("log/F4.java", f4, null, "LF", "java"));
        assertEquals(f4, r4.text(), "Helper.create() 가 로거보다 먼저");
        assertTrue(skipsOf(r4, Cleanup.LOG).stream().anyMatch(k -> k.endsWith(LogFix.EARLY_INIT)), r4.skips().toString());
        String c1 = imports + "class C1 {\n    private final String name = init();\n    private final Logger log = LoggerFactory.getLogger(C1.class);\n\n"
                + "    String init() {\n        System.out.println(\"init\");\n        return \"n\";\n    }\n}\n";
        Cleanup.Result r1 = cleanup(null).clean(new Source("log/C1.java", c1, null, "LF", "java"));
        assertEquals(c1, r1.text(), "인스턴스 필드 초기화가 인스턴스 로거보다 먼저");
        assertTrue(skipsOf(r1, Cleanup.LOG).stream().anyMatch(k -> k.endsWith(LogFix.EARLY_INIT)), r1.skips().toString());
        String after = imports + "class C2 {\n    private final Logger log = LoggerFactory.getLogger(C2.class);\n    private final String name = init();\n\n"
                + "    String init() {\n        System.out.println(\"init\");\n        return \"n\";\n    }\n}\n";
        assertTrue(cleanup(null).clean(new Source("log/C2.java", after, null, "LF", "java")).text().contains("log.debug(\"init\");"), "로거 뒤 초기화");
    }

    /** 5-111 — 형식 이름 Log 는 로깅 패키지에서 온 것만 로거 — 도메인 Log 필드에 debug 를 부르지 않는다 */
    @Test
    void logDomainTypeIsNotLogger() {
        String domain = "class T {\n    private Log log;\n\n    void m() {\n        System.out.println(\"x\");\n    }\n}\n";
        Cleanup.Result r = cleanup(null).clean(new Source("log/T.java", domain, null, "LF", "java"));
        assertFalse(r.text().contains("log.debug"), r.text());
        assertEquals(List.of("줄 5: 로거가 없고 같은 소스 루트에 베낄 로거 꼴도 없다"), skipsOf(r, Cleanup.LOG));
        String commons = "import org.apache.commons.logging.Log;\nimport org.apache.commons.logging.LogFactory;\n\nclass T {\n"
                + "    private static final Log log = LogFactory.getLog(T.class);\n\n    void m() {\n        System.out.println(\"x\");\n    }\n}\n";
        assertTrue(cleanup(null).clean(new Source("log/T.java", commons, null, "LF", "java")).text().contains("log.debug(\"x\");"));
    }

    /** 5-143(E-11·E-12) — sql·jsp 도 주석 뗀 줄로 따옴표를 훑는다 · 매퍼 XML 안 SQL 의 맨 숫자는 값이 아니다 */
    @Test
    void piiScansBareLinesAndMapperSql() {
        String pan = "4030000010001234";
        for (String[] c : new String[][] {{"z/C6.sql", "/* don't */ SELECT " + pan + " FROM dual;\n"},
                {"z/f2.jsp", "<%-- it's --%> var n = " + pan + ";\n"},
                {"z/C3.xml", "<mapper namespace=\"a\">\n<select id=\"s\">SELECT * FROM T WHERE CARD_NO = " + pan + "</select>\n</mapper>\n"}}) {
            Cleanup.Result r = cleanup(null).clean(new Source(c[0], c[1], null, "LF", null));
            assertEquals(c[1], r.text(), c[0]);
            assertTrue(!skipsOf(r, Cleanup.PII).isEmpty() && skipsOf(r, Cleanup.PII).stream().allMatch(k -> k.endsWith(PiiFix.UNQUOTED)), c[0] + " " + r.skips());
        }
        String conf = "<beans>\n<value>" + pan + "</value>\n</beans>\n";
        assertEquals(conf.replace(pan, "************1234"), cleanup(null).clean(new Source("z/conf.xml", conf, null, "LF", null)).text(), "설정 XML 값은 가린다");
        String q = "SELECT * FROM T WHERE C = '" + pan + "';\n";
        assertEquals(q.replace(pan, "************1234"), cleanup(null).clean(new Source("z/q.sql", q, null, "LF", null)).text());
    }

    /** 5-112(설계 70 §3-12) — 따옴표 안은 왼쪽부터 한 번 훑어 정한다(\ 건너뜀) — 홀짝 셈은 다른 따옴표·이스케이프에 속는다 */
    @Test
    void piiQuoteScan() {
        Cleanup c = new Cleanup(RuleSet.load(profile(null), null), Set.of("security.piiCardPlain"), "LF");
        for (String bare : List.of("var a = \"it's\" + 4111111111111111;\n", "var b = 'a\\'b' + 4111111111111111;\n")) {
            Cleanup.Result r = c.clean(new Source("z/a.js", bare, null, "LF", "js"));
            assertEquals(bare, r.text(), bare);
            assertEquals(List.of("줄 1: " + PiiFix.UNQUOTED), skipsOf(r, Cleanup.PII), bare);
        }
        String in = "var c = \"it's\", d = '4111111111111111';\n";
        assertEquals("var c = \"it's\", d = '************1111';\n", c.clean(new Source("z/b.js", in, null, "LF", "js")).text());
        // 5-115 ③ — 주석 속 ' 는 따옴표를 안 연다
        String commented = "/* it's */ var n = 4111111111111111;\n";
        assertEquals(commented, c.clean(new Source("z/c.js", commented, null, "LF", "js")).text(), "맨 숫자는 그대로");
        assertEquals("/* it's */ var s = \"************1111\";\n",
                c.clean(new Source("z/d.js", "/* it's */ var s = \"4111111111111111\";\n", null, "LF", "js")).text());
    }

    /** 5-112 — 한 줄에 카드 꼴 5만 개: 일치마다 앞머리를 다시 안 센다 */
    @Test
    void piiLongLineIsLinear() {
        String line = "var x = [" + "'4111111111111111',".repeat(50_000) + "];";
        Edits e = new Edits(line);
        List<Cleanup.Skip> skips = new ArrayList<>();
        RuleSet rules = RuleSet.load(profile(null), null);
        List<Finding> cur = List.of(new Finding("a.js", 1, "security", LuhnRule.CARD_PLAIN, "warn", ""));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> PiiFix.apply(line, "js", cur, Set.of(LuhnRule.CARD_PLAIN), rules, e, skips));
        assertEquals(50_000, e.result().split("\\*{12}1111", -1).length - 1, skips.toString());
    }

    private static int entries(String java) {
        return new com.github.javaparser.JavaParser().parse(java).getResult().orElseThrow()
                .findAll(com.github.javaparser.ast.stmt.SwitchEntry.class).size();
    }
}
