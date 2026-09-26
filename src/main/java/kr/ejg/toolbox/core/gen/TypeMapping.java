package kr.ejg.toolbox.core.gen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.ejg.toolbox.core.meta.Column;

/**
 * 컬럼 → 자바 타입(1-9). 표는 {@code /gen/type-mapping.yaml} — M7 CRUD 생성기가 같은 표를 쓴다.
 * 못 정하면 null(생성기가 Object + TODO 로 낸다).
 */
public final class TypeMapping {

    public record Rule(List<String> nativeNames, List<String> dialects, String java) {
        public Rule {
            nativeNames = List.copyOf(nativeNames);
            dialects = List.copyOf(dialects);
        }
    }

    private static final Map<Integer, String> TYPE_NAMES = new HashMap<>();

    static {
        for (Field f : Types.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
                try {
                    TYPE_NAMES.put(f.getInt(null), f.getName());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private final List<Rule> rules;
    private final Map<String, String> jdbc;

    TypeMapping(List<Rule> rules, Map<String, String> jdbc) {
        this.rules = List.copyOf(rules);
        this.jdbc = Map.copyOf(jdbc);
    }

    @SuppressWarnings("unchecked")
    public static TypeMapping load() {
        try (InputStream in = TypeMapping.class.getResourceAsStream("/gen/type-mapping.yaml")) {
            if (in == null) {
                throw new IllegalStateException("gen/type-mapping.yaml 이 없다");
            }
            Map<String, Object> doc = new ObjectMapper(new YAMLFactory()).readValue(in, Map.class);
            List<Rule> rules = new java.util.ArrayList<>();
            for (Map<String, Object> r : (List<Map<String, Object>>) doc.get("native")) {
                List<String> names = ((List<Object>) r.get("native")).stream().map(o -> norm(String.valueOf(o))).toList();
                List<String> dialects = r.get("dialects") == null ? List.of()
                        : ((List<Object>) r.get("dialects")).stream().map(o -> String.valueOf(o).toLowerCase(Locale.ROOT)).toList();
                rules.add(new Rule(names, dialects, String.valueOf(r.get("java"))));
            }
            Map<String, String> jdbc = new HashMap<>();
            ((Map<String, Object>) doc.get("jdbc")).forEach((k, v) -> jdbc.put(k, String.valueOf(v)));
            return new TypeMapping(rules, jdbc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 대문자, 괄호 크기 떼고, 공백 하나로 — 「timestamp(6) with time zone」 → 「TIMESTAMP WITH TIME ZONE」 */
    static String norm(String nativeType) {
        String t = nativeType == null ? "" : nativeType.toUpperCase(Locale.ROOT).replaceAll("\\([^)]*\\)", " ");
        return t.trim().replaceAll("\\s+", " ");
    }

    /**
     * @param dialect oracle·tibero·postgresql·mariadb·mssql 등 — 모르면 null
     * @return 자바 타입(패키지 포함, 예 java.math.BigDecimal·Long·byte[]) 또는 null
     */
    public String javaType(Column c, String dialect) {
        String n = norm(c.nativeType());
        String d = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
        if (!n.isEmpty()) {
            for (Rule r : rules) {
                if (!r.dialects().isEmpty() && !r.dialects().contains(d)) {
                    continue;
                }
                for (String name : r.nativeNames()) {
                    if (n.equals(name) || n.startsWith(name + " ")) {
                        return resolve(r.java(), c);
                    }
                }
            }
        }
        if (c.jdbcType() != null) {
            String j = jdbc.get(TYPE_NAMES.get(c.jdbcType()));
            if (j != null) {
                return resolve(j, c);
            }
        }
        return null;
    }

    private static String resolve(String java, Column c) {
        if (!java.equals("decimal")) {
            return java;
        }
        Integer s = c.scale();
        Integer p = c.precision();
        if (s != null && s > 0 || p == null || p == 0) {
            return "java.math.BigDecimal"; // NUMBER 만 쓰면 정밀도가 없다 — 값 손실 없는 쪽
        }
        // 19 까지 Long — Oracle 의 BIGINT 관례가 NUMBER(19,0)(JPA·Hibernate 가 Long 을 이것으로 만든다)
        return p <= 9 ? "Integer" : p <= 19 ? "Long" : "java.math.BigDecimal";
    }

    /** DB 제품명·버전 문자열 → 방언 이름(스냅샷의 dbVersion 에서). 모르면 null */
    public static String dialectOf(String product) {
        String p = product == null ? "" : product.toLowerCase(Locale.ROOT);
        if (p.contains("tibero")) {
            return "tibero";
        }
        if (p.contains("oracle")) {
            return "oracle";
        }
        if (p.contains("postgres")) {
            return "postgresql";
        }
        if (p.contains("mariadb") || p.contains("mysql")) {
            return "mariadb";
        }
        if (p.contains("microsoft") || p.contains("sql server")) {
            return "mssql";
        }
        return null;
    }
}
