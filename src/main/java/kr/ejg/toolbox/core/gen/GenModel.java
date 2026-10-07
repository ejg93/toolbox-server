package kr.ejg.toolbox.core.gen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Table;

/**
 * 스냅샷 표 하나 → 생성기 템플릿 모델(7-2). 이름은 1-9 와 같은 규칙({@link DtoGenerator#className}·{@link DtoGenerator#fieldName}),
 * 타입은 {@link TypeMapping}. 모델은 Map·List·문자열·참거짓·수만 — 템플릿 엔진이 자바 빈을 안 받는다(7-1).
 * PK 없는 표·뷰는 모델을 안 만들고 경고(목록 전용 꼴은 안 만든다).
 */
public final class GenModel {

    /**
     * 생성기가 아는 방언 — 매퍼 페이징·검색 식이 이 다섯만 안다(7-10). DDL 생성(7-6)의 대상도 이 목록 하나다(8-0) —
     * 방언을 더하면 {@code gen/ddl-types.yaml} 에 그 방언 줄이 없을 때 {@code DdlGen} 이 첫 호출에서 멈춘다.
     */
    public static final List<String> DIALECTS = List.of("oracle", "tibero", "postgresql", "mariadb", "mssql");

    /** module 이 비면 표 이름 소문자. dialect 는 {@link #DIALECTS} 하나(비면 oracle) */
    public record Options(String basePackage, String module, List<String> skipTokens, Map<String, String> logicalNames, String dialect) {

        public Options {
            skipTokens = skipTokens == null ? List.of() : List.copyOf(skipTokens);
            logicalNames = logicalNames == null ? Map.of() : Map.copyOf(logicalNames);
        }
    }

    /** model 이 비면 건너뛴 표(warnings 에 사유) */
    public record Result(Map<String, Object> model, List<String> warnings) {

        public Result {
            warnings = List.copyOf(warnings);
            model = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(model == null ? Map.of() : model));
        }
    }

    private GenModel() {
    }

    /** @param vars 템플릿 세트 변수({@code set.yaml} 의 vars) — 모델 {@code vars} 로 그대로 */
    public static Result of(Table t, Options o, Map<String, String> vars, TypeMapping types) {
        List<String> warnings = new ArrayList<>();
        if (t.type() != null && t.type().toUpperCase(Locale.ROOT).contains("VIEW")) {
            warnings.add(t.name() + ": 뷰 — 건너뜀");
            return new Result(Map.of(), warnings);
        }
        if (t.pk() == null || t.pk().columns().isEmpty()) {
            warnings.add(t.name() + ": PK 없음 — 건너뜀");
            return new Result(Map.of(), warnings);
        }
        Set<String> pkCols = new HashSet<>();
        t.pk().columns().forEach(c -> pkCols.add(c.toUpperCase(Locale.ROOT)));

        String cls = DtoGenerator.className(t.name(), o.skipTokens());
        String lower = Character.toLowerCase(cls.charAt(0)) + cls.substring(1);
        String module = o.module() == null || o.module().isBlank() ? cls.toLowerCase(Locale.ROOT) : o.module().trim();
        String pkg = o.basePackage().trim() + "." + module;
        String dialect = o.dialect() == null || o.dialect().isBlank() ? "oracle" : o.dialect().trim().toLowerCase(Locale.ROOT);
        if (!DIALECTS.contains(dialect)) {
            throw new IllegalArgumentException("생성기가 모르는 방언: " + o.dialect() + " — " + String.join("·", new TreeSet<>(DIALECTS)));
        }

        Set<String> imports = new TreeSet<>();
        // 1-31d — 세트 vars.valid 가 "true" 면 검증 어노테이션, 네임스페이스는 vars.ee(jakarta 면 jakarta)
        Validation.Ns ns = vars != null && "true".equals(vars.get("valid"))
                ? ("jakarta".equals(vars.get("ee")) ? Validation.Ns.JAKARTA : Validation.Ns.JAVAX) : null;
        Set<String> used = new HashSet<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        List<Map<String, Object>> pk = new ArrayList<>();
        List<Map<String, Object>> nonPk = new ArrayList<>();
        Map<String, Object> searchField = null;
        Map<String, Object> firstString = null;
        for (Column c : t.columns()) {
            String key = c.name().toUpperCase(Locale.ROOT);
            String full = types.javaType(c, dialect);
            if (full == null) {
                full = "Object";
                warnings.add(t.name() + "." + c.name() + ": 타입을 모른다(" + c.nativeType() + ") — Object");
            }
            int dot = full.lastIndexOf('.');
            if (dot > 0) {
                imports.add(full);
            }
            String simple = dot > 0 ? full.substring(dot + 1) : full;
            String name = DtoGenerator.fieldName(c.name());
            while (!used.add(name)) {
                name = name + "_";
            }
            String comment = c.comment() != null && !c.comment().isBlank() ? c.comment().trim()
                    : o.logicalNames().getOrDefault(key, c.name());
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("column", c.name());
            f.put("name", name);
            f.put("Name", Character.toUpperCase(name.charAt(0)) + name.substring(1));
            f.put("javaType", simple);
            f.put("string", simple.equals("String"));
            boolean isPk = pkCols.contains(key);
            f.put("pk", isPk);
            f.put("nullable", c.nullable());
            f.put("comment", comment);
            Validation.Result v = Validation.of(t, c, full, dialect, ns);
            imports.addAll(v.imports());
            f.put("annotations", v.annotations());
            f.put("doc", v.notes().isEmpty() ? comment : comment + " · " + String.join(" · ", v.notes()));
            f.put("length", c.length() == null ? 0 : (int) Math.min(Integer.MAX_VALUE, c.length()));
            fields.add(f);
            (isPk ? pk : nonPk).add(f);
            if (simple.equals("String")) {
                if (firstString == null) {
                    firstString = f;
                }
                if (!isPk && searchField == null) {
                    searchField = f;
                }
            }
        }
        if (pk.size() != pkCols.size()) {
            warnings.add(t.name() + ": PK 컬럼 중 표에 없는 것이 있다 — 건너뜀");
            return new Result(Map.of(), warnings);
        }
        if (searchField == null) {
            searchField = firstString;
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("table", t.name());
        m.put("schema", t.schema() == null ? "" : t.schema());
        m.put("comment", t.comment() != null && !t.comment().isBlank() ? t.comment().trim() : t.name());
        m.put("Name", cls);
        m.put("name", lower);
        m.put("module", module);
        m.put("packageName", pkg);
        m.put("packagePath", pkg.replace('.', '/'));
        m.put("modulePath", module.replace('.', '/'));
        m.put("urlBase", "/" + module.replace('.', '/'));
        m.put("viewBase", pkg.replace('.', '/'));
        m.put("dialect", dialect);
        m.put("dialectFile", switch (dialect) {
            case "postgresql" -> "postgres";
            case "mariadb" -> "maria";
            default -> dialect;
        });
        m.put("vars", vars == null ? Map.of() : new LinkedHashMap<>(vars));
        m.put("fields", fields);
        m.put("pk", pk);
        m.put("nonPk", nonPk);
        m.put("imports", new ArrayList<>(imports));
        if (searchField != null) {
            m.put("searchField", searchField);
        }
        return new Result(m, warnings);
    }
}
