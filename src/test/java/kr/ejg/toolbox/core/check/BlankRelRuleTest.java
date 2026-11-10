package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 5-75a — jsp.blankNoopener(설계 61): JSP·HTML 의 target=_blank 인 {@code <a>} 에 rel 이 없으면 태그 첫 줄. 태그 훑기 꼴별 판정 */
class BlankRelRuleTest {

    private static final String JSP = "jsp";
    private static final String BLANK_D = " target=\"_blank\">x</a>";

    private static List<BlankRelRule.Tag> tags(String text) {
        return BlankRelRule.tags(text, JSP);
    }

    private static void assertFlags(String text, int line, char quote) {
        List<BlankRelRule.Tag> t = tags(text);
        assertEquals(1, t.size(), text);
        assertEquals(line, t.get(0).line(), text);
        assertEquals(quote, t.get(0).quote(), text);
    }

    private static void assertNoTag(String text) {
        assertEquals(List.of(), tags(text), text);
    }

    @Test
    void flagged() {
        assertFlags("<a href=\"/x\"" + BLANK_D + "\n", 1, '"');
        assertFlags("<p>\n<a href=\"<c:url value='/a'/>\"" + BLANK_D + "\n", 2, '"');
        assertFlags("<a href=\"<c:out value=\"${v}\"/>\" target='_blank'>x</a>\n", 1, '\'');
        assertFlags("<a class=\"b\"\n   href=\"/y\"\n   target=\"_blank\">여러 줄</a>\n", 1, '"');
        assertFlags("<A HREF=\"/x\" TARGET=\"_BLANK\">x</A>\n", 1, '"');
        assertFlags("<a href=\"/x\" target=_blank>x</a>\n", 1, (char) 0);
        assertFlags("<a href=\"/x\" <c:if test=\"${n}\">target=\"_blank\"</c:if>>x</a>\n", 1, '"');
        assertFlags("<a href=\"<%= url %>\"" + BLANK_D + "\n", 1, '"');
        assertEquals(2, tags("<a href=\"/c\"" + BLANK_D + " <a href=\"/d\"" + BLANK_D + "\n").size(), "한 줄 둘 — 두 건");
    }

    @Test
    void notFlagged() {
        assertNoTag("<a href=\"/x\" target=\"_blank\" rel=\"nofollow\">x</a>\n");
        assertNoTag("<a href=\"/x\" target=\"_blank\"\n   rel=\"noopener\">x</a>\n");
        assertNoTag("<a href=\"/x\" target=\"_self\">x</a>\n");
        assertNoTag("<a href=\"/x\" data-target=\"_blank\">x</a>\n");
        assertNoTag("<a href=\"/x\" th:target=\"_blank\">x</a>\n");
        assertNoTag("<a href=\"/x\" target=\"${t}\">x</a>\n");
        assertNoTag("<script>var h = \"<a href='/z' target=_blank >\";</script>\n");
        assertNoTag("<%-- <a href=\"/z\" target=\"_blank\">주석</a> --%>\n<!-- <a href=\"/z\" target=\"_blank\">주석</a> -->\n");
        assertNoTag("<% out.print(\"<a target=\\\"_blank\\\">\"); %>\n");
        assertNoTag("<a href=\"/x\" target=\"_blank\"");
        assertNoTag("<abbr target=\"_blank\">x</abbr>\n");
        assertNoTag("<a href=\"/x\">x</a>\n");
    }

    @Test
    void atIsAfterTargetValue() {
        String text = "<a href=\"/x\"" + BLANK_D + "\n";
        BlankRelRule.Tag t = tags(text).get(0);
        assertTrue(text.substring(0, t.at()).endsWith("target=\"_blank\""), text.substring(0, t.at()));
    }
}
