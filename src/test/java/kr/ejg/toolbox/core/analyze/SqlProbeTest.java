package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.check.Source;
import kr.ejg.toolbox.core.conn.ReadOnlyJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 6-34a — 정적 SELECT 를 WHERE 1=0 으로 H2 에 묻는다(읽기 전용 래퍼). 동적·${}·SELECT 아님·방언 파일 고르기 */
class SqlProbeTest {

    Connection raw;
    Connection ro;

    @BeforeEach
    void open() throws Exception {
        raw = DriverManager.getConnection("jdbc:h2:mem:probe" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement st = raw.createStatement()) {
            st.execute("CREATE TABLE T(A INT, B VARCHAR(10))");
            st.execute("INSERT INTO T VALUES (1, 'x')");
        }
        ro = ReadOnlyJdbc.wrap(raw);
    }

    @AfterEach
    void close() throws Exception {
        raw.close();
    }

    static Source xml(String name, String body) {
        return new Source(name, "<mapper namespace=\"P\">\n" + body + "\n</mapper>", null, null, null);
    }

    static Map<String, String> statuses(SqlProbe.Report r) {
        Map<String, String> m = new LinkedHashMap<>();
        r.rows().forEach(o -> m.put(o.nsId(), o.status()));
        return m;
    }

    @Test
    void staticSelectsAreAskedOthersSkipped() {
        SqlProbe.Report r = SqlProbe.run(List.of(xml("P_SQL.xml", """
                <sql id="cols">A, B</sql>
                <sql id="dynCond"><if test="a != null">AND A = #{a}</if></sql>
                <select id="ok">SELECT A, B FROM T WHERE A = #{a} AND B = #{b.name}</select>
                <select id="bad">SELECT A, NOPE FROM T</select>
                <select id="dyn">SELECT A FROM T <where><if test="a">A = #{a}</if></where></select>
                <select id="dollar">SELECT A FROM T ORDER BY ${col}</select>
                <select id="incOk">SELECT <include refid="cols"/> FROM T</select>
                <select id="incDyn">SELECT A FROM T WHERE 1=1 <include refid="dynCond"/></select>
                <select id="semi"><![CDATA[SELECT A FROM T WHERE A < #{a};]]></select>
                <update id="upd">UPDATE T SET A = 2</update>
                """)), ro, "h2", null);
        assertEquals(Map.of("P.bad", "ERROR", "P.ok", "PASS", "P.incOk", "PASS", "P.semi", "PASS", "P.dyn", "SKIP_DYNAMIC",
                "P.incDyn", "SKIP_DYNAMIC", "P.dollar", "SKIP_DOLLAR"), statuses(r));
        assertEquals(List.of("P.bad", "P.incOk", "P.ok", "P.semi", "P.dyn", "P.incDyn", "P.dollar"),
                r.rows().stream().map(SqlProbe.Outcome::nsId).toList(), "ERROR 먼저, 그다음 PASS·건너뜀 — 상태 안은 문장 id 순");
        assertEquals(List.of(7, 3, 1, 3), List.of(r.total(), r.passed(), r.failed(), r.skipped()), "update 는 세지 않는다");
        assertEquals(Map.of("SKIP_DYNAMIC", 2, "SKIP_DOLLAR", 1), r.skippedBy());
        assertTrue(r.rows().get(0).message().contains("NOPE"), r.rows().get(0).message());
    }

    /** 방언 파일 둘(같은 ns.id) — 접속 방언에 맞는 파일만 던진다. tibero 는 oracle 파일, mariadb 는 maria 없으면 mysql, 맞는 파일 없으면 건너뜀 */
    @Test
    void dialectFileIsPicked() {
        List<Source> two = List.of(xml("X_SQL_oracle.xml", "<select id=\"q\">SELECT NOPE FROM T</select>"),
                xml("X_SQL_mysql.xml", "<select id=\"q\">SELECT A FROM T</select>"));
        Map<String, String> got = new LinkedHashMap<>();
        for (String d : List.of("mysql", "mariadb", "oracle", "tibero", "h2", "postgresql")) {
            got.put(d, SqlProbe.run(two, ro, d, null).rows().get(0).status());
        }
        assertEquals(Map.of("mysql", "PASS", "mariadb", "PASS", "oracle", "ERROR", "tibero", "ERROR", "h2", "ERROR",
                "postgresql", "SKIP_NO_DIALECT_FILE"), got);
    }

    @Test
    void wrapKeepsCommentsAndFixesMssqlOrderBy() {
        int[] b = new int[1];
        assertEquals("SELECT 1 FROM (\nSELECT A FROM T -- 끝 주석\n) tb_probe WHERE 1=0", SqlProbe.wrap("SELECT A FROM T -- 끝 주석", "h2", b));
        assertEquals(0, b[0]);
        assertEquals("SELECT 1 FROM (\nSELECT A FROM T WHERE A = ? ORDER BY A DESC\nOFFSET 0 ROWS\n) tb_probe WHERE 1=0",
                SqlProbe.wrap("SELECT A FROM T WHERE A = #{a} ORDER BY A DESC;", "mssql", b));
        assertEquals(1, b[0]);
        assertEquals("SELECT 1 FROM (\nSELECT TOP 5 A FROM T ORDER BY A\n) tb_probe WHERE 1=0", SqlProbe.wrap("SELECT TOP 5 A FROM T ORDER BY A", "mssql", b));
        // MySQL·MariaDB — 끝 LIMIT 이 없으면 감싸지 않는다(파생 표의 같은 컬럼 이름 오류를 피한다), 있으면 감싼다
        assertEquals("SELECT u.*, d.dept_id FROM U u JOIN D d ON d.dept_id = u.dept_id\nLIMIT 0",
                SqlProbe.wrap("SELECT u.*, d.dept_id FROM U u JOIN D d ON d.dept_id = u.dept_id", "mariadb", b));
        assertEquals("SELECT 1 FROM (\nSELECT A FROM T LIMIT ?, ?\n) tb_probe WHERE 1=0", SqlProbe.wrap("SELECT A FROM T LIMIT #{o}, #{n}", "mysql", b));
        assertEquals("SELECT A FROM T WHERE B IN (SELECT B FROM X LIMIT 1)\nLIMIT 0",
                SqlProbe.wrap("SELECT A FROM T WHERE B IN (SELECT B FROM X LIMIT 1)", "mysql", b), "괄호 안 LIMIT 은 끝 LIMIT 이 아니다");
    }
}
