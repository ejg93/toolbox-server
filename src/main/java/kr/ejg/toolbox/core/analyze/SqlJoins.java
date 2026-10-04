package kr.ejg.toolbox.core.analyze;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.check.RegexRule;

/**
 * SQL 한 문장에서 서로 다른 두 표를 잇는 컬럼 등식(6-13, 설계 14 U-5f). 주석 제거·토큰화는 {@link SqlTables} 와 같은 스캐너다.
 * 별칭 표는 FROM·JOIN·쉼표·INTO·UPDATE 뒤의 「표 [AS] 별칭」(별칭이 없으면 표 이름이 별칭) — 서브쿼리 범위는 가르지 않고 문장 전체가
 * 별칭 표 하나다. 등식은 양쪽이 다 「별칭.컬럼」 인 = 만(Oracle (+) 허용). 별칭을 못 풀거나 두 쪽이 같은 표면 버린다.
 * 표·컬럼 이름만 남긴다(규칙 3 — SQL 글은 안 남긴다)
 */
public final class SqlJoins {

    /** 대문자·스키마 뗌, (tableA, colA) &lt; (tableB, colB) 로 정렬 */
    public record Join(String tableA, String colA, String tableB, String colB) {
    }

    private SqlJoins() {
    }

    public static List<Join> extract(String sql) {
        List<String> t = SqlTables.tokens(RegexRule.stripComments(sql == null ? "" : sql, "sql"));
        Map<String, String> alias = aliases(t);
        Set<Join> out = new LinkedHashSet<>();
        for (int i = 0; i + 2 < t.size(); i++) {
            if (i > 0 && t.get(i - 1).equals(".")) {
                continue; // 스키마.표.컬럼 의 가운데
            }
            int[] left = ref(t, i);
            if (left == null) {
                continue;
            }
            int eq = plus(t, left[1]);
            if (eq >= t.size() || !t.get(eq).equals("=")) {
                continue;
            }
            int[] right = ref(t, eq + 1);
            if (right == null || (right[1] < t.size() && t.get(right[1]).equals("."))) {
                continue;
            }
            String ta = alias.get(t.get(i));
            String tb = alias.get(t.get(eq + 1));
            if (ta == null || tb == null || ta.equals(tb)) {
                continue;
            }
            String ca = t.get(i + 2);
            String cb = t.get(eq + 3);
            out.add((ta + "." + ca).compareTo(tb + "." + cb) <= 0 ? new Join(ta, ca, tb, cb) : new Join(tb, cb, ta, ca));
        }
        return new ArrayList<>(out);
    }

    /** i 에서 「단어 . 단어」 면 {시작, 끝 다음}, 아니면 null */
    private static int[] ref(List<String> t, int i) {
        if (i + 2 < t.size() && word(t.get(i)) && t.get(i + 1).equals(".") && word(t.get(i + 2))) {
            return new int[] {i, i + 3};
        }
        return null;
    }

    /** Oracle 외부 조인 표시 (+) 를 건너뛴다 */
    private static int plus(List<String> t, int i) {
        if (i + 2 < t.size() && t.get(i).equals("(") && t.get(i + 1).equals("+") && t.get(i + 2).equals(")")) {
            return i + 3;
        }
        return i;
    }

    /** 별칭 → 표(스키마 뗌). 표 이름도 자기 별칭 */
    static Map<String, String> aliases(List<String> t) {
        Map<String, String> out = new HashMap<>();
        boolean expect = false;
        for (int i = 0; i < t.size(); i++) {
            String x = t.get(i);
            if (x.equals("FROM") || x.equals("JOIN") || x.equals("INTO") || x.equals("UPDATE")) {
                expect = true;
                continue;
            }
            if (!expect) {
                continue;
            }
            expect = false;
            if (!word(x) || SqlTables.STOP.contains(x)) {
                continue;
            }
            String table = x;
            while (i + 2 < t.size() && t.get(i + 1).equals(".") && word(t.get(i + 2))) {
                table = t.get(i + 2);
                i += 2;
            }
            out.put(table, table);
            int j = i + 1;
            if (j < t.size() && t.get(j).equals("AS")) {
                j++;
            }
            if (j < t.size() && word(t.get(j)) && !SqlTables.STOP.contains(t.get(j))) {
                out.put(t.get(j), table);
                i = j;
            }
            if (i + 1 < t.size() && t.get(i + 1).equals(",")) {
                expect = true; // 쉼표 조인 — 다음도 표
                i++;
            }
        }
        return out;
    }

    private static boolean word(String x) {
        char c = x.charAt(0);
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#';
    }
}
