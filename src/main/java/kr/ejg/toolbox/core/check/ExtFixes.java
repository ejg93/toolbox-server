package kr.ejg.toolbox.core.check;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 외부 분석기 지적 유형별 권장 수정(5-43, 설계 36). 지적의 규칙 이름이 {@code spotbugs.<BUG_TYPE>}·{@code findsecbugs.<BUG_TYPE>} 이라
 * 규칙 단위 {@code fix} 가 안 닿는다 — 표 {@code /check/ext-fixes.yaml}(고치는 법이 하나로 정해진 「상」 유형만, 사용자 2026-10-24)을
 * 같은 키로 읽고, 외부 규칙 정의의 {@code params.fixes}({@code {BUG_TYPE: 문구}})로 덮는다. 빈 문구는 그 유형을 지운다
 */
public final class ExtFixes {

    static final String TABLE = "/check/ext-fixes.yaml";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    /** 표의 절 이름·외부 규칙 id → 지적 규칙 이름의 접두 */
    private static final Map<String, String> PREFIX = Map.of("spotbugs", "spotbugs.", "findsecbugs", "findsecbugs.",
            SpotBugsRun.SPOTBUGS, "spotbugs.", SpotBugsRun.FINDSECBUGS, "findsecbugs.");

    private ExtFixes() {
    }

    /** 내장 표 — 「spotbugs.EI_EXPOSE_REP2」 → 문구, 표 순서대로 */
    @SuppressWarnings("unchecked")
    public static Map<String, String> builtin() {
        try (InputStream in = ExtFixes.class.getResourceAsStream(TABLE)) {
            if (in == null) {
                throw new IllegalStateException(TABLE + " 가 없다");
            }
            Map<String, Map<String, String>> raw = YAML.readValue(in, Map.class);
            Map<String, String> out = new LinkedHashMap<>();
            raw.forEach((section, types) -> types.forEach((type, fix) -> out.put(PREFIX.get(section) + type, fix)));
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 내장 표 + 외부 규칙 정의(켬과 무관하게 — 지난 이력의 xlsx 에도 쓴다)의 {@code params.fixes} */
    public static Map<String, String> of(RuleSet rules) {
        Map<String, String> out = builtin();
        for (Rule.Def d : rules.defs()) {
            String prefix = PREFIX.get(d.id());
            if (!"external".equals(d.kind()) || prefix == null || !(d.params().get("fixes") instanceof Map<?, ?> fixes)) {
                continue;
            }
            fixes.forEach((type, fix) -> {
                if (type == null) {
                    return;
                }
                String key = prefix + type;
                if (fix == null || fix instanceof String s && s.isBlank()) {
                    out.remove(key);
                } else if (fix instanceof String s) {
                    out.put(key, s.strip());
                } // 마무리 리뷰 — 글자가 아닌 값(YAML no·숫자·목록)은 「false」 같은 문구로 새지 않게 버린다
            });
        }
        return out;
    }
}
