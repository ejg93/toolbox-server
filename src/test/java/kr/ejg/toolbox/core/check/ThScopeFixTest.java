package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 설계 62 — a11y.thScope 고침의 표 판정. scan 이 준 자리에 scope 를 끼운 글과 건너뜀 사유를 본다 */
class ThScopeFixTest {
    private static final String TABLE_END = "</table>";
    private static final String LINE1 = "줄 1: ";

    /** scan 이 준 자리에 뒤에서부터 scope 를 끼운 글 */
    static String fix(String html) {
        ThScopeFix.Scan sc = ThScopeFix.scan(html, "jsp");
        List<ThScopeFix.Th> ths = new ArrayList<>(sc.ths());
        StringBuilder sb = new StringBuilder(html);
        for (int i = ths.size() - 1; i >= 0; i--) {
            sb.insert(ths.get(i).at(), " scope=\"" + ths.get(i).scope() + "\"");
        }
        return sb.toString();
    }

    static List<String> skips(String html) {
        return ThScopeFix.scan(html, "jsp").skips();
    }

    @Test
    void colAndRow() {
        assertEquals("<table><thead><tr><th scope=\"col\">a</th><th scope=\"col\">b</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody>"
                + TABLE_END, fix("<table><thead><tr><th>a</th><th>b</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody>" + TABLE_END),
                "thead 안 th");
        assertEquals("<table><tr><th scope=\"col\">a</th></tr><tr><td>1</td></tr>" + TABLE_END, fix("<table><tr><th>a</th></tr><tr><td>1</td></tr>"
                + TABLE_END), "첫 행만 th");
        assertEquals("<table><tr><th scope=\"row\">a</th><td>1</td><th scope=\"row\">b</th><td>2</td></tr>" + TABLE_END,
                fix("<table><tr><th>a</th><td>1</td><th>b</th><td>2</td></tr>" + TABLE_END), "행 머리 — 한 행에 th 둘");
        assertEquals("<table><thead><tr><th scope=\"col\">h</th><th scope=\"col\">v</th></tr></thead><tr><th scope=\"row\">a</th><td>1</td></tr>"
                + TABLE_END, fix("<table><thead><tr><th>h</th><th>v</th></tr></thead><tr><th>a</th><td>1</td></tr>" + TABLE_END), "thead + 행 머리");
        assertEquals("<table><tr><th scope=\"row\">a</th><td><table><tr><th scope=\"col\">b</th></tr>" + TABLE_END + "</td></tr>" + TABLE_END,
                fix("<table><tr><th>a</th><td><table><tr><th>b</th></tr>" + TABLE_END + "</td></tr>" + TABLE_END), "중첩 — 안쪽은 자기 표");
        assertEquals("<TABLE><TR><TH scope=\"col\">a</TH></TR></TABLE>", fix("<TABLE><TR><TH>a</TH></TR></TABLE>"), "대문자");
        assertEquals("<table><tr><th scope=\"col\"\n class=\"w\">a</th></tr>" + TABLE_END, fix("<table><tr><th\n class=\"w\">a</th></tr>" + TABLE_END),
                "여러 줄 th — <th 바로 뒤");
        assertEquals("<table><tr><th scope=\"col\">a</th></tr>", fix("<table><tr><th>a</th></tr>"), "안 닫힌 표(JSP 조각)도 판정");
    }

    @Test
    void alreadyScoped() {
        assertEquals("<table><tr><th scope=\"col\">a</th><th scope=\"col\">b</th></tr>" + TABLE_END,
                fix("<table><tr><th scope=\"col\">a</th><th>b</th></tr>" + TABLE_END), "이미 scope 인 th 는 목록 밖");
        assertEquals("<table><tr><th headers=\"x\">a</th><th scope=\"col\">b</th></tr>" + TABLE_END,
                fix("<table><tr><th headers=\"x\">a</th><th>b</th></tr>" + TABLE_END), "headers 도 같다");
    }

    @Test
    void skipsWholeTable() {
        String notFirst = "<table><tr><td>1</td></tr><tr><th>a</th></tr>" + TABLE_END;
        assertEquals(notFirst, fix(notFirst));
        assertEquals(List.of(LINE1 + ThScopeFix.NOT_FIRST), skips(notFirst));
        String mid = "<table><tr><th>a</th><td>1</td></tr>\n<tr><td>1</td><th>h</th></tr>" + TABLE_END;
        assertEquals(mid, fix(mid), "한 행이라도 못 가르면 표째 — 행 머리 행도 안 끼운다");
        assertEquals(List.of(LINE1 + ThScopeFix.MID_ROW), skips(mid));
        String outside = "<th>a</th>\n<th>b</th>";
        assertEquals(outside, fix(outside));
        assertEquals(List.of(LINE1 + ThScopeFix.OUTSIDE), skips(outside), "table 밖 th 는 한 번");
        String noRow = "<table><th>a</th>" + TABLE_END;
        assertEquals(noRow, fix(noRow));
        assertEquals(List.of(LINE1 + ThScopeFix.NO_ROW), skips(noRow));
        String pair = "<table><tr><th>a</th><th>b</th><td>1</td></tr>\n<tr><th>c</th><td>2</td></tr>" + TABLE_END;
        assertEquals(pair, fix(pair), "이웃 th 가 있으면 행 머리 행도 안 끼운다");
        assertEquals(List.of(LINE1 + ThScopeFix.TH_PAIR), skips(pair));
    }

    /** 5-81 — 파일 끝 <th 도 토큰(when 과 같은 경계) */
    @Test
    void lineEndTh() {
        assertEquals("<table><tr><th scope=\"col\"", fix("<table><tr><th"), "안 닫힌 th 도 자리");
    }

    /** 5-81 — 태그 안 따옴표 값을 공백으로. 길이·줄바꿈·따옴표 글자 보존 */
    @Test
    void quietAttrs() {
        assertEquals("<a b=\"   \">x<c d='  '>", ThScopeFix.quietAttrs("<a b=\"<th\">x<c d='<t'>"));
        assertEquals("<a b=\"\n\">", ThScopeFix.quietAttrs("<a b=\"\n\">"), "줄바꿈 보존");
        for (String s : List.of("값<기준 \"x\"<td>", "<1 \"q\"", "<한 \"q\"")) {
            assertEquals(s, ThScopeFix.quietAttrs(s), "ASCII 글자가 아닌 것 뒤 < 는 태그가 아니다: " + s);
        }
        assertEquals("<b \"  \">", ThScopeFix.quietAttrs("<b \"xx\">"), "<b 는 태그 시작");
    }

    @Test
    void notTh() {
        String dead = "<script>var s = \"<table><tr><th>x</th></tr></table>\";</script>\n<%-- <table><tr><th>y</th></tr></table> --%>\n"
                + "<!-- <th>z</th> -->\n<p><%= \"<th>\" %></p>";
        assertEquals(dead, fix(dead), "script·주석·스크립틀릿 안");
        assertEquals(List.of(), skips(dead));
        String block = "<table><tr><th:block th:each=\"x : ${xs}\"><td>1</td></th:block></tr>" + TABLE_END;
        assertEquals(block, fix(block), "<th:block> 은 th 가 아니다");
        assertEquals(List.of(), skips(block));
        String attr = "<table><tr><th>a</th><td><input value=\"<th>x</th>\" onclick=\"f('<table><tr><td>')\"></td></tr>" + TABLE_END;
        assertEquals(attr.replace("<th>a", "<th scope=\"row\">a"), fix(attr), "속성 값 안 <th·<table 은 칸·표가 아니다(5-81)");
        assertEquals(List.of(), skips(attr));
        String phantom = "<input value=\"<table>\"><tr><th>a</th></tr>";
        assertEquals(phantom, fix(phantom), "값 안 <table 이 표를 열지 않는다 — 조각은 조각");
        assertEquals(List.of(LINE1 + ThScopeFix.OUTSIDE), skips(phantom));
    }
}
