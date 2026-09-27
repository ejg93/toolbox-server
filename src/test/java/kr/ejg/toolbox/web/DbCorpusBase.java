package kr.ejg.toolbox.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.DbCorpus;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.sqlrun.SqlRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * 컨테이너 실물 표본(번들 10) — 방언별 클래스 넷이 이 베이스를 상속하고 컨테이너는 클래스마다 **한 번** 뜬다(메모리 때문에 한 번에 하나, 1-3).
 * {@code @BeforeAll} 이 표본 원본 DDL 을 넣고(변환 없음, {@link DbCorpus}), 절은 순서대로 돈다:
 * (1) INSERT 실행 — 빈 표(V-10) · (2) 데이터 적재(V-9) · (3) 메타(V-9) · (4) 품질(V-9) · (5) 스니펫(V-8).
 */
@Tag("db")
@Tag("corpus")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class DbCorpusBase {

    @TempDir
    Path tmp;

    protected Connection conn;
    protected final Map<String, Object> golden = new TreeMap<>();

    abstract DbCorpus.Dialect dialect();

    abstract Connection open() throws SQLException;

    String name() {
        return dialect().name().toLowerCase(Locale.ROOT);
    }

    @BeforeAll
    void loadDdl() throws Exception {
        CorpusFiles.verify();
        conn = open();
        List<String> failed = new ArrayList<>();
        int ok = 0;
        for (Path p : DbCorpus.ddl(dialect())) {
            DbCorpus.Loaded l = DbCorpus.load(conn, p, dialect(), DbCorpus.Kind.DDL);
            ok += l.ok();
            failed.addAll(l.failed());
        }
        // 시퀀스 스니펫이 볼 것 — PG·Maria·MSSQL 은 만들고, Oracle CURRVAL 은 세션에서 NEXTVAL 을 먼저 불러야 한다(ORA-8002)
        try (Statement s = conn.createStatement()) {
            if (dialect() == DbCorpus.Dialect.ORACLE) {
                s.execute("SELECT EMPLOYEES_SEQ.NEXTVAL FROM DUAL");
            } else {
                s.execute("CREATE SEQUENCE corpus_seq");
                if (dialect() == DbCorpus.Dialect.POSTGRES) {
                    s.execute("SELECT nextval('corpus_seq')"); // currval 도 세션에서 한 번 뽑은 뒤에만
                }
            }
        }
        golden.put("ddlOk", ok);
        golden.put("ddlFailed", failed.size());
        CorpusFiles.conformance("db-" + name() + "-ddl-failed", failed); // 원본 스크립트가 이 컨테이너에서 못 넣는 문장 — 목록만(바뀌면 빨강)
    }

    @AfterAll
    void close() throws Exception {
        GoldenFiles.assertJson("corpus/db-" + name() + ".json", golden);
        if (conn != null) {
            conn.close();
        }
    }

    // ---------------------------------------------------------------- (5) 스니펫(V-8)

    static JsonNode rendered;

    /** 순수본 SNIPPETS 전부를 한 번 렌더(앱 + corpus-snippets.js) — 탭별로 나눠 쓴다 */
    synchronized JsonNode rendered() throws Exception {
        if (rendered == null) {
            rendered = new ObjectMapper().readTree(CorpusNode.run("corpus-snippets.js", "corpus-snippets", tmp).out().resolve("summary.json").toFile());
        }
        return rendered;
    }

    static final Pattern QUERY = Pattern.compile("(?is)^(SELECT|WITH|SHOW|DESC|DESCRIBE|EXPLAIN)\\b.*");
    static final Pattern DML = Pattern.compile("(?is)^(INSERT|UPDATE|DELETE|MERGE)\\b.*");
    static final Pattern PLSQL = Pattern.compile("(?is).*\\b(BEGIN|DECLARE)\\b.*\\bEND\\b.*");
    /** 시스템 뷰·컨테이너에 없는 확장·우리가 안 만든 자리 표시 객체 — 이 이름이 든 문장의 「없는 객체」 는 B */
    static final Pattern SYSTEM = Pattern.compile("(?i)(pg_stat|pg_catalog|information_schema|performance_schema|mysql\\.|sys\\.|"
            + "\\bdba_|\\bv\\$|\\bgv\\$|\\bx\\$|\\ball_|\\buser_|dbms_|corpus_obj|corpus_seq|msdb\\.|master\\.)");

    enum Outcome { RUN, PARSED, SKIPPED, A, B }

    record Verdict(Outcome outcome, String code, String msg) {
        Verdict(Outcome outcome, String code) {
            this(outcome, code, "");
        }
    }

    /** 방언별 오류 코드 → A(스니펫이 틀렸다)·B(권한·없는 시스템 객체·확장). 없는 **컬럼**(PG 42703·Maria 1054·MSSQL 207·Oracle 904)은
     * 시스템 뷰라도 A — 컬럼은 권한과 무관하게 있어야 한다(첫 판 PG tbl_size·idx_bloat·seq_list 가 B 로 새던 것) */
    Verdict judge(SQLException e, String st) {
        Verdict v = judge0(e, st);
        String m = String.valueOf(e.getMessage()).lines().findFirst().orElse("").strip();
        return new Verdict(v.outcome(), v.code(), m.length() > 160 ? m.substring(0, 160) : m);
    }

    Verdict judge0(SQLException e, String st) {
        String state = String.valueOf(e.getSQLState());
        int code = e.getErrorCode();
        boolean sys = SYSTEM.matcher(st).find();
        return switch (dialect()) {
            case POSTGRES -> state.equals("42501") ? new Verdict(Outcome.B, state)
                    : (state.equals("42P01") || state.equals("42883") || state.equals("42704")) && sys ? new Verdict(Outcome.B, state)
                    : new Verdict(Outcome.A, state);
            case MARIA -> (code == 1142 || code == 1227 || code == 1044 || code == 1045 || code == 1143) ? new Verdict(Outcome.B, "" + code)
                    : (code == 1146 || code == 1305 || code == 1109) && sys ? new Verdict(Outcome.B, "" + code)
                    : new Verdict(Outcome.A, "" + code);
            case MSSQL -> (code == 229 || code == 297 || code == 300 || code == 262 || code == 15247) ? new Verdict(Outcome.B, "" + code)
                    : (code == 208 || code == 2812 || code == 4121) && sys ? new Verdict(Outcome.B, "" + code)
                    : new Verdict(Outcome.A, "" + code);
            case ORACLE -> code == 1031 ? new Verdict(Outcome.B, "ORA-" + code)
                    : (code == 942 || code == 4043 || code == 2289) && sys ? new Verdict(Outcome.B, "ORA-" + code)
                    : new Verdict(Outcome.A, "ORA-" + code);
        };
    }

    /** 문장 하나 — 조회는 실행(10행·30초), DML 은 파싱만, 그 밖(DDL·세션·PL/SQL)은 실행하지 않는다(MSSQL 은 PARSEONLY 로 전부 파싱) */
    Verdict one(String st) {
        String s = st.strip();
        try (Statement x = conn.createStatement()) {
            if (QUERY.matcher(s).matches()) {
                SqlRunner.run(conn, s, List.of(), 10, 30);
                return new Verdict(Outcome.RUN, "");
            }
            switch (dialect()) {
                case MSSQL -> {
                    x.execute("SET PARSEONLY ON");
                    try {
                        x.execute(s);
                    } finally {
                        x.execute("SET PARSEONLY OFF");
                    }
                    return new Verdict(Outcome.PARSED, "");
                }
                case POSTGRES, MARIA -> {
                    if (!DML.matcher(s).matches()) {
                        return new Verdict(Outcome.SKIPPED, "");
                    }
                    x.execute("EXPLAIN " + s);
                    return new Verdict(Outcome.PARSED, "");
                }
                case ORACLE -> {
                    if (!DML.matcher(s).matches()) {
                        return new Verdict(Outcome.SKIPPED, "");
                    }
                    x.execute("EXPLAIN PLAN FOR " + s);
                    return new Verdict(Outcome.PARSED, "");
                }
                default -> throw new IllegalStateException();
            }
        } catch (SQLException e) {
            return judge(e, s);
        } finally {
            try {
                conn.rollback();
            } catch (SQLException e) {
                // 자동 커밋이면 롤백할 것이 없다
            }
        }
    }

    @Test
    @Order(5)
    void snippets() throws Exception {
        JsonNode items = rendered().get("tabs").get(dialect().tab);
        conn.setAutoCommit(false);
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<Outcome, Integer> count = new TreeMap<>();
        int total = 0;
        for (JsonNode it : items) {
            String key = it.get("key").asText();
            String sql = it.path("sql").asText("");
            List<String> sts = PLSQL.matcher(sql).matches() ? List.of() : DbCorpus.statements(sql, dialect() == DbCorpus.Dialect.MSSQL
                    ? DbCorpus.Dialect.MSSQL : DbCorpus.Dialect.POSTGRES); // 스니펫 글은 SQL*Plus 줄이 없다 — ; 나누기만
            if (sts.isEmpty()) {
                skipped.add(key + " (PL/SQL·빈 글)");
                count.merge(Outcome.SKIPPED, 1, Integer::sum);
                continue;
            }
            for (int i = 0; i < sts.size(); i++) {
                total++;
                Verdict v = one(sts.get(i));
                count.merge(v.outcome(), 1, Integer::sum);
                String id = key + "#" + i + (v.code().isEmpty() ? "" : " " + v.code());
                switch (v.outcome()) {
                    case A -> a.add(id + " — " + v.msg()); // 공개 스니펫의 오류 문구 — portfolio 로 넘길 결함 목록
                    case B -> b.add(id);
                    case SKIPPED -> skipped.add(key + "#" + i);
                    default -> { }
                }
            }
        }
        conn.setAutoCommit(true);
        golden.put("snippets", new LinkedHashMap<>(Map.of("items", items.size(), "statements", total, "count", count)));
        CorpusFiles.conformance("db-" + name() + "-snippets-skipped", skipped);
        CorpusFiles.conformance("db-" + name() + "-snippets-b", b);
        // 스니펫 A 는 순수본 결함 — 고치는 곳이 portfolio 라 번들 안에서 안 고친다(설계 5). 알려진 목록으로 얼리고 새 A 가 생기면 빨강
        CorpusFiles.conformance("db-" + name() + "-snippets-a", a);
    }

    Set<String> unused() {
        return Set.of();
    }
}
