package kr.ejg.toolbox.core.text;

import java.util.ArrayList;
import java.util.List;
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

    private static final Pattern PREPARING = Pattern.compile("Preparing:\\s?(.*)$");
    private static final Pattern PARAMETERS = Pattern.compile("Parameters:\\s?(.*)$");
    /** 값 뒤의 {@code (Type)} — 다음 {@code ", "} 나 줄 끝 앞이어야 한다 */
    private static final Pattern TYPE_END = Pattern.compile("\\(([A-Za-z_][\\w.$]*)\\)(?=, |$)");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Set<String> RAW = Set.of("Integer", "Long", "Short", "Byte", "BigDecimal", "BigInteger", "Double", "Float",
            "Boolean", "int", "long", "short", "byte", "double", "float", "boolean");

    private LogSql() {
    }

    public static List<Item> restore(String text) {
        List<Item> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        String[] lines = text.replace("\r\n", "\n").split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher p = PREPARING.matcher(lines[i]);
            if (!p.find()) {
                continue;
            }
            String sql = p.group(1).trim();
            String paramLine = null;
            for (int j = i + 1; j < lines.length; j++) {
                if (PREPARING.matcher(lines[j]).find()) {
                    break;
                }
                Matcher m = PARAMETERS.matcher(lines[j]);
                if (m.find()) {
                    paramLine = m.group(1);
                    break;
                }
            }
            out.add(item(sql, paramLine));
        }
        return out;
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
        return new Item(sql, params, s.toString(), null);
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
        while (pos < s.length()) {
            if (s.startsWith("null", pos) && (pos + 4 == s.length() || s.startsWith(", ", pos + 4))) {
                out.add(new Param(null, null));
                pos += 6;
                continue;
            }
            Matcher m = TYPE_END.matcher(s);
            if (m.find(pos)) {
                out.add(new Param(s.substring(pos, m.start()), m.group(1)));
                pos = m.end() + 2;
            } else {
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
        if (t != null && RAW.contains(t.substring(t.lastIndexOf('.') + 1))) {
            return p.value();
        }
        if (t == null && NUMBER.matcher(p.value()).matches()) {
            return p.value();
        }
        return "'" + p.value().replace("'", "''") + "'";
    }
}
