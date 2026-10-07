package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 규칙 하나 — 정의({@link Def})와 적용. 파일 사이 상태가 없는 규칙만 이 꼴이다(5-1) */
public interface Rule {

    Def def();

    /** 글롭이 맞는 입력에만 불린다 */
    List<Finding> apply(Source s);

    default boolean accepts(Source s) {
        return def().matches(s);
    }

    /**
     * {@code check/rules.yaml}·{@code customRules} 의 한 항목. {@code kind} 는 regex(기본)·absent(5-17)·file·jsUnusedImport·java·mybatis.
     * {@code enabled} 는 프로필 덮어쓰기까지 반영한 값(묶음이 꺼졌으면 false).
     */
    record Def(String id, String group, String severity, String kind, List<String> globs, String regex, List<String> flags,
            Boolean skipComments, String message, Map<String, Object> params, Boolean enabled) {

        public static final List<String> SEVERITIES = List.of("error", "warn", "info");

        public Def {
            globs = globs == null ? List.of() : List.copyOf(globs);
            flags = flags == null ? List.of() : List.copyOf(flags);
            params = params == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(params));
            kind = kind == null || kind.isBlank() ? "regex" : kind;
        }

        public boolean on() {
            return !Boolean.FALSE.equals(enabled);
        }

        /** 5-23 — 파일 전체에 대한 지적(인코딩·줄바꿈·머리 주석). 줄 1 에 저장되지만 화면·xlsx 의 줄 칸은 「파일」. mixedIndent 는 실제 줄 */
        public boolean fileLevel() {
            return "file".equals(kind) && !"mixedIndent".equals(String.valueOf(params.get("check")));
        }

        public Def withEnabled(boolean on) {
            return new Def(id, group, severity, kind, globs, regex, flags, skipComments, message, params, on);
        }

        /** 프로필 {@code codecheck.rules.<id>} 덮어쓰기 — true/false 또는 {enabled, severity, regex, flags, globs, message, params} */
        @SuppressWarnings("unchecked")
        public Def override(Object o) {
            if (o instanceof Boolean b) {
                return withEnabled(b);
            }
            if (!(o instanceof Map<?, ?> m)) {
                return this;
            }
            Map<String, Object> p = new LinkedHashMap<>(params);
            if (m.get("params") instanceof Map<?, ?> pm) {
                p.putAll((Map<String, Object>) pm);
            }
            return new Def(id, group, str(m.get("severity"), severity), kind, list(m.get("globs"), globs), str(m.get("regex"), regex),
                    list(m.get("flags"), flags), m.get("skipComments") instanceof Boolean sc ? sc : skipComments,
                    str(m.get("message"), message), p, m.get("enabled") instanceof Boolean en ? en : enabled);
        }

        private static String str(Object v, String dflt) {
            return v == null ? dflt : v.toString();
        }

        private static List<String> list(Object v, List<String> dflt) {
            if (!(v instanceof List<?> l)) {
                return dflt;
            }
            List<String> out = new ArrayList<>();
            l.forEach(x -> out.add(String.valueOf(x)));
            return out;
        }

        public Pattern pattern() {
            int f = 0;
            for (String x : flags) {
                switch (x.toLowerCase(Locale.ROOT)) {
                    case "i" -> f |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                    case "m" -> f |= Pattern.MULTILINE;
                    case "s" -> f |= Pattern.DOTALL;
                    default -> throw new IllegalArgumentException(id + ": 모르는 flags " + x);
                }
            }
            return Pattern.compile(regex, f);
        }

        /** 글롭은 파일 이름에(`*.java`), `/` 가 있으면 상대 경로 전체에 맞춘다. 붙여넣기는 {@code x.<lang>} 으로 */
        boolean matches(Source s) {
            String name = s.rel() != null && s.rel().startsWith("(") ? "x." + s.lang() : s.fileName();
            String path = s.rel() == null ? name : s.rel().replace('\\', '/');
            for (String g : globs) {
                if (glob(g).matcher(g.contains("/") ? path : name).matches()) {
                    return true;
                }
            }
            return false;
        }

        static Pattern glob(String g) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < g.length(); i++) {
                char c = g.charAt(i);
                if (c == '*' && i + 1 < g.length() && g.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i++;
                } else if (c == '*') {
                    sb.append("[^/]*");
                } else if (c == '?') {
                    sb.append("[^/]");
                } else {
                    sb.append(Pattern.quote(String.valueOf(c)));
                }
            }
            return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
        }
    }
}
