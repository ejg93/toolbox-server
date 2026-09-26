package kr.ejg.toolbox.core.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * 3-5 — 방언 다섯은 순수본 genDDL 을 Puppeteer 로 뜬 JS 실물과 글자까지 같다(시각 고정 「2026. 9. 27. 오전 9:00:00」).
 * Tibero 는 순수본에 없는 방언이라 자바 골든.
 */
class CommentDdlTest {

    static final LocalDateTime AT = LocalDateTime.of(2026, 9, 27, 9, 0, 0);

    @ParameterizedTest
    @CsvSource({"ORACLE,oracle", "MARIADB,mysql", "POSTGRESQL,pg", "MSSQL,mssql", "SYBASE,sybase"})
    void matchesPureJs(Dialect d, String js) throws Exception {
        String expected = Files.readString(LogicalRunSampleTest.GOLDEN.resolve("comments-" + js + ".sql"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        CommentDdl.Ddl ddl = CommentDdl.generate(LogicalRunSampleTest.sampleResult(), d, true, AT);
        assertEquals(expected, ddl.text());
        assertEquals(1048, ddl.lines());
        assertEquals(388, ddl.reviewCount());
    }

    @Test
    void tiberoUsesOracleSyntax() throws Exception {
        CommentDdl.Ddl ddl = CommentDdl.generate(LogicalRunSampleTest.sampleResult(), Dialect.TIBERO, true, AT);
        GoldenFiles.assertText("logical/comments-tibero.sql", ddl.text());
        assertTrue(ddl.text().contains("COMMENT ON TABLE  SHOP.TB_CUST_MST IS '고객수분';"));
    }

    @Test
    void quotesAndReviewMarkPosition() {
        LogicalRun.Result r = new LogicalRun.Result(
                List.of(new LogicalRun.Row("S", "T", "C1", "고객's", "word", List.of(), "", "", "", "", "", "1"),
                        new LogicalRun.Row("S", "T", "C2", "고객X", "mix", List.of("X"), "", "", "", "", "", "2")),
                List.of(), List.of(), java.util.Map.of(), java.util.Set.of(), new LogicalRun.Stats(2, 0, 1, 0, 1, 0));
        String ora = CommentDdl.generate(r, Dialect.ORACLE, true, AT).text();
        assertTrue(ora.contains("IS '고객''s';"), "작은따옴표 두 번");
        assertTrue(ora.contains("\n-- [검토] COMMENT ON COLUMN S.T.C2 IS '고객X';"), "실행형은 앞에");
        String syb = CommentDdl.generate(r, Dialect.SYBASE, true, AT).text();
        assertTrue(syb.contains("= '고객X'   ← [검토] 논리명에 영문 잔존"), "대조표형은 뒤에");
        assertEquals(List.of("COMMENT ON COLUMN S.T.C1 IS '고객''s';"), CommentDdl.executableLines(r, Dialect.ORACLE, true),
                "실행 줄은 [검토] 를 뺀다(3-9)");
        assertEquals(List.of(), CommentDdl.executableLines(r, Dialect.SYBASE, true));
    }
}
