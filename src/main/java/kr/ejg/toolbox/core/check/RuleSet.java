package kr.ejg.toolbox.core.check;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * 규칙 모음(5-1). 내장 {@code check/rules.yaml} → 프로필 {@code codecheck.customRules} 파일(같은 꼴, 같은 id 면 바꿈) →
 * {@code codecheck.rules} 덮어쓰기 → {@code codecheck.groups} 로 켜고 끔. 프로필에 없는 묶음은 켠다.
 * 규칙이 켜지는 조건은 「묶음 켬 ∧ 규칙 켬」.
 */
public final class RuleSet {

    public static final String BUILTIN = "/check/rules.yaml";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /** YAML 파일 꼴 */
    public record RulesFile(List<Rule.Def> rules) {
        public RulesFile {
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    private final List<Rule.Def> defs;
    private final List<Rule> enabled;

    private RuleSet(List<Rule.Def> defs, List<Rule> enabled) {
        this.defs = List.copyOf(defs);
        this.enabled = List.copyOf(enabled);
    }

    /** 내장 규칙만, 프로필 없이 — 전부 켬 */
    public static RuleSet builtin() {
        return load(null, null);
    }

    /**
     * @param base {@code customRules} 상대 경로의 기준(서버 작업 폴더). null 이면 customRules 를 안 읽는다
     */
    public static RuleSet load(Profile profile, Path base) {
        Map<String, Rule.Def> byId = new LinkedHashMap<>();
        try (InputStream in = RuleSet.class.getResourceAsStream(BUILTIN)) {
            if (in == null) {
                throw new IllegalStateException(BUILTIN + " 가 없다");
            }
            YAML.readValue(in, RulesFile.class).rules().forEach(d -> byId.put(d.id(), d));
        } catch (IOException e) {
            throw new IllegalStateException(BUILTIN + ": " + e.getMessage(), e);
        }
        Profile.CodeCheck cc = profile == null ? null : profile.codecheck();
        if (cc != null && cc.customRules() != null && !cc.customRules().isBlank() && base != null) {
            Path p = base.resolve(cc.customRules());
            if (!Files.isRegularFile(p)) {
                throw new IllegalArgumentException("codecheck.customRules 파일이 없다: " + cc.customRules());
            }
            try {
                YAML.readValue(Files.readString(p), RulesFile.class).rules().forEach(d -> byId.put(d.id(), d));
            } catch (IOException e) {
                throw new IllegalArgumentException("codecheck.customRules 를 못 읽었다: " + e.getMessage(), e);
            }
        }
        List<Rule.Def> defs = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        for (Rule.Def d0 : byId.values()) {
            Rule.Def d = cc != null && cc.rules().containsKey(d0.id()) ? d0.override(cc.rules().get(d0.id())) : d0;
            boolean groupOn = cc == null || !Boolean.FALSE.equals(cc.groups().get(d.group()));
            d = d.withEnabled(groupOn && d.on());
            validate(d);
            defs.add(d);
            if (d.on()) {
                rules.add(build(d, profile));
            }
        }
        return new RuleSet(defs, rules);
    }

    private static void validate(Rule.Def d) {
        if (d.id() == null || d.group() == null || !Rule.Def.SEVERITIES.contains(d.severity()) || d.globs().isEmpty()) {
            throw new IllegalArgumentException("규칙 정의가 모자란다(id·group·severity error|warn|info·globs): " + d.id());
        }
        if (d.kind().equals("regex")) {
            if (d.regex() == null || d.regex().isBlank()) {
                throw new IllegalArgumentException(d.id() + ": regex 가 없다");
            }
            try {
                d.pattern();
            } catch (java.util.regex.PatternSyntaxException e) {
                throw new IllegalArgumentException(d.id() + ": 정규식 오류 — " + e.getDescription(), e);
            }
        }
    }

    private static Rule build(Rule.Def d, Profile p) {
        return switch (d.kind()) {
            case "regex" -> new RegexRule(d);
            case "jsUnusedImport" -> new JsImportRule(d);
            case "file" -> new FileRule(d, expected(d, p));
            default -> throw new IllegalArgumentException(d.id() + ": 모르는 kind " + d.kind());
        };
    }

    private static String expected(Rule.Def d, Profile p) {
        Object check = d.params().get("check");
        if (p == null) {
            return null;
        }
        if ("encoding".equals(check)) {
            return p.project() == null ? null : p.project().encoding();
        }
        if ("lineEnding".equals(check)) {
            return p.project() == null ? null : p.project().lineEnding();
        }
        if ("header".equals(check)) {
            return p.naming() == null ? null : String.valueOf(Boolean.TRUE.equals(p.naming().headerRequired()));
        }
        return null;
    }

    /** 전체 정의(켜짐 상태 포함) — 화면 체크박스·{@code GET /check/rules} */
    public List<Rule.Def> defs() {
        return defs;
    }

    public List<Rule> enabled() {
        return enabled;
    }

    /** 켜진 규칙의 글롭 합집합 — 폴더 목록을 거를 때 */
    public List<String> globs() {
        return enabled.stream().flatMap(r -> r.def().globs().stream()).distinct().toList();
    }

    /** 입력 하나에 켜진 규칙 전부. 줄·규칙 순 */
    public List<Finding> apply(Source s) {
        List<Finding> out = new ArrayList<>();
        for (Rule r : enabled) {
            if (r.accepts(s)) {
                out.addAll(r.apply(s));
            }
        }
        out.sort(java.util.Comparator.comparingInt(Finding::line).thenComparing(Finding::rule));
        return out;
    }
}
