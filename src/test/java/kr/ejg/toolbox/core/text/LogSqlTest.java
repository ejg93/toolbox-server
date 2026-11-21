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

    /** R-G-9 — 스레드가 섞인 로그는 앞머리(스레드·로거)가 같은 줄끼리 짝짓는다 */
    @Test
    void interleavedThreadsPairByPrefix() {
        List<LogSql.Item> r = LogSql.restore("[exec-1] a.UserMapper.sel : ==>  Preparing: select * from A where id = ?\n"
                + "[exec-2] a.DeptMapper.sel : ==>  Preparing: select * from B where id = ?\n"
                + "[exec-1] a.UserMapper.sel : ==> Parameters: 111(Integer)\n"
                + "[exec-2] a.DeptMapper.sel : ==> Parameters: 222(Integer)\n");
        assertEquals("select * from A where id = 111", r.get(0).restored());
        assertEquals("select * from B where id = 222", r.get(1).restored());
        assertNull(r.get(0).warning());
        assertNull(r.get(1).warning());
    }

    /** G2-K9 — 같은 앞머리의 Preparing 이 연달아 오면 가를 수 없다 — 뒤 문장은 복원하되 경고 */
    @Test
    void samePrefixBackToBackIsWarned() {
        List<LogSql.Item> r = LogSql.restore("Preparing: select * from A where id = ?\nPreparing: select * from B where id = ?\nParameters: 1(Integer)\n");
        assertEquals("Parameters 줄이 없다 — 원문 그대로", r.get(0).warning());
        assertEquals("select * from B where id = 1", r.get(1).restored());
        assertEquals(LogSql.MAYBE_PREVIOUS, r.get(1).warning());
    }

    /** 마무리 리뷰 I3 — 같은 앞머리 둘에 Parameters 도 둘이면 어느 쪽도 확신 못 한다 · 열쇠가 다른 폴백은 붙이되 경고 */
    @Test
    void ambiguousPairsAreAllWarned() {
        List<LogSql.Item> r = LogSql.restore("Preparing: select * from A where id = ?\nPreparing: select * from B where id = ?\n"
                + "Parameters: 1(Integer)\nParameters: 2(Integer)\n");
        assertEquals(LogSql.MAYBE_PREVIOUS, r.get(0).warning());
        assertEquals(LogSql.MAYBE_PREVIOUS, r.get(1).warning());
        List<LogSql.Item> f = LogSql.restore("[exec-1] a.A.sel : ==>  Preparing: select * from A where id = ?\n"
                + "[exec-2] b.B.sel : ==> Parameters: 222(Integer)\n"
                + "[exec-1] a.A.sel : ==> Parameters: 111(Integer)\n");
        assertEquals(LogSql.MAYBE_PREVIOUS, f.get(0).warning(), f.get(0).restored());
    }

    /** 시각·pid 는 줄마다 달라도 짝 열쇠가 아니다 */
    @Test
    void timestampsDoNotSplitPairs() {
        List<LogSql.Item> r = LogSql.restore("2026-09-27 10:00:00.001 DEBUG 1234 --- [main] a.M.s : ==>  Preparing: select ?\n"
                + "2026-09-27 10:00:00.007 DEBUG 1234 --- [main] a.M.s : ==> Parameters: 1(Integer)\n");
        assertEquals("select 1", r.get(0).restored());
        assertNull(r.get(0).warning());
    }

    /** G1-K18 — 타입 없는 긴 파라미터 줄이 제곱 시간이 아니다 */
    @Test
    void untypedParamsAreLinear() {
        String log = "Preparing: ?\nParameters: " + "a, ".repeat(200_000) + "a\n";
        List<LogSql.Item> r = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> LogSql.restore(log));
        assertTrue(r.get(0).warning().startsWith("? 1개 ≠"), r.get(0).warning());
    }
}
