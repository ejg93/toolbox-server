package kr.ejg.toolbox.core.check;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.ejg.toolbox.core.text.SqlTokens;
import kr.ejg.toolbox.core.text.SqlTokens.Tok;

/**
 * 매퍼 SQL 판정 다섯(5-57b, 설계 47 §3-3~8) — 주석을 지운 SQL 글 하나에서 자리(글자 위치)만 낸다. 줄 환산은 {@link MyBatisRules}.
 * <ul>
 *   <li>NULL_COMPARE — 조건 절(WHERE·ON·HAVING·WHEN 뒤) 안 {@code = NULL}·{@code <> NULL}·{@code != NULL}. SET 의 {@code = NULL} 은 대입</li>
 *   <li>NOT_IN_SUBQUERY — {@code NOT IN (SELECT} — 서브쿼리에 NULL 이 하나라도 있으면 빈 결과</li>
 *   <li>INSERT_NO_COLUMNS — {@code INSERT [수식어] INTO 표} 바로 뒤가 VALUES·SELECT·WITH</li>
 *   <li>PAGING_NO_ORDER — SELECT·WITH 문장에 ORDER BY 가 없는데 LIMIT·OFFSET·ROWNUM·TOP·FETCH(한 행 꼴은 뺀다)</li>
 *   <li>CROSS_JOIN — 쉼표 묶음(둘 이상)·ON 없는 JOIN 의 표 가운데 「별칭.열 = 별칭.열」 등식에 안 나오는 첫 표(사용자 2026-10-31 — 등식만)</li>
 * </ul>
 * partial — {@code <sql>} 조각이나 {@code <include>} 가 든 문장이라 문장 전체를 모른다 → 문장 단위 둘(페이징·교차 조인)은 안 잰다.
 */
public final class SqlLint {

    public enum Kind {
        NULL_COMPARE("mybatis.nullCompare"),
        CROSS_JOIN("mybatis.crossJoin"),
        PAGING_NO_ORDER("mybatis.pagingNoOrder"),
        INSERT_NO_COLUMNS("mybatis.insertNoColumns"),
        NOT_IN_SUBQUERY("mybatis.notInSubquery");

        private final String id;

        Kind(String rule) {
            this.id = rule;
        }

        public String rule() {
            return id;
        }
    }

    /** 판정 하나 — at 은 주석 지운 글의 글자 위치 */
    public record Hit(Kind kind, int at) {
    }

    private static final String EQ = "=";
    private static final String LT = "<";
    private static final String GT = ">";
    private static final String BANG = "!";
    private static final String DOT = ".";
    private static final String COMMA = ",";
    private static final String OPEN = "(";
    private static final String CLOSE = ")";
    private static final String PLUS_SIGN = "+";
    private static final String ONE = "1";
    private static final String NULL = "NULL";
    private static final String NOT = "NOT";
    private static final String IN = "IN";
    private static final String SELECT = "SELECT";
    private static final String WITH = "WITH";
    private static final String INSERT = "INSERT";
    private static final String INTO = "INTO";
    private static final String ORDER = "ORDER";
    private static final String BY = "BY";
    private static final String FROM = "FROM";
    private static final String DISTINCT = "DISTINCT";
    private static final String JOIN = "JOIN";
    private static final String FOR = "FOR";
    private static final String AS = "AS";
    private static final String LIMIT = "LIMIT";
    private static final String OFFSET = "OFFSET";
    private static final String ROWNUM = "ROWNUM";
    private static final String TOP = "TOP";
    private static final String FETCH = "FETCH";
    /** 쉼표 묶음이 교차 조인 후보가 되는 표 수 */
    private static final int GROUP_MIN = 2;

    private static final Set<String> COND_ON = Set.of("WHERE", "ON", "HAVING", "WHEN");
    private static final Set<String> COND_OFF = Set.of("SET", SELECT, "VALUES", "THEN", "ELSE");
    private static final Set<String> INSERT_MODS = Set.of("IGNORE", "LOW_PRIORITY", "DELAYED", "HIGH_PRIORITY");
    private static final Set<String> INSERT_BODY = Set.of("VALUES", SELECT, WITH);
    private static final Set<String> NO_CROSS = Set.of("CROSS", "NATURAL");
    private static final Set<String> JOIN_COND = Set.of("ON", "USING");
    private static final Set<String> JOIN_END = Set.of(JOIN, "WHERE", "GROUP", ORDER, "HAVING", "UNION", LIMIT, ";", "LEFT", "RIGHT", "INNER",
            "FULL", "CROSS", "NATURAL", "OUTER");
    private static final Set<String> FIRST_NEXT = Set.of("FIRST", "NEXT");

    /** 표 하나 — 별칭(없으면 이름)과 글자 위치 */
    private record Item(String alias, int at, int end) {
    }

    private SqlLint() {
    }

    public static List<Hit> check(String stripped, boolean partial) {
        List<Tok> t = SqlTokens.scan(stripped == null ? "" : stripped);
        List<Hit> out = new ArrayList<>();
        nullCompare(t, out);
        notIn(t, out);
        insertNoColumns(t, out);
        if (!partial) {
            paging(t, out);
            crossJoin(t, out);
        }
        out.sort(Comparator.comparingInt(Hit::at));
        return out;
    }

    private static String s(List<Tok> t, int i) {
        return i >= 0 && i < t.size() ? t.get(i).s() : "";
    }

    private static boolean word(List<Tok> t, int i) {
        return SqlTokens.word(s(t, i));
    }

    /** 조건 절 안의 NULL 등식·부등식 — 괄호마다 조건 여부를 쌓아 서브쿼리 뒤에도 이어진다 */
    private static void nullCompare(List<Tok> t, List<Hit> out) {
        boolean cond = false;
        Deque<Boolean> stack = new ArrayDeque<>();
        for (int i = 0; i < t.size(); i++) {
            String x = s(t, i);
            if (OPEN.equals(x)) {
                stack.push(cond);
            } else if (CLOSE.equals(x)) {
                cond = stack.isEmpty() ? cond : stack.pop();
            } else if (COND_ON.contains(x)) {
                cond = true;
            } else if (COND_OFF.contains(x)) {
                cond = false;
            } else if (cond && nullOperator(t, i)) {
                out.add(new Hit(Kind.NULL_COMPARE, t.get(i).at()));
            }
        }
    }

    /** i 가 {@code = NULL}(앞이 {@code !}·{@code <}·{@code >} 아님)·{@code <> NULL}·{@code != NULL} 의 첫 글자인가 */
    private static boolean nullOperator(List<Tok> t, int i) {
        String x = s(t, i);
        if (EQ.equals(x)) {
            String prev = s(t, i - 1);
            return NULL.equals(s(t, i + 1)) && !BANG.equals(prev) && !LT.equals(prev) && !GT.equals(prev);
        }
        boolean ne = LT.equals(x) && GT.equals(s(t, i + 1)) || BANG.equals(x) && EQ.equals(s(t, i + 1));
        return ne && NULL.equals(s(t, i + 2));
    }

    private static void notIn(List<Tok> t, List<Hit> out) {
        for (int i = 0; i + 3 < t.size(); i++) {
            if (NOT.equals(s(t, i)) && IN.equals(s(t, i + 1)) && OPEN.equals(s(t, i + 2)) && SELECT.equals(s(t, i + 3))) {
                out.add(new Hit(Kind.NOT_IN_SUBQUERY, t.get(i).at()));
            }
        }
    }

    /** INSERT 마다(selectKey 글이 앞에 붙어도) — INTO 표 사슬 바로 뒤가 VALUES·SELECT·WITH */
    private static void insertNoColumns(List<Tok> t, List<Hit> out) {
        for (int i = 0; i < t.size(); i++) {
            if (!INSERT.equals(s(t, i))) {
                continue;
            }
            int j = i + 1;
            while (INSERT_MODS.contains(s(t, j))) {
                j++;
            }
            if (!INTO.equals(s(t, j)) || !word(t, j + 1)) {
                continue;
            }
            int k = chainEnd(t, j + 1);
            if (INSERT_BODY.contains(s(t, k + 1))) {
                out.add(new Hit(Kind.INSERT_NO_COLUMNS, t.get(k + 1).at()));
            }
        }
    }

    /** 이름 사슬 {@code a.b.c} 의 끝 */
    private static int chainEnd(List<Tok> t, int i) {
        int k = i;
        while (DOT.equals(s(t, k + 1)) && word(t, k + 2)) {
            k += 2;
        }
        return k;
    }

    private static void paging(List<Tok> t, List<Hit> out) {
        String first = s(t, 0);
        if (!SELECT.equals(first) && !WITH.equals(first)) {
            return;
        }
        for (int i = 0; i + 1 < t.size(); i++) {
            if (ORDER.equals(s(t, i)) && BY.equals(s(t, i + 1))) {
                return;
            }
        }
        for (int i = 0; i < t.size(); i++) {
            if (pagingAt(t, i)) {
                out.add(new Hit(Kind.PAGING_NO_ORDER, t.get(i).at()));
                return;
            }
        }
    }

    /** 페이징 표지인가 — 한 행 꼴({@code LIMIT 1}·{@code ROWNUM = 1}·{@code TOP 1}·{@code FETCH FIRST 1})은 아니다 */
    private static boolean pagingAt(List<Tok> t, int i) {
        return switch (s(t, i)) {
            case LIMIT -> !ONE.equals(s(t, i + 1));
            case OFFSET -> true;
            case ROWNUM -> !rownumOne(t, i);
            case TOP -> !ONE.equals(s(t, i + 1)) && !(OPEN.equals(s(t, i + 1)) && ONE.equals(s(t, i + 2)));
            case FETCH -> FIRST_NEXT.contains(s(t, i + 1)) && !ONE.equals(s(t, i + 2));
            default -> false;
        };
    }

    /** {@code ROWNUM = 1}·{@code ROWNUM < 1}·{@code ROWNUM <= 1} */
    private static boolean rownumOne(List<Tok> t, int i) {
        int j = i + 1;
        if (!EQ.equals(s(t, j)) && !LT.equals(s(t, j))) {
            return false;
        }
        j++;
        if (EQ.equals(s(t, j))) {
            j++;
        }
        return ONE.equals(s(t, j));
    }

    private static void crossJoin(List<Tok> t, List<Hit> out) {
        List<Item> cands = new ArrayList<>();
        for (int i = 0; i < t.size(); i++) {
            if (FROM.equals(s(t, i)) && !DISTINCT.equals(s(t, i - 1))) {
                commaGroup(t, i + 1, cands);
            } else if (JOIN.equals(s(t, i)) && plainJoin(t, i)) {
                Item b = item(t, i + 1);
                if (b != null && !conditioned(t, b.end() + 1)) {
                    cands.add(b);
                }
            }
        }
        Set<String> linked = linked(t);
        cands.stream().filter(c -> !linked.contains(c.alias())).min(Comparator.comparingInt(Item::at))
                .ifPresent(c -> out.add(new Hit(Kind.CROSS_JOIN, c.at())));
    }

    /** CROSS·NATURAL JOIN · 인덱스 힌트 {@code FOR JOIN} 이 아닌 JOIN */
    private static boolean plainJoin(List<Tok> t, int i) {
        for (int k = i - 3; k < i; k++) {
            if (NO_CROSS.contains(s(t, k))) {
                return false;
            }
        }
        return !FOR.equals(s(t, i - 1));
    }

    /** FROM 뒤 쉼표 묶음 — 항목이 괄호(서브쿼리)면 묶음을 버린다. 둘 이상이면 전부 후보 */
    private static void commaGroup(List<Tok> t, int from, List<Item> cands) {
        List<Item> group = new ArrayList<>();
        int j = from;
        while (true) {
            if (OPEN.equals(s(t, j))) {
                return;
            }
            Item it = item(t, j);
            if (it == null) {
                break;
            }
            group.add(it);
            if (!COMMA.equals(s(t, it.end() + 1))) {
                break;
            }
            j = it.end() + 2;
        }
        if (group.size() >= GROUP_MIN) {
            cands.addAll(group);
        }
    }

    /** j 의 표 항목 — 이름 사슬 [AS] 별칭, 또는 괄호(짝 괄호 뒤 별칭이 있을 때만). 아니면 null */
    private static Item item(List<Tok> t, int j) {
        int k;
        String name;
        if (OPEN.equals(s(t, j))) {
            k = matchingParen(t, j);
            name = null;
        } else if (word(t, j) && !SqlTokens.STOP.contains(s(t, j))) {
            k = chainEnd(t, j);
            name = s(t, k);
        } else {
            return null;
        }
        if (k < 0) {
            return null;
        }
        int n = AS.equals(s(t, k + 1)) ? k + 2 : k + 1;
        if (word(t, n) && !SqlTokens.STOP.contains(s(t, n))) {
            return new Item(s(t, n), t.get(j).at(), n);
        }
        return name == null ? null : new Item(name, t.get(j).at(), k);
    }

    /** JOIN 항목 뒤 깊이 0 에 ON·USING 이 다음 절보다 먼저 오는가 */
    private static boolean conditioned(List<Tok> t, int from) {
        int depth = 0;
        for (int m = from; m < t.size(); m++) {
            String x = s(t, m);
            if (OPEN.equals(x)) {
                depth++;
            } else if (CLOSE.equals(x)) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (depth == 0 && JOIN_COND.contains(x)) {
                return true;
            } else if (depth == 0 && JOIN_END.contains(x)) {
                return false;
            }
        }
        return false;
    }

    /** 「별칭.열 [(+)] = 별칭.열 [(+)]」 등식에 나온 별칭(두 쪽이 다를 때) */
    private static Set<String> linked(List<Tok> t) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i + 2 < t.size(); i++) {
            if (!qualified(t, i) || DOT.equals(s(t, i - 1))) {
                continue;
            }
            int j = plus(t, i + 3);
            if (EQ.equals(s(t, j)) && qualified(t, j + 1) && !DOT.equals(s(t, j + 4)) && !s(t, i).equals(s(t, j + 1))) {
                out.add(s(t, i));
                out.add(s(t, j + 1));
            }
        }
        return out;
    }

    private static boolean qualified(List<Tok> t, int i) {
        return word(t, i) && DOT.equals(s(t, i + 1)) && word(t, i + 2);
    }

    /** Oracle 외부 조인 표시 {@code (+)} 를 건너뛴다 */
    private static int plus(List<Tok> t, int i) {
        return OPEN.equals(s(t, i)) && PLUS_SIGN.equals(s(t, i + 1)) && CLOSE.equals(s(t, i + 2)) ? i + 3 : i;
    }

    private static int matchingParen(List<Tok> t, int open) {
        int depth = 0;
        for (int i = open; i < t.size(); i++) {
            if (OPEN.equals(s(t, i))) {
                depth++;
            } else if (CLOSE.equals(s(t, i))) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
