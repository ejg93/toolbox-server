package kr.ejg.toolbox.core.analyze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kr.ejg.toolbox.core.check.RegexRule;

/**
 * SQL 글 한 문장 → 쓰는 테이블과 CRUD 글자(6-1). 정규식·파서 의존 없이 토큰을 한 번 훑는다.
 * <p>
 * 동사는 주석을 지운 첫 단어(SELECT·INSERT·UPDATE·DELETE·MERGE·TRUNCATE), 아니면 문장 태그. 글자는 동사의 대상 표가 C·U·D(MERGE 는 C·U),
 * 그 밖에 FROM·JOIN·USING 으로 읽는 표는 R. update 로 하는 삭제는 U — 컬럼 이름으로 추정하지 않는다(3.2, 설계 8).
 * <p>
 * {@code (} 를 만나면 표 문맥을 닫는다 — 안쪽 SELECT 는 제 FROM 이 잡고 {@code ) TB} 의 별칭은 문맥 밖이다.
 * MyBatis 분기({@code <when>}·{@code <otherwise>})는 호출 쪽이 {@link #BRANCH} 로 표시해 넘긴다 — 분기마다 다른 표가 별칭으로 먹히지 않게.
 */
public final class SqlTables {

    /** {@code <when>}·{@code <otherwise>} 시작 표시(MapperIndex 가 넣는다) */
    public static final char BRANCH = '\u0001';

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

    /** 표 자리에 오면 표가 아닌 말 — 실물 표본 A 가 표 이름 전체와 대조한다(6-8) */
    static final Set<String> STOP = Set.of("SELECT", "DUAL", "ON", "WHERE", "SET", "VALUES", "AS", "LEFT", "RIGHT", "INNER", "OUTER",
            "FULL", "CROSS", "NATURAL", "JOIN", "GROUP", "ORDER", "HAVING", "UNION", "MINUS", "EXCEPT", "INTERSECT", "LIMIT", "OFFSET",
            "WITH", "START", "CONNECT", "USING", "WHEN", "THEN", "FETCH", "FOR", "RETURNING", "PARTITION", "WINDOW", "USE", "FORCE",
            "IGNORE", "INTO", "FROM", "UPDATE", "DELETE", "INSERT", "MERGE", "AND", "OR", "NOT", "IN", "EXISTS", "IS", "NULL", "LATERAL",
            "ONLY", "TABLE");

    private enum State {
        CLOSED, TABLE, ALIAS_OK, ALIAS, AFTER_ALIAS
    }

    private SqlTables() {
    }

    /** tag 는 매퍼 문장 요소 이름(select·insert·update·delete) — 첫 단어가 동사가 아닐 때 쓴다 */
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
        String verb = null;
        for (String x : t) {
            if (word(x)) {
                verb = VERBS.contains(x) ? x : null;
                break;
            }
        }
        if (verb != null && tagVerb != null && BASIC.contains(verb) && !verb.equals(tagVerb)) {
            unresolved.add("tagVerb");
        }
        if (verb == null) {
            verb = tagVerb;
        }

        Map<String, EnumSet<Crud>> refs = new TreeMap<>();
        State st = State.CLOSED;
        EnumSet<Crud> pending = EnumSet.of(Crud.R); // 다음에 읽는 표의 글자
        boolean targetDone = false;
        boolean table = false; // 이 문맥이 대상 표 문맥인가
        for (int i = 0; i < t.size(); i++) {
            String x = t.get(i);
            // 문맥을 여는 말 — 어느 상태에서든 먼저
            EnumSet<Crud> open = opener(x, verb, targetDone);
            if (open != null) {
                if (x.equals("FROM") && table && st == State.TABLE) {
                    continue; // DELETE FROM — 대상 그대로
                }
                table = !open.equals(EnumSet.of(Crud.R)) || x.equals("DELETE");
                if (table) {
                    targetDone = true;
                }
                pending = open;
                st = State.TABLE;
                continue;
            }
            switch (st) {
                case TABLE -> {
                    if (x.equals(String.valueOf(BRANCH)) || x.equals("TABLE") || x.equals("ONLY")) {
                        continue;
                    }
                    if (DUMMY.contains(x)) {
                        st = State.ALIAS_OK;
                    } else if (word(x) && !STOP.contains(x)) {
                        String name = x;
                        while (i + 2 < t.size() && t.get(i + 1).equals(".") && word(t.get(i + 2))) {
                            name = t.get(i + 2);
                            i += 2;
                        }
                        refs.computeIfAbsent(name, k -> EnumSet.noneOf(Crud.class)).addAll(pending);
                        pending = EnumSet.of(Crud.R);
                        table = false;
                        st = State.ALIAS_OK;
                    } else {
                        if (!x.equals("(") && !word(x)) {
                            unresolved.add("table");
                        }
                        st = State.CLOSED;
                    }
                }
                case ALIAS_OK -> {
                    if (x.equals(",") || x.equals(String.valueOf(BRANCH))) {
                        st = State.TABLE;
                    } else if (x.equals("AS")) {
                        st = State.ALIAS;
                    } else if (word(x) && !STOP.contains(x)) {
                        st = State.AFTER_ALIAS;
                    } else {
                        st = State.CLOSED;
                    }
                }
                case ALIAS -> st = word(x) ? State.AFTER_ALIAS : State.CLOSED;
                case AFTER_ALIAS -> st = x.equals(",") || x.equals(String.valueOf(BRANCH)) ? State.TABLE : State.CLOSED;
                default -> {
                }
            }
        }
        List<Ref> out = new ArrayList<>();
        refs.forEach((k, v) -> out.add(new Ref(k, v)));
        return new Result(verb, out, unresolved.stream().distinct().toList());
    }

    /** 문맥을 여는 말이면 다음 표의 글자, 아니면 null. 대상 동사(INSERT INTO·UPDATE·DELETE·MERGE INTO·TRUNCATE)는 첫 번째만 */
    private static EnumSet<Crud> opener(String x, String verb, boolean targetDone) {
        switch (x) {
            case "FROM", "JOIN", "USING":
                return EnumSet.of(Crud.R);
            case "INTO":
                if ("INSERT".equals(verb)) {
                    return EnumSet.of(Crud.C); // INSERT ALL 의 INTO 여럿도 C
                }
                return "MERGE".equals(verb) && !targetDone ? EnumSet.of(Crud.C, Crud.U) : null;
            case "UPDATE":
                return "UPDATE".equals(verb) && !targetDone ? EnumSet.of(Crud.U) : null;
            case "DELETE":
                return "DELETE".equals(verb) && !targetDone ? EnumSet.of(Crud.D) : null;
            case "TRUNCATE":
                return "TRUNCATE".equals(verb) && !targetDone ? EnumSet.of(Crud.D) : null;
            default:
                return null;
        }
    }

    private static boolean word(String x) {
        char c = x.charAt(0);
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#';
    }

    /**
     * 토큰 — 단어(대문자, 따옴표·백틱 식별자는 벗김)·{@code (}·{@code )}·{@code ,}·{@code .}·분기 표시·그 밖 한 글자.
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
                i++;
                while (i < n) {
                    if (s.charAt(i) == '\'') {
                        if (i + 1 < n && s.charAt(i + 1) == '\'') {
                            i += 2;
                            continue;
                        }
                        break;
                    }
                    i++;
                }
                i++;
                out.add("'");
            } else if ((c == '#' || c == '$') && i + 1 < n && s.charAt(i + 1) == '{') {
                int e = s.indexOf('}', i + 2);
                i = e < 0 ? n : e + 1;
                out.add("?");
            } else if (c == '"' || c == '`') {
                int e = s.indexOf(c, i + 1);
                int end = e < 0 ? n : e;
                String id = s.substring(i + 1, end).trim();
                out.add(id.isEmpty() ? String.valueOf(c) : id.toUpperCase(Locale.ROOT));
                i = end + 1;
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#') {
                int b = i;
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '$' || s.charAt(i) == '#')) {
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
}
