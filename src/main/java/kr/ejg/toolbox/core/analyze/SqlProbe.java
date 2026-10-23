package kr.ejg.toolbox.core.analyze;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.check.Source;
import kr.ejg.toolbox.core.job.JobContext;

/**
 * SELECT 컬럼 DB 대조(6-34, 설계 33) — 매퍼의 정적 SELECT 를 {@code SELECT 1 FROM (원문) tb_probe WHERE 1=0}(MySQL·MariaDB 는 {@code 원문 LIMIT 0})으로 실제 DB 에 던져
 * 컬럼·표·문법 오류를 DB 가 말하게 한다. 행은 안 온다. 동적 태그·{@code ${}}·SELECT 아님은 건너뛴다. 한 문장이 방언 파일 여럿이면
 * 접속 방언에 맞는 파일 하나를 고른다. 연결은 {@code ConnectionRegistry.open} 의 읽기 전용 래퍼다(절대 규칙 6).
 * 결과(문장 글·DB 오류 글)는 응답에만 — 저장·로그하지 않는다(규칙 3)
 */
public final class SqlProbe {

    public static final String PASS = "PASS";
    public static final String ERROR = "ERROR";
    public static final String SKIP_DYNAMIC = "SKIP_DYNAMIC";
    public static final String SKIP_DOLLAR = "SKIP_DOLLAR";
    public static final String SKIP_NO_DIALECT_FILE = "SKIP_NO_DIALECT_FILE";

    /** 판정 하나 — message 는 ERROR 일 때 DB 오류문 첫 줄(200자) */
    public record Outcome(String nsId, String file, int line, String status, String message) {
    }

    /** total — SELECT 문장 수(건너뜀 포함). skippedBy — 건너뛴 까닭별 수. rows — ERROR·PASS·건너뜀 차례 */
    public record Report(int total, int passed, int failed, int skipped, Map<String, Integer> skippedBy, List<Outcome> rows) {

        public Report {
            skippedBy = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(skippedBy));
            rows = List.copyOf(rows);
        }
    }

    static final int TIMEOUT_SEC = 10;
    static final int MESSAGE = 200;
    private static final Pattern HASH = Pattern.compile("#\\{[^}]*\\}");
    /** MSSQL — 파생 표 안 ORDER BY 는 TOP·OFFSET 없이 못 쓴다. 끝 ORDER BY(괄호 없음) 뒤에 OFFSET 0 ROWS */
    private static final Pattern TAIL_ORDER_BY = Pattern.compile("(?is)\\border\\s+by\\b[^()']*$");
    private static final Pattern OFFSET_TOP = Pattern.compile("(?i)\\b(offset|top)\\b");
    /** 끝 LIMIT(괄호 밖) — MySQL·MariaDB 는 LIMIT 0 을 못 붙이면 감싼다 */
    private static final Pattern TAIL_LIMIT = Pattern.compile("(?is)\\blimit\\b[^()]*$");
    private static final List<String> STATUS_ORDER = List.of(ERROR, PASS, SKIP_DYNAMIC, SKIP_DOLLAR, SKIP_NO_DIALECT_FILE);

    private SqlProbe() {
    }

    /**
     * @param dialect 접속의 dialect 글(oracle·tibero·postgresql·mariadb·mysql·mssql·h2 …) — 방언 파일 고르기와 바인드 꼴
     * @param ctx 진행률·취소 — 없으면 null
     */
    public static Report run(List<Source> xml, Connection conn, String dialect, JobContext ctx) {
        MapperIndex.Parsed p = MapperIndex.parse(xml);
        Map<String, List<MapperIndex.Raw>> byId = new TreeMap<>();
        for (MapperIndex.Raw r : p.raws()) {
            if (r.tag().equals("select")) {
                byId.computeIfAbsent(r.ns() + "." + r.id(), k -> new ArrayList<>()).add(r);
            }
        }
        String d = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
        List<Outcome> out = new ArrayList<>();
        Map<String, Integer> skippedBy = new LinkedHashMap<>();
        int n = byId.size();
        int i = 0;
        for (Map.Entry<String, List<MapperIndex.Raw>> e : byId.entrySet()) {
            if (ctx != null) {
                ctx.checkCancelled();
                if (i % 50 == 0) {
                    ctx.progress(n == 0 ? 0 : i * 100 / n, i + "/" + n + " 문장");
                }
            }
            i++;
            String nsId = e.getKey();
            MapperIndex.Raw r = pick(e.getValue(), d);
            if (r == null) {
                MapperIndex.Raw f = e.getValue().get(0);
                out.add(new Outcome(nsId, f.file(), f.line(), SKIP_NO_DIALECT_FILE, ""));
                continue;
            }
            if (r.dynamic() || MapperIndex.dynamicInclude(r, r.text(), p.fragDynamic(), p.fragments(), 0)) {
                out.add(new Outcome(nsId, r.file(), r.line(), SKIP_DYNAMIC, ""));
                continue;
            }
            String text = MapperIndex.include(r, r.text(), p.fragments(), new ArrayList<>(), new HashSet<>(), 0);
            if (text.contains("${")) {
                out.add(new Outcome(nsId, r.file(), r.line(), SKIP_DOLLAR, ""));
                continue;
            }
            out.add(probe(conn, nsId, r, text, d));
        }
        if (ctx != null) {
            ctx.progress(100, n + "/" + n + " 문장");
        }
        int passed = 0;
        int failed = 0;
        for (Outcome o : out) {
            if (o.status().equals(PASS)) {
                passed++;
            } else if (o.status().equals(ERROR)) {
                failed++;
            } else {
                skippedBy.merge(o.status(), 1, Integer::sum);
            }
        }
        out.sort(Comparator.comparingInt((Outcome o) -> STATUS_ORDER.indexOf(o.status())).thenComparing(Outcome::nsId));
        return new Report(out.size(), passed, failed, out.size() - passed - failed, skippedBy, out);
    }

    /** 방언 파일 고르기 — 하나면 그것. 여럿이면 파일 이름에 방언 낱말(차례대로)이 든 것, h2·모름은 첫 파일. 없으면 null */
    static MapperIndex.Raw pick(List<MapperIndex.Raw> rs, String dialect) {
        if (rs.size() == 1) {
            return rs.get(0);
        }
        List<String> words = switch (dialect) {
            case "oracle" -> List.of("oracle");
            case "tibero" -> List.of("tibero", "oracle");
            case "postgresql", "postgres", "pg" -> List.of("postgres");
            case "mariadb", "maria" -> List.of("maria", "mysql");
            case "mysql" -> List.of("mysql", "maria");
            case "mssql", "sqlserver" -> List.of("mssql", "sqlserver");
            default -> List.of();
        };
        if (words.isEmpty()) {
            return rs.get(0);
        }
        for (String w : words) {
            for (MapperIndex.Raw r : rs) {
                String f = r.file().replace('\\', '/');
                if (f.substring(f.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT).contains(w)) {
                    return r;
                }
            }
        }
        return null;
    }

    /** 대조 SQL — {@code #{…}} 를 ? 로, 끝 ; 를 떼고 파생 표로 감싼다. 바인드 수를 돌려준다 */
    static String wrap(String text, String dialect, int[] binds) {
        return wrap(text, dialect, binds, "?");
    }

    /** @param slot {@code #{…}} 자리에 넣을 글 — {@code ?}(바인드) · {@code NULL} · {@code 0} */
    static String wrap(String text, String dialect, int[] binds, String slot) {
        Matcher m = HASH.matcher(text);
        StringBuilder sb = new StringBuilder();
        int b = 0;
        while (m.find()) {
            m.appendReplacement(sb, slot);
            b++;
        }
        m.appendTail(sb);
        binds[0] = b;
        String sql = sb.toString().strip();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).strip();
        }
        if ((dialect.equals("mssql") || dialect.equals("sqlserver")) && TAIL_ORDER_BY.matcher(sql).find() && !OFFSET_TOP.matcher(sql).find()) {
            sql = sql + "\nOFFSET 0 ROWS";
        }
        // MySQL·MariaDB 파생 표는 같은 컬럼 이름을 못 가진다(원문 SELECT u.*, d.dept_id 는 맞다, V-29 RuoYi 실측) — 끝 LIMIT 이 없으면 감싸지 않고 LIMIT 0
        if ((dialect.equals("mariadb") || dialect.equals("maria") || dialect.equals("mysql")) && !TAIL_LIMIT.matcher(sql).find()) {
            return sql + "\nLIMIT 0";
        }
        // 바깥은 컬럼을 안 부른다(SELECT 1) — Oracle·PostgreSQL 은 안쪽 같은 이름이 있어도 부르지만 않으면 된다
        return "SELECT 1 FROM (\n" + sql + "\n) tb_probe WHERE 1=0";
    }

    /**
     * 바인드 꼴을 차례로 — 하나라도 통과하면 PASS, 다 실패하면 첫 꼴의 오류. 값 없는 바인드는 형 추론이 DB 마다 달라
     * (PostgreSQL: varchar NULL 은 정수와 못 견주고 형 미정은 {@code '%' || ? || '%'}·{@code OFFSET ?} 에서 형을 못 정한다, 6-34a 실측)
     * 한 꼴로는 거짓 오류가 난다. 없는 컬럼·표·문법 오류는 어느 꼴로도 통과하지 못한다. 실제 앱은 MyBatis 가 형 있는 값을 넣는다
     */
    private static Outcome probe(Connection conn, String nsId, MapperIndex.Raw r, String text, String dialect) {
        boolean pg = dialect.startsWith("postgres") || dialect.equals("pg");
        List<Object[]> tries = new ArrayList<>(); // {자리 글, 바인드 형(없으면 null)}
        if (pg) {
            tries.add(new Object[] {"?", Types.OTHER});
        }
        tries.add(new Object[] {"?", Types.VARCHAR});
        tries.add(new Object[] {"NULL", null});
        tries.add(new Object[] {"0", null});
        SQLException first = null;
        for (Object[] t : tries) {
            int[] binds = new int[1];
            String sql = wrap(text, dialect, binds, (String) t[0]);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int k = 1; t[1] != null && k <= binds[0]; k++) {
                    ps.setNull(k, (Integer) t[1]);
                }
                try {
                    ps.setQueryTimeout(TIMEOUT_SEC);
                } catch (SQLFeatureNotSupportedException ignored) {
                    // 타임아웃을 모르는 드라이버 — 그대로 돈다
                }
                ps.setMaxRows(1);
                try (ResultSet rs = ps.executeQuery()) {
                    return new Outcome(nsId, r.file(), r.line(), PASS, "");
                }
            } catch (SQLException e) {
                if (first == null) {
                    first = e;
                }
                if (binds[0] == 0) {
                    break; // 자리가 없으면 다른 꼴도 같은 글
                }
            }
        }
        return new Outcome(nsId, r.file(), r.line(), ERROR, firstLine(first));
    }

    /** 오류문 첫 줄 — mariadb 드라이버의 「(conn=N) 」 는 뗀다(실행마다 다르다). 200자 */
    static String firstLine(SQLException e) {
        String s = String.valueOf(e.getMessage()).lines().findFirst().orElse("").strip().replaceFirst("^\\(conn=\\d+\\) ", "");
        return s.length() > MESSAGE ? s.substring(0, MESSAGE) : s;
    }
}
