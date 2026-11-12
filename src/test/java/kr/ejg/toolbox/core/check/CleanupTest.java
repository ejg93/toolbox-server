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
        assertTrue(t.contains("        } catch (IOException e) {\n            return -1;\n        }\n    }"), "catch 는 남고 finally 만 — " + t);
        assertTrue(t.contains("try { // 주석") && t.contains("try (InputStream x = new FileInputStream(a)) {")
                && t.contains("            InputStream y = new FileInputStream(b);\n            try {"), "줄 꼴 틀림·중첩 안쪽은 그대로 — " + t);
        assertFalse(t.contains("e.printStackTrace()"), t);
        assertEquals(List.of("줄 70: 줄 꼴이 달라 못 고침(한 줄에 다른 코드·여러 줄 식·여러 줄 close)", "줄 84: 겹친 try",
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
        return new Cleanup(RuleSet.load(p, null), Cleanup.RULES, "LF");
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
        assertEquals(List.of("줄 32: 블록 안 선언 — 범위가 바뀐다", "줄 39: 머리 줄 끝에 다른 글", "줄 41: 중첩된 중괄호 없는 문장", "줄 41: 중첩된 중괄호 없는 문장"),
                skipsOf(r, Cleanup.SYNTAX));
        assertEquals(List.of("java.emptyStatement 2→0", "java.needBraces 9→3", "java.unneededBlock 2→1"), r.steps().stream()
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
        assertEquals(List.of("java.defaultLast 7→4"), r.steps().stream().filter(s -> s.stage().equals(Cleanup.SWITCH))
                .map(s -> s.rule() + " " + s.before() + "→" + s.after()).toList());
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
        assertTrue(t.contains("        } catch (IOException | InterruptedException e) {\n            log(e);\n"), "union 은 밖 — " + t);
        assertEquals(5, t.split(java.util.regex.Pattern.quote(add), -1).length - 1, "이미 있는 것 하나 + 끼운 넷");
        assertEquals(List.of("줄 41: catch 몸이 한 줄"), skipsOf(r, Cleanup.INTERRUPT));
        assertEquals(List.of("java.interruptedIgnored 5→1"), r.steps().stream().filter(s -> s.stage().equals(Cleanup.INTERRUPT))
                .map(s -> s.rule() + " " + s.before() + "→" + s.after()).toList());

        String other = "package p;\n\nimport q.Thread;\n\nclass A {\n    void a() {\n        try {\n            java.lang.Thread.sleep(1);\n"
                + "        } catch (InterruptedException e) {\n        }\n    }\n}\n";
        Edits e = new Edits(other);
        List<Cleanup.Skip> skips = new java.util.ArrayList<>();
        InterruptFix.apply(other, List.of(), new JavaSource(), e, skips);
        assertTrue(e.isEmpty(), "다른 Thread import 면 안 고친다");
        assertEquals(List.of("줄 9: Thread 이름이 다른 형식"), skips.stream().map(Cleanup.Skip::reason).toList());
    }

    /** 설계 64 §3-8 — 카드번호 꼴을 끝 넷만 남겨 가린다(언어 무관 줄 치환). 주석 속 번호·자리 넘는 숫자는 그대로 */
    @Test
    void piiStage(@TempDir Path data) throws Exception {
        String j = clean("pii", "Pos.java", data).text();
        assertTrue(j.contains("    static final String CARD = \"****-****-****-3456\";\n"), j);
        assertTrue(j.contains("return \"****-****-****-7654\" + \" / \" + \"****-****-****-0003\";"), j);
        assertTrue(j.contains("// 예: 1111-2222-3333-4444 — 주석"), j);
        assertTrue(j.contains("\"1234-5678-9012-34567\""), j);
        Cleanup.Result p = clean("pii", "pos.properties", data);
        assertEquals("# 설계 64 — 코드 정리 pii(카드번호 가리기)\n# 시험 1111-2222-3333-4444 은 주석이라 그대로\ncard.test=****-****-****-9010\n"
                + "card.pair=****-****-****-0004,****-****-****-0002\n", p.text());
        assertEquals(List.of("security.piiCard 2→0"), p.steps().stream().filter(s -> s.stage().equals(Cleanup.PII))
                .map(s -> s.rule() + " " + s.before() + "→" + s.after()).toList());
        assertEquals(List.of(), skipsOf(p, Cleanup.PII));
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
        assertTrue(star.text().contains("import java.util.*;\nimport java.util.Objects;\n") && star.text().contains("return Objects.equals(a, b);"), star.text());
        Cleanup.Result starUse = cleanup(null).clean(new Source("E.java",
                "package e;\n\nimport java.util.*;\n\nclass E {\n    boolean f(Integer a, Integer b) {\n        return a == b;\n    }\n\n"
                        + "    boolean g(List<Object> l) {\n        return Objects.isEmpty(l);\n    }\n}\n",
                "UTF-8", "LF", "java"));
        assertTrue(starUse.text().contains("return a == b;") && !starUse.text().contains("import java.util.Objects"), starUse.text());
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
}
