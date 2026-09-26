package kr.ejg.toolbox.core.meta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * 스냅샷 둘의 차이(5-3, 1-6) — 산출물 「테이블 변경이력」의 입력. 접속을 가리지 않는다(개발 vs 운영, 3.5).
 * <ul>
 *   <li>테이블 키는 이름. {@code ignoreSchema}(기본) 면 스키마를 무시한다 — 개발 DEV·운영 PRD 처럼 접속마다 스키마명이 다르다.
 *       한 스냅샷 안에 같은 이름이 여러 스키마에 있으면 그 이름만 {@code 스키마.이름} 으로 가른다</li>
 *   <li>컬럼은 이름으로 짝짓고 필드 일곱(타입·길이·정밀도·소수·NULL·기본값·코멘트)을 비교한다</li>
 *   <li>제약은 이름이 아니라 <b>구조</b>로 비교한다 — 운영은 SYS_C… 처럼 시스템 이름이 붙어 개발과 이름이 다르다</li>
 *   <li>모든 목록은 이름순</li>
 * </ul>
 */
public final class SnapshotDiff {

    public record Result(List<String> addedTables, List<String> removedTables, List<TableDiff> changedTables) {
        public Result {
            addedTables = List.copyOf(addedTables);
            removedTables = List.copyOf(removedTables);
            changedTables = List.copyOf(changedTables);
        }

        public boolean isEmpty() {
            return addedTables.isEmpty() && removedTables.isEmpty() && changedTables.isEmpty();
        }
    }

    /** changes 는 테이블 자체(코멘트·종류) 변경 — 7장에 없던 칸(1-6 계획 밖) */
    public record TableDiff(String name, List<Change> changes, List<String> addedColumns, List<String> removedColumns,
            List<Change> changedColumns, List<String> addedConstraints, List<String> removedConstraints) {
        public TableDiff {
            changes = List.copyOf(changes);
            addedColumns = List.copyOf(addedColumns);
            removedColumns = List.copyOf(removedColumns);
            changedColumns = List.copyOf(changedColumns);
            addedConstraints = List.copyOf(addedConstraints);
            removedConstraints = List.copyOf(removedConstraints);
        }
    }

    /** name 은 컬럼명(테이블 변경이면 테이블명) */
    public record Change(String name, String field, String before, String after) {
    }

    private SnapshotDiff() {
    }

    public static Result compare(List<Schema> a, List<Schema> b, boolean ignoreSchema) {
        Map<String, Table> ma = index(a, ignoreSchema);
        Map<String, Table> mb = index(b, ignoreSchema);
        List<String> added = new ArrayList<>(new TreeSet<>(minus(mb.keySet(), ma.keySet())));
        List<String> removed = new ArrayList<>(new TreeSet<>(minus(ma.keySet(), mb.keySet())));
        List<TableDiff> changed = new ArrayList<>();
        for (String key : new TreeSet<>(ma.keySet())) {
            if (!mb.containsKey(key)) {
                continue;
            }
            TableDiff d = table(key, ma.get(key), mb.get(key), ignoreSchema);
            if (!d.changes().isEmpty() || !d.addedColumns().isEmpty() || !d.removedColumns().isEmpty()
                    || !d.changedColumns().isEmpty() || !d.addedConstraints().isEmpty() || !d.removedConstraints().isEmpty()) {
                changed.add(d);
            }
        }
        return new Result(added, removed, changed);
    }

    private static TableDiff table(String key, Table ta, Table tb, boolean ignoreSchema) {
        List<Change> changes = new ArrayList<>();
        field(changes, key, "type", ta.type(), tb.type());
        field(changes, key, "comment", ta.comment(), tb.comment());

        Map<String, Column> ca = byName(ta.columns(), Column::name);
        Map<String, Column> cb = byName(tb.columns(), Column::name);
        List<String> addedCols = new ArrayList<>(new TreeSet<>(minus(cb.keySet(), ca.keySet())));
        List<String> removedCols = new ArrayList<>(new TreeSet<>(minus(ca.keySet(), cb.keySet())));
        List<Change> changedCols = new ArrayList<>();
        for (String n : new TreeSet<>(ca.keySet())) {
            Column x = ca.get(n);
            Column y = cb.get(n);
            if (y == null) {
                continue;
            }
            field(changedCols, n, "nativeType", x.nativeType(), y.nativeType());
            field(changedCols, n, "length", x.length(), y.length());
            field(changedCols, n, "precision", x.precision(), y.precision());
            field(changedCols, n, "scale", x.scale(), y.scale());
            field(changedCols, n, "nullable", x.nullable(), y.nullable());
            field(changedCols, n, "defaultValue", x.defaultValue(), y.defaultValue());
            field(changedCols, n, "comment", x.comment(), y.comment());
        }

        TreeSet<String> ka = constraints(ta, ignoreSchema);
        TreeSet<String> kb = constraints(tb, ignoreSchema);
        return new TableDiff(key, changes, addedCols, removedCols, changedCols,
                new ArrayList<>(minus(kb, ka)), new ArrayList<>(minus(ka, kb)));
    }

    /** 구조 문자열 — 이름 없이. PK(A,B) / FK(A)->[스키마.]T(X) / UQ(A) */
    private static TreeSet<String> constraints(Table t, boolean ignoreSchema) {
        TreeSet<String> out = new TreeSet<>();
        if (t.pk() != null) {
            out.add("PK(" + String.join(",", t.pk().columns()) + ")");
        }
        for (ForeignKey fk : t.fks()) {
            String ref = (!ignoreSchema && fk.refSchema() != null ? fk.refSchema() + "." : "") + fk.refTable();
            out.add("FK(" + String.join(",", fk.columns()) + ")->" + ref + "(" + String.join(",", fk.refColumns()) + ")");
        }
        for (UniqueKey uq : t.uniques()) {
            out.add("UQ(" + String.join(",", uq.columns()) + ")");
        }
        return out;
    }

    private static Map<String, Table> index(List<Schema> schemas, boolean ignoreSchema) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (ignoreSchema) {
            schemas.forEach(s -> s.tables().forEach(t -> counts.merge(t.name(), 1, Integer::sum)));
        }
        Map<String, Table> out = new LinkedHashMap<>();
        for (Schema s : schemas) {
            for (Table t : s.tables()) {
                boolean plain = ignoreSchema && counts.get(t.name()) == 1;
                out.put(plain ? t.name() : t.schema() + "." + t.name(), t);
            }
        }
        return out;
    }

    private static <T> Map<String, T> byName(List<T> xs, Function<T, String> name) {
        Map<String, T> out = new LinkedHashMap<>();
        xs.forEach(x -> out.put(name.apply(x), x));
        return out;
    }

    private static TreeSet<String> minus(java.util.Collection<String> a, java.util.Collection<String> b) {
        TreeSet<String> out = new TreeSet<>(a);
        out.removeAll(b);
        return out;
    }

    private static void field(List<Change> out, String name, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            out.add(new Change(name, field, before == null ? null : before.toString(), after == null ? null : after.toString()));
        }
    }
}
