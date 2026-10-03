package kr.ejg.toolbox.core.logical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 7-8 개인정보 마스킹 SQL — 논리명·코멘트 키워드와 컬럼 이름 정규식으로 개인정보 컬럼을 찾고, 방언별 마스킹 UPDATE 를 만든다.
 * <b>만들기만 한다.</b> 실행하는 길은 없다 — 운영 데이터를 되돌릴 수 없게 바꾸는 일이라 사람이 개발·시험 사본에서 돌린다.
 * 값은 읽지도 저장하지도 않는다(규칙 2·3) — 이름(표·컬럼·논리명)만 다룬다.
 */
public final class Masking {

    public record Kind(String id, String label, List<String> keywords, Pattern columns, List<String> exclude, List<String> notContaining,
            Pattern excludeColumns, int head, int tail) {
        public Kind {
            keywords = List.copyOf(keywords);
            exclude = List.copyOf(exclude);
            notContaining = List.copyOf(notContaining);
        }
    }

    public record Rules(List<Kind> kinds) {
        public Rules {
            kinds = List.copyOf(kinds);
        }

        public Kind kind(String id) {
            return kinds.stream().filter(k -> k.id().equals(id)).findFirst().orElseThrow();
        }
    }

    /** 탐지 입력 한 컬럼. logicalName 은 사전 조립(LogicalRun), comment 는 DB 코멘트 — 둘 다 없어도 된다 */
    public record Input(String owner, String table, String col, String logicalName, String comment, String dtype, Long dlen) {
    }

    /**
     * @param reason keyword(논리명·코멘트) 또는 column(컬럼 이름)
     * @param text   문자열형이면 true — 아니면 SQL 에 안 넣는다
     */
    public record Candidate(String owner, String table, String col, String kind, String reason, String logicalName, String comment,
            String dtype, Long dlen, boolean text) {
    }

    public record Detection(List<Candidate> candidates, List<String> warnings) {
        public Detection {
            candidates = List.copyOf(candidates);
            warnings = List.copyOf(warnings);
        }
    }

    private static final Pattern TEXT = Pattern.compile("CHAR|TEXT|CLOB|STRING");
    private static final Pattern PLAIN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$#]*$");
    private static volatile Rules rules;

    private Masking() {
    }

    @SuppressWarnings("unchecked")
    public static Rules rules() {
        Rules r = rules;
        if (r != null) {
            return r;
        }
        try (InputStream in = Masking.class.getResourceAsStream("/logical/masking.yaml")) {
            if (in == null) {
                throw new IllegalStateException("logical/masking.yaml 이 없다");
            }
            Map<String, Object> doc = new ObjectMapper(new YAMLFactory()).readValue(in, Map.class);
            List<Kind> kinds = new ArrayList<>();
            for (Map<String, Object> k : (List<Map<String, Object>>) doc.get("kinds")) {
                Map<String, Object> keep = (Map<String, Object>) k.get("keep");
                kinds.add(new Kind(String.valueOf(k.get("id")), String.valueOf(k.get("label")), strings(k.get("keywords")),
                        Pattern.compile(String.valueOf(k.get("columns"))), strings(k.get("exclude")), strings(k.get("notContaining")),
                        k.get("excludeColumns") == null ? null : Pattern.compile(String.valueOf(k.get("excludeColumns"))),
                        ((Number) keep.get("head")).intValue(), ((Number) keep.get("tail")).intValue()));
            }
            r = new Rules(kinds);
            rules = r;
            return r;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        if (o == null) {
            return List.of();
        }
        return ((List<Object>) o).stream().map(String::valueOf).toList();
    }

    /** 변환 결과 + 코멘트(키 「OWNER.TABLE.COL」 대문자, owner 없으면 「.TABLE.COL」) → 탐지 입력 */
    public static List<Input> inputs(LogicalRun.Result r, Map<String, String> comments) {
        List<Input> out = new ArrayList<>();
        for (LogicalRun.Row row : r.rows()) {
            String key = (nz(row.owner()) + "." + nz(row.table()) + "." + nz(row.col())).toUpperCase(Locale.ROOT);
            out.add(new Input(row.owner(), row.table(), row.col(), row.name(), comments == null ? null : comments.get(key), row.dtype(),
                    parseLong(row.dlen())));
        }
        return out;
    }

    public static Detection detect(List<Input> inputs, Rules rules) {
        List<Candidate> out = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Input in : inputs) {
            String col = nz(in.col()).toUpperCase(Locale.ROOT);
            Kind hit = null;
            String reason = null;
            for (Kind k : rules.kinds()) {
                if (excluded(k, in, col)) {
                    continue;
                }
                if (has(in.logicalName(), k.keywords()) || has(in.comment(), k.keywords())) {
                    hit = k;
                    reason = "keyword";
                    break;
                }
            }
            if (hit == null) {
                for (Kind k : rules.kinds()) {
                    if (!excluded(k, in, col) && k.columns().matcher(col).find()) {
                        hit = k;
                        reason = "column";
                        break;
                    }
                }
            }
            if (hit == null) {
                continue;
            }
            boolean text = in.dtype() != null && TEXT.matcher(in.dtype().toUpperCase(Locale.ROOT)).find();
            if (!text) {
                warnings.add(nz(in.table()) + "." + in.col() + ": " + hit.label() + " 후보지만 문자열 아님(" + in.dtype() + ") — 건너뜀");
            }
            out.add(new Candidate(in.owner(), in.table(), in.col(), hit.id(), reason, in.logicalName(), in.comment(), in.dtype(), in.dlen(), text));
        }
        return new Detection(out, warnings);
    }

    private static boolean excluded(Kind k, Input in, String col) {
        for (String e : k.exclude()) {
            if (endsWith(in.logicalName(), e) || endsWith(in.comment(), e)) {
                return true;
            }
        }
        if (has(in.logicalName(), k.notContaining()) || has(in.comment(), k.notContaining())) {
            return true;
        }
        return k.excludeColumns() != null && k.excludeColumns().matcher(col).find();
    }

    private static boolean endsWith(String s, String suffix) {
        return s != null && s.replace(" ", "").endsWith(suffix);
    }

    private static boolean has(String s, List<String> keywords) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String t = s.replace(" ", "");
        return keywords.stream().anyMatch(t::contains);
    }

    /**
     * 표마다 UPDATE 하나. 문자열 아닌 후보는 뺀다.
     *
     * @param dialect oracle·tibero·postgresql·mariadb·mssql
     */
    public static String sql(List<Candidate> candidates, String dialect, Rules rules) {
        Dialect d = Dialect.of(dialect);
        if (d == Dialect.SYBASE) {
            throw new IllegalArgumentException("마스킹 SQL 은 Sybase 를 안 한다");
        }
        Map<String, List<Candidate>> byTable = new LinkedHashMap<>();
        int cols = 0;
        for (Candidate c : candidates) {
            if (!c.text()) {
                continue;
            }
            byTable.computeIfAbsent(nz(c.owner()) + "\u0000" + nz(c.table()), k -> new ArrayList<>()).add(c);
            cols++;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("-- 개인정보 마스킹 UPDATE — 개발·시험 사본에만. 운영 DB 에 돌리지 않는다. 되돌릴 수 없다(돌리기 전에 백업).\n");
        sb.append("-- 방언 ").append(d.label()).append(", 표 ").append(byTable.size()).append(", 컬럼 ").append(cols).append('\n');
        for (List<Candidate> list : byTable.values()) {
            Candidate first = list.get(0);
            String table = (blank(first.owner()) ? "" : id(first.owner(), d) + ".") + id(first.table(), d);
            List<String> sets = new ArrayList<>();
            List<String> notNull = new ArrayList<>();
            StringBuilder note = new StringBuilder("-- ").append(first.table()).append(':');
            for (Candidate c : list) {
                Kind k = rules.kind(c.kind());
                String col = id(c.col(), d);
                String expr = c.kind().equals("email") ? email(col, k, d) : mask(col, k.head(), k.tail(), d);
                sets.add(col + " = " + expr);
                notNull.add(col + " IS NOT NULL");
                note.append(' ').append(c.col()).append('(').append(k.label()).append(')');
            }
            sb.append('\n').append(note).append('\n');
            sb.append("UPDATE ").append(table).append(" SET\n    ").append(String.join(",\n    ", sets)).append("\nWHERE ")
                    .append(String.join(" OR ", notNull)).append(";\n");
        }
        return sb.toString();
    }

    /** 앞 h·뒤 t 글자만 남기고 가운데를 * 로. 길이가 h+t 이하면 전부 * */
    static String mask(String c, int h, int t, Dialect d) {
        String len = len(c, d);
        String all = stars(len, d);
        if (h + t == 0) {
            return all;
        }
        List<String> parts = new ArrayList<>();
        if (h > 0) {
            parts.add(left(c, String.valueOf(h), d));
        }
        parts.add(stars(len + " - " + (h + t), d));
        if (t > 0) {
            parts.add(right(c, String.valueOf(t), d));
        }
        return "CASE WHEN " + len + " <= " + (h + t) + " THEN " + all + " ELSE " + concat(parts, d) + " END";
    }

    /** @ 앞만 가린다. @ 가 없으면 일반 규칙 */
    static String email(String c, Kind k, Dialect d) {
        String at = switch (d) {
            case ORACLE, TIBERO -> "INSTR(" + c + ", '@')";
            case POSTGRESQL -> "STRPOS(" + c + ", '@')";
            case MARIADB -> "LOCATE('@', " + c + ")";
            default -> "CHARINDEX('@', " + c + ")";
        };
        String local = left(c, at + " - 1", d);
        String domain = switch (d) {
            case ORACLE, TIBERO -> "SUBSTR(" + c + ", " + at + ")";
            case POSTGRESQL -> "SUBSTR(" + c + ", " + at + ")";
            case MARIADB -> "SUBSTRING(" + c + ", " + at + ")";
            default -> "SUBSTRING(" + c + ", " + at + ", LEN(" + c + "))";
        };
        return "CASE WHEN " + at + " > 1 THEN " + concat(List.of(mask(local, k.head(), k.tail(), d), domain), d) + " ELSE "
                + mask(c, k.head(), k.tail(), d) + " END";
    }

    private static String len(String c, Dialect d) {
        return switch (d) {
            case ORACLE, TIBERO -> "LENGTH(" + c + ")";
            case POSTGRESQL, MARIADB -> "CHAR_LENGTH(" + c + ")";
            default -> "LEN(" + c + ")";
        };
    }

    private static String stars(String n, Dialect d) {
        return switch (d) {
            case ORACLE, TIBERO -> "RPAD('*', " + n + ", '*')";
            case POSTGRESQL, MARIADB -> "REPEAT('*', " + n + ")";
            default -> "REPLICATE('*', " + n + ")";
        };
    }

    private static String left(String c, String n, Dialect d) {
        return d == Dialect.ORACLE || d == Dialect.TIBERO ? "SUBSTR(" + c + ", 1, " + n + ")" : "LEFT(" + c + ", " + n + ")";
    }

    private static String right(String c, String n, Dialect d) {
        return d == Dialect.ORACLE || d == Dialect.TIBERO ? "SUBSTR(" + c + ", -" + n + ")" : "RIGHT(" + c + ", " + n + ")";
    }

    private static String concat(List<String> parts, Dialect d) {
        return switch (d) {
            case MARIADB -> "CONCAT(" + String.join(", ", parts) + ")";
            case MSSQL -> String.join(" + ", parts);
            default -> String.join(" || ", parts);
        };
    }

    private static String id(String name, Dialect d) {
        if (PLAIN.matcher(name).matches()) {
            return name;
        }
        return switch (d) {
            case MARIADB -> "`" + name.replace("`", "``") + "`";
            case MSSQL -> "[" + name.replace("]", "]]") + "]";
            default -> "\"" + name.replace("\"", "\"\"") + "\"";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Long parseLong(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
