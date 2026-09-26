package kr.ejg.toolbox.core.dict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.text.Csv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 3-1 — 기대 수치는 순수본 applyDict·applyOv 규칙으로 샘플을 센 값(2026-09-27 node 실측):
 * 행안부 3,284행 중 같은 약어(ACEF 「실효」, 뒷줄은 폐기 기록) 1 → 3,283. 기관 111. 도메인 129.
 */
class DictStoreTest {

    @TempDir
    Path tmp;

    Db db;
    DictStore store;

    @BeforeEach
    void up() {
        db = Db.open(tmp);
        store = new DictStore(db);
    }

    @AfterEach
    void down() {
        db.close();
    }

    static final Path SAMPLE_MOI = Path.of("src/test/resources/sample/logical/moi-words-20251101.csv");

    /** 동봉본은 판이 바뀐다 — 건수는 안 박고 적재가 되는지만(0-32). 순수본 규칙 수치는 아래 샘플 기준 */
    @Test
    void bundledLoadsOnce() throws Exception {
        assertTrue(store.importMoi() > 3000);
        assertEquals(0, store.importMoi(), "같은 판이면 두 번째는 건너뛴다");
        assertTrue(store.moiSource().startsWith("moi-"));
        List<List<String>> rows = kr.ejg.toolbox.core.text.Csv.parse(kr.ejg.toolbox.core.text.Csv.decode(store.moiCsv()));
        assertTrue(rows.get(0).contains("공통표준단어영문약어명"), "행안부 머리");
    }

    @Test
    void moiLoadsOnceWithPureRules() throws Exception {
        assertEquals(3283, store.importMoiFile(Files.readAllBytes(SAMPLE_MOI), "moi-words-20251101.csv").imported());
        store.importMoiDomains();
        assertEquals(3283, store.count("word"));
        assertEquals(129, store.domains().size());
        DictStore.Domain first = store.domains().get(0);
        assertEquals("가격N9,2", first.name());
        assertEquals("NUMERIC", first.dataType());

        Dictionaries d = store.load();
        assertEquals(3283, d.word().size());
        assertEquals(3281, d.domKor().size(), "순수본 DOMKOR 키 수와 같다");
        assertEquals("실효", d.word().get("ACEF"));
        assertEquals("N", d.wordMeta().get("ACEF").formWord());
    }

    /** 0-32 ① — 옛 판이 든 DB 를 새 동봉본 jar 로 열면 공통표준단어만 바뀐다 */
    @Test
    void olderEditionIsReplacedKeepingOrgAndUser() throws Exception {
        byte[] sample = Files.readAllBytes(SAMPLE_MOI);
        assertEquals("moi-20240101", store.importMoiFile(sample, "행정안전부_공공데이터 공통표준단어_20240101.csv").source());
        store.importOrg(Files.readAllBytes(Path.of("src/test/resources/sample/logical/org-words.csv")));
        store.putUser("ZZQ", "테스트");
        assertTrue(Files.exists(store.rawMoi()), "올린 원본을 둔다");
        assertTrue(store.importMoi() > 3000, "동봉본(더 새 판)으로 교체");
        assertEquals(DictStore.MOI_SOURCE, store.moiSource());
        assertEquals(111, store.count("org"), "기관 그대로");
        assertEquals("테스트", store.load().user().get("ZZQ"), "사용자 그대로");
        assertFalse(Files.exists(store.rawMoi()), "옛 판 원본은 버린다");
    }

    /** 0-32 ② — 사용자가 올린 더 새 판은 다음 기동이 동봉본으로 덮지 않는다 */
    @Test
    void newerUploadIsNotDowngraded() throws Exception {
        byte[] sample = Files.readAllBytes(SAMPLE_MOI);
        store.importMoiFile(sample, "공통표준단어_20991231.csv");
        assertEquals(0, store.importMoi());
        assertEquals("moi-20991231", store.moiSource());
        assertEquals(sample.length, store.moiCsv().length, "사용여부 CSV 는 올린 원본으로");
        assertEquals("manual-20260927", DictStore.sourceOf("내 단어.csv", java.time.LocalDate.of(2026, 9, 27)), "날짜 없으면 manual");
        assertFalse(DictStore.olderThan("manual-20260927", "moi-20251101"), "올린 날이 동봉본 판보다 뒤면 안 덮는다");
        assertFalse(DictStore.olderThan(null, "moi-20251101"));
    }

    @Test
    void badHeaderUploadKeepsCurrentWords() throws Exception {
        store.importMoi();
        int before = store.count("word");
        byte[] bad = "단어,약어\n가,GA\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> store.importMoiFile(bad, "x_20991231.csv"), "행안부 머리가 아니면 멈춘다");
        assertEquals(before, store.count("word"), "되돌린다");
        assertEquals(DictStore.MOI_SOURCE, store.moiSource());
    }

    @Test
    void orgReplacesAndUserUpserts() throws Exception {
        byte[] org = Files.readAllBytes(Path.of("src/test/resources/sample/logical/org-words.csv"));
        assertEquals(111, store.importOrg(org));
        assertEquals(111, store.importOrg(org), "통째 교체 — 쌓이지 않는다");
        assertEquals(111, store.count("org"));

        store.putUser("upd", "수정");
        store.putUser("UPD", "변경");
        store.putUser("tel", "전화");
        Dictionaries d = store.load();
        assertEquals("변경", d.user().get("UPD"), "같은 약어는 바꾼다");
        assertEquals(2, d.user().size());
        assertTrue(store.deleteUser("tel"));
        assertEquals(1, store.count("user"));
        assertThrows(IllegalArgumentException.class, () -> store.putUser(" ", "x"));
        assertEquals("UPD", store.words("up", "user").get(0).abbr());
    }

    @Test
    void csvParsesLikePure() {
        assertEquals(java.util.List.of(java.util.List.of("a", "b,c", "d\"e")),
                Csv.parse("\"a\",\"b,c\",\"d\"\"e\"\r\n\r\n , \n"), "빈 줄·공백만 줄은 뺀다");
        assertEquals('\t', Csv.detectDelim("a\tb\tc\n1\t2\t3"));
        assertEquals(1, Csv.guess(java.util.List.of("OWNER", "TABLE_NAME"), java.util.List.of("TABLE_NAME", "테이블"), false));
        assertEquals(0, Csv.guess(java.util.List.of("x", "y"), java.util.List.of("z"), false));
        assertEquals(-1, Csv.guess(java.util.List.of("x", "y"), java.util.List.of("z"), true));
        assertEquals("한글", Csv.decode(new byte[] {(byte) 0xC7, (byte) 0xD1, (byte) 0xB1, (byte) 0xDB}), "CP949 로 떨어진다");
    }
}
