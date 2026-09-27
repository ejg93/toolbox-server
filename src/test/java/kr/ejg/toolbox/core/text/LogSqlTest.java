package kr.ejg.toolbox.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/** 4-8 — MyBatis 로그 → 복원 SQL. 골든은 픽스처 로그 한 벌 */
class LogSqlTest {

    @Test
    void mybatisGolden() throws IOException {
        String log = Files.readString(Path.of("src/test/resources/fixtures/logsql/mybatis.log"), StandardCharsets.UTF_8);
        GoldenFiles.assertJson("text/logsql-mybatis.json", LogSql.restore(log));
    }

    @Test
    void cases() {
        List<LogSql.Item> r = LogSql.restore("==>  Preparing: SELECT * FROM T WHERE A = ? AND B = '?' AND \"C?\" = ?\r\n"
                + "==> Parameters: x'y(String), 2026-01-02(Date)\r\n"
                + "==>  Preparing: SELECT ?\n"
                + "==>  Preparing: DELETE FROM T WHERE A = ? AND B = ?\n"
                + "==> Parameters: 1(Integer)\n");
        assertEquals(3, r.size(), "문장 여럿");
        assertEquals("SELECT * FROM T WHERE A = 'x''y' AND B = '?' AND \"C?\" = '2026-01-02'", r.get(0).restored(),
                "문자열·따옴표 이름 안 ? 는 건너뛰고 인용은 따옴표 두 번");
        assertNull(r.get(0).warning());
        assertEquals("SELECT ?", r.get(1).restored(), "Parameters 가 다음 Preparing 전에 없으면 원문");
        assertTrue(r.get(1).warning().startsWith("Parameters 줄이 없다"));
        assertEquals("DELETE FROM T WHERE A = ? AND B = ?", r.get(2).restored());
        assertEquals("? 2개 ≠ 파라미터 1개 — 원문 그대로", r.get(2).warning());
        assertEquals("NULL, 7, 'ab', 1.5", LogSql.restore("Preparing: ?, ?, ?, ?\nParameters: null, 7, ab, 1.5\n").get(0).restored(),
                "타입 없으면 수 모양만 그대로, null 은 NULL");
    }
}
