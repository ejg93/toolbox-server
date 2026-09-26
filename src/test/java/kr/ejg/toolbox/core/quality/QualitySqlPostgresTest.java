package kr.ejg.toolbox.core.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.sqlrun.ResultTable;
import kr.ejg.toolbox.core.sqlrun.SqlRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 2-6 — 순수본 90번 PG 판이 진짜 PostgreSQL 에서 도는지. 순수본 실사례(ISO·14자리·영문 혼재) 날짜로 [정제]·[검증] 값을 잰다.
 * 여덟 종 전부 첫 문장이 문법 오류 없이 돈다.
 */
@Tag("db")
@Testcontainers
class QualitySqlPostgresTest {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");

    static Connection conn() throws Exception {
        return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }

    @BeforeAll
    static void data() throws Exception {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA app");
            st.execute("CREATE TABLE app.tb_order (ord_no VARCHAR(10), line_no INT, ord_dt VARCHAR(30))");
            st.execute("INSERT INTO app.tb_order VALUES ('A',1,'2018-09-18 10:00:00'), ('A',2,'20180918123456'), ('B',1,'Mar 25 2021 10:00AM'),"
                    + " ('B',1,'2018/09/18'), ('C',1,'12'), ('D',1,'20181318'), ('E',1,NULL), ('F',1,'-')");
        }
    }

    static QualitySql.Rendered render(String id) {
        return QualitySql.render(id, "pg", "APP", "TB_ORDER", "ORD_DT", List.of("ORD_NO", "LINE_NO"), List.of("app"));
    }

    /** 주석 줄을 떼고 첫 문장 */
    static String first(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String l : sql.split("\n")) {
            if (!l.trim().startsWith("--")) {
                sb.append(l).append('\n');
            }
        }
        return sb.toString().split(";")[0].trim();
    }

    static List<List<Object>> run(String sql) throws Exception {
        try (Connection c = conn()) {
            ResultTable r = SqlRunner.run(c, sql, List.of(), 1000, 30);
            return r.rows();
        }
    }

    @Test
    void everyKindRunsOnPostgres() throws Exception {
        List<String> failed = new ArrayList<>();
        for (QualitySql.Kind k : QualitySql.kinds()) {
            try {
                run(first(render(k.id()).sql()));
            } catch (Exception e) {
                failed.add(k.id() + ": " + e.getMessage());
            }
        }
        assertEquals(List.of(), failed);
    }

    @Test
    void datefixNormalizesRealWorldMix() throws Exception {
        Map<Object, Object> got = new LinkedHashMap<>();
        for (List<Object> r : run(first(render("datefix").sql()))) {
            got.put(r.get(0), r.get(1));
        }
        assertEquals("20180918", got.get("2018-09-18 10:00:00"));
        assertEquals("20180918", got.get("20180918123456"));
        assertEquals("20210325", got.get("Mar 25 2021 10:00AM"), "영문 월");
        assertEquals("20180918", got.get("2018/09/18"));
        assertEquals(null, got.get("12"), "ELSE NULL — 모르는 모양은 드러낸다");
    }

    @Test
    void datecheckCatchesFailuresAndBadMonth() throws Exception {
        List<Object> bad = new ArrayList<>();
        run(first(render("datecheck").sql())).forEach(r -> bad.add(r.get(0)));
        assertTrue(bad.contains("12") && bad.contains("20181318") && bad.contains("-"), "정규화 실패·13월: " + bad);
        assertTrue(!bad.contains("2018-09-18 10:00:00"), bad.toString());
    }

    @Test
    void keydupFindsDuplicateCandidateKey() throws Exception {
        List<List<Object>> dup = run(first(render("keydup").sql()));
        assertEquals(1, dup.size(), "(B,1) 두 번");
        assertEquals("B", dup.get(0).get(0));
    }

    @Test
    void nopkListsTable() throws Exception {
        List<Object> names = new ArrayList<>();
        run(first(render("nopk").sql())).forEach(r -> names.add(r.get(1)));
        assertTrue(names.contains("tb_order"), names.toString());
    }
}
