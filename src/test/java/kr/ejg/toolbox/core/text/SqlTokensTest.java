package kr.ejg.toolbox.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 5-57a(설계 47 §3-1) — 위치 있는 SQL 토큰. analyze(SqlTables·SqlJoins)·check(SqlLint) 공용 */
class SqlTokensTest {

    /** 글자 리터럴은 여는 따옴표 자리, 바인드는 # 자리, 따옴표 식별자는 여는 따옴표 자리 */
    @Test
    void positions() {
        List<SqlTokens.Tok> t = SqlTokens.scan("SELECT a, 'x''y' FROM \"T\" WHERE b = #{b}");
        assertEquals(List.of("SELECT", "A", ",", "'", "FROM", "T", "WHERE", "B", "=", "?"), t.stream().map(SqlTokens.Tok::s).toList());
        assertEquals(List.of(0, 7, 8, 10, 17, 22, 26, 32, 34, 36), t.stream().map(SqlTokens.Tok::at).toList());
        assertEquals(t.stream().map(SqlTokens.Tok::s).toList(), SqlTokens.tokens("SELECT a, 'x''y' FROM \"T\" WHERE b = #{b}"));
    }

    @Test
    void wordsAndStop() {
        assertTrue(SqlTokens.word("A1"));
        assertTrue(SqlTokens.word("#TMP"));
        assertFalse(SqlTokens.word("("));
        assertTrue(SqlTokens.STOP.contains("FROM"));
        assertFalse(SqlTokens.STOP.contains("COMTNBBS"));
    }
}
