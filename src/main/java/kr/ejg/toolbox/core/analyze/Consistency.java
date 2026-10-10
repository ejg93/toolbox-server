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
import kr.ejg.toolbox.core.meta.Scope;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.meta.Table;

/**
 * 교차 정합성(6-11) — 분석 실행(코드)과 DB 스냅샷을 맞댄다. 표 이름은 대문자로 맞추고 스키마는 안 가른다.
 * <ul>
 *   <li>missingInDb — 코드가 쓰는데 스냅샷에 없는 표(그 표를 쓰는 프로그램 수 · 사유 — 범위 밖 / 스키마 밖일 수 있음 / 빈 표 / 없음)</li>
 *   <li>unusedInCode — 스냅샷에 있는데 어느 프로그램도 안 쓰는 표</li>
 *   <li>deadStatements·orphanJsps — 분석 때 저장한 고아(안 불리는 매퍼 문장·뷰가 안 가리키는 JSP)</li>
 *   <li>missingJsps — view 가 가리키는데 폴더에 없는 JSP(6-26, 코드만 보는 값 — 스냅샷 없이도. 옛 실행은 빔)</li>
 * </ul>
 * 스냅샷 없이 부르면 앞 둘은 비운다. 전부 후보다 — 동적 SQL·리플렉션·타일즈는 못 본다. 식별자만 다룬다(규칙 3)
 */
public final class Consistency {

    /** reason — 범위 밖(규칙) / 스키마 밖일 수 있음 / 빈 표라 빠졌을 수 있음 / 없음(6-23) */
    public record Missing(String table, int programs, String reason) {
    }

    public record Unused(String schema, String table, String type) {
    }

    /** view 이름과 그 이름을 돌려주는 프로그램 수(6-26) */
    public record MissingJsp(String view, int programs) {
    }

    /** scopeSummary — 그 스냅샷을 찍을 때 쓴 범위 한 줄(옛 스냅샷·스냅샷 없이 null) */
    public record Report(Long snapshotId, String scopeSummary, List<Missing> missingInDb, List<Unused> unusedInCode, List<String> deadStatements,
            List<String> orphanJsps, List<MissingJsp> missingJsps) {

        public Report {
            missingInDb = List.copyOf(missingInDb);
            unusedInCode = List.copyOf(unusedInCode);
            deadStatements = List.copyOf(deadStatements);
            orphanJsps = List.copyOf(orphanJsps);
            missingJsps = List.copyOf(missingJsps);
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
        // 6-26 — view 가 가리키는데 폴더에 없는 JSP(코드만 보는 값 — 스냅샷 없이도 나온다). 옛 실행(view_file 없음)은 빔
        Map<String, Integer> files = analyze.viewFiles(runId);
        Map<String, Integer> gone = new TreeMap<>();
        for (AnalyzeStore.ProgramRow r : analyze.programs(runId)) {
            for (JavaGraph.View v : r.views()) {
                String name = v.name().startsWith("/") ? v.name().substring(1) : v.name();
                if (v.kind().equals("view") && Integer.valueOf(0).equals(files.get(name))) {
                    gone.merge(name, 1, Integer::sum);
                }
            }
        }
        List<MissingJsp> missingJsps = new ArrayList<>();
        gone.forEach((v, n) -> missingJsps.add(new MissingJsp(v, n)));
        if (snapshotId == null) {
            return Optional.of(new Report(null, null, List.of(), List.of(), dead, jsps, missingJsps));
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
        Scope scope = snapshots.summary(snapshotId).map(SnapshotStore.Summary::scope).orElse(null);
        List<Missing> missing = new ArrayList<>();
        code.forEach((t, n) -> {
            if (!db.contains(t)) {
                missing.add(new Missing(t, n, reasonFor(scope, t)));
            }
        });
        return Optional.of(new Report(snapshotId, scope == null ? null : scope.summary(), missing, unused, dead, jsps, missingJsps));
    }

    /** 「없음」 과 「범위 밖」 을 가른다(6-23). 코드 쪽 표는 스키마·행 수를 모르므로 이름 규칙만 확정, 나머지는 「…일 수 있음」 */
    static String reasonFor(Scope scope, String table) {
        if (scope == null) {
            return "없음";
        }
        String r = scope.reason(table);
        if (!r.isEmpty()) {
            return "범위 밖 — " + r;
        }
        if (!scope.schemas().isEmpty()) {
            return "스키마 " + String.join("·", scope.schemas()) + " 밖일 수 있음";
        }
        if (Boolean.TRUE.equals(scope.skipEmpty())) {
            return "빈 표라 빠졌을 수 있음";
        }
        return "없음";
    }
}
