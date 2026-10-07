package kr.ejg.toolbox.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 1-33 — 열 너비는 머리·값 중 가장 긴 글 폭 + 2(한글 2·그 밖 1), 6~60 */
class ColumnWidthsTest {

    /** 사용자 예 ① — 값 「DB서비스모니터링로그정보」(2 + 11×2 = 24)가 머리(13)보다 길다 → 26 */
    @Test
    void longestValueWins() {
        ColumnWidths w = new ColumnWidths(1);
        w.see(0, "관련 엔터티명");
        for (String v : new String[] {"행정코드", "공통분류코드", "DB서비스모니터링로그정보"}) {
            w.see(0, v);
        }
        assertEquals(26, w.chars(0));
        assertFalse(w.wraps(0));
    }

    /** 사용자 예 ② — 값 「EGOV」 만이면 머리 「테이블 소유자」(13)가 가장 길다 → 15 */
    @Test
    void headerIsACandidate() {
        ColumnWidths w = new ColumnWidths(1);
        w.see(0, "테이블 소유자");
        w.see(0, "EGOV");
        assertEquals(15, w.chars(0));
    }

    @Test
    void boundsAndWrap() {
        ColumnWidths w = new ColumnWidths(3);
        w.see(1, null);
        w.see(1, "");
        assertEquals(ColumnWidths.MIN, w.chars(1), "빈 열은 하한");
        w.see(2, "x".repeat(80));
        assertEquals(ColumnWidths.MAX, w.chars(2), "상한에서 끊는다");
        assertTrue(w.wraps(2), "넘는 열은 줄바꿈");
        w.see(0, "짧은 줄\n" + "x".repeat(20));
        assertEquals(22, w.chars(0), "여러 줄이면 가장 긴 줄");
    }
}
