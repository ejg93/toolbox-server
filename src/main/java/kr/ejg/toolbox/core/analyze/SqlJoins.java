package kr.ejg.toolbox.core.analyze;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.check.RegexRule;

/**
 * SQL 한 문장에서 서로 다른 두 표를 잇는 컬럼 등식(6-13, 설계 14 U-5f). 주석 제거·토큰화는 {@link SqlTables} 와 같은 스캐너다.
 * 별칭 표는 FROM·JOIN·쉼표·INTO·UPDATE·USING 뒤의 「표 [AS] 별칭」(별칭이 없으면 표 이름이 별칭). 별칭 범위는 문장 바닥·질의 괄호
 * ((SELECT·(WITH)·집합 연산 가지 — 안쪽은 바깥 별칭을 보고 옆 가지·안쪽은 못 본다. WITH 이름은 표가 아니라 그 별칭으로 온 등식은 버린다(R-C-M1).
 * 한계 — 파생 표 별칭({@code ) v})의 관계는 안 잡는다 · 괄호 안 DML 의 별칭은 바깥 범위에 든다 · GO 는 문장 경계로 안 본다.
 * 등식은 양쪽이 다 「별칭.컬럼」 인 = 만(Oracle (+) 허용). 별칭을 못 풀거나 두 쪽이 같은 표면 버린다.
 * SET 대입({@code SET B.X = A.X})은 등식으로 안 센다(C-JOIN-SET).
 * 표·컬럼 이름만 남긴다(규칙 3 — SQL 글은 안 남긴다)
 */
public final class SqlJoins {

    /** 뒤 낱말이 표인 말 — USING 은 MERGE·PG DELETE 의 표(6-66). JOIN … USING (열) 은 뒤가 ( 라 표가 아니다 */
    private static final Set<String> OPENERS = Set.of("FROM", "JOIN", "INTO", "UPDATE", "USING");
    private static final Set<String> SET_OPS = Set.of("UNION", "INTERSECT", "EXCEPT", "MINUS");
    private static final Set<String> SET_END = Set.of("WHERE", "FROM", "WHEN", ";");
    private static final Set<String> QUERY = Set.of("SELECT", "WITH");
    private static final String OPEN = "(";
    private static final String CLOSE = ")";
    private static final String WHEN = "WHEN";

    /** 대문자·스키마 뗌, (tableA, colA) &lt; (tableB, colB) 로 정렬 */
    public record Join(String tableA, String colA, String tableB, String colB) {
    }

    private SqlJoins() {
    }

    public static List<Join> extract(String sql) {
        List<String> all = withoutBindSchema(SqlTables.tokens(RegexRule.stripComments(sql == null ? "" : sql, "sql")));
        Set<Join> out = new LinkedHashSet<>();
        int from = 0;
        int depth = 0;
        for (int i = 0; i < all.size(); i++) {
            String x = all.get(i);
            depth = Math.max(0, depth + ("(".equals(x) ? 1 : ")".equals(x) ? -1 : 0));
            if (depth == 0 && ";".equals(x)) {
                statement(all.subList(from, i), out); // WITH 이름·별칭은 문장마다
                from = i + 1;
            }
        }
        statement(all.subList(from, all.size()), out);
        return new ArrayList<>(out);
    }

    /** 문장 하나의 등식 — 별칭은 자리의 범위에서 푼다(R-C-M1) */
    private static void statement(List<String> raw, Set<Join> out) {
        Scopes sc = Scopes.of(raw, SqlTables.cteNames(raw));
        List<String> t = outsideSet(raw); // C-JOIN-SET — 길이가 같다, 자리가 raw 와 맞는다
        for (int i = 0; i + 2 < t.size(); i++) {
            if (i > 0 && ".".equals(t.get(i - 1))) {
                continue; // 스키마.표.컬럼 의 가운데
            }
            int[] left = ref(t, i);
            if (left == null) {
                continue;
            }
            int eq = plus(t, left[1]);
            if (eq >= t.size() || !"=".equals(t.get(eq))) {
                continue;
            }
            int[] right = ref(t, eq + 1);
            if (right == null || (right[1] < t.size() && ".".equals(t.get(right[1])))) {
                continue;
            }
            String ta = sc.table(i, t.get(i));
            String tb = sc.table(eq + 1, t.get(eq + 1));
            if (ta == null || tb == null || ta.equals(tb)) {
                continue;
            }
            String ca = t.get(i + 2);
            String cb = t.get(eq + 3);
            out.add((ta + "." + ca).compareTo(tb + "." + cb) <= 0 ? new Join(ta, ca, tb, cb) : new Join(tb, cb, ta, ca));
        }
    }

    /** 표 자리의 스키마 바인드 「? .」 를 뗀다 — ${schema}.TB a 의 별칭이 읽히게(6-65 와 같은 꼴) */
    static List<String> withoutBindSchema(List<String> t) {
        List<String> out = new ArrayList<>(t.size());
        int i = 0;
        while (i < t.size()) {
            boolean bind = "?".equals(t.get(i)) && i > 0 && i + 2 < t.size() && ".".equals(t.get(i + 1)) && word(t.get(i + 2))
                    && (OPENERS.contains(t.get(i - 1)) || ",".equals(t.get(i - 1)));
            if (!bind) {
                out.add(t.get(i));
            }
            i += bind ? 2 : 1;
        }
        return out;
    }

    /**
     * SET 절 안 토큰을 「~」 로 — SET 과 같은 괄호 깊이의 토큰만, 같은 깊이의 WHERE·FROM·WHEN MATCHED·WHEN NOT·; 앞까지, 또는 그 SET 이 든 괄호가 닫힐 때까지
     * (6-68 — CAST(… CHARACTER SET utf8)). 대입 값 안 서브쿼리의 상관 등식은 조인이고, 그 뒤 대입은 다시 가린다 · CASE WHEN 은 끝이 아니다(마무리 리뷰 M1)
     */
    static List<String> outsideSet(List<String> t) {
        List<String> out = new ArrayList<>(t.size());
        int depth = 0;
        int on = -1; // SET 을 만난 괄호 깊이. -1 = 꺼짐
        for (int i = 0; i < t.size(); i++) {
            String x = t.get(i);
            depth = Math.max(0, depth + (OPEN.equals(x) ? 1 : CLOSE.equals(x) ? -1 : 0));
            if (depth < on || depth == on && setEnd(t, i)) {
                on = -1;
            }
            out.add(on >= 0 && depth == on ? "~" : x);
            if ("SET".equals(x) && on < 0) {
                on = depth;
            }
        }
        return out;
    }

    /** SET 절을 끝내는 말인가 — WHEN 은 MERGE 의 WHEN MATCHED·WHEN NOT 만(CASE WHEN 은 아니다) */
    private static boolean setEnd(List<String> t, int i) {
        String x = t.get(i);
        if (WHEN.equals(x)) {
            String next = i + 1 < t.size() ? t.get(i + 1) : "";
            return "MATCHED".equals(next) || "NOT".equals(next);
        }
        return SET_END.contains(x);
    }

    /** i 에서 「단어 . 단어」 면 {시작, 끝 다음}, 아니면 null */
    private static int[] ref(List<String> t, int i) {
        if (i + 2 < t.size() && word(t.get(i)) && ".".equals(t.get(i + 1)) && word(t.get(i + 2))) {
            return new int[] {i, i + 3};
        }
        return null;
    }

    /** Oracle 외부 조인 표시 (+) 를 건너뛴다 */
    private static int plus(List<String> t, int i) {
        if (i + 2 < t.size() && "(".equals(t.get(i)) && "+".equals(t.get(i + 1)) && ")".equals(t.get(i + 2))) {
            return i + 3;
        }
        return i;
    }

    /**
     * 별칭 범위(R-C-M1) — 문장 바닥, 질의 괄호((SELECT·(WITH), 집합 연산(UNION·INTERSECT·EXCEPT·MINUS)의 가지가 범위 하나.
     * 안쪽 범위는 바깥 별칭을 본다(상관 서브쿼리). 옆 가지·안쪽 범위의 별칭은 못 본다. 괄호 친 표·ON (…)·함수 괄호는 범위를 안 연다
     */
    private static final class Scopes {
        private final List<Map<String, String>> alias = new ArrayList<>();
        private final List<Integer> parent = new ArrayList<>();
        /** 토큰 자리 → 범위 */
        private final int[] at;

        private Scopes(int size) {
            at = new int[size];
        }

        static Scopes of(List<String> t, Set<String> cte) {
            Scopes s = new Scopes(t.size());
            Deque<Integer> opened = new ArrayDeque<>(); // ( 마다 그때의 범위
            int cur = s.open(-1);
            for (int i = 0; i < t.size(); i++) {
                String x = t.get(i);
                if (OPEN.equals(x)) {
                    opened.push(cur);
                    cur = i + 1 < t.size() && QUERY.contains(t.get(i + 1)) ? s.open(cur) : cur;
                } else if (CLOSE.equals(x) && !opened.isEmpty()) {
                    cur = opened.pop();
                } else if (SET_OPS.contains(x)) {
                    cur = s.open(s.parent.get(cur)); // 옆 가지 — 부모가 같다
                }
                s.at[i] = cur;
            }
            s.fill(t, cte);
            return s;
        }

        private int open(int p) {
            alias.add(new HashMap<>());
            parent.add(p);
            return alias.size() - 1;
        }

        /** i 자리에서 보이는 별칭의 표 — 제 범위에서 바깥으로. 없거나 WITH 이름이면 null */
        String table(int i, String name) {
            for (int s = at[i]; s >= 0; s = parent.get(s)) {
                String v = alias.get(s).get(name);
                if (v != null) {
                    return v.isEmpty() ? null : v;
                }
            }
            return null;
        }

        /** 여는 말 뒤 표 낱말과 별칭을 그 낱말의 범위에 적는다. 쉼표 조인은 다음도 표, 여는 말 뒤 ( 는 괄호 친 표(USING ( 는 열 목록) */
        private void fill(List<String> t, Set<String> cte) {
            boolean expect = false;
            int i = 0;
            while (i < t.size()) {
                String x = t.get(i);
                boolean table = expect && word(x) && !SqlTables.STOP.contains(x);
                expect = OPENERS.contains(x) || expect && "(".equals(x) && !"USING".equals(t.get(i - 1));
                if (table) {
                    i = entry(t, i, cte);
                    expect = i + 1 < t.size() && ",".equals(t.get(i + 1));
                    i += expect ? 1 : 0;
                }
                i++;
            }
        }

        /** i 의 표(스키마 사슬은 끝 마디 — 표 이름도 자기 별칭)와 뒤 별칭을 적고 먹은 끝 자리를 돌려준다. WITH 이름은 빈 글 — 표가 아니고 바깥의 같은 이름을 가린다 */
        private int entry(List<String> t, int i, Set<String> cte) {
            Map<String, String> m = alias.get(at[i]);
            int k = i;
            while (k + 2 < t.size() && ".".equals(t.get(k + 1)) && word(t.get(k + 2))) {
                k += 2;
            }
            String real = cte.contains(t.get(k)) ? "" : t.get(k);
            m.put(t.get(k), real);
            int j = k + 1 < t.size() && "AS".equals(t.get(k + 1)) ? k + 2 : k + 1;
            if (j < t.size() && word(t.get(j)) && !SqlTables.STOP.contains(t.get(j))) {
                m.put(t.get(j), real);
                return j;
            }
            return k;
        }
    }

    private static boolean word(String x) {
        char c = x.charAt(0);
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#';
    }
}
