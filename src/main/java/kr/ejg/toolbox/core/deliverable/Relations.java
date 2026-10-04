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
 * 강 = 어느 한 문장에서 한쪽 표(P)의 PK 컬럼 전부가 다른 표(C)와의 등식에 들고, 반대쪽은 그렇지 않다 → 부모 P·자식 C.
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

    public record Result(List<Inferred> strong, List<Candidate> weak) {
        public Result {
            strong = List.copyOf(strong);
            weak = List.copyOf(weak);
        }

        public static Result empty() {
            return new Result(List.of(), List.of());
        }
    }

    private Relations() {
    }

    public static Result infer(List<Schema> snapshot, List<AnalyzeStore.JoinRow> joins) {
        Map<String, Table> tables = new HashMap<>();
        for (Table t : Definitions.sorted(snapshot)) {
            tables.putIfAbsent(up(t.name()), t);
        }
        Set<String> declared = new HashSet<>();
        for (Table t : tables.values()) {
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
        return new Result(strong, weak);
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
