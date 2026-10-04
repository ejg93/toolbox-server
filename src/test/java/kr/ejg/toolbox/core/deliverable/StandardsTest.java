package kr.ejg.toolbox.core.deliverable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.dict.Dictionaries;
import kr.ejg.toolbox.core.logical.ColumnInputs;
import kr.ejg.toolbox.core.logical.DomainMatcher;
import kr.ejg.toolbox.core.logical.LogicalRun;
import kr.ejg.toolbox.core.db.Db;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 2-2 — 3-2 샘플(컬럼 1000·기관표준단어)로 05·06·07 골든 + 규칙 R21~R26. 행은 3-6 후보와 같은 코드라 개수도 같아야 한다 */
class StandardsTest {

    static final Path SAMPLE = Path.of("src/test/resources/sample/logical");
    static LogicalRun.Result result;
    static Dictionaries dicts;
    static DomainMatcher dm;
    static List<Doc> docs;

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        try (Db db = Db.open(tmp)) {
            DictStore store = new DictStore(db);
            store.importMoiFile(Files.readAllBytes(SAMPLE.resolve("moi-words-20251101.csv")), "moi-words-20251101.csv"); // 동봉본과 끊음(0-32)
            store.importMoiDomains();
            store.importOrg(Files.readAllBytes(SAMPLE.resolve("org-words.csv")));
            dicts = store.load();
            dm = new DomainMatcher(store.domains());
        }
        result = LogicalRun.run(ColumnInputs.fromCsv(Files.readAllBytes(SAMPLE.resolve("columns-1000.csv")), null), dicts, List.of("TB"), true);
        docs = Standards.build(result, dicts, dm, new Standards.Options("행정기관", "정보화팀", "SAMPLE", true));
    }

    static Doc doc(String no) {
        return docs.stream().filter(d -> d.no().equals(no)).findFirst().orElseThrow();
    }

    static int csvRows(String kind) throws Exception {
        return Files.readString(Path.of("src/test/resources/golden/logical/candidates-" + kind + ".csv"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").split("\n").length - 1;
    }

    @Test
    void golden() {
        for (Doc d : docs) {
            GoldenFiles.assertJson("deliverable/sample-" + d.no() + ".json", d);
        }
    }

    @Test
    void sameRowsAsCandidateCsv() throws Exception {
        assertEquals(csvRows("words"), doc("05").rows().size(), "06 표준단어사전 CSV 와 같은 행");
        assertEquals(csvRows("domains"), doc("06").rows().size());
        assertEquals(csvRows("terms"), doc("07").rows().size(), "검토 제외 끈 용어 후보와 같은 행");
    }

    @Test
    void r21OrgDeptAndBlankEnactDate() {
        for (Doc d : docs) {
            assertEquals("행정기관", d.cell(0, "기관명"), d.no());
            assertEquals("정보화팀", d.cell(0, "관리부서명"), d.no());
            assertEquals("", d.cell(0, "제정일자"), d.no() + " 제정일자는 수기");
            assertEquals("SAMPLE", d.cell(0, "DB명"));
        }
    }

    @Test
    void r22r23WordMetaOnlyForCommonWords() {
        Doc d05 = doc("05");
        int yn = -1;
        boolean sawOrg = false;
        for (int i = 0; i < d05.rows().size(); i++) {
            if ("YN".equals(d05.cell(i, "단어 영문약어명"))) {
                yn = i;
            }
            if (String.valueOf(d05.cell(i, "특이사항")).startsWith("기관표준단어")) {
                sawOrg = true;
                assertEquals("", d05.cell(i, "단어 영문명"), "기관 단어는 영문명 빈칸");
                assertEquals("", d05.cell(i, "단어 설명"));
            }
        }
        assertTrue(sawOrg, "샘플엔 기관표준단어 행이 있다");
        assertTrue(yn >= 0, "YN 이 쓰였다");
        assertEquals("여부", d05.cell(yn, "표준단어명"));
        assertEquals("Yes or No", d05.cell(yn, "단어 영문명"), "공통표준단어 영문명");
        assertEquals("Y", d05.cell(yn, "형식단어 여부"), "R23");
        assertEquals("여부", d05.cell(yn, "도메인 분류명"));
        assertFalse(String.valueOf(d05.cell(yn, "단어 설명")).isEmpty());
    }

    @Test
    void r24DomainMatchedVersusReview() {
        Doc d06 = doc("06");
        boolean matched = false;
        boolean review = false;
        for (int i = 0; i < d06.rows().size(); i++) {
            boolean hasGroup = !String.valueOf(d06.cell(i, "표준도메인 그룹명")).isEmpty();
            boolean hasNote = !String.valueOf(d06.cell(i, "특이사항")).isEmpty();
            assertTrue(hasGroup != hasNote, "행안부 일치면 문구 없음, 아니면 문구 있음 — " + d06.rows().get(i));
            matched |= hasGroup;
            review |= hasNote;
        }
        assertTrue(matched && review);
    }

    @Test
    void r25r26Terms() {
        Doc d07 = doc("07");
        assertEquals("테이블", String.valueOf(d07.cell(0, "특이사항")).split(" · ")[0], "첫 행은 테이블(3-6 순서)");
        boolean flagged = false;
        for (int i = 0; i < d07.rows().size(); i++) {
            String note = String.valueOf(d07.cell(i, "특이사항"));
            flagged |= note.contains("미매칭") || note.contains("부분매칭");
        }
        assertTrue(flagged, "검토 필요는 특이사항에");
        long distinct = d07.rows().stream().map(r -> String.valueOf(r.get(5)).toUpperCase() + "|" + String.valueOf(r.get(14)).startsWith("테이블"))
                .distinct().count();
        assertEquals(d07.rows().size(), distinct, "R26 테이블·컬럼 distinct");
    }
}
