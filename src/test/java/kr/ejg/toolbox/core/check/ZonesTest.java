package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 5-105(설계 70 §3-5) — 줄 구역 훑개. 따옴표·템플릿·${ } 깊이 · 마크업 날글 줄 */
class ZonesTest {

    static final String JSP = "jsp";

    /** 5-129(D-15) — <script> 몸 안 글자 태그(JS 문자열·주석 속 <textarea>·<pre>)는 날글 구역을 안 연다 */
    @Test
    void rawLinesSkipsScriptBodies() {
        String jsp = "<html><body>\n<script>\nvar t = $(\"<textarea>\").val(x);\nconsole.log(1);\n</script>\n<p>a</p>\n</body></html>\n";
        assertArrayEquals(new boolean[8], Zones.rawLines(jsp, JSP));
        String commented = jsp.replace("var t = $(\"<textarea>\").val(x);", "// <pre> 를 만든다");
        assertArrayEquals(new boolean[8], Zones.rawLines(commented, JSP));
        boolean[] outside = Zones.rawLines("<p>a</p>\n<textarea>\nx\n</textarea>\n<p>b</p>\n", "html");
        assertArrayEquals(new boolean[] {false, true, true, true, false, false}, outside);
    }

    /** 5-146b(D-1c) — 주석 안 짝 없는 <script 는 구역을 안 연다: 1~5행은 스크립트 밖 본문 */
    @Test
    void commentedOpenTagIsNotAScript() {
        String jsp = "<%-- <script src=\"old.js\"> --%>\n<!-- old block\n  alert(1); eval(x);\n-->\n<p>x</p>\n<script>\nvar a = 1;\n</script>\n";
        int[] z = Zones.zones(jsp, JSP);
        assertArrayEquals(new int[] {2, 2, 2, 2, 2}, java.util.Arrays.copyOf(z, 5));
        assertEquals(0, z[6], "살아 있는 script 몸");
    }

    @Test
    void templateLines() {
        Zones.Js one = Zones.js("const a = `x`;\ndebugger;\n");
        assertFalse(one.inTemplate()[1], "한 줄 템플릿 뒤 줄은 밖");
        assertTrue(one.balanced());

        Zones.Js multi = Zones.js("const a = `x\ndebugger;\n`;\nconsole.log(1);\n");
        assertTrue(multi.inTemplate()[1], "여러 줄 템플릿 안 줄");
        assertTrue(multi.inTemplate()[2], "닫는 백틱이 든 줄의 첫 자리도 안");
        assertFalse(multi.inTemplate()[3], "닫힌 뒤 줄은 밖");

        Zones.Js quoted = Zones.js("const t = '`';\ndebugger;\n");
        assertFalse(quoted.inTemplate()[1], "따옴표 문자열 안 백틱은 안 센다");
        assertTrue(quoted.balanced());

        Zones.Js nested = Zones.js("const h = `${xs.map(x => `\ndebugger;\n`)}\nf(1);\n`;\ng();\n");
        assertTrue(nested.inTemplate()[1], "겹친 여러 줄 템플릿의 안쪽 줄");
        assertTrue(nested.inTemplate()[2], "${ } 안 코드 줄도 참");
        assertTrue(nested.inTemplate()[3], "바깥 템플릿 글 줄");
        assertFalse(nested.inTemplate()[5], "다 닫힌 뒤 줄");
        assertTrue(nested.balanced());

        Zones.Js inner = Zones.js("const h = `a ${\nf({ b: 1 })\n} c`;\nd();\n");
        assertTrue(inner.inTemplate()[1], "${ 안 코드 줄");
        assertFalse(inner.inTemplate()[3], "안 중괄호가 깊이를 지켜 닫힘을 안 놓친다");
        assertTrue(inner.balanced());
    }

    @Test
    void unbalancedIsFlagged() {
        Zones.Js js = Zones.js("const r = /`/;\ndebugger;\n");
        assertFalse(js.balanced(), "백틱 하나 — 정규식 리터럴은 모른다");
        assertEquals(Zones.UNBALANCED, Zones.reason(js, new boolean[3], 1, "debugger"));
        assertFalse(Zones.js("const s = `${a`;\n").balanced(), "안 닫힌 ${");
    }

    @Test
    void codeMask() {
        String s = "f(\"a(b)\");";
        Zones.Js js = Zones.js(s);
        assertTrue(js.code()[s.indexOf('(')], "부르는 괄호는 코드");
        assertFalse(js.code()[s.indexOf("(b")], "따옴표 안 괄호는 글");
        assertFalse(js.code()[s.indexOf('"')], "따옴표 글자 자신도 거짓");

        String t = "`x ${f(1)} y`";
        Zones.Js tj = Zones.js(t);
        assertTrue(tj.code()[t.indexOf('f')], "${ } 안 f");
        assertTrue(tj.code()[t.indexOf("(1")], "${ } 안 (");
        assertFalse(tj.code()[t.indexOf('x')], "템플릿 글 x");
        assertFalse(tj.code()[t.indexOf('y')], "템플릿 글 y");
        assertFalse(tj.code()[0], "백틱 자신");
        assertEquals(t.length(), tj.code().length);
    }

    @Test
    void rawLines() {
        String jsp = "<div>\n<pre>\nconsole.log(1);\n</pre>\n<TEXTAREA name=\"a\">\ndebugger;\n</textarea>\n<p>\n";
        assertArrayEquals(new boolean[] {false, true, true, true, true, true, true, false, false}, Zones.rawLines(jsp, JSP));

        boolean[] commented = Zones.rawLines("<!-- <pre> -->\nx\n", JSP);
        assertArrayEquals(new boolean[] {false, false, false}, commented, "주석 안 태그는 안 센다");

        boolean[] pretty = Zones.rawLines("<pretty>\nx\n", "html");
        assertArrayEquals(new boolean[] {false, false, false}, pretty, "<pretty> 는 pre 가 아니다");

        assertArrayEquals(new boolean[] {false, false, false}, Zones.rawLines("<pre>\nx\n", "ts"), "마크업 꼴 밖");

        assertArrayEquals(new boolean[] {false, true, true, true}, Zones.rawLines("a\n<textarea>\nb\n", "html"), "안 닫히면 끝까지");

        Zones.Js js = Zones.js("x\n");
        assertEquals("<pre>·<textarea> 안 console", Zones.reason(js, new boolean[] {true, false}, 0, "console"));
        assertNull(Zones.reason(js, new boolean[] {false, false}, 0, "console"));
        assertEquals("템플릿 리터럴 안 debugger", Zones.reason(Zones.js("`\nx\n`\n"), new boolean[4], 1, "debugger"));
    }
}
