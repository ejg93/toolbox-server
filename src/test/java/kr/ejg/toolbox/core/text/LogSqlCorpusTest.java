package kr.ejg.toolbox.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.CorpusHr;
import kr.ejg.toolbox.GoldenFiles;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * V-6 — 로그 SQL 복원(4-8)의 실물 표본. 로그는 누구나 gitignore 해 공개 표본이 없어, MyBatis 를 HR(H2) 위에서 실제로 1,000번 돌려
 * 진짜 로거 출력을 받는다. 파라미터는 Integer·Long·String(쉼표·따옴표·?·%)·BigDecimal·Timestamp·Date·Boolean·Double·null.
 * <ul>
 *   <li>등급 A: 복원문이 H2 에서 실패 · SELECT 건수가 MyBatis 결과와 다름 · 문장 수가 실행 수와 다름</li>
 *   <li>등급 B: warning 이 난 문장(? 수 ≠ 파라미터 수 등)</li>
 * </ul>
 */
@Tag("corpus")
class LogSqlCorpusTest {

    static final String MAPPER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="corpus.Hr">
              <select id="selEmp" resultType="map">
                SELECT EMPLOYEE_ID, LAST_NAME FROM EMPLOYEES
                WHERE EMPLOYEE_ID = #{id} OR LAST_NAME = #{name}
              </select>
              <select id="cntHire" resultType="map">
                SELECT COUNT(*) AS N FROM EMPLOYEES WHERE HIRE_DATE >= #{d} AND SALARY >= #{sal}
              </select>
              <update id="updPct">
                UPDATE EMPLOYEES SET COMMISSION_PCT = #{pct,jdbcType=NUMERIC} WHERE EMPLOYEE_ID = #{id}
              </update>
              <insert id="insReg">
                INSERT INTO REGIONS (REGION_ID, REGION_NAME) VALUES (#{rid}, #{rname})
              </insert>
              <select id="selJob" resultType="map">
                SELECT JOB_ID FROM JOBS WHERE (#{flag} = TRUE AND MIN_SALARY > #{min}) OR JOB_TITLE LIKE #{pat}
              </select>
            </mapper>
            """;

    record Run(String kind, int rows) {
    }

    static final List<Run> RUNS = new ArrayList<>();
    static final List<String> LOG = new ArrayList<>();
    static CorpusHr.Loaded hr;

    @BeforeAll
    static void run() throws Exception {
        CorpusFiles.verify();
        hr = CorpusHr.open();
        Configuration cfg = new Configuration(new Environment("corpus", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", hr.url(), "sa", "")));
        cfg.setLogImpl(Slf4jImpl.class);
        new XMLMapperBuilder(new ByteArrayInputStream(MAPPER.getBytes(StandardCharsets.UTF_8)), cfg, "corpus-hr.xml",
                cfg.getSqlFragments()).parse();
        SqlSessionFactory f = new SqlSessionFactoryBuilder().build(cfg);

        Logger lg = (Logger) LoggerFactory.getLogger("corpus");
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        lg.addAppender(app);
        lg.setLevel(Level.DEBUG);
        try {
            String[] names = {"King", "O'Neil, Jr.", "who?", "100%", "a,b", "Kochhar"};
            for (int i = 0; i < 200; i++) {
                try (SqlSession s = f.openSession(false)) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("id", 100 + i % 107);
                    p.put("name", names[i % names.length]);
                    RUNS.add(new Run("select", s.selectList("corpus.Hr.selEmp", p).size()));

                    p = new HashMap<>();
                    p.put("d", i % 2 == 0 ? Timestamp.valueOf("2005-01-01 00:00:00") : java.sql.Date.valueOf("2006-06-15"));
                    p.put("sal", new BigDecimal(3000 + i * 10 + ".50"));
                    RUNS.add(new Run("select", s.selectList("corpus.Hr.cntHire", p).size()));

                    p = new HashMap<>();
                    p.put("pct", i % 3 == 0 ? null : new BigDecimal("0." + (10 + i % 80)));
                    p.put("id", (long) (100 + i % 107));
                    s.update("corpus.Hr.updPct", p);
                    RUNS.add(new Run("dml", -1));

                    p = new HashMap<>();
                    p.put("rid", 1000L + i);
                    p.put("rname", "지역 " + i + ", '" + names[i % names.length] + "'");
                    s.insert("corpus.Hr.insReg", p);
                    RUNS.add(new Run("dml", -1));

                    p = new HashMap<>();
                    p.put("flag", i % 2 == 0);
                    p.put("min", 1000.5 + i);
                    p.put("pat", "%" + (i % 2 == 0 ? "Manager" : "Clerk") + "%");
                    RUNS.add(new Run("select", s.selectList("corpus.Hr.selJob", p).size()));
                    s.rollback(true);
                }
            }
        } finally {
            lg.detachAppender(app);
        }
        // 실무 로그처럼 앞머리(시각·레벨·스레드·로거)를 붙인다 — LogSql 은 앞머리를 무시해야 한다
        for (ILoggingEvent e : app.list) {
            LOG.add("2026-09-27 10:00:00.000 DEBUG 1234 --- [main] " + e.getLoggerName() + " : " + e.getFormattedMessage());
        }
    }

    @Test
    void restoredSqlRunsWithSameResult() throws Exception {
        List<LogSql.Item> items = LogSql.restore(String.join("\n", LOG));
        List<String> bad = new ArrayList<>();
        if (items.size() != RUNS.size()) {
            bad.add("문장 " + items.size() + " ≠ 실행 " + RUNS.size());
        }
        try (Connection c = java.sql.DriverManager.getConnection(hr.url(), "sa", "")) {
            c.setAutoCommit(false);
            for (int i = 0; i < Math.min(items.size(), RUNS.size()); i++) {
                LogSql.Item it = items.get(i);
                if (it.warning() != null) {
                    continue; // B
                }
                try (Statement s = c.createStatement()) {
                    if (RUNS.get(i).kind().equals("select")) {
                        int n = 0;
                        try (ResultSet r = s.executeQuery(it.restored())) {
                            while (r.next()) {
                                n++;
                            }
                        }
                        if (n != RUNS.get(i).rows()) {
                            bad.add(i + " 건수 " + n + " ≠ " + RUNS.get(i).rows());
                        }
                    } else {
                        s.executeUpdate(it.restored());
                    }
                } catch (java.sql.SQLException e) {
                    bad.add(i + " " + e.getSQLState());
                } finally {
                    c.rollback();
                }
            }
        }
        CorpusFiles.none("로그 SQL 복원(문장 " + RUNS.size() + ")", bad, RUNS.size());
    }

    @Test
    void warningsBaseline() throws Exception {
        List<String> b = new ArrayList<>();
        List<LogSql.Item> items = LogSql.restore(String.join("\n", LOG));
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).warning() != null) {
                b.add("문장 " + i);
            }
        }
        CorpusFiles.baseline("logsql-warnings", b, items.size());
        Map<String, Object> g = new java.util.LinkedHashMap<>();
        g.put("statements", items.size());
        g.put("logLines", LOG.size());
        g.put("paramTypes", items.stream().flatMap(x -> x.params().stream()).map(p -> String.valueOf(p.type()))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new)));
        GoldenFiles.assertJson("corpus/logsql-summary.json", g);
        assertEquals(RUNS.size(), items.size());
    }
}
