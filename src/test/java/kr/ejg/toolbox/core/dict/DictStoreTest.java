package kr.ejg.toolbox.core.dict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void moiLoadsOnceWithPureRules() throws Exception {
        assertEquals(3283, store.importMoi());
        assertEquals(0, store.importMoi(), "두 번째는 건너뛴다");
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
