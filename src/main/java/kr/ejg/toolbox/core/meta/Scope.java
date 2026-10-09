package kr.ejg.toolbox.core.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 수집 범위 — 프로필 {@code scope} 와 같은 타입(6장).
 * <ul>
 *   <li>{@code schemas} 가 있으면 그 스키마만</li>
 *   <li>{@code include.tables} 가 있으면 그 목록만 — 제외 규칙은 안 본다</li>
 *   <li>없으면 제외: 접두·접미·정규식·목록</li>
 *   <li>{@code skipEmpty} 면 {@code rowCount == 0} 만 뺀다. 모름(null)은 남긴다</li>
 * </ul>
 * 이름 비교는 대소문자를 가리지 않는다 — 오라클은 대문자, PostgreSQL 은 소문자로 돌려준다.
 */
public record Scope(List<String> schemas, Exclude exclude, Include include, Boolean skipEmpty) {

    public Scope {
        schemas = schemas == null ? List.of() : List.copyOf(schemas);
    }

    /** 제한 없음 */
    public static Scope all() {
        return new Scope(null, null, null, null);
    }

    /**
     * 스키마 지정·include·exclude 중 하나라도 있거나 skipEmpty 면 거른 것(1-14). 필드가 늘면 여기만 고친다(1-24 — 전엔 SnapshotStore 가 필드를 따로 나열했다).
     * JSON(스냅샷 {@code scope} 열)에 안 싣는다
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isFiltered() {
        boolean excludes = exclude != null
                && !(exclude.prefixes().isEmpty() && exclude.suffixes().isEmpty() && exclude.regex().isEmpty() && exclude.tables().isEmpty());
        boolean includes = include != null && !include.tables().isEmpty();
        return !schemas.isEmpty() || excludes || includes || Boolean.TRUE.equals(skipEmpty);
    }

    /** 범위 한 줄(3-15·6-23) — 화면·xlsx·정합성이 같은 글을 쓴다. 거르지 않으면 「없음(전부)」 */
    public String summary() {
        if (!isFiltered()) {
            return "없음(전부)";
        }
        List<String> parts = new ArrayList<>();
        if (!schemas.isEmpty()) {
            parts.add("스키마 " + few(schemas));
        }
        if (include != null && !include.tables().isEmpty()) {
            parts.add("포함 목록 " + include.tables().size() + "개"); // accepts 가 include 만 보므로 제외는 안 적는다
        } else if (exclude != null) {
            if (!exclude.prefixes().isEmpty()) {
                parts.add("제외 접두 " + few(exclude.prefixes()));
            }
            if (!exclude.suffixes().isEmpty()) {
                parts.add("제외 접미 " + few(exclude.suffixes()));
            }
            if (!exclude.regex().isEmpty()) {
                parts.add("제외 정규식 " + few(exclude.regex()));
            }
            if (!exclude.tables().isEmpty()) {
                parts.add("제외 목록 " + exclude.tables().size() + "개");
            }
        }
        if (Boolean.TRUE.equals(skipEmpty)) {
            parts.add("빈 표 제외");
        }
        return String.join(" · ", parts);
    }

    /** 다섯까지 · 로, 넘으면 「 외 n」 */
    static String few(List<String> xs) {
        return xs.size() <= 5 ? String.join("·", xs) : String.join("·", xs.subList(0, 5)) + " 외 " + (xs.size() - 5);
    }

    public record Exclude(List<String> prefixes, List<String> suffixes, List<String> regex, List<String> tables) {
        public Exclude {
            prefixes = prefixes == null ? List.of() : List.copyOf(prefixes);
            suffixes = suffixes == null ? List.of() : List.copyOf(suffixes);
            regex = regex == null ? List.of() : List.copyOf(regex);
            tables = tables == null ? List.of() : List.copyOf(tables);
            // 잘못된 정규식은 수집 때가 아니라 프로필을 읽을 때 터지게(2026-09-27 AI 리뷰 — 0-3 모르는 키 예외와 같은 결)
            for (String r : regex) {
                try {
                    Pattern.compile(r);
                } catch (java.util.regex.PatternSyntaxException e) {
                    throw new IllegalArgumentException("scope.exclude.regex 가 정규식이 아니다: " + r, e);
                }
            }
        }
    }

    public record Include(List<String> tables) {
        public Include {
            tables = tables == null ? List.of() : List.copyOf(tables);
        }
    }

    public boolean acceptsSchema(String schema) {
        return schemas.isEmpty() || upper(schemas).contains(up(schema));
    }

    public boolean accepts(Table t) {
        return reason(t).isEmpty(); // 6-23 — 사유와 판정이 한 몸
    }

    /** accepts 와 같은 순서로 전부 — 스키마 → skipEmpty → 이름 규칙. 걸린 규칙 한 줄, 안 걸리면 "" */
    public String reason(Table t) {
        if (!acceptsSchema(t.schema())) {
            return "스키마 " + few(schemas) + " 밖";
        }
        if (Boolean.TRUE.equals(skipEmpty) && t.rowCount() != null && t.rowCount() == 0L) {
            return "빈 표(skipEmpty)";
        }
        return reason(t.name());
    }

    /** 이름 규칙만 — 걸린 규칙 한 줄, 안 걸리면 "". 스키마·행 수를 모르는 표(정합성 6-23)에 쓴다 */
    public String reason(String table) {
        String name = up(table);
        if (include != null && !include.tables().isEmpty()) {
            return upper(include.tables()).contains(name) ? "" : "include 목록 밖";
        }
        if (exclude == null) {
            return "";
        }
        for (String p : exclude.prefixes()) {
            if (name.startsWith(up(p))) {
                return "제외 접두 " + p;
            }
        }
        for (String s : exclude.suffixes()) {
            if (name.endsWith(up(s))) {
                return "제외 접미 " + s;
            }
        }
        for (String r : exclude.regex()) {
            if (Pattern.compile(r, Pattern.CASE_INSENSITIVE).matcher(table == null ? "" : table).find()) {
                return "제외 정규식 " + r;
            }
        }
        return upper(exclude.tables()).contains(name) ? "제외 목록" : "";
    }

    private static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    private static Set<String> upper(List<String> xs) {
        return xs.stream().map(Scope::up).collect(Collectors.toSet());
    }
}
