package kr.ejg.toolbox.core.meta;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.ejg.toolbox.core.job.JobContext;

/**
 * 1-47 — 통계가 없는 표(rowCount null, 뷰 제외)만 실제로 센다. 산출물 02 의 테이블 볼륨이 「통계 전이면 빈칸」 이던 것을 채운다.
 * 통계가 있는 표는 세지 않는다(운영 DB 부하). 표마다 5초·전체 60초 — 못 센 표는 null 그대로 두고 경고 rowCount(SQLState·코드별 건수).
 * 센 값·SQL 은 로그에 안 남긴다(규칙 3).
 */
final class RowCounter {

    static final int PER_TABLE_SEC = 5;
    static final long BUDGET_MS = 60_000;

    record Out(List<Schema> schemas, List<MetaSource.Warning> warnings) {
    }

    private RowCounter() {
    }

    static Out fill(Connection conn, List<Schema> schemas, JobContext ctx) throws SQLException {
        int total = 0;
        for (Schema s : schemas) {
            for (Table t : s.tables()) {
                if (target(t)) {
                    total++;
                }
            }
        }
        if (total == 0) {
            return new Out(schemas, List.of());
        }
        String q = conn.getMetaData().getIdentifierQuoteString();
        String quote = q == null || q.isBlank() ? "" : q.trim();
        Map<List<Object>, Integer> failed = new LinkedHashMap<>();
        long end = System.currentTimeMillis() + BUDGET_MS;
        int k = 0;
        int skipped = 0;
        List<Schema> out = new ArrayList<>();
        for (Schema s : schemas) {
            List<Table> tables = new ArrayList<>();
            for (Table t : s.tables()) {
                if (!target(t)) {
                    tables.add(t);
                    continue;
                }
                ctx.checkCancelled();
                k++;
                ctx.progress(80 + 10 * k / total, "행 수 " + k + "/" + total + " — " + t.name());
                if (System.currentTimeMillis() > end) {
                    skipped++;
                    tables.add(t);
                    continue;
                }
                try (Statement st = conn.createStatement()) {
                    st.setQueryTimeout(PER_TABLE_SEC);
                    String name = (t.schema() == null || t.schema().isEmpty() ? "" : quote(t.schema(), quote) + ".") + quote(t.name(), quote);
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + name)) {
                        rs.next();
                        tables.add(t.withStats(rs.getLong(1), t.createdAt(), t.lastDdlAt()));
                    }
                } catch (SQLException e) {
                    failed.merge(List.of(e.getSQLState() == null ? "" : e.getSQLState(), e.getErrorCode()), 1, Integer::sum);
                    tables.add(t);
                }
            }
            out.add(s.withTables(tables));
        }
        List<MetaSource.Warning> warnings = new ArrayList<>();
        failed.forEach((key, n) -> warnings.add(new MetaSource.Warning("rowCount", (String) key.get(0), (Integer) key.get(1), n)));
        if (skipped > 0) {
            warnings.add(new MetaSource.Warning("rowCount", "", 0, skipped));
        }
        return new Out(out, warnings);
    }

    /** 통계가 없는 표 — 뷰는 안 센다 */
    static boolean target(Table t) {
        return t.rowCount() == null && (t.type() == null || !t.type().toUpperCase(Locale.ROOT).contains("VIEW"));
    }

    private static String quote(String name, String q) {
        return q.isEmpty() ? name : q + name.replace(q, q + q) + q;
    }
}
