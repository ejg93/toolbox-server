package kr.ejg.toolbox.core.meta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 스냅샷 둘의 차이(5-3, 1-6) — 산출물 「테이블 변경이력」의 입력. 접속을 가리지 않는다(개발 vs 운영, 3.5).
 * <ul>
 *   <li>테이블 키는 이름. {@code ignoreSchema}(기본) 면 스키마를 무시한다 — 개발 DEV·운영 PRD 처럼 접속마다 스키마명이 다르다.
 *       어느 한쪽 스냅샷 안에서든 같은 이름이 여러 스키마에 있으면 그 이름만 양쪽 다 {@code 스키마.이름} 으로 가른다(1-98)</li>
 *   <li>컬럼은 이름으로 짝짓고 필드 일곱(타입·길이·정밀도·소수·NULL·기본값·코멘트)을 비교한다</li>
 *   <li>제약은 이름이 아니라 <b>구조</b>로 비교한다 — 운영은 SYS_C… 처럼 시스템 이름이 붙어 개발과 이름이 다르다.
 *       FK 는 기본이 아닌 규칙까지, CHECK 는 조건 글로(1-97). 자동증가는 안 본다</li>
 *   <li>인덱스도 구조로(1-61) — {@code IX(A,B DESC)}·유니크 {@code UX(A)}. 정렬은 DESC 만 붙인다(옛 스냅샷의 모름 = ASC).
 *       제약이 받치는 인덱스(유니크 + PK·UQ 와 같은 컬럼)는 뺀다 — 그 변화는 제약 칸이 낸다. JDBC 는 유니크 제약과 유니크 인덱스를 못 가르고,
 *       H2 는 PK 인덱스 이름이 PK 이름과 달라 {@code indexes} 에 남는다</li>
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
            List<Change> changedColumns, List<String> addedConstraints, List<String> removedConstraints,
            List<String> addedIndexes, List<String> removedIndexes) {
        public TableDiff {
            changes = List.copyOf(changes);
            addedColumns = List.copyOf(addedColumns);
            removedColumns = List.copyOf(removedColumns);
            changedColumns = List.copyOf(changedColumns);
            addedConstraints = List.copyOf(addedConstraints);
            removedConstraints = List.copyOf(removedConstraints);
            addedIndexes = List.copyOf(addedIndexes);
            removedIndexes = List.copyOf(removedIndexes);
        }
    }

    /** name 은 컬럼명(테이블 변경이면 테이블명) */
    public record Change(String name, String field, String before, String after) {
    }

    private SnapshotDiff() {
    }

    public static Result compare(List<Schema> a, List<Schema> b, boolean ignoreSchema) {
        Keyed k = index(a, b, ignoreSchema);
        Map<String, Table> ma = k.a();
        Map<String, Table> mb = k.b();
        List<String> added = new ArrayList<>(new TreeSet<>(minus(mb.keySet(), ma.keySet())));
        List<String> removed = new ArrayList<>(new TreeSet<>(minus(ma.keySet(), mb.keySet())));
        List<TableDiff> changed = new ArrayList<>();
        for (String key : new TreeSet<>(ma.keySet())) {
            if (!mb.containsKey(key)) {
                continue;
            }
            TableDiff d = table(key, ma.get(key), mb.get(key), ignoreSchema);
            if (!d.changes().isEmpty() || !d.addedColumns().isEmpty() || !d.removedColumns().isEmpty()
                    || !d.changedColumns().isEmpty() || !d.addedConstraints().isEmpty() || !d.removedConstraints().isEmpty()
                    || !d.addedIndexes().isEmpty() || !d.removedIndexes().isEmpty()) {
                changed.add(d);
            }
        }
        return new Result(added, removed, changed);
    }

    private static TableDiff table(String key, Table ta, Table tb, boolean ignoreSchema) {
        List<Change> changes = new ArrayList<>();
        field(changes, key, "type", ta.type(), tb.type());
        field(changes, key, "comment", ta.comment(), tb.comment());

        // 1-128 F-07 — 컬럼·제약 이름은 대문자로 접어 짝짓는다(반영 DDL AlterGen 과 같은 규칙). 보여 주는 이름은 B 쪽(없으면 A 쪽) 글자 그대로
        Map<String, Column> ca = byName(ta.columns(), c -> c.name().toUpperCase(Locale.ROOT));
        Map<String, Column> cb = byName(tb.columns(), c -> c.name().toUpperCase(Locale.ROOT));
        List<String> addedCols = new ArrayList<>(new TreeSet<>(minus(cb.keySet(), ca.keySet()).stream().map(k -> cb.get(k).name()).toList()));
        List<String> removedCols = new ArrayList<>(new TreeSet<>(minus(ca.keySet(), cb.keySet()).stream().map(k -> ca.get(k).name()).toList()));
        List<Change> changedCols = new ArrayList<>();
        for (Column x : ca.values().stream().sorted(java.util.Comparator.comparing(Column::name)).toList()) {
            Column y = cb.get(x.name().toUpperCase(Locale.ROOT));
            if (y == null) {
                continue;
            }
            String n = y.name();
            field(changedCols, n, "nativeType", x.nativeType(), y.nativeType());
            field(changedCols, n, "length", x.length(), y.length());
            field(changedCols, n, "precision", x.precision(), y.precision());
            field(changedCols, n, "scale", x.scale(), y.scale());
            field(changedCols, n, "nullable", x.nullable(), y.nullable());
            field(changedCols, n, "defaultValue", x.defaultValue(), y.defaultValue());
            field(changedCols, n, "comment", x.comment(), y.comment());
        }

        Map<String, String> ka = folded(constraints(ta, ignoreSchema));
        Map<String, String> kb = folded(constraints(tb, ignoreSchema));
        Map<String, String> ia = folded(indexes(ta)); // 1-136(A4-07) — 인덱스 구조 글도 같은 접기
        Map<String, String> ib = folded(indexes(tb));
        return new TableDiff(key, changes, addedCols, removedCols, changedCols,
                shown(minus(kb.keySet(), ka.keySet()), kb), shown(minus(ka.keySet(), kb.keySet()), ka),
                shown(minus(ib.keySet(), ia.keySet()), ib), shown(minus(ia.keySet(), ib.keySet()), ia));
    }

    /** 구조 문자열 — 이름 없이. IX(A,B DESC) / UX(A). 제약이 받치는 유니크 인덱스는 뺀다 */
    private static TreeSet<String> indexes(Table t) {
        List<List<String>> backed = new ArrayList<>();
        if (t.pk() != null) {
            backed.add(upper(t.pk().columns()));
        }
        t.uniques().forEach(u -> backed.add(upper(u.columns())));
        TreeSet<String> out = new TreeSet<>();
        for (Index ix : t.indexes()) {
            if (ix.unique() && backed.contains(upper(ix.columns()))) { // 1-136 — 대소문자 무시
                continue;
            }
            List<String> cols = new ArrayList<>();
            for (int i = 0; i < ix.columns().size(); i++) {
                cols.add(ix.columns().get(i) + (ix.sortAt(i) == SortOrder.DESC ? " DESC" : ""));
            }
            out.add((ix.unique() ? "UX(" : "IX(") + String.join(",", cols) + ")");
        }
        return out;
    }

    /** 구조 문자열 — 이름 없이. PK(A,B) / FK(A)->[스키마.]T(X)[ ON DELETE 규칙][ ON UPDATE 규칙] / UQ(A) / CK(조건) */
    private static TreeSet<String> constraints(Table t, boolean ignoreSchema) {
        TreeSet<String> out = new TreeSet<>();
        if (t.pk() != null) {
            out.add("PK(" + String.join(",", t.pk().columns()) + ")");
        }
        for (ForeignKey fk : t.fks()) {
            String ref = (!ignoreSchema && fk.refSchema() != null ? fk.refSchema() + "." : "") + fk.refTable();
            out.add("FK(" + String.join(",", fk.columns()) + ")->" + ref + "(" + String.join(",", fk.refColumns()) + ")"
                    + rule(" ON DELETE ", fk.deleteRule()) + rule(" ON UPDATE ", fk.updateRule()));
        }
        for (UniqueKey uq : t.uniques()) {
            out.add("UQ(" + String.join(",", uq.columns()) + ")");
        }
        for (Check k : t.checks()) {
            if (k.condition() != null && !k.condition().isBlank()) {
                out.add("CK(" + k.condition().strip().replaceAll("\\s+", " ") + ")");
            }
        }
        return out;
    }

    /** 기본 동작(모름·NO ACTION·RESTRICT)은 안 적는다 — 옛 스냅샷·벤더 차이가 차이로 안 뜬다(접기는 FkRule.folded 한 자리 — 1-128) */
    private static String rule(String what, FkRule r) {
        String f = FkRule.folded(r);
        return f.isEmpty() ? "" : what + f;
    }

    private static List<String> upper(List<String> cols) {
        return cols.stream().map(c -> c.toUpperCase(Locale.ROOT)).toList();
    }

    /** 제약 구조 글 → {대문자로 접은 글: 보여 줄 글}(1-128) */
    private static Map<String, String> folded(TreeSet<String> shapes) {
        Map<String, String> out = new LinkedHashMap<>();
        shapes.forEach(x -> out.putIfAbsent(x.toUpperCase(Locale.ROOT), x));
        return out;
    }

    /** 접은 열쇠들의 보여 줄 글(정렬) */
    private static List<String> shown(java.util.Collection<String> keys, Map<String, String> display) {
        return new ArrayList<>(new TreeSet<>(keys.stream().map(display::get).toList()));
    }

    /** 표 키 — 스키마를 무시하면 이름만(같은 이름이 여러 스키마면 그것만 스키마.이름). 1-60a 반영 DDL 이 같은 짝짓기로 쓴다 */
    public static Map<String, Table> index(List<Schema> schemas, boolean ignoreSchema) {
        return keyed(schemas, ignoreSchema, twice(schemas));
    }

    /** 두 스냅샷의 표 키(1-98) — 어느 한쪽에서든 이름이 겹치면 양쪽 다 「스키마.이름」. 스키마 이름까지 다른 접속 사이의 겹친 이름은 못 짝짓는다(삭제+추가) */
    public record Keyed(Map<String, Table> a, Map<String, Table> b) {
        public Keyed {
            a = Collections.unmodifiableMap(new LinkedHashMap<>(a));
            b = Collections.unmodifiableMap(new LinkedHashMap<>(b));
        }
    }

    public static Keyed index(List<Schema> a, List<Schema> b, boolean ignoreSchema) {
        Set<String> twice = new HashSet<>(twice(a));
        twice.addAll(twice(b));
        return new Keyed(keyed(a, ignoreSchema, twice), keyed(b, ignoreSchema, twice));
    }

    /** 한 스냅샷 안에서 두 번 이상 나오는 표 이름 */
    private static Set<String> twice(List<Schema> schemas) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        schemas.forEach(s -> s.tables().forEach(t -> counts.merge(t.name(), 1, Integer::sum)));
        return counts.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static Map<String, Table> keyed(List<Schema> schemas, boolean ignoreSchema, Set<String> twice) {
        Map<String, Table> out = new LinkedHashMap<>();
        for (Schema s : schemas) {
            for (Table t : s.tables()) {
                boolean plain = ignoreSchema && !twice.contains(t.name());
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
