package kr.ejg.toolbox.core.analyze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kr.ejg.toolbox.core.check.RegexRule;

/**
 * SQL 글 한 문장 → 쓰는 테이블과 CRUD 글자(6-1). 정규식·파서 의존 없이 토큰을 한 번 훑는다.
 * <p>
 * 동사는 주석을 지운 글의 괄호 밖 첫 동사(SELECT·INSERT·UPDATE·DELETE·MERGE·TRUNCATE), 아니면 문장 태그. 글자는 동사의 대상 표가 C·U·D(MERGE 는 C·U),
 * 그 밖에 FROM·JOIN·USING 으로 읽는 표는 R. update 로 하는 삭제는 U — 컬럼 이름으로 추정하지 않는다(3.2, 설계 8).
 * <p>
 * {@code (} 를 만나면 표 문맥을 닫는다 — 안쪽 SELECT 는 제 FROM 이 잡고 {@code ) TB} 의 별칭은 문맥 밖이다. 괄호 층마다 켜짐을 두어
 * SELECT 가 연 층에서만 FROM·JOIN·USING 이 문맥을 연다 — {@code EXTRACT(DAY FROM d)}·{@code CONVERT(x USING y)} 는 표가 아니다(6-36a, 설계 46).
 * 괄호 밖 {@code ;}·{@code GO} 는 문장 경계, WITH 이름은 표가 아니다.
 * MyBatis 분기({@code <when>}·{@code <otherwise>})는 호출 쪽이 {@link #BRANCH} 로 표시해 넘긴다 — 분기마다 다른 표가 별칭으로 먹히지 않게.
 */
public final class SqlTables {

    /** {@code <when>}·{@code <otherwise>} 시작 표시(MapperIndex 가 넣는다) */
    public static final char BRANCH = '\u0001';

    /** 표 자리에 오면 표가 아닌 말 — 실물 표본 A 가 표 이름 전체와 대조한다(6-8) */
    static final Set<String> STOP = Set.of("SELECT", "DUAL", "ON", "WHERE", "SET", "VALUES", "AS", "LEFT", "RIGHT", "INNER", "OUTER",
            "FULL", "CROSS", "NATURAL", "JOIN", "GROUP", "ORDER", "HAVING", "UNION", "MINUS", "EXCEPT", "INTERSECT", "LIMIT", "OFFSET",
            "WITH", "START", "CONNECT", "USING", "WHEN", "THEN", "FETCH", "FOR", "RETURNING", "PARTITION", "WINDOW", "USE", "FORCE",
            "INTO", "FROM", "UPDATE", "DELETE", "INSERT", "MERGE", "AND", "OR", "NOT", "IN", "EXISTS", "IS", "NULL", "LATERAL",
            "ONLY", "TABLE");

    public enum Crud {
        C, R, U, D
    }

    /** 표 하나와 그 글자. 이름은 대문자, 스키마는 뗀다 */
    public record Ref(String table, Set<Crud> crud) {

        public Ref {
            crud = Collections.unmodifiableSet(crud.isEmpty() ? EnumSet.noneOf(Crud.class) : EnumSet.copyOf(crud));
        }

        public String letters() {
            StringBuilder sb = new StringBuilder();
            crud.forEach(c -> sb.append(c.name()));
            return sb.toString();
        }
    }

    /** verb — SELECT 등 대문자(못 정하면 null). unresolved — {@code tagVerb}(태그 ≠ 동사)·{@code table}(표 자리가 식별자 아님) */
    public record Result(String verb, List<Ref> refs, List<String> unresolved) {

        public Result {
            refs = List.copyOf(refs);
            unresolved = List.copyOf(unresolved);
        }
    }

    private static final Set<String> VERBS = Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "TRUNCATE");
    private static final Set<String> BASIC = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");

    /** 한 행 더미 표 — 표로 안 센다. DB_ROOT 는 CUBRID 의 DUAL(eGov cubrid 매퍼 29, V-13) */
    private static final Set<String> DUMMY = Set.of("DUAL", "DB_ROOT");
    /** 표 자리에서 건너뛰는 말 — {@code DELETE LOW_PRIORITY QUICK IGNORE FROM t}·{@code MERGE INTO t}(MERGE 가 이미 열었다) (6-36b) */
    private static final Set<String> SKIP = Set.of("TABLE", "ONLY", "INTO", "LOW_PRIORITY", "QUICK", "IGNORE", "DELAYED", "HIGH_PRIORITY",
            "TEMP", "TEMPORARY", "PERCENT");
    /** {@code 이름(} 뒤 이 말이면 표 함수가 아니라 표 + 잠금 힌트({@code FROM t (NOLOCK)}) */
    private static final Set<String> HINTS = Set.of("NOLOCK", "READUNCOMMITTED", "READPAST", "ROWLOCK", "UPDLOCK", "HOLDLOCK", "TABLOCK",
            "TABLOCKX", "XLOCK", "NOWAIT", "INDEX", "FORCESEEK");
    /** 괄호 친 표가 아니라 질의인 괄호의 첫 말 */
    private static final Set<String> PAREN_QUERY = Set.of("SELECT", "WITH", "VALUES");

    private enum State {
        CLOSED, TABLE, ALIAS_OK, ALIAS, AFTER_ALIAS
    }

    /** 훑는 동안의 문장 상태 — 괄호 층별 켜짐(바닥은 문장 동사가 있을 때)·동사·대상 문맥(6-36a, 설계 46 §3-4·5) */
    private static final class Scan {
        final List<Boolean> on = new ArrayList<>();
        String verb;
        boolean targetDone;
        /** 이 문맥이 대상 표 문맥인가 */
        boolean target;
        State st;
        /** 다음에 읽는 표의 글자 */
        Set<Crud> pending;

        Scan(String verb) {
            reset(verb);
        }

        /** 새 문장 — 괄호 층은 바닥만 */
        void reset(String v) {
            verb = v;
            targetDone = false;
            target = false;
            st = State.CLOSED;
            pending = EnumSet.of(Crud.R);
            on.clear();
            on.add(v != null);
        }
    }

    private SqlTables() {
    }

    /** tag 는 매퍼 문장 요소 이름(select·insert·update·delete) — 괄호 밖 첫 동사가 없을 때 쓴다 */
    public static Result extract(String text, String tag) {
        List<String> t = tokens(RegexRule.stripComments(text == null ? "" : text, "sql"));
        List<String> unresolved = new ArrayList<>();
        String tagVerb = tag == null ? null : switch (tag.toLowerCase(Locale.ROOT)) {
            case "select" -> "SELECT";
            case "insert" -> "INSERT";
            case "update" -> "UPDATE";
            case "delete" -> "DELETE";
            default -> null;
        };
        String verb = firstVerb(t, 0);
        if (verb != null && tagVerb != null && BASIC.contains(verb) && !verb.equals(tagVerb)) {
            unresolved.add("tagVerb");
        }
        if (verb == null) {
            verb = tagVerb;
        }

        Map<String, EnumSet<Crud>> refs = new TreeMap<>();
        Scan sc = new Scan(verb);
        for (int i = 0; i < t.size(); i++) {
            String x = t.get(i);
            if (track(t, i, sc)) {
                continue;
            }
            // 문맥을 여는 말 — 켜진 층에서는 어느 상태에서든 먼저
            EnumSet<Crud> open = open(t, i, sc);
            if (open != null) {
                if (x.equals("FROM") && sc.target && sc.st == State.TABLE) {
                    continue; // DELETE FROM — 대상 그대로
                }
                sc.target = !open.equals(EnumSet.of(Crud.R)) || x.equals("DELETE");
                if (sc.target) {
                    sc.targetDone = true;
                }
                sc.pending = open;
                sc.st = State.TABLE;
                continue;
            }
            if (sc.st == State.TABLE) {
                i = table(t, i, sc, refs, unresolved);
            } else {
                alias(x, sc);
            }
        }
        refs.keySet().removeAll(cteNames(t));
        List<Ref> out = new ArrayList<>();
        refs.forEach((k, v) -> out.add(new Ref(k, v)));
        return new Result(verb, out, unresolved.stream().distinct().toList());
    }

    /** 괄호 층을 켜고 끈다 — {@code (} 는 꺼진 층, 동사(괄호 안 SELECT·DML CTE 의 DELETE 등)는 그 층을 켠다. 바닥은 안 뺀다. 문장 경계면 새 문장으로 두고 참 */
    private static boolean track(List<String> t, int i, Scan sc) {
        String x = t.get(i);
        boolean bottom = sc.on.size() == 1;
        if (bottom && (x.equals(";") || x.equals("GO"))) {
            sc.reset(firstVerb(t, i + 1));
            return true;
        }
        if (x.equals("(")) {
            sc.on.add(false);
        } else if (x.equals(")")) {
            if (!bottom) {
                sc.on.remove(sc.on.size() - 1);
            }
        } else if (VERBS.contains(x)) {
            sc.on.set(sc.on.size() - 1, true);
        }
        return false;
    }

    /** 이 자리가 문맥을 여는가 — 켜진 층에서만. {@code IS DISTINCT FROM}·인덱스 힌트 {@code FOR JOIN (i)} 는 아니다 */
    private static EnumSet<Crud> open(List<String> t, int i, Scan sc) {
        String x = t.get(i);
        String prev = i > 0 ? t.get(i - 1) : "";
        if (!lit(sc) || x.equals("FROM") && prev.equals("DISTINCT") || x.equals("JOIN") && prev.equals("FOR")) {
            return null;
        }
        return opener(x, sc.verb, sc.targetDone);
    }

    private static boolean lit(Scan sc) {
        return sc.on.get(sc.on.size() - 1);
    }

    /** 표 자리의 토큰 하나 — 표면 refs 에 더하고 별칭 자리로. 이름 사슬({@code a.b.c}·{@code a..b})을 먹었으면 그 끝 위치를 돌려준다 */
    private static int table(List<String> t, int i, Scan sc, Map<String, EnumSet<Crud>> refs, List<String> unresolved) {
        String x = t.get(i);
        int skip = skipEnd(t, i);
        if (skip >= 0) {
            return skip;
        }
        if (x.equals("(") && parenTable(t, i)) {
            sc.on.set(sc.on.size() - 1, true); // 괄호 친 표 — 안에서도 JOIN 이 연다
            return i;
        }
        if (DUMMY.contains(x)) {
            sc.st = State.ALIAS_OK;
        } else if (word(x) && !STOP.contains(x)) {
            int k = chainEnd(t, i);
            if (isFunction(t, k, sc.pending)) {
                sc.st = State.CLOSED;
                return k;
            }
            refs.computeIfAbsent(t.get(k), n -> EnumSet.noneOf(Crud.class)).addAll(sc.pending);
            if (!(sc.target && sc.pending.contains(Crud.D))) { // DELETE 대상 목록(DELETE t1, t2 FROM …)은 쉼표 뒤도 D
                sc.pending = EnumSet.of(Crud.R);
                sc.target = false;
            }
            sc.st = State.ALIAS_OK;
            return k;
        } else {
            if (!x.equals("(") && !word(x)) {
                unresolved.add("table");
            }
            sc.st = State.CLOSED;
        }
        return i;
    }

    /** 표 자리에서 건너뛸 말이면 그 끝 위치, 아니면 -1 — 수식어·분기 표시·앞 점·tsql {@code TOP (n) [PERCENT]} */
    private static int skipEnd(List<String> t, int i) {
        String x = t.get(i);
        if (SKIP.contains(x) || x.equals(String.valueOf(BRANCH)) || x.equals(".")) {
            return i;
        }
        if (x.equals("TOP")) {
            return at(t, i + 1, "(") ? Math.max(i, matchingParen(t, i + 1)) : i + 1;
        }
        return -1;
    }

    /** 이름 사슬의 끝 — 앞에서부터 점(여럿 허용) 뒤 단어를 따라간다 */
    private static int chainEnd(List<String> t, int i) {
        int k = i;
        while (at(t, k + 1, ".")) {
            int d = k + 1;
            while (at(t, d, ".")) {
                d++;
            }
            if (d >= t.size() || !word(t.get(d))) {
                break;
            }
            k = d;
        }
        return k;
    }

    /** 읽는 자리의 {@code 이름(} — 표 함수(JSON_TABLE·UNNEST·OPENQUERY …). {@code (} 안이 잠금 힌트면 표 */
    private static boolean isFunction(List<String> t, int k, Set<Crud> pending) {
        return at(t, k + 1, "(") && pending.equals(EnumSet.of(Crud.R)) && !(k + 2 < t.size() && HINTS.contains(t.get(k + 2)));
    }

    /** 표 자리의 {@code (} 가 괄호 친 표({@code FROM (t a)}·{@code JOIN ((b JOIN c ON …))})인가 — 안쪽 첫 말이 질의가 아닌 단어. {@code USING (id)} 는 아니다 */
    private static boolean parenTable(List<String> t, int i) {
        if (i > 0 && t.get(i - 1).equals("USING")) {
            return false;
        }
        int j = i + 1;
        while (at(t, j, "(")) {
            j++;
        }
        return j < t.size() && word(t.get(j)) && !PAREN_QUERY.contains(t.get(j));
    }

    /** 표 이름 뒤 — 별칭·쉼표·분기 표시 */
    private static void alias(String x, Scan sc) {
        switch (sc.st) {
            case ALIAS_OK -> {
                if (x.equals(",") || x.equals(String.valueOf(BRANCH))) {
                    sc.st = State.TABLE;
                } else if (x.equals("AS")) {
                    sc.st = State.ALIAS;
                } else if (word(x) && !STOP.contains(x)) {
                    sc.st = State.AFTER_ALIAS;
                } else {
                    sc.st = State.CLOSED;
                }
            }
            case ALIAS -> sc.st = word(x) ? State.AFTER_ALIAS : State.CLOSED;
            case AFTER_ALIAS -> sc.st = x.equals(",") || x.equals(String.valueOf(BRANCH)) ? State.TABLE : State.CLOSED;
            default -> {
            }
        }
    }

    /** from 부터 그 문장(괄호 밖 {@code ;}·{@code GO} 까지)의 괄호 밖 첫 동사, 없으면 null */
    private static String firstVerb(List<String> t, int from) {
        int depth = 0;
        for (int i = from; i < t.size(); i++) {
            String x = t.get(i);
            if (x.equals("(")) {
                depth++;
            } else if (x.equals(")")) {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && (x.equals(";") || x.equals("GO"))) {
                return null;
            } else if (depth == 0 && VERBS.contains(x)) {
                return x;
            }
        }
        return null;
    }

    /** WITH 이름 — {@code WITH [RECURSIVE] 이름 [(열…)] AS [NOT] [MATERIALIZED] (…) [, …]} 꼴이 끝까지 맞는 WITH 만 */
    static Set<String> cteNames(List<String> t) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < t.size(); i++) {
            if (t.get(i).equals("WITH")) {
                cte(t, i + 1, out);
            }
        }
        return out;
    }

    /** WITH 뒤 이름 목록 하나 — 다 맞으면 out 에 더한다. {@code WITH (NOLOCK)}·{@code WITH CHECK OPTION}·{@code START WITH x} 는 안 맞는다 */
    private static void cte(List<String> t, int from, Set<String> out) {
        List<String> names = new ArrayList<>();
        int j = at(t, from, "RECURSIVE") ? from + 1 : from;
        while (j < t.size() && word(t.get(j))) {
            String name = t.get(j);
            int c = at(t, j + 1, "(") ? matchingParen(t, j + 1) : j;
            if (c < 0 || !at(t, c + 1, "AS")) {
                return;
            }
            j = c + 2;
            j += at(t, j, "NOT") ? 1 : 0;
            j += at(t, j, "MATERIALIZED") ? 1 : 0;
            c = at(t, j, "(") ? matchingParen(t, j) : -1;
            if (c < 0) {
                return;
            }
            names.add(name);
            if (!at(t, c + 1, ",")) {
                out.addAll(names);
                return;
            }
            j = c + 2;
        }
    }

    /** open 의 {@code (} 와 짝인 {@code )} 위치, 없으면 -1 */
    private static int matchingParen(List<String> t, int open) {
        int depth = 0;
        for (int i = open; i < t.size(); i++) {
            if (t.get(i).equals("(")) {
                depth++;
            } else if (t.get(i).equals(")")) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean at(List<String> t, int i, String s) {
        return i < t.size() && t.get(i).equals(s);
    }

    /** 문맥을 여는 말이면 다음 표의 글자, 아니면 null. 대상 동사(INSERT INTO·UPDATE·DELETE·MERGE·TRUNCATE)는 문장마다 첫 번째만 */
    private static EnumSet<Crud> opener(String x, String verb, boolean targetDone) {
        switch (x) {
            case "FROM", "JOIN", "USING", "APPLY":
                return EnumSet.of(Crud.R);
            case "INTO":
                if ("INSERT".equals(verb)) {
                    return EnumSet.of(Crud.C); // INSERT ALL 의 INTO 여럿도 C
                }
                return targetDone ? null : target(x, verb);
            default:
                return targetDone ? null : target(x, verb);
        }
    }

    /** 대상 동사 자리 — 문장 동사와 같은 말(MERGE 는 INTO 도, INTO 없는 {@code MERGE t USING} 도)이면 그 글자 */
    private static EnumSet<Crud> target(String x, String verb) {
        if (!x.equals(verb) && !(x.equals("INTO") && "MERGE".equals(verb))) {
            return null;
        }
        return switch (x) {
            case "UPDATE" -> EnumSet.of(Crud.U);
            case "DELETE", "TRUNCATE" -> EnumSet.of(Crud.D);
            case "MERGE", "INTO" -> EnumSet.of(Crud.C, Crud.U);
            default -> null;
        };
    }

    private static boolean word(String x) {
        return wordChar(x.charAt(0));
    }

    private static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#';
    }

    /**
     * 토큰 — 단어(대문자, 따옴표·백틱·대괄호 식별자는 벗김)·{@code (}·{@code )}·{@code ,}·{@code .}·분기 표시·그 밖 한 글자.
     * 문자열 리터럴은 {@code '} 하나로, {@code #{…}}·{@code ${…}} 는 {@code ?} 로 줄인다
     */
    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '\'') {
                i = literalEnd(s, i);
                out.add("'");
            } else if ((c == '#' || c == '$') && i + 1 < n && s.charAt(i + 1) == '{') {
                int e = s.indexOf('}', i + 2);
                i = e < 0 ? n : e + 1;
                out.add("?");
            } else if (c == '"' || c == '`' || c == '[' && s.indexOf(']', i + 1) > 0) {
                i = quoted(s, i, c == '[' ? ']' : c, out);
            } else if (wordChar(c)) {
                int b = i;
                while (i < n && wordChar(s.charAt(i))) {
                    i++;
                }
                out.add(s.substring(b, i).toUpperCase(Locale.ROOT));
            } else {
                out.add(String.valueOf(c));
                i++;
            }
        }
        return out;
    }

    /** 홑따옴표 글자의 끝 다음 위치 — {@code ''} 는 글자 안 */
    private static int literalEnd(String s, int i) {
        int n = s.length();
        int k = i + 1;
        while (k < n && !(s.charAt(k) == '\'' && (k + 1 >= n || s.charAt(k + 1) != '\''))) {
            k += s.charAt(k) == '\'' ? 2 : 1;
        }
        return k + 1;
    }

    /** 따옴표·백틱·대괄호 식별자 — 따옴표·백틱의 겹친 닫는 글자({@code ""})는 글자 하나(6-36a). 끝 다음 위치를 돌려준다 */
    private static int quoted(String s, int i, char close, List<String> out) {
        StringBuilder sb = new StringBuilder();
        int n = s.length();
        int k = i + 1;
        while (k < n && !(s.charAt(k) == close && (close == ']' || k + 1 >= n || s.charAt(k + 1) != close))) {
            sb.append(s.charAt(k));
            k += s.charAt(k) == close ? 2 : 1;
        }
        String id = sb.toString().trim();
        out.add(id.isEmpty() ? String.valueOf(s.charAt(i)) : id.toUpperCase(Locale.ROOT));
        return k + 1;
    }
}
