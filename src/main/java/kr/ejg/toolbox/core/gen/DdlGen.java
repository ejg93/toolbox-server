package kr.ejg.toolbox.core.gen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.FkRule;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.SortOrder;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;

/**
 * 7-6 DDL 생성·방언 변환 — 스냅샷 표 → 대상 방언 CREATE 스크립트.
 * 타입은 자바 타입을 축으로 바꾼다(원본 → {@link TypeMapping#javaType} → {@code /gen/ddl-types.yaml} → 대상).
 * 원본과 대상 방언이 같으면 원본 타입을 그대로 낸다. FK 는 표 생성 순서·순환과 무관하게 맨 끝에 ALTER 로 붙인다.
 * 실행은 하지 않는다 — 글만 만든다.
 */
public final class DdlGen {

    /**
     * @param source 원본 방언(스냅샷 제품명에서 — 모르면 null). 같은 방언이면 원본 타입 그대로
     * @param target 대상 방언 — {@link #targets()} 하나
     * @param schema 있으면 이름 앞에 {@code schema.}
     * @param prefix 표·제약·인덱스 이름 앞에 붙인다(시험용 — 같은 DB 에 겹치지 않게). 비면 없음
     */
    public record Options(String source, String target, String schema, boolean includeFk, boolean includeIndex, boolean includeComments,
            String prefix) {
    }

    public record Result(String sql, List<String> warnings, int tables) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    record Spec(List<String> targets, Map<String, Long> limits, Map<String, Integer> maxPrecision, Map<String, Map<String, String>> types,
            Set<String> now, Map<String, String> nowTarget, Set<String> reserved, Map<String, Long> fixedLimits) {
    }

    private static final Pattern PLAIN = Pattern.compile("^[A-Za-z][A-Za-z0-9_$]*$");
    private static final Pattern PLAIN_HASH = Pattern.compile("^[A-Za-z][A-Za-z0-9_$#]*$");
    private static final Pattern NUM = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");
    private static final Pattern STR = Pattern.compile("^N?('(?:[^']|'')*+')$");
    /** PostgreSQL 기본값 꼴 「'Y'::character varying」·「0::numeric」 */
    private static final Pattern PG_CAST = Pattern.compile("^('(?:[^']|'')*+'|-?\\d+(?:\\.\\d+)?)::[A-Za-z][A-Za-z0-9_ ]*(?:\\[\\])?$");
    private static final Set<String> FIXED = Set.of("CHAR", "NCHAR", "CHARACTER", "BPCHAR");
    private static final String STRING_TYPE = "String";
    private static final String NULL_WORD = "NULL";
    private static final String MSSQL = "mssql";
    /** MSSQL 이 자동증가 컬럼 타입 이름 뒤에 붙이는 낱말 */
    private static final Pattern IDENTITY_WORD = Pattern.compile("(?i)\\s+identity\\b");
    private static final String POSTGRESQL = "postgresql";
    /** 어느 방언에도 이보다 긴 길이 지정은 없다(1-90) */
    private static final long NO_SIZE = 10_485_760L;
    private static final Set<String> SIZED_CHAR = Set.of("VARCHAR", "VARCHAR2", "NVARCHAR", "NVARCHAR2", "CHARACTER VARYING", "CHAR", "NCHAR",
            "CHARACTER", "BPCHAR", "VARBINARY", "BINARY", "RAW");
    private static final Set<String> SIZED_NUM = Set.of("NUMBER", "NUMERIC", "DECIMAL", "DEC");

    private static volatile Spec spec;

    private DdlGen() {
    }

    /**
     * 1-121 — 대상 꼴 열쇠(j 자바 형식 · n 원본 타입 정규화). 문자: 길이 없음·상한 초과는 long, 고정 길이는 고정 상한 안에서만 fixed(넘으면
     * 가변 + 경고, A1-19) · 수: 자동증가인 BigDecimal 은 Long(정수여야 한다 + 경고, A1-21), 정밀도 없으면 free
     */
    private static String typeKey(Gen g, Table t, Column c, String j, String n, Long len) {
        if (STRING_TYPE.equals(j)) {
            if (len == null || len <= 0 || len > g.sp.limits().get(g.target)) {
                return "String.long";
            }
            if (!FIXED.contains(n)) {
                return j;
            }
            if (len <= g.sp.fixedLimits().get(g.target)) {
                return "String.fixed";
            }
            g.warnings.add(t.name() + "." + c.name() + ": 고정 길이 " + len + " 이 대상 상한을 넘어 가변 길이로 냈다");
            return j;
        }
        if (!"java.math.BigDecimal".equals(j)) {
            return j;
        }
        if (auto(c)) {
            g.warnings.add(t.name() + "." + c.name() + ": 자동증가 — 정수 타입으로 냈다");
            return "Long";
        }
        if (autoParent(g, t, c)) {
            g.warnings.add(t.name() + "." + c.name() + ": FK 부모가 자동증가 — 같은 정수 타입으로 냈다");
            return "Long";
        }
        return c.precision() == null || c.precision() <= 0 ? "java.math.BigDecimal.free" : j;
    }

    /** 1-134(A4-05) — FK 부모 컬럼이 자동증가(BigDecimal) — 자식도 같은 정수 타입이어야 FK 가 선다 */
    private static boolean autoParent(Gen g, Table t, Column c) {
        return refColumn(g, t, c).filter(p -> auto(p) && "java.math.BigDecimal".equals(g.types.javaType(p, g.source.isEmpty() ? null : g.source)))
                .isPresent();
    }

    /** 1-134 — c 가 든 FK 의 같은 자리 참조 컬럼(참조 컬럼 목록이 비면 부모 PK 의 같은 자리). 부모 표·컬럼이 없으면 빈 값 */
    private static Optional<Column> refColumn(Gen g, Table t, Column c) {
        for (ForeignKey fk : t.fks()) {
            int i = indexIgnoreCase(fk.columns(), c.name());
            Table parent = i < 0 ? null : g.byTable.get(fk.refTable() == null ? "" : fk.refTable().toUpperCase(Locale.ROOT));
            List<String> refs = parent == null ? List.of() : fk.refColumns().isEmpty() && parent.pk() != null ? parent.pk().columns() : fk.refColumns();
            if (i >= 0 && i < refs.size()) {
                String rn = refs.get(i);
                Optional<Column> hit = parent.columns().stream().filter(x -> x.name().equalsIgnoreCase(rn)).findFirst();
                if (hit.isPresent()) {
                    return hit;
                }
            }
        }
        return Optional.empty();
    }

    private static int indexIgnoreCase(List<String> names, String name) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** 1-134(A4-17) — FK 자식이면 부모 컬럼 길이(대상 상한 안일 때), 아니면 dflt */
    private static long fkLen(Gen g, Table t, Column c, long dflt) {
        Long len = fkColumn(t, c) ? refColumn(g, t, c).map(Column::length).orElse(null) : null;
        return len != null && len > 0 && len <= g.sp.limits().get(g.target) ? len : dflt;
    }

    /** 1-119(A1-01) — FK 자식 컬럼인가(대소문자 무시) — 부모 키처럼 길이를 준다 */
    private static boolean fkColumn(Table t, Column c) {
        return t.fks().stream().flatMap(fk -> fk.columns().stream()).anyMatch(n -> n.equalsIgnoreCase(c.name()));
    }

    /** 1-119(A1-04) — 유니크 제약 이름도 스키마 단위 이름 공간(mariadb 밖)에서 겹치면 표 이름을 넣은 이름으로 */
    private static String ukName(Gen g, Table t, UniqueKey k, int u) {
        String fallback = "UK_" + t.name() + "_" + u;
        String name = nameOr(k.name(), fallback);
        if (g.mariadb() || g.ixNames.add(name.toUpperCase(Locale.ROOT))) {
            return name;
        }
        g.warnings.add(t.name() + ": 유니크 이름 " + name + " 가 다른 표와 겹쳐 " + fallback + " 로 바꿨다");
        g.ixNames.add(fallback.toUpperCase(Locale.ROOT));
        return fallback;
    }

    /** 0-85i — {@code ddl-types.yaml} 의 예약어(다섯 방언 합집합). 품질 SQL 이 같이 쓴다 */
    public static boolean reserved(String name) {
        return name != null && spec().reserved().contains(name.toUpperCase(Locale.ROOT));
    }

    /** 대상 방언 — {@link GenModel#DIALECTS} 그대로(8-0, 목록은 한 자리) */
    public static List<String> targets() {
        return spec().targets();
    }

    @SuppressWarnings("unchecked")
    static Spec spec() {
        Spec s = spec;
        if (s != null) {
            return s;
        }
        try (InputStream in = DdlGen.class.getResourceAsStream("/gen/ddl-types.yaml")) {
            if (in == null) {
                throw new IllegalStateException("gen/ddl-types.yaml 이 없다");
            }
            Map<String, Object> doc = new ObjectMapper(new YAMLFactory()).readValue(in, Map.class);
            Map<String, Long> limits = new HashMap<>();
            ((Map<String, Object>) doc.get("limits")).forEach((k, v) -> limits.put(k, ((Number) v).longValue()));
            Map<String, Long> fixedLimits = new HashMap<>();
            ((Map<String, Object>) doc.get("fixedLimits")).forEach((k, v) -> fixedLimits.put(k, ((Number) v).longValue()));
            Map<String, Integer> maxP = new HashMap<>();
            ((Map<String, Object>) doc.get("maxPrecision")).forEach((k, v) -> maxP.put(k, ((Number) v).intValue()));
            Map<String, Map<String, String>> types = new HashMap<>();
            ((Map<String, Object>) doc.get("types")).forEach((k, v) -> {
                Map<String, String> m = new HashMap<>();
                ((Map<String, Object>) v).forEach((d, f) -> m.put(d, String.valueOf(f)));
                types.put(k, Map.copyOf(m));
            });
            Set<String> now = new HashSet<>();
            ((List<Object>) doc.get("now")).forEach(x -> now.add(String.valueOf(x).toUpperCase(Locale.ROOT)));
            Map<String, String> nowTarget = new HashMap<>();
            ((Map<String, Object>) doc.get("nowTarget")).forEach((k, v) -> nowTarget.put(k, String.valueOf(v)));
            Set<String> reserved = new HashSet<>();
            ((List<Object>) doc.get("reserved")).forEach(x -> reserved.add(String.valueOf(x).toUpperCase(Locale.ROOT)));
            List<String> targets = GenModel.DIALECTS;
            for (String d : targets) { // 방언을 더하고 표를 안 채우면 여기서 멈춘다(8-0)
                need(limits.containsKey(d), "limits", d);
                need(fixedLimits.containsKey(d), "fixedLimits", d);
                need(maxP.containsKey(d), "maxPrecision", d);
                need(nowTarget.containsKey(d), "nowTarget", d);
                types.forEach((k, m) -> need(m.containsKey(d), "types." + k, d));
            }
            s = new Spec(targets, Map.copyOf(limits), Map.copyOf(maxP), Map.copyOf(types), Set.copyOf(now), Map.copyOf(nowTarget),
                    Set.copyOf(reserved), Map.copyOf(fixedLimits));
            spec = s;
            return s;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void need(boolean ok, String key, String dialect) {
        if (!ok) {
            throw new IllegalStateException("gen/ddl-types.yaml 의 " + key + " 에 " + dialect + " 가 없다 — GenModel.DIALECTS 의 방언은 전부 있어야 한다");
        }
    }

    public static Result generate(List<Table> tables, Options o, TypeMapping types) {
        return generate(tables, o, types, false);
    }

    /** 1-131(A4-01) — exactSchema: 스키마가 스냅샷에서 왔다 — 정확 이름(반영 DDL 의 새 표가 Run.gen 과 같은 식으로 부른다) */
    static Result generate(List<Table> tables, Options o, TypeMapping types, boolean exactSchema) {
        Spec sp = spec();
        String target = lower(o.target());
        if (!sp.targets().contains(target)) {
            throw new IllegalArgumentException("DDL 대상 방언: " + o.target() + " — " + String.join("·", sp.targets()));
        }
        Gen g = new Gen(sp, o, target, lower(o.source()), types);
        g.exactSchema = exactSchema;
        return g.run(tables);
    }

    static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** 원본 이름이 DB 가 지은 이름(SYS_C…·CONSTRAINT_…·PK__…)이거나 비면 fallback */
    static String nameOr(String raw, String fallback) {
        if (blank(raw)) {
            return fallback;
        }
        String u = raw.toUpperCase(Locale.ROOT);
        if (u.startsWith("SYS_") || u.startsWith("CONSTRAINT_") || u.startsWith("PRIMARY_KEY") || "PRIMARY".equals(u) || u.contains("__")) {
            return fallback;
        }
        return raw;
    }

    /** 렌더 조각 — 1-60a 반영 DDL(AlterGen)이 같은 패키지에서 같이 쓴다(타입 매핑·인용·경고를 한 곳에) */
    /**
     * 4-36 — 맨 이름으로 나가도 되는 꼴인가(db 는 InsertGen 방언 키). $ 는 다섯 방언 다, # 는 oracle·mssql·sybase 만(mysql 은 주석 시작, pg 는 못 쓴다).
     * InsertGen.ident·uid 와 DdlGen.quoted 가 같이 쓴다
     */
    static boolean plainName(String name, String db) {
        return ("oracle".equals(db) || "mssql".equals(db) || "sybase".equals(db) ? PLAIN_HASH : PLAIN).matcher(name).matches();
    }

    /** 1-102 — 자동증가인가: 수집 표시, 또는 MSSQL 타입 이름의 identity 낱말(V016 전 스냅샷은 표시가 없다) */
    static boolean auto(Column c) {
        return c.autoIncrement() || c.nativeType() != null && IDENTITY_WORD.matcher(c.nativeType()).find();
    }

    /** 타입 이름의 identity 낱말을 뗀 컬럼 — 절은 autoHead 가 낸다(V-65·1-102) */
    private static Column plain(Column c) {
        if (c.nativeType() == null || !IDENTITY_WORD.matcher(c.nativeType()).find()) {
            return c;
        }
        return new Column(c.name(), c.ordinal(), IDENTITY_WORD.matcher(c.nativeType()).replaceFirst(""), c.jdbcType(), c.length(), c.precision(),
                c.scale(), c.nullable(), c.defaultValue(), c.comment(), c.domain(), true);
    }

    static final class Gen {
        final Spec sp;
        final Options o;
        final String target;
        final String source;
        final TypeMapping types;
        final String prefix;
        final Set<String> warnings = new LinkedHashSet<>();
        final StringBuilder sb = new StringBuilder();
        /** 이번 실행에서 낸 인덱스 이름(대문자) — 스키마 단위 이름 공간(1-90) */
        final Set<String> ixNames = new HashSet<>();
        /** 1-134 — 이번 실행의 표(대문자 이름) — FK 자식이 부모 키를 찾는다 */
        Map<String, Table> byTable = Map.of();
        /** 1-118(A1-13) — 스키마가 스냅샷에서 왔다(정확 이름 — 대소문자 섞이면 감싼다). 사용자가 준 스키마면 거짓 */
        boolean exactSchema;
        /** 1-103 — CREATE TABLE 의 컬럼을 내는 중인가. 그 안에는 PK 만 있다(유니크·인덱스는 뒤 문장) */
        private boolean inCreate;

        Gen(Spec sp, Options o, String target, String source, TypeMapping types) {
            this.sp = sp;
            this.o = o;
            this.target = target;
            this.source = source;
            this.types = types;
            this.prefix = o.prefix() == null ? "" : o.prefix();
        }

        boolean mariadb() {
            return "mariadb".equals(target);
        }

        boolean oracleLike() {
            return "oracle".equals(target) || "tibero".equals(target);
        }

        Result run(List<Table> input) {
            List<Table> tables = new ArrayList<>();
            for (Table t : input) {
                if (t.type() != null && t.type().toUpperCase(Locale.ROOT).contains("VIEW")) {
                    warnings.add(t.name() + ": 뷰 — 건너뜀");
                    continue;
                }
                tables.add(t);
            }
            Map<String, Table> byName = new LinkedHashMap<>();
            tables.forEach(t -> byName.put(t.name().toUpperCase(Locale.ROOT), t));
            byTable = byName;
            sb.append("-- DDL 생성 — 원본 ").append(source.isEmpty() ? "모름" : source).append(" → ").append(target)
                    .append(", 표 ").append(tables.size()).append('\n');
            for (Table t : tables) {
                sb.append('\n');
                table(t);
            }
            if (o.includeFk()) {
                StringBuilder fks = new StringBuilder();
                for (Table t : tables) {
                    int i = 0;
                    for (ForeignKey fk : t.fks()) {
                        i++;
                        fk(fks, t, fk, i, byName);
                    }
                }
                if (fks.length() > 0) {
                    sb.append("\n-- 외래 키\n").append(fks);
                }
            }
            if (o.includeComments() && !mariadb()) {
                StringBuilder cm = new StringBuilder();
                for (Table t : tables) {
                    comments(cm, t);
                }
                if (cm.length() > 0) {
                    sb.append("\n-- 코멘트\n").append(cm);
                }
            }
            return new Result(sb.toString(), new ArrayList<>(warnings), tables.size());
        }

        void table(Table t) {
            Set<String> pkCols = new HashSet<>();
            if (t.pk() != null) {
                t.pk().columns().forEach(c -> pkCols.add(c.toUpperCase(Locale.ROOT)));
            }
            sb.append("CREATE TABLE ").append(tbl(t.name())).append(" (\n");
            List<String> items = new ArrayList<>();
            List<Column> cols = new ArrayList<>(t.columns());
            cols.sort(Comparator.comparingInt(Column::ordinal));
            inCreate = true;
            try {
                for (Column c : cols) {
                    items.add("    " + column(t, c, pkCols.contains(c.name().toUpperCase(Locale.ROOT))));
                }
            } finally {
                inCreate = false;
            }
            if (!pkCols.isEmpty()) {
                items.add("    CONSTRAINT " + cname(nameOr(t.pk().name(), "PK_" + t.name())) + " PRIMARY KEY (" + idList(t.pk().columns()) + ")");
            } else {
                warnings.add(t.name() + ": PK 없음");
            }
            sb.append(String.join(",\n", items)).append("\n)");
            if (mariadb() && o.includeComments() && !blank(t.comment())) {
                sb.append(" COMMENT = ").append(lit(t.comment(), true));
            }
            sb.append(";\n");
            List<List<String>> keyCols = new ArrayList<>();
            if (t.pk() != null) {
                keyCols.add(upper(t.pk().columns()));
            }
            int u = 0;
            for (UniqueKey k : t.uniques()) {
                u++;
                if (k.columns().isEmpty() || keyCols.contains(upper(k.columns()))) {
                    continue; // PK 와 같은 컬럼의 유니크(H2 메타가 PK 를 유니크로도 낸다) — Oracle 은 거절(ORA-02261)
                }
                keyCols.add(upper(k.columns()));
                sb.append("ALTER TABLE ").append(tbl(t.name())).append(" ADD CONSTRAINT ").append(cname(ukName(this, t, k, u)))
                        .append(" UNIQUE (").append(idList(k.columns())).append(");\n");
            }
            checks(t);
            if (o.includeIndex()) {
                int x = 0;
                for (Index ix : t.indexes()) {
                    x++;
                    if (ix.columns().isEmpty() || keyCols.contains(upper(ix.columns()))) {
                        continue; // PK·유니크 키·앞 인덱스가 이미 만든다(Oracle 은 같은 컬럼 목록 인덱스 둘을 거절 — ORA-01408)
                    }
                    keyCols.add(upper(ix.columns()));
                    sb.append(ix.unique() ? "CREATE UNIQUE INDEX " : "CREATE INDEX ").append(cname(ixName(t, ix, x)))
                            .append(" ON ").append(tbl(t.name())).append(" (").append(ixList(ix)).append(");\n");
                }
            }
        }

        String column(Table t, Column c, boolean inPk) {
            String type = type(t, c);
            StringBuilder d = new StringBuilder(id(c.name())).append(' ').append(type).append(autoHead(t, c, type));
            String def = auto(c) ? null : defaultOf(t, c, type);
            if (def != null) {
                d.append(" DEFAULT ").append(def);
            }
            if (!c.nullable() || inPk) {
                d.append(" NOT NULL");
            }
            d.append(autoTail(t, c));
            if (mariadb() && o.includeComments() && !blank(c.comment())) {
                d.append(" COMMENT ").append(lit(c.comment(), true));
            }
            return d.toString();
        }

        String type(Table t, Column raw) {
            Column c = plain(raw);
            String n = TypeMapping.norm(c.nativeType());
            if (!source.isEmpty() && source.equals(target) && !n.isEmpty()) {
                return nativeWithSize(c);
            }
            String j = types.javaType(c, source.isEmpty() ? null : source);
            if (j == null) {
                warnings.add(t.name() + "." + c.name() + ": 타입 확인 — 원본 타입 " + c.nativeType() + " 그대로");
                return blank(c.nativeType()) ? "VARCHAR(255)" : nativeWithSize(c);
            }
            Long len = keyLen(t, c, n);
            String key = typeKey(this, t, c, j, n, len);
            Map<String, String> forms = sp.types().get(key);
            if (forms == null || forms.get(target) == null) {
                warnings.add(t.name() + "." + c.name() + ": 타입 확인 — 자바 " + j + " 의 대상 꼴이 없다, 원본 " + c.nativeType() + " 그대로");
                return blank(c.nativeType()) ? "VARCHAR(255)" : nativeWithSize(c);
            }
            int p = c.precision() == null || c.precision() <= 0 ? 10 : c.precision();
            int max = sp.maxPrecision().get(target);
            if (p > max) {
                warnings.add(t.name() + "." + c.name() + ": 정밀도 " + p + " → " + max);
                p = max;
            }
            int s = c.scale() == null ? 0 : Math.max(0, Math.min(c.scale(), p));
            if ("java.time.LocalTime".equals(j) && oracleLike()) {
                warnings.add(t.name() + "." + c.name() + ": 시각만 담는 타입이 없어 VARCHAR2(8) 로");
            }
            return forms.get(target).replace("{n}", String.valueOf(len)).replace("{p}", String.valueOf(p)).replace("{s}", String.valueOf(s));
        }

        String nativeWithSize(Column c) {
            String raw = c.nativeType().trim().replaceAll("\\(\\s*\\)", ""); // 1-121 A1-17 — numeric() 빈 괄호
            if (raw.contains("(")) {
                return raw;
            }
            String n = TypeMapping.norm(raw);
            if (SIZED_CHAR.contains(n) && c.length() != null && c.length() > 0) {
                return sized(raw, c.length());
            }
            if (SIZED_NUM.contains(n) && c.precision() != null && c.precision() > 0) {
                return raw + "(" + c.precision() + (c.scale() != null && c.scale() > 0 ? "," + c.scale() : "") + ")";
            }
            return raw;
        }

        /** R-A-I1 — 길이 상한을 넘으면 mssql (MAX), 그 밖은 크기 없이 */
        private String sized(String raw, long len) {
            if (len <= (MSSQL.equals(target) ? 8000 : NO_SIZE)) {
                return raw + "(" + len + ")";
            }
            return MSSQL.equals(target) ? raw + "(MAX)" : raw;
        }

        /** R-A-I13 — 대상 타입이 BOOLEAN 이면 0·1 을 FALSE·TRUE 로 */
        private static String bool(String v, String type) {
            if (!"BOOLEAN".equalsIgnoreCase(type)) {
                return v;
            }
            return "1".equals(v) ? "TRUE" : "0".equals(v) ? "FALSE" : v;
        }

        /** A-16 — 길이 없는 문자 타입이 키 컬럼이면 CLOB 대신 길이를 준다 */
        private Long keyLen(Table t, Column c, String norm) {
            Long len = c.length();
            boolean given = len != null && len > 0;
            if (given && len <= sp.limits().get(target) || !STRING_TYPE.equals(types.javaType(c, source.isEmpty() ? null : source)) || !(inKey(t, c) || fkColumn(t, c))
                    || given && POSTGRESQL.equals(target)) { // 1-106 — postgresql 은 TEXT 키가 합법
                return len;
            }
            long n = fkLen(this, t, c, "UUID".equals(norm) || "UNIQUEIDENTIFIER".equals(norm) ? 36 : 255);
            warnings.add(t.name() + "." + c.name() + (given ? ": 길이 " + len + " 문자 타입이 키 컬럼 — 대상 상한을 넘어 길이 " + n + " 으로 냈다"
                    : ": 길이 없는 문자 타입이 키 컬럼 — 길이 " + n + " 으로 냈다"));
            return n;
        }

        /** A-09 — 스키마 단위 이름 공간(oracle·tibero·postgresql)에서 겹치면 표 이름을 넣은 이름으로 */
        private String ixName(Table t, Index ix, int x) {
            String fallback = "IX_" + t.name() + "_" + x;
            String name = nameOr(ix.name(), fallback);
            if (mariadb() || MSSQL.equals(target) || ixNames.add(name.toUpperCase(Locale.ROOT))) {
                return name;
            }
            warnings.add(t.name() + ": 인덱스 이름 " + name + " 가 다른 표와 겹쳐 " + fallback + " 로 바꿨다");
            ixNames.add(fallback.toUpperCase(Locale.ROOT));
            return fallback;
        }

        String defaultOf(Table t, Column c, String type) {
            String v = c.defaultValue();
            if (blank(v)) {
                return null;
            }
            v = bareDefault(v);
            if (nullDefault(v)) { // 1-120 A1-18 — NULL::타입 도 기본값 없음(lostDefault 와 같은 판정)
                return null;
            }
            Matcher cast = PG_CAST.matcher(v);
            if (cast.matches()) {
                v = cast.group(1);
            }
            if (NUM.matcher(v).matches()) {
                return bool(v, type);
            }
            Matcher str = STR.matcher(v);
            if (str.matches()) {
                return strDefault(this, str.group(1));
            }
            if ("TRUE".equalsIgnoreCase(v) || "FALSE".equalsIgnoreCase(v)) {
                boolean b = "TRUE".equalsIgnoreCase(v);
                return POSTGRESQL.equals(target) ? (b ? "TRUE" : "FALSE") : (b ? "1" : "0");
            }
            String fn = v.replaceAll("\\(\\s*\\)$", "").trim().toUpperCase(Locale.ROOT);
            if (sp.now().contains(fn)) {
                if (mariadb() && "DATE".equalsIgnoreCase(type)) {
                    return "(CURRENT_DATE)";
                }
                return sp.nowTarget().get(target);
            }
            warnings.add(t.name() + "." + c.name() + ": 기본값 식은 옮기지 않았다 — " + (v.length() > 60 ? v.substring(0, 60) : v));
            return null;
        }

        void fk(StringBuilder out, Table t, ForeignKey fk, int i, Map<String, Table> byName) {
            Table ref = byName.get(fk.refTable() == null ? "" : fk.refTable().toUpperCase(Locale.ROOT));
            String name = nameOr(fk.name(), "FK_" + t.name() + "_" + i);
            if (ref == null) {
                warnings.add(t.name() + ": FK " + name + " — 참조 표 " + fk.refTable() + " 가 대상에 없어 건너뜀");
                out.append("-- [건너뜀] ").append(name).append(" → ").append(fk.refTable()).append(" (대상에 없는 표)\n");
                return;
            }
            List<String> refCols = fk.refColumns();
            if (refCols.isEmpty() && ref.pk() != null) {
                refCols = ref.pk().columns();
            }
            out.append("ALTER TABLE ").append(tbl(t.name())).append(" ADD CONSTRAINT ").append(cname(name)).append(" FOREIGN KEY (")
                    .append(idList(fk.columns())).append(") REFERENCES ").append(tbl(ref.name())).append(" (").append(idList(refCols)).append(')')
                    .append(rules(t, fk, name)).append(";\n");
        }

        /** FK 삭제·갱신 규칙 절 — AlterGen 과 같이 쓴다(1-89). 기본 동작은 안 쓴다 */
        String rules(Table t, ForeignKey fk, String name) {
            return rule("DELETE", fk.deleteRule(), t, name) + rule("UPDATE", fk.updateRule(), t, name);
        }

        private String rule(String what, FkRule r, Table t, String name) {
            if (r == null || r == FkRule.NO_ACTION || r == FkRule.RESTRICT) {
                return "";
            }
            if (oracleLike() && ("UPDATE".equals(what) || r == FkRule.SET_DEFAULT)) {
                warnings.add(t.name() + ": FK " + name + " — " + target + " 는 ON " + what + " " + r.label() + " 를 못 쓴다, 뺐다");
                return "";
            }
            return " ON " + what + " " + r.label();
        }

        /** CHECK 한 줄 — 같은 방언이면 문장, 다르면 null(1-89·1-91 이 같이 쓴다) */
        String checkSql(Table t, Check k, int n) {
            if (source.isEmpty() || !source.equals(target)) {
                return null;
            }
            return "ALTER TABLE " + tbl(t.name()) + " ADD CONSTRAINT " + cname(nameOr(k.name(), "CK_" + t.name() + "_" + n)) + " CHECK ("
                    + k.condition().strip() + ")";
        }

        /** 1-89 — 같은 방언이면 CHECK 문장, 다르면 주석 + 표마다 경고 한 줄 */
        void checks(Table t) {
            int n = 0;
            int left = 0;
            for (Check k : t.checks()) {
                n++;
                if (blank(k.condition())) {
                    continue;
                }
                String sql = checkSql(t, k, n);
                if (sql != null) {
                    sb.append(sql).append(";\n");
                    continue;
                }
                left++;
                String c = k.condition().strip().replaceAll("\\s+", " ");
                sb.append("-- [확인] ").append(t.name()).append(" CHECK (").append(c.length() > 80 ? c.substring(0, 80) : c)
                        .append(") — 방언이 달라 옮기지 않았다\n");
            }
            if (left > 0) {
                warnings.add(t.name() + ": CHECK " + left + "개는 방언이 달라 옮기지 않았다 — 주석으로 남겼다");
            }
        }

        /** 키 컬럼인가 — PK·유니크·인덱스(1-89·1-90) */
        boolean inKey(Table t, Column c) {
            List<List<String>> keys = new ArrayList<>();
            if (t.pk() != null) {
                keys.add(t.pk().columns());
            }
            t.uniques().forEach(k -> keys.add(k.columns()));
            t.indexes().forEach(ix -> keys.add(ix.columns()));
            return keys.stream().flatMap(List::stream).anyMatch(n -> n.equalsIgnoreCase(c.name()));
        }

        /** 타입 뒤 자동증가 절 */
        String autoHead(Table t, Column c, String type) {
            if (!auto(c)) {
                return "";
            }
            if (MSSQL.equals(target)) {
                return " IDENTITY(1,1)";
            }
            if (POSTGRESQL.equals(target)) {
                return type.toUpperCase(Locale.ROOT).endsWith("SERIAL") ? "" : " GENERATED BY DEFAULT AS IDENTITY";
            }
            if (oracleLike()) {
                warnings.add(t.name() + "." + c.name() + ": 자동증가 — " + target + " 는 시퀀스·트리거(12c+ 는 IDENTITY)를 손으로");
            }
            return "";
        }

        /** NOT NULL 뒤 자동증가 절 — mariadb 는 키 컬럼만 */
        String autoTail(Table t, Column c) {
            if (!auto(c) || !mariadb()) {
                return "";
            }
            boolean pk = t.pk() != null && t.pk().columns().stream().anyMatch(n -> n.equalsIgnoreCase(c.name()));
            if (inCreate ? pk : inKey(t, c)) {
                return " AUTO_INCREMENT";
            }
            warnings.add(t.name() + "." + c.name() + (inKey(t, c) ? ": 자동증가 — PK 가 아닌 키라 CREATE TABLE 안에 키가 없다, 뺐다(키를 만든 뒤 ALTER 로)"
                    : ": 자동증가 — 키 컬럼이 아니라 mariadb 가 못 받는다, 뺐다"));
            return "";
        }

        void comments(StringBuilder out, Table t) {
            List<Column> cols = new ArrayList<>(t.columns());
            cols.sort(Comparator.comparingInt(Column::ordinal));
            if (MSSQL.equals(target)) {
                String schema = blank(o.schema()) ? "dbo" : o.schema();
                String head = "EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N";
                String lv = ", @level0type=N'SCHEMA', @level0name=N" + lit(schema) + ", @level1type=N'TABLE', @level1name=N" + lit(prefix + t.name());
                if (!blank(t.comment())) {
                    out.append(head).append(lit(t.comment())).append(lv).append(";\n");
                }
                for (Column c : cols) {
                    if (!blank(c.comment())) {
                        out.append(head).append(lit(c.comment())).append(lv).append(", @level2type=N'COLUMN', @level2name=N").append(lit(c.name()))
                                .append(";\n");
                    }
                }
                return;
            }
            if (!blank(t.comment())) {
                out.append("COMMENT ON TABLE ").append(tbl(t.name())).append(" IS ").append(lit(t.comment())).append(";\n");
            }
            for (Column c : cols) {
                if (!blank(c.comment())) {
                    out.append("COMMENT ON COLUMN ").append(tbl(t.name())).append('.').append(id(c.name())).append(" IS ").append(lit(c.comment()))
                            .append(";\n");
                }
            }
        }

        String tbl(String name) {
            return (blank(o.schema()) ? "" : quoted(o.schema(), exactSchema && exact(o.schema())) + ".") + named(name, exact(name)); // 1-118 — DROP INDEX 와 같은 꼴
        }

        /** 제약·인덱스 이름 — prefix 를 붙인다. 우리가 지은 이름이 섞여 정확 이름으로 안 본다 */
        String cname(String name) {
            return named(name, false);
        }

        /** prefix 를 붙인 표·제약·인덱스 이름. exact 는 prefix 앞 글자로 잰 값 */
        private String named(String name, boolean exact) {
            String n = prefix + name;
            if (oracleLike() && n.length() > 30) {
                warnings.add(n + ": 30자 넘음 — Oracle 12.1 이하는 못 만든다");
            }
            return quoted(n, exact);
        }

        /** 컬럼 이름 — 원본이 준 글자로 정확 이름을 잰다 */
        String id(String name) {
            return quoted(name, exact(name));
        }

        String quoted(String name, boolean exactName) {
            boolean plain = plainName(name, InsertGen.db(target));
            boolean exact = plain && exactName;
            if (plain && !exact && !sp.reserved().contains(name.toUpperCase(Locale.ROOT))) {
                return name;
            }
            warnings.add(name + (exact ? ": 대소문자 섞인 이름 — 따옴표로 감쌌다" : ": 예약어·특수 글자 — 따옴표로 감쌌다"));
            String n = plain && !exact ? fold(name) : name;
            return switch (target) {
                case "mariadb" -> "`" + n.replace("`", "``") + "`";
                case MSSQL -> "[" + n.replace("]", "]]") + "]";
                default -> "\"" + n.replace("\"", "\"\"") + "\"";
            };
        }

        /** 설계 71 §3-2 — 원본 방언이 접는 글자와 다르면 원본에서 따옴표로 만든 이름 */
        boolean exact(String name) {
            if (source == null) {
                return false;
            }
            return switch (source) {
                case "oracle", "tibero" -> !name.equals(name.toUpperCase(Locale.ROOT));
                case POSTGRESQL -> !name.equals(name.toLowerCase(Locale.ROOT));
                default -> false;
            };
        }

        /** 예약어를 감쌀 때 대상이 접는 글자로 — 감싸도 안 감싼 것과 같은 이름 */
        private String fold(String name) {
            if (oracleLike()) {
                return name.toUpperCase(Locale.ROOT);
            }
            return POSTGRESQL.equals(target) ? name.toLowerCase(Locale.ROOT) : name;
        }

        String idList(List<String> names) {
            List<String> out = new ArrayList<>();
            names.forEach(n -> out.add(id(n)));
            return String.join(", ", out);
        }

        /** 인덱스 컬럼 목록 — DESC 만 붙인다(1-64, 1-61a 와 같은 규칙. ASC·모름은 기본값이라 안 쓴다) */
        String ixList(Index ix) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < ix.columns().size(); i++) {
                out.add(id(ix.columns().get(i)) + (ix.sortAt(i) == SortOrder.DESC ? " DESC" : ""));
            }
            return String.join(", ", out);
        }
    }

    static List<String> upper(List<String> l) {
        return l.stream().map(x -> x.toUpperCase(Locale.ROOT)).toList();
    }

    static String lit(String v) {
        return lit(v, false);
    }

    /** A-28 — mariadb 기본 sql_mode 는 \ 가 탈출 글자라 겹친다(NO_BACKSLASH_ESCAPES 는 안 가린다) */
    static String lit(String v, boolean backslash) {
        String s = backslash ? v.replace("\\", "\\\\") : v;
        return "'" + s.replace("'", "''") + "'";
    }

    /** 기본값 글의 겉 — 앞뒤 공백과 통째로 싼 괄호(SQL Server 「((0))」·「(getdate())」)를 벗긴다 */
    static String bareDefault(String v) {
        String s = v.trim();
        while (s.startsWith("(") && s.endsWith(")") && closes(s)) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /** 기본값이 있는데(빈 값·NULL·PG NULL::타입 아님) 대상 글로 못 옮겼나 — moved 는 defaultOf 가 준 값(R-A-M2 · 마무리 리뷰 I1) */
    static boolean lostDefault(Column c, String moved) {
        return moved == null && !noDefault(c.defaultValue());
    }

    /** 1-135(A4-11) — 기본값이 없다: 빈 글 · NULL · NULL::타입(MariaDB 스냅샷은 기본값 없는 nullable 컬럼에 NULL 글을 싣는다) */
    static boolean noDefault(String v) {
        return blank(v) || nullDefault(bareDefault(v));
    }

    /** 1-120 — NULL 이거나 NULL::타입(대소문자 무시) — defaultOf·lostDefault 가 같이 쓴다 */
    private static boolean nullDefault(String bare) {
        String v = bare.toUpperCase(Locale.ROOT);
        return NULL_WORD.equals(v) || v.startsWith(NULL_WORD + "::");
    }

    /** 1-120(A1-08) — 다른 방언의 문자열 기본값을 mariadb 대상으로: 역슬래시를 겹친다(MariaDB 는 \t·\n 을 탈출로 읽는다) */
    private static String strDefault(Gen g, String lit) {
        boolean other = !blank(g.source) && !g.source.startsWith("maria") && !g.source.startsWith("mysql");
        return g.mariadb() && other ? lit.replace("\\", "\\\\") : lit;
    }

    /** v 가 「(」 로 시작해 끝의 「)」 가 그 짝인가 */
    private static boolean closes(String v) {
        int depth = 0;
        boolean q = false;
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch == '\'') {
                q = !q;
            } else if (!q && ch == '(') {
                depth++;
            } else if (!q && ch == ')') {
                depth--;
                if (depth == 0 && i < v.length() - 1) {
                    return false;
                }
            }
        }
        return depth == 0;
    }
}
