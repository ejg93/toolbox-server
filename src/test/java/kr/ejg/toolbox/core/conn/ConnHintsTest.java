package kr.ejg.toolbox.core.conn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 1-18 — 접속 실패 안내. 표의 줄은 No suitable driver 와 V-21 이 옛 판에서 뜬 오류문뿐이다 */
class ConnHintsTest {

    static final ConnHints HINTS = ConnHints.load();

    @Test
    void loadsAndCompiles() {
        assertFalse(HINTS.hints().isEmpty());
        HINTS.hints().forEach(h -> assertTrue(h.hint() != null && !h.hint().isBlank(), h.toString()));
    }

    @Test
    void noSuitableDriver() {
        Optional<String> h = HINTS.hint("oracle", new SQLException("No suitable driver found for jdbc:nosuch:", "08001"));
        assertTrue(h.orElse("").contains("drivers"), h.toString());
    }

    @Test
    void unknownErrorHasNoHint() {
        assertEquals(Optional.empty(), HINTS.hint("postgresql", new SQLException("FATAL: password authentication failed", "28P01")));
    }

    /** V-21 목록 「판 | 드라이버 | SQLState | 코드 | 첫 줄」 — 줄마다 안내가 하나씩 맞는다 */
    @Test
    void everyOldVersionErrorHasHint() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("src/test/resources/golden/corpus/dbold-conn-errors.txt"), StandardCharsets.UTF_8)
                .stream().filter(l -> !l.isBlank()).toList();
        assertFalse(lines.isEmpty(), "V-21 이 남긴 오류문이 있어야 한다");
        for (String l : lines) {
            String[] f = l.split(" \\| ", 5);
            String dialect = f[0].replaceAll("\\d+$", "").replace("mysql", "mariadb").replace("postgres", "postgresql");
            SQLException e = new SQLException(f[4], f[2].equals("null") ? null : f[2], Integer.parseInt(f[3]));
            assertTrue(HINTS.hint(dialect, e).isPresent(), "안내 없음: " + l);
        }
    }
}
