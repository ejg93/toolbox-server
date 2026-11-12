package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 설계 65 5-97 — security.piiCardPlain(kind luhn): 대시 없는 16 자리 숫자 가운데 Luhn 을 지나는 것만 줄마다 한 건 */
class LuhnRuleTest {

    private static List<Integer> lines(String text) {
        Rule r = RuleSet.builtin().enabled().stream().filter(x -> LuhnRule.CARD_PLAIN.equals(x.def().id())).findFirst().orElseThrow();
        return r.apply(new Source("A.java", text, null, null, "java")).stream().map(Finding::line).toList();
    }

    @Test
    void valid() {
        assertTrue(LuhnRule.valid("4030000010001234"));
        assertTrue(LuhnRule.valid("5100000020002000"));
        assertTrue(LuhnRule.valid("4111111111111111"));
        assertFalse(LuhnRule.valid("4030000010001235"));
        assertFalse(LuhnRule.valid("5100000020002001"));
    }

    @Test
    void flagged() {
        assertEquals(List.of(1), lines("String pan = \"4030000010001234\";\n"));
        assertEquals(List.of(2), lines("class A {\n  String a = \"5100000020002000\", b = \"4030000010001234\";\n}\n"), "한 줄 둘 — 한 건");
    }

    @Test
    void notFlagged() {
        assertEquals(List.of(), lines("String serial = \"4030000010001235\";\n"), "Luhn 실패");
        assertEquals(List.of(), lines("// 예: 4030000010001234 — 주석\nint x;\n"), "주석 속 번호");
        assertEquals(List.of(), lines("String id = \"40300000100012340000\";\n"), "20 자리 안의 16 자리");
        assertEquals(List.of(), lines("String v = \"1.4030000010001234\";\n"), "소수점 뒤");
        assertEquals(List.of(), lines("String v = \"x-4030000010001234\";\n"), "대시 뒤");
        assertEquals(List.of(), lines("String v = \"1030000010001234\";\n"), "첫 자리 3~6 밖");
    }
}
