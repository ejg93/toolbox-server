package kr.ejg.toolbox.core.deliverable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kr.ejg.toolbox.core.analyze.AnalyzeStore;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;

/**
 * 선언 안 된 FK 추정(2-19, 설계 14 U-5f ②) — 매퍼 조인 등식(6-13)을 표 쌍으로 묶어 부모가 뚜렷한 쌍만 「강」 으로 04 에 싣고,
 * 나머지는 「약」 으로 작성안내 「관계 후보」 시트에 목록만 남긴다.
 * 강 = 문장마다 한쪽 표(P)의 PK 컬럼 전부가 다른 표(C)와의 등식에 들고 반대쪽은 아닌지 보아, 그런 문장이 있고 반대 방향 문장은 없을 때 → 부모 P·자식 C
 * (두 방향이 섞이면 약 — 2-19 계획 밖 결정).
 * 두 표가 다 스냅샷에 있어야 하고(표 이름 대문자로 맞춘다), 그 둘 사이에 선언 FK 가 있으면 뺀다. 뷰가 끼면 언제나 약
 */
public final class Relations {

    public record Inferred(String childTable, List<String> childCols, String parentTable, List<String> parentCols, int statements) {
        public Inferred {
            childCols = List.copyOf(childCols);
            parentCols = List.copyOf(parentCols);
        }
    }

    public record Candidate(String tableA, String colA, String tableB, String colB, int statements, boolean view) {
    }

    /** 2-27 — 이름 기반 후보: 자식 컬럼 이름·타입·길이가 다른 표의 단일 컬럼 PK 와 같다. 코드 근거가 없어 04 에는 안 싣는다 */
    /** pkPair — 자식 컬럼도 자식 표의 단일 PK(2-31). 1:1 이거나 우연히 이름이 같은 것 — 방향을 몰라 한 쌍 한 줄(표 이름 앞선 쪽이 child) */
    public record Named(String childTable, String childCol, String parentTable, String parentCol, boolean pkPair) {
        public Named(String childTable, String childCol, String parentTable, String parentCol) {
            this(childTable, childCol, parentTable, parentCol, false);
        }
    }

    /** notes — 추정에서 뺀 것의 알림(2-42 — 같은 이름 표·PK 이름 몰림). 작성안내 요약 시트에 한 줄씩 */
    public record Result(List<Inferred> strong, List<Candidate> weak, List<Named> named, List<String> notes) {
        public Result {
            strong = List.copyOf(strong);
            weak = List.copyOf(weak);
            named = List.copyOf(named);
            notes = List.copyOf(notes);
        }

        public Result(List<Inferred> strong, List<Candidate> weak, List<Named> named) {
            this(strong, weak, named, List.of());
        }

        public Result(List<Inferred> strong, List<Candidate> weak) {
            this(strong, weak, List.of());
        }

        public static Result empty() {
            return new Result(List.of(), List.of());
        }
    }

    /** 단일 PK 이름을 이만큼보다 많은 표가 같이 쓰면 이름 기반 후보에서 뺀다(2-42 B-07 — ID·SEQ 같은 흔한 이름) */
    static final int CROWD = 30;

    private Relations() {
    }

    public static Result infer(List<Schema> snapshot, List<AnalyzeStore.JoinRow> joins) {
        Set<String> twice = twice(snapshot);
        Map<String, Table> tables = new HashMap<>();
        for (Table t : Definitions.sorted(snapshot)) {
            tables.putIfAbsent(up(t.name()), t);
        }
        tables.keySet().removeAll(twice); // B-02 — 어느 스키마 것인지 모르는 이름은 추정에서 뺀다
        Set<String> declared = new HashSet<>();
        for (Table t : Definitions.sorted(snapshot)) { // 전 스키마 — 뺀 이름의 선언 FK 도 본다
            for (ForeignKey fk : t.fks()) {
                declared.add(pair(up(t.name()), up(fk.refTable())));
            }
        }
        // 표 쌍 → 문장 → 컬럼 쌍(표 이름 순으로 맞춘 A·B)
        Map<String, Map<String, Set<String[]>>> byPair = new TreeMap<>();
        for (AnalyzeStore.JoinRow j : joins) {
            String a = up(j.tableA());
            String b = up(j.tableB());
            if (!tables.containsKey(a) || !tables.containsKey(b) || a.equals(b) || declared.contains(pair(a, b))) {
                continue;
            }
            boolean swap = a.compareTo(b) > 0;
            String[] cols = swap ? new String[] {up(j.colB()), up(j.colA())} : new String[] {up(j.colA()), up(j.colB())};
            byPair.computeIfAbsent(pair(a, b), k -> new LinkedHashMap<>()).computeIfAbsent(j.nsId(), k -> new LinkedHashSet<>()).add(cols);
        }
        List<Inferred> strong = new ArrayList<>();
        List<Candidate> weak = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String[]>>> e : byPair.entrySet()) {
            String[] ab = e.getKey().split("\\|");
            Table ta = tables.get(ab[0]);
            Table tb = tables.get(ab[1]);
            boolean view = isView(ta) || isView(tb);
            List<String> pkA = pk(ta);
            List<String> pkB = pk(tb);
            int aParent = 0;
            int bParent = 0;
            Map<String, String> aToB = new LinkedHashMap<>();
            Map<String, String> bToA = new LinkedHashMap<>();
            for (Set<String[]> stmt : e.getValue().values()) {
                Set<String> colsA = new HashSet<>();
                Set<String> colsB = new HashSet<>();
                stmt.forEach(c -> {
                    colsA.add(c[0]);
                    colsB.add(c[1]);
                });
                boolean aFull = !pkA.isEmpty() && colsA.containsAll(pkA);
                boolean bFull = !pkB.isEmpty() && colsB.containsAll(pkB);
                if (aFull && !bFull) {
                    aParent++;
                    stmt.forEach(c -> aToB.putIfAbsent(c[0], c[1]));
                } else if (bFull && !aFull) {
                    bParent++;
                    stmt.forEach(c -> bToA.putIfAbsent(c[1], c[0]));
                }
            }
            int n = e.getValue().size();
            if (!view && aParent > 0 && bParent == 0) {
                strong.add(new Inferred(tb.name(), names(tb, pkA.stream().map(aToB::get).toList()), ta.name(), names(ta, pkA), n));
            } else if (!view && bParent > 0 && aParent == 0) {
                strong.add(new Inferred(ta.name(), names(ta, pkB.stream().map(bToA::get).toList()), tb.name(), names(tb, pkB), n));
            } else {
                Map<String, Integer> perCols = new TreeMap<>();
                for (Set<String[]> stmt : e.getValue().values()) {
                    Set<String> once = new HashSet<>();
                    stmt.forEach(c -> once.add(c[0] + "|" + c[1]));
                    once.forEach(k -> perCols.merge(k, 1, Integer::sum));
                }
                perCols.forEach((k, cnt) -> {
                    String[] c = k.split("\\|");
                    weak.add(new Candidate(ta.name(), c[0], tb.name(), c[1], cnt, view));
                });
            }
        }
        strong.sort((x, y) -> (x.parentTable() + "|" + x.childTable()).compareTo(y.parentTable() + "|" + y.childTable()));
        return new Result(strong, weak, named(tables, declared), notes(twice, tables));
    }

    /** 같은 이름(대문자)이 스키마 둘 이상에 있는 표 이름 */
    private static Set<String> twice(List<Schema> snapshot) {
        Map<String, Set<String>> schemas = new HashMap<>();
        for (Schema s : snapshot) {
            for (Table t : s.tables()) {
                schemas.computeIfAbsent(up(t.name()), k -> new HashSet<>()).add(up(String.valueOf(t.schema())));
            }
        }
        return schemas.entrySet().stream().filter(e -> e.getValue().size() > 1).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** ① 같은 이름 표(조인이 없어도 — 이름 기반 후보에서도 빠진다, 마무리 리뷰 I5) ② 몰린 PK 이름 */
    private static List<String> notes(Set<String> twice, Map<String, Table> tables) {
        List<String> out = new ArrayList<>();
        if (!twice.isEmpty()) {
            out.add("같은 이름 표가 여러 스키마에 있다 — 관계 추정(조인·이름 기반)에서 뺐다: " + String.join(", ", new java.util.TreeSet<>(twice)));
        }
        crowded(tables).forEach((n, cnt) -> out.add("PK 컬럼 이름 " + n + " 를 표 " + cnt + "개가 같이 쓴다 — 이름 기반 후보에서 뺐다"));
        return out;
    }

    /** 뷰 아닌 단일 컬럼 PK 표를 PK 이름(대문자)별로 세어 CROWD 를 넘는 것만, 이름순 */
    private static Map<String, Integer> crowded(Map<String, Table> tables) {
        Map<String, Integer> n = new TreeMap<>();
        for (Table p : tables.values()) {
            if (!isView(p) && p.pk() != null && p.pk().columns().size() == 1) {
                n.merge(up(p.pk().columns().get(0)), 1, Integer::sum);
            }
        }
        n.values().removeIf(c -> c <= CROWD);
        return n;
    }

    /**
     * 2-27 — 표 C 의 컬럼 c 가 다른 표 P 의 단일 컬럼 PK 와 이름(대문자)·타입(대문자, null 은 같지 않음)·길이(둘 다 null 이면 같음)가 같으면 후보.
     * 선언 FK 가 있는 쌍·뷰는 뺀다. 조인이 없어도(프로그램 분석을 안 골라도) 스냅샷만으로 돈다
     */
    static List<Named> named(Map<String, Table> tables, Set<String> declared) {
        // 부모 후보를 PK 컬럼 이름(대문자)으로 색인 — 컬럼마다 같은 이름 PK 를 가진 표만 본다(표 × 컬럼 × 표 를 안 돈다)
        Map<String, List<Table>> byPk = new HashMap<>();
        Map<Table, kr.ejg.toolbox.core.meta.Column> pkCol = new HashMap<>();
        for (Table p : tables.values()) {
            if (isView(p) || p.pk() == null || p.pk().columns().size() != 1) {
                continue;
            }
            String pkName = up(p.pk().columns().get(0));
            p.columns().stream().filter(x -> up(x.name()).equals(pkName)).findFirst().ifPresent(pc -> {
                pkCol.put(p, pc);
                byPk.computeIfAbsent(pkName, k -> new ArrayList<>()).add(p);
            });
        }
        byPk.values().removeIf(l -> l.size() > CROWD);
        List<Named> out = new ArrayList<>();
        for (Table c : tables.values()) {
            if (isView(c)) {
                continue;
            }
            for (kr.ejg.toolbox.core.meta.Column col : c.columns()) {
                for (Table p : byPk.getOrDefault(up(col.name()), List.of())) {
                    kr.ejg.toolbox.core.meta.Column pc = pkCol.get(p);
                    if (p == c || declared.contains(pair(up(c.name()), up(p.name()))) || col.nativeType() == null || pc.nativeType() == null
                            || !up(col.nativeType()).equals(up(pc.nativeType())) || !java.util.Objects.equals(col.length(), pc.length())) {
                        continue;
                    }
                    boolean pkPair = c.pk() != null && c.pk().columns().size() == 1 && up(c.pk().columns().get(0)).equals(up(col.name()));
                    if (pkPair && up(c.name()).compareTo(up(p.name())) > 0) {
                        continue; // 2-31 — PK끼리는 양방향으로 두 번 잡힌다, 한 줄만
                    }
                    out.add(new Named(c.name(), col.name(), p.name(), pc.name(), pkPair));
                }
            }
        }
        out.sort(java.util.Comparator.comparing(Named::childTable).thenComparing(Named::childCol).thenComparing(Named::parentTable));
        return out;
    }

    /** PK 컬럼 이름(대문자) — 순서대로 */
    private static List<String> pk(Table t) {
        return t.pk() == null ? List.of() : t.pk().columns().stream().map(Relations::up).toList();
    }

    /** 대문자 PK 이름 → 표의 실제 컬럼 이름 */
    private static List<String> names(Table t, List<String> upper) {
        return upper.stream().map(u -> t.columns().stream().map(c -> c.name()).filter(c -> up(c).equals(u)).findFirst().orElse(u)).toList();
    }

    private static boolean isView(Table t) {
        return t.type() != null && t.type().toUpperCase(Locale.ROOT).contains("VIEW");
    }

    private static String pair(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }
}
