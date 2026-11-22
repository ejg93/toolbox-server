package kr.ejg.toolbox.core.text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그 SQL 복원(4-8, 3.5). MyBatis 로그의 {@code Preparing: …?…} 와 바로 다음 {@code Parameters: 1(Integer), abc(String), null} 을 짝지어
 * {@code ?} 에 값을 넣는다. 앞머리(시각·레벨·로거)는 {@code Preparing:}·{@code Parameters:} 앞이라 무시된다.
 * log4jdbc {@code jdbc.sqltiming} 은 이미 값이 채워져 대상이 아니다.
 * <ul>
 *   <li>인용: String·Timestamp·Date·Time 등은 {@code '…'}(따옴표 두 번), 수·Boolean 은 그대로, {@code null} 은 NULL.
 *       타입 괄호가 없으면 수 모양이면 그대로, 아니면 인용</li>
 *   <li>SQL 문자열·따옴표 이름 안의 {@code ?} 는 건너뛴다</li>
 *   <li>{@code ?} 수와 파라미터 수가 다르면 warning + 원문 그대로</li>
 * </ul>
 * 결과는 응답으로만 — 어디에도 저장하지 않는다(절대 규칙 3).
 */
public final class LogSql {

    public record Param(String value, String type) {
    }

    public record Item(String sql, List<Param> params, String restored, String warning) {
        public Item {
            params = List.copyOf(params);
        }
    }

    static final String ARRAY_VALUE = "배열·바이너리 값은 못 옮긴다 — 자리 표시(NULL)로 두었다";
    static final String MAYBE_PREVIOUS = "같은 앞머리의 Preparing 이 연달아 왔다 — 앞 문장의 Parameters 일 수 있다, 확인";
    /** 앞머리에서 줄마다 달라지는 것 — 날짜·시각·맨 숫자(pid)·화살표 */
    private static final Pattern VOLATILE = Pattern.compile("\\d{2,4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}:\\d{2}(?::\\d{2})?(?:[.,]\\d+)?|(?<![\\w\\[.-])\\d+(?![\\w\\].-])|[=<>-]+>?");
    private static final Pattern PREPARING = Pattern.compile("Preparing:\\s?(.*)$");
    private static final Pattern PARAMETERS = Pattern.compile("Parameters:\\s?(.*)$");
    /** 값 뒤의 {@code (Type)} — 다음 {@code ", "} 나 줄 끝 앞이어야 한다 */
    private static final Pattern TYPE_END = Pattern.compile("\\(([A-Za-z_][\\w.$]*+(?:\\[\\])*+)\\)(?=, |$)"); // 4-50 G-08 — byte[]·String[]
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Set<String> RAW = Set.of("Integer", "Long", "Short", "Byte", "BigDecimal", "BigInteger", "Double", "Float",
            "Boolean", "int", "long", "short", "byte", "double", "float", "boolean");

    private LogSql() {
    }

    /** 짝을 기다리는 Preparing 하나 — doubt: 같은 열쇠의 짝 없는 Preparing 과 겹쳤거나 다른 열쇠의 Parameters 가 붙었다(가를 수 없다) */
    private static final class Pending {
        final String sql;
        final String key;
        boolean doubt;
        String params;
        boolean paired;

        Pending(String sql, String key) {
            this.sql = sql;
            this.key = key;
        }
    }

    /**
     * R-G-9 — 줄마다 앞머리 열쇠({@link #key})로 Preparing 과 Parameters 를 짝짓는다(스레드가 섞인 로그). Parameters 는 같은 열쇠의 가장 최근 짝 없는
     * Preparing 에, 같은 열쇠가 없으면 바로 앞 Preparing 이 짝 없을 때 거기에(옛 동작). 같은 열쇠가 연달아 오면 뒤 문장에 {@link #MAYBE_PREVIOUS}
     */
    public static List<Item> restore(String text) {
        List<Item> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        List<Pending> all = new ArrayList<>();
        Map<String, Deque<Pending>> open = new HashMap<>();
        for (String line : text.replace("\r\n", "\n").split("\n")) {
            Matcher p = PREPARING.matcher(line);
            if (p.find()) {
                String k = key(line, p.start());
                Deque<Pending> q = open.computeIfAbsent(k, n -> new ArrayDeque<>());
                Pending x = new Pending(p.group(1).trim(), k);
                x.doubt = !q.isEmpty();
                if (!q.isEmpty()) {
                    q.peek().doubt = true; // 마무리 리뷰 I3 — 겹친 쪽 모두 확신 못 한다. 머리 아래는 들어올 때 이미 표시됐다(4-50 G-06 — 선형)
                }
                q.push(x);
                all.add(x);
                continue;
            }
            Matcher m = PARAMETERS.matcher(line);
            if (m.find()) {
                attach(all, open, key(line, m.start()), m.group(1));
            }
        }
        for (Pending x : all) {
            Item it = item(x.sql, x.params);
            out.add(x.doubt && it.warning() == null ? new Item(it.sql(), it.params(), it.restored(), MAYBE_PREVIOUS) : it);
        }
        return out;
    }

    /** Parameters 한 줄을 짝에 붙인다 — 같은 열쇠의 가장 최근 짝 없는 것, 없으면 바로 앞이 짝 없을 때, 그것도 없으면 버린다 */
    private static void attach(List<Pending> all, Map<String, Deque<Pending>> open, String key, String params) {
        Deque<Pending> q = open.get(key);
        Pending x = q == null || q.isEmpty() ? null : q.pop();
        Pending last = all.isEmpty() ? null : all.get(all.size() - 1);
        if (x == null && last != null && !last.paired) {
            x = last;
            Deque<Pending> own = open.get(x.key);
            if (own != null) {
                own.remove(x); // 4-50 G-06 — 자기 열쇠 큐에서만(전에는 큐 전부를 훑어 제곱 시간)
            }
            x.doubt |= !key.isEmpty() && !x.key.isEmpty(); // 마무리 리뷰 I3 — 열쇠가 둘 다 있는데 다르면 붙이되 경고
        } else if (x == null && last != null && last.key.equals(key)) {
            x = new Pending(last.sql, last.key); // 4-50 G-07 — 배치 실행기: 문장 하나에 Parameters 여러 줄, 줄마다 항목
            x.doubt = last.doubt;
            all.add(x);
        }
        if (x != null) {
            x.params = params;
            x.paired = true;
        }
    }

    /** 짝 열쇠 — 앞머리(at 앞)에서 VOLATILE 을 빼고 공백을 하나로. 앞머리가 없으면 빈 글(전부 같은 열쇠 — 옛 동작) */
    static String key(String line, int at) {
        return VOLATILE.matcher(line.substring(0, at)).replaceAll(" ").replaceAll("\\s+", " ").strip();
    }

    static Item item(String sql, String paramLine) {
        List<Integer> marks = marks(sql);
        List<Param> params = paramLine == null ? List.of() : params(paramLine);
        if (paramLine == null && !marks.isEmpty()) {
            return new Item(sql, params, sql, "Parameters 줄이 없다 — 원문 그대로");
        }
        if (marks.size() != params.size()) {
            return new Item(sql, params, sql, "? " + marks.size() + "개 ≠ 파라미터 " + params.size() + "개 — 원문 그대로");
        }
        StringBuilder s = new StringBuilder();
        int from = 0;
        for (int k = 0; k < marks.size(); k++) {
            s.append(sql, from, marks.get(k)).append(literal(params.get(k)));
            from = marks.get(k) + 1;
        }
        s.append(sql.substring(from));
        boolean array = params.stream().anyMatch(p -> p.type() != null && p.type().endsWith("[]"));
        return new Item(sql, params, s.toString(), array ? ARRAY_VALUE : null);
    }

    /** 문자열('…')·따옴표 이름("…") 밖의 {@code ?} 위치 */
    static List<Integer> marks(String sql) {
        List<Integer> out = new ArrayList<>();
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '?') {
                out.add(i);
            }
        }
        return out;
    }

    /** {@code 1(Integer), a, b(String), null} — 값 안의 쉼표는 뒤에 {@code (Type)} 이 붙은 자리까지 한 값으로 본다 */
    static List<Param> params(String line) {
        List<Param> out = new ArrayList<>();
        String s = line.strip();
        int pos = 0;
        Matcher m = TYPE_END.matcher(s);
        boolean typed = true; // 한 번 못 찾으면 그 줄 뒤에도 없다 — 값마다 줄 끝까지 다시 훑지 않는다(G1-K18)
        while (pos < s.length()) {
            if (s.startsWith("null", pos) && (pos + 4 == s.length() || s.startsWith(", ", pos + 4))) {
                out.add(new Param(null, null));
                pos += 6;
                continue;
            }
            if (typed && m.find(pos)) {
                out.add(new Param(s.substring(pos, m.start()), m.group(1)));
                pos = m.end() + 2;
            } else {
                typed = false;
                // 타입 괄호가 없는 꼴 — 남은 것을 ", " 로 끊는다
                int comma = s.indexOf(", ", pos);
                int end = comma < 0 ? s.length() : comma;
                out.add(new Param(s.substring(pos, end), null));
                pos = end + 2;
            }
        }
        return out;
    }

    static String literal(Param p) {
        if (p.value() == null) {
            return "NULL";
        }
        String t = p.type();
        if (t != null && t.endsWith("[]")) {
            return "/* " + t + " */ NULL"; // 4-50 G-08 — 로그의 값은 [B@… 같은 참조 글이다
        }
        if (t != null && RAW.contains(t.substring(t.lastIndexOf('.') + 1))) {
            return p.value();
        }
        if (t == null && NUMBER.matcher(p.value()).matches()) {
            return p.value();
        }
        return "'" + p.value().replace("'", "''") + "'";
    }
}
