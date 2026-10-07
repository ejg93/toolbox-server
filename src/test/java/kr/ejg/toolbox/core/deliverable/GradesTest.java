package kr.ejg.toolbox.core.deliverable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 2-13 — 등급표의 모든 (문서, 열)이 그 문서 열 목록에 있다(열 이름이 바뀌면 같이 고치게) */
class GradesTest {

    static final Map<String, List<String>> COLS = Map.ofEntries(Map.entry("01", Definitions.COLS_01), Map.entry("02", Definitions.COLS_02),
            Map.entry("03", Definitions.COLS_03), Map.entry("04", Definitions.COLS_04), Map.entry("05", Standards.COLS_05),
            Map.entry("06", Standards.COLS_06), Map.entry("07", Standards.COLS_07), Map.entry("08", CodeAndLink.COLS_08),
            Map.entry("09", CodeAndLink.COLS_09), Map.entry("10", Definitions.COLS_10), Map.entry("11", Definitions.COLS_11));

    @Test
    void everyGradedColumnExists() {
        List<String> bad = new ArrayList<>();
        for (Grades.Grade g : Grades.all()) {
            List<String> cols = COLS.get(g.doc());
            if (cols == null || !cols.contains(g.column())) {
                bad.add(g.doc() + " " + g.column());
            }
        }
        assertEquals(List.of(), bad);
    }

    @Test
    void levelsAreThreeWordsAndHowIsFilled() {
        for (Grades.Grade g : Grades.all()) {
            assertTrue(Set.of(Grades.AUTO, Grades.GUESS, Grades.MANUAL).contains(g.level()), g.toString());
            assertTrue(!g.how().isBlank(), g.toString());
        }
        assertEquals(Grades.GUESS, Grades.of("03").stream().filter(g -> g.column().equals("개인정보 여부")).findFirst().orElseThrow().level());
    }

    /** 1-34 — 문서 판정은 고정값(feasibility 표). 12개 번호순, ● 는 02·03·08·10·11·18 */
    @Test
    void docMarksArePinned() {
        List<String> nos = Grades.marks().stream().map(Grades.DocMark::no).toList();
        assertEquals(List.of("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "18"), nos);
        for (Grades.DocMark d : Grades.marks()) {
            assertTrue(Set.of(Grades.FULL, Grades.PART, Grades.NONE).contains(d.mark()), d.toString());
            assertTrue(!d.reason().isBlank() && !d.name().isBlank(), d.toString());
        }
        assertEquals(Set.of("02", "03", "08", "10", "11", "18"),
                Grades.marks().stream().filter(d -> d.mark().equals(Grades.FULL)).map(Grades.DocMark::no).collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of(5, 2, 8), java.util.Arrays.stream(Grades.counts("02")).boxed().toList(), "02 자동·추정·수동");
        assertEquals(List.of(0, 0, 0), java.util.Arrays.stream(Grades.counts("18")).boxed().toList());
    }
}
