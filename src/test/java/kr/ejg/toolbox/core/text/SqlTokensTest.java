package kr.ejg.toolbox.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 5-57a(설계 47 §3-1) — 위치 있는 SQL 토큰. analyze(SqlTables·SqlJoins)·check(SqlLint) 공용 */
class SqlTokensTest {

    static final String FROM = "FROM";
    static final String WHERE = "WHERE";

    /** 글자 리터럴은 여는 따옴표 자리, 바인드는 # 자리, 따옴표 식별자는 여는 따옴표 자리 */
    @Test
    void positions() {
        List<SqlTokens.Tok> t = SqlTokens.scan("SELECT a, 'x''y' FROM \"T\" WHERE b = #{b}");
        assertEquals(List.of("SELECT", "A", ",", "'", FROM, "T", WHERE, "B", "=", "?"), t.stream().map(SqlTokens.Tok::s).toList());
        assertEquals(List.of(0, 7, 8, 10, 17, 22, 26, 32, 34, 36), t.stream().map(SqlTokens.Tok::at).toList());
        assertEquals(t.stream().map(SqlTokens.Tok::s).toList(), SqlTokens.tokens("SELECT a, 'x''y' FROM \"T\" WHERE b = #{b}"));
    }

    /** 6-47(G2-K10) — \' 는 뒤가 낱말 글자일 때만 글자 안 · PG 달러 인용·Oracle q 인용은 리터럴 하나 · LIKE … ESCAPE '\' 는 그대로 */
    @Test
    void literalShapes() {
        assertEquals(SqlTokens.tokens("SELECT 'ab' FROM t1 WHERE c = 'd'"), SqlTokens.tokens("SELECT 'a\\'b' FROM t1 WHERE c = 'd'"));
        List<String> one = SqlTokens.tokens("SELECT 'x' FROM a");
        assertEquals(4, one.size(), one.toString());
        assertEquals(one, SqlTokens.tokens("SELECT $$it's$$ FROM a"));
        assertEquals(one, SqlTokens.tokens("SELECT $fn$x'y$fn$ FROM a"));
        assertEquals(one, SqlTokens.tokens("SELECT q'[it's]' FROM a"));
        assertEquals(List.of("X", "LIKE", "'", "ESCAPE", "'", "AND", "Y", "=", "1"), SqlTokens.tokens("x LIKE '%\\' ESCAPE '\\' AND y = 1"));
        assertTrue(SqlTokens.tokens("SELECT $$open FROM a").contains("SELECT"), "안 닫힌 $$ 도 끝난다");
    }

    /** C-TBL-KEYWORD · C-TBL-DOLLAR · C-TBL-HASH — 따옴표 친 예약어는 따옴표 꼴로 · 이름에 붙은 ${} 는 ? 하나 · MySQL # 줄 주석 */
    @Test
    void quotedKeywordBindSuffixHashComment() {
        assertEquals(List.of("SELECT", "*", FROM, "\"ORDER\"", "O"), SqlTokens.tokens("SELECT * FROM `order` o"));
        assertEquals(List.of(FROM, "USER_TB"), SqlTokens.tokens("FROM \"user_tb\""));
        assertEquals(List.of(FROM, "?", WHERE, "A", "=", "?"), SqlTokens.tokens("FROM TB_LOG_${ym} WHERE A = #{a}"));
        assertEquals(List.of(FROM, "T1", WHERE, "X", "=", "1"), SqlTokens.tokens("FROM T1 # FROM T9\nWHERE X = 1"));
        assertEquals(List.of(FROM, "#TEMP", WHERE, "COL#", "=", "1"), SqlTokens.tokens("FROM #TEMP WHERE COL# = 1"));
        assertEquals("ORDER", SqlTokens.scan("FROM `order`").get(1).s(), "scan 의 글자는 그대로(SqlLint)");
    }

    @Test
    void wordsAndStop() {
        assertTrue(SqlTokens.word("A1"));
        assertTrue(SqlTokens.word("#TMP"));
        assertFalse(SqlTokens.word("("));
        assertTrue(SqlTokens.STOP.contains(FROM));
        assertFalse(SqlTokens.STOP.contains("COMTNBBS"));
    }
}
