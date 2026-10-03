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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
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
            Set<String> now, Map<String, String> nowTarget, Set<String> reserved) {
    }

    private static final Pattern PLAIN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final Pattern NUM = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");
    private static final Pattern STR = Pattern.compile("^N?('(?:[^']|'')*')$");
    /** PostgreSQL 기본값 꼴 「'Y'::character varying」·「0::numeric」 */
    private static final Pattern PG_CAST = Pattern.compile("^('(?:[^']|'')*'|-?\\d+(?:\\.\\d+)?)::[A-Za-z][A-Za-z0-9_ ]*(?:\\[\\])?$");
    private static final Set<String> FIXED = Set.of("CHAR", "NCHAR", "CHARACTER", "BPCHAR");
    private static final Set<String> SIZED_CHAR = Set.of("VARCHAR", "VARCHAR2", "NVARCHAR", "NVARCHAR2", "CHARACTER VARYING", "CHAR", "NCHAR",
            "CHARACTER", "BPCHAR", "VARBINARY", "BINARY", "RAW");
    private static final Set<String> SIZED_NUM = Set.of("NUMBER", "NUMERIC", "DECIMAL", "DEC");

    private static volatile Spec spec;

    private DdlGen() {
    }

    /** 대상 방언 다섯(oracle·tibero·postgresql·mariadb·mssql) */
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
            List<String> targets = ((List<Object>) doc.get("targets")).stream().map(String::valueOf).toList();
            s = new Spec(targets, Map.copyOf(limits), Map.copyOf(maxP), Map.copyOf(types), Set.copyOf(now), Map.copyOf(nowTarget),
                    Set.copyOf(reserved));
            spec = s;
            return s;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Result generate(List<Table> tables, Options o, TypeMapping types) {
        Spec sp = spec();
        String target = lower(o.target());
        if (!sp.targets().contains(target)) {
            throw new IllegalArgumentException("DDL 대상 방언: " + o.target() + " — " + String.join("·", sp.targets()));
        }
        return new Gen(sp, o, target, lower(o.source()), types).run(tables);
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** 원본 이름이 DB 가 지은 이름(SYS_C…·CONSTRAINT_…·PK__…)이거나 비면 fallback */
    static String nameOr(String raw, String fallback) {
        if (blank(raw)) {
            return fallback;
        }
        String u = raw.toUpperCase(Locale.ROOT);
        if (u.startsWith("SYS_") || u.startsWith("CONSTRAINT_") || u.startsWith("PRIMARY_KEY") || u.equals("PRIMARY") || u.contains("__")) {
            return fallback;
        }
        return raw;
    }

    private static final class Gen {
        final Spec sp;
        final Options o;
        final String target;
        final String source;
        final TypeMapping types;
        final String prefix;
        final Set<String> warnings = new LinkedHashSet<>();
        final StringBuilder sb = new StringBuilder();

        Gen(Spec sp, Options o, String target, String source, TypeMapping types) {
            this.sp = sp;
            this.o = o;
            this.target = target;
            this.source = source;
            this.types = types;
            this.prefix = o.prefix() == null ? "" : o.prefix();
        }

        boolean mariadb() {
            return target.equals("mariadb");
        }

        boolean oracleLike() {
            return target.equals("oracle") || target.equals("tibero");
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
            for (Column c : cols) {
                items.add("    " + column(t, c, pkCols.contains(c.name().toUpperCase(Locale.ROOT))));
            }
            if (!pkCols.isEmpty()) {
                items.add("    CONSTRAINT " + cname(nameOr(t.pk().name(), "PK_" + t.name())) + " PRIMARY KEY (" + idList(t.pk().columns()) + ")");
            } else {
                warnings.add(t.name() + ": PK 없음");
            }
            sb.append(String.join(",\n", items)).append("\n)");
            if (mariadb() && o.includeComments() && !blank(t.comment())) {
                sb.append(" COMMENT = ").append(lit(t.comment()));
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
                sb.append("ALTER TABLE ").append(tbl(t.name())).append(" ADD CONSTRAINT ").append(cname(nameOr(k.name(), "UK_" + t.name() + "_" + u)))
                        .append(" UNIQUE (").append(idList(k.columns())).append(");\n");
            }
            if (o.includeIndex()) {
                int x = 0;
                for (Index ix : t.indexes()) {
                    x++;
                    if (ix.columns().isEmpty() || keyCols.contains(upper(ix.columns()))) {
                        continue; // PK·유니크 키·앞 인덱스가 이미 만든다(Oracle 은 같은 컬럼 목록 인덱스 둘을 거절 — ORA-01408)
                    }
                    keyCols.add(upper(ix.columns()));
                    sb.append(ix.unique() ? "CREATE UNIQUE INDEX " : "CREATE INDEX ").append(cname(nameOr(ix.name(), "IX_" + t.name() + "_" + x)))
                            .append(" ON ").append(tbl(t.name())).append(" (").append(idList(ix.columns())).append(");\n");
                }
            }
        }

        String column(Table t, Column c, boolean inPk) {
            String type = type(t, c);
            StringBuilder d = new StringBuilder(id(c.name())).append(' ').append(type);
            String def = defaultOf(t, c, type);
            if (def != null) {
                d.append(" DEFAULT ").append(def);
            }
            if (!c.nullable() || inPk) {
                d.append(" NOT NULL");
            }
            if (mariadb() && o.includeComments() && !blank(c.comment())) {
                d.append(" COMMENT ").append(lit(c.comment()));
            }
            return d.toString();
        }

        String type(Table t, Column c) {
            String n = TypeMapping.norm(c.nativeType());
            if (!source.isEmpty() && source.equals(target) && !n.isEmpty()) {
                return nativeWithSize(c);
            }
            String j = types.javaType(c, source.isEmpty() ? null : source);
            if (j == null) {
                warnings.add(t.name() + "." + c.name() + ": 타입 확인 — 원본 타입 " + c.nativeType() + " 그대로");
                return blank(c.nativeType()) ? "VARCHAR(255)" : nativeWithSize(c);
            }
            String key = j;
            Long len = c.length();
            if (j.equals("String")) {
                if (len == null || len <= 0 || len > sp.limits().get(target)) {
                    key = "String.long";
                } else if (FIXED.contains(n)) {
                    key = "String.fixed";
                }
            } else if (j.equals("java.math.BigDecimal") && (c.precision() == null || c.precision() <= 0)) {
                key = "java.math.BigDecimal.free";
            }
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
            if (j.equals("java.time.LocalTime") && oracleLike()) {
                warnings.add(t.name() + "." + c.name() + ": 시각만 담는 타입이 없어 VARCHAR2(8) 로");
            }
            return forms.get(target).replace("{n}", String.valueOf(len)).replace("{p}", String.valueOf(p)).replace("{s}", String.valueOf(s));
        }

        String nativeWithSize(Column c) {
            String raw = c.nativeType().trim();
            if (raw.contains("(")) {
                return raw;
            }
            String n = TypeMapping.norm(raw);
            if (SIZED_CHAR.contains(n) && c.length() != null && c.length() > 0) {
                return raw + "(" + c.length() + ")";
            }
            if (SIZED_NUM.contains(n) && c.precision() != null && c.precision() > 0) {
                return raw + "(" + c.precision() + (c.scale() != null && c.scale() > 0 ? "," + c.scale() : "") + ")";
            }
            return raw;
        }

        String defaultOf(Table t, Column c, String type) {
            String v = c.defaultValue();
            if (blank(v)) {
                return null;
            }
            v = v.trim();
            while (v.startsWith("(") && v.endsWith(")") && closes(v)) {
                v = v.substring(1, v.length() - 1).trim(); // SQL Server 「((0))」·「(getdate())」
            }
            if (v.equalsIgnoreCase("NULL")) {
                return null;
            }
            Matcher cast = PG_CAST.matcher(v);
            if (cast.matches()) {
                v = cast.group(1);
            }
            if (NUM.matcher(v).matches()) {
                return v;
            }
            Matcher str = STR.matcher(v);
            if (str.matches()) {
                return str.group(1);
            }
            if (v.equalsIgnoreCase("TRUE") || v.equalsIgnoreCase("FALSE")) {
                boolean b = v.equalsIgnoreCase("TRUE");
                return target.equals("postgresql") ? (b ? "TRUE" : "FALSE") : (b ? "1" : "0");
            }
            String fn = v.replaceAll("\\(\\s*\\)$", "").trim().toUpperCase(Locale.ROOT);
            if (sp.now().contains(fn)) {
                if (mariadb() && type.equalsIgnoreCase("DATE")) {
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
                    .append(idList(fk.columns())).append(") REFERENCES ").append(tbl(ref.name())).append(" (").append(idList(refCols)).append(");\n");
        }

        void comments(StringBuilder out, Table t) {
            List<Column> cols = new ArrayList<>(t.columns());
            cols.sort(Comparator.comparingInt(Column::ordinal));
            if (target.equals("mssql")) {
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
            return (blank(o.schema()) ? "" : id(o.schema()) + ".") + cname(name);
        }

        /** 표·제약·인덱스 이름 — prefix 를 붙인다 */
        String cname(String name) {
            String n = prefix + name;
            if (oracleLike() && n.length() > 30) {
                warnings.add(n + ": 30자 넘음 — Oracle 12.1 이하는 못 만든다");
            }
            return id(n);
        }

        String id(String name) {
            if (PLAIN.matcher(name).matches() && !sp.reserved().contains(name.toUpperCase(Locale.ROOT))) {
                return name;
            }
            warnings.add(name + ": 예약어·특수 글자 — 따옴표로 감쌌다");
            return switch (target) {
                case "mariadb" -> "`" + name.replace("`", "``") + "`";
                case "mssql" -> "[" + name.replace("]", "]]") + "]";
                default -> "\"" + name.replace("\"", "\"\"") + "\"";
            };
        }

        String idList(List<String> names) {
            List<String> out = new ArrayList<>();
            names.forEach(n -> out.add(id(n)));
            return String.join(", ", out);
        }
    }

    private static List<String> upper(List<String> l) {
        return l.stream().map(x -> x.toUpperCase(Locale.ROOT)).toList();
    }

    private static String lit(String v) {
        return "'" + v.replace("'", "''") + "'";
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
