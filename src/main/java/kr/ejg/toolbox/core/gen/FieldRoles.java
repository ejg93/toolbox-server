package kr.ejg.toolbox.core.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * 컬럼 역할·입력 꼴 판정(7-16b, 설계 52 §3-3) — 목록·등록·수정에 보일지, 검색 조건(LIKE·EQ·BETWEEN), 입력 꼴(text·textarea·number·
 * date·datetime·time·select·radio·checkbox), 필수, 감사 컬럼. 기본 규칙 위에 프로필 {@code generator.tables.<표>.columns} 가 덮는다.
 */
final class FieldRoles {

    static final String K_AUDIT = "audit";
    static final String K_NOW = "now";
    static final String K_HTML = "html";
    static final String K_OPTIONS = "options";
    static final String K_LIST = "list";
    static final String K_INSERT = "insert";
    static final String K_EDIT = "edit";
    static final String K_QUERY = "query";
    static final String K_REQUIRED = "required";
    static final String K_STEP = "step";

    static final String NONE = "NONE";
    static final String LIKE = "LIKE";
    static final String EQ = "EQ";
    static final String BETWEEN = "BETWEEN";

    static final String TEXT = "text";
    static final String TEXTAREA = "textarea";
    static final String NUMBER = "number";
    static final String DATE = "date";
    static final String DATETIME = "datetime";
    static final String TIME = "time";
    static final String SELECT = "select";
    static final String RADIO = "radio";
    static final String CHECKBOX = "checkbox";

    static final String CREATED = "created";
    static final String CREATOR = "creator";
    static final String UPDATED = "updated";
    static final String UPDATER = "updater";

    private static final String K_NULLABLE = "nullable";
    private static final String EMPTY = "";
    private static final String T_BOOLEAN = "Boolean";
    private static final String T_DATE = "LocalDate";
    private static final String T_DATETIME = "LocalDateTime";
    private static final String T_TIME = "LocalTime";
    private static final String T_BYTES = "byte[]";
    private static final String T_STRING = "String";

    private static final Set<String> HTMLS = Set.of(TEXT, TEXTAREA, NUMBER, DATE, DATETIME, TIME, SELECT, RADIO, CHECKBOX);
    private static final Set<String> QUERIES = Set.of(NONE, LIKE, EQ, BETWEEN);
    private static final Set<String> NUMBERS = Set.of("Integer", "Long", "Short", "BigDecimal", "Double");
    private static final Set<String> FRACTIONS = Set.of("BigDecimal", "Double");
    private static final Set<String> CHOICES = Set.of(SELECT, RADIO, CHECKBOX);
    private static final Set<String> RANGES = Set.of(DATE, DATETIME);
    private static final Set<String> STAMPS = Set.of(CREATED, UPDATED);
    private static final Set<String> TIMES = Set.of(T_DATE, T_DATETIME);
    private static final Set<String> CREATES = Set.of(CREATED, CREATOR);
    private static final Set<String> UPDATES = Set.of(UPDATED, UPDATER);
    /** 여부 컬럼 — eGov 「여부」 용어(*_AT CHAR(1)) */
    private static final String FLAG_TYPE = "CHAR";
    private static final String FLAG_SUFFIX = "_AT";
    private static final Long FLAG_LENGTH = 1L;
    private static final long TEXTAREA_MIN = 1000;
    private static final List<String> YES_NO = List.of("Y", "N");
    private static final Profile.ColumnGen NO_OVERRIDE = new Profile.ColumnGen(null, null, null, null, null, null);
    private static final Map<String, String> NOW = Map.of("oracle", "SYSDATE", "tibero", "SYSDATE", "postgresql", "NOW()", "mariadb", "NOW()",
            "mssql", "GETDATE()");

    private FieldRoles() {
    }

    /**
     * f 에 역할·꼴 키 열을 넣는다. 되돌리는 것은 경고(모르는 html·query 값). insert·edit 재정의가 하나라도 있으면 감사 시각도 사용자 값이다
     * (now false, 7-18a). PK 는 edit 재정의가 있어도 수정 칸이 아니다 — 수정 화면에 PK 줄이 둘, UPDATE SET 에 PK 가 생기는 것을 막는다(경고 없음)
     */
    static List<String> apply(Map<String, Object> f, Column c, String javaType, boolean pk, Table t, GenModel.Options o) {
        List<String> warnings = new ArrayList<>();
        String where = t.name() + "." + c.name();
        String audit = auditOf(c.name(), o.audit());
        boolean shown = audit.isEmpty() && !T_BYTES.equals(javaType);
        Profile.ColumnGen g = override(o.table(), c.name());
        String html = choose(g.html(), HTMLS, htmlOf(c, javaType, t), where + ": 모르는 html 값 ", warnings);
        boolean list = g.list() == null ? shown : g.list();
        boolean fixed = g.insert() != null || g.edit() != null;
        f.put(K_AUDIT, audit);
        f.put(K_NOW, !fixed && STAMPS.contains(audit) && TIMES.contains(javaType));
        f.put(K_HTML, html);
        f.put(K_OPTIONS, g.options().isEmpty() ? optionsOf(html, c, t) : g.options());
        f.put(K_LIST, list);
        f.put(K_INSERT, orDefault(g.insert(), shown));
        f.put(K_EDIT, pk ? Boolean.FALSE : orDefault(g.edit(), shown));
        f.put(K_QUERY, choose(g.query(), QUERIES, queryOf(html, list, pk), where + ": 모르는 query 값 ", warnings));
        f.put(K_REQUIRED, !c.nullable() && (c.defaultValue() == null || c.defaultValue().isBlank()));
        f.put(K_STEP, NUMBER.equals(html) && FRACTIONS.contains(javaType));
        return warnings;
    }

    /** 모델 수준 목록 일곱 — listFields·insertFields·editFields·search·insertCols·updateCols·now(방언 현재시각 식) */
    static Map<String, Object> lists(List<Map<String, Object>> fields, String dialect) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("listFields", pick(fields, K_LIST));
        m.put("insertFields", pick(fields, K_INSERT));
        m.put("editFields", pick(fields, K_EDIT));
        m.put("search", fields.stream().filter(f -> !NONE.equals(f.get(K_QUERY))).toList());
        m.put("insertCols", sqlCols(fields, true));
        m.put("updateCols", sqlCols(fields, false));
        m.put(K_NOW, NOW.getOrDefault(dialect, "CURRENT_TIMESTAMP"));
        return m;
    }

    static List<Map<String, Object>> pick(List<Map<String, Object>> fields, String key) {
        return fields.stream().filter(f -> Boolean.TRUE.equals(f.get(key))).toList();
    }

    /** INSERT — 등록 칸 + 등록 감사 + NOT NULL 수정 감사. UPDATE — 수정 칸 + 수정 감사 */
    static List<Map<String, Object>> sqlCols(List<Map<String, Object>> fields, boolean insert) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            Object audit = f.get(K_AUDIT);
            boolean take = insert
                    ? Boolean.TRUE.equals(f.get(K_INSERT)) || CREATES.contains(audit)
                            || UPDATES.contains(audit) && Boolean.FALSE.equals(f.get(K_NULLABLE))
                    : Boolean.TRUE.equals(f.get(K_EDIT)) || UPDATES.contains(audit);
            if (take) {
                out.add(f);
            }
        }
        return out;
    }

    static String htmlOf(Column c, String javaType, Table t) {
        return switch (javaType) {
            case T_BOOLEAN -> CHECKBOX;
            case T_DATE -> DATE;
            case T_DATETIME -> DATETIME;
            case T_TIME -> TIME;
            case T_BYTES -> EMPTY;
            case T_STRING -> stringHtml(c, t);
            default -> NUMBERS.contains(javaType) ? NUMBER : TEXT;
        };
    }

    private static String stringHtml(Column c, Table t) {
        if (!inSet(c, t).isEmpty()) {
            return SELECT;
        }
        if (isFlag(c)) {
            return RADIO;
        }
        return c.length() == null || c.length() >= TEXTAREA_MIN ? TEXTAREA : TEXT;
    }

    private static boolean isFlag(Column c) {
        return c.nativeType() != null && FLAG_TYPE.equalsIgnoreCase(c.nativeType().trim()) && FLAG_LENGTH.equals(c.length())
                && c.name().toUpperCase(Locale.ROOT).endsWith(FLAG_SUFFIX);
    }

    static List<String> optionsOf(String html, Column c, Table t) {
        if (SELECT.equals(html)) {
            return inSet(c, t);
        }
        return RADIO.equals(html) ? YES_NO : List.of();
    }

    /** CHECK 가 이 컬럼 하나의 따옴표 값 IN (…) 이면 그 값들 */
    private static List<String> inSet(Column c, Table t) {
        for (Check k : t.checks()) {
            String cond = k.condition() == null ? EMPTY : k.condition().strip();
            List<String> cols = Validation.columnsIn(cond, t);
            if (cols.size() != 1 || !cols.get(0).equalsIgnoreCase(c.name())) {
                continue;
            }
            if (Validation.parse(cond) instanceof Validation.InSet in && in.quoted() && in.column().equalsIgnoreCase(c.name())) {
                return in.values();
            }
        }
        return List.of();
    }

    private static String queryOf(String html, boolean list, boolean pk) {
        if (!list || pk) {
            return NONE;
        }
        if (TEXT.equals(html)) {
            return LIKE;
        }
        if (CHOICES.contains(html)) {
            return EQ;
        }
        return RANGES.contains(html) ? BETWEEN : NONE;
    }

    private static String auditOf(String column, Profile.Audit a) {
        if (has(a.created(), column)) {
            return CREATED;
        }
        if (has(a.creator(), column)) {
            return CREATOR;
        }
        if (has(a.updated(), column)) {
            return UPDATED;
        }
        return has(a.updater(), column) ? UPDATER : EMPTY;
    }

    private static boolean has(List<String> names, String column) {
        return names.stream().anyMatch(n -> n != null && n.trim().equalsIgnoreCase(column));
    }

    /** 재정의 값이 있으면 그것, 없으면 기본값 — 둘 다 Boolean 으로(풀었다 다시 싸지 않게, SpotBugs) */
    private static Boolean orDefault(Boolean asked, boolean fallback) {
        return asked == null ? Boolean.valueOf(fallback) : asked;
    }

    private static Profile.ColumnGen override(Profile.TableGen tg, String column) {
        return tg.columns().entrySet().stream().filter(e -> e.getKey() != null && e.getKey().trim().equalsIgnoreCase(column))
                .map(Map.Entry::getValue).filter(java.util.Objects::nonNull).findFirst().orElse(NO_OVERRIDE);
    }

    /** 재정의 값이 허용 목록(대소문자 무시)에 있으면 그것, 없으면 기본값 + 경고 */
    private static String choose(String asked, Set<String> allowed, String fallback, String warning, List<String> warnings) {
        if (asked == null || asked.isBlank()) {
            return fallback;
        }
        String a = asked.trim();
        for (String v : allowed) {
            if (v.equalsIgnoreCase(a)) {
                return v;
            }
        }
        warnings.add(warning + a + " — 기본값");
        return fallback;
    }
}
