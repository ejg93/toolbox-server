package kr.ejg.toolbox.core.analyze;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.meta.Table;

/**
 * 교차 정합성(6-11) — 분석 실행(코드)과 DB 스냅샷을 맞댄다. 표 이름은 대문자로 맞추고 스키마는 안 가른다.
 * <ul>
 *   <li>missingInDb — 코드가 쓰는데 스냅샷에 없는 표(그 표를 쓰는 프로그램 수)</li>
 *   <li>unusedInCode — 스냅샷에 있는데 어느 프로그램도 안 쓰는 표</li>
 *   <li>deadStatements·orphanJsps — 분석 때 저장한 고아(안 불리는 매퍼 문장·뷰가 안 가리키는 JSP)</li>
 * </ul>
 * 스냅샷 없이 부르면 앞 둘은 비운다. 전부 후보다 — 동적 SQL·리플렉션·타일즈는 못 본다. 식별자만 다룬다(규칙 3)
 */
public final class Consistency {

    public record Missing(String table, int programs) {
    }

    public record Unused(String schema, String table, String type) {
    }

    public record Report(Long snapshotId, List<Missing> missingInDb, List<Unused> unusedInCode, List<String> deadStatements,
            List<String> orphanJsps) {

        public Report {
            missingInDb = List.copyOf(missingInDb);
            unusedInCode = List.copyOf(unusedInCode);
            deadStatements = List.copyOf(deadStatements);
            orphanJsps = List.copyOf(orphanJsps);
        }
    }

    private Consistency() {
    }

    /** 스냅샷이 없으면 {@link Optional#empty()} — 실행이 없는지는 부르는 쪽이 먼저 본다 */
    public static Optional<Report> of(AnalyzeStore analyze, SnapshotStore snapshots, long runId, Long snapshotId) throws SQLException {
        List<String> dead = new ArrayList<>();
        List<String> jsps = new ArrayList<>();
        for (AnalyzeRunner.Orphan o : analyze.orphans(runId)) {
            (o.kind().equals("statement") ? dead : jsps).add(o.name());
        }
        if (snapshotId == null) {
            return Optional.of(new Report(null, List.of(), List.of(), dead, jsps));
        }
        Optional<List<Schema>> schemas = snapshots.get(snapshotId);
        if (schemas.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Integer> code = new TreeMap<>();
        for (AnalyzeStore.ProgramRow r : analyze.programs(runId)) {
            r.crud().keySet().forEach(t -> code.merge(t.toUpperCase(Locale.ROOT), 1, Integer::sum));
        }
        Set<String> db = new TreeSet<>();
        List<Unused> unused = new ArrayList<>();
        for (Schema s : schemas.get()) {
            for (Table t : s.tables()) {
                String name = t.name().toUpperCase(Locale.ROOT);
                db.add(name);
                if (!code.containsKey(name)) {
                    unused.add(new Unused(s.name(), t.name(), t.type()));
                }
            }
        }
        unused.sort(Comparator.comparing(Unused::schema, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(Unused::table));
        List<Missing> missing = new ArrayList<>();
        code.forEach((t, n) -> {
            if (!db.contains(t)) {
                missing.add(new Missing(t, n));
            }
        });
        return Optional.of(new Report(snapshotId, missing, unused, dead, jsps));
    }
}
