package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/**
 * 5-9 — 순수본 코드 검사(portfolio, {@code file://} 단일 HTML)가 내장할 정규식 규칙 JSON. 원본은 {@code check/rules.yaml} 이고
 * 순수본은 이 골든({@code golden/check/pure-rules.json})을 그대로 붙인다(손으로 안 고친다 — {@code docs/pure-code-check.md}).
 * 규칙 YAML 이 바뀌면 골든이 빨개진다 — 갱신하고 portfolio 에 다시 넣으라는 신호. 같은 테스트가 JS 로 옮길 수 없는 자바 전용 문법을 막는다.
 */
class PureRulesJsonTest {

    /** 자바 정규식에만 있는 문법 — JS {@code RegExp} 가 못 읽거나 뜻이 달라진다 */
    static final List<String> JAVA_ONLY = List.of("*+", "++", "?+", "}+", "(?>", "(?i", "(?s", "(?m", "(?x", "(?u", "(?d", "\\Q", "\\E",
            "\\A", "\\Z", "\\z", "\\G", "\\h", "\\R", "\\X", "&&", "\\p{");

    static List<Map<String, Object>> pureRules() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Rule.Def d : RuleSet.builtin().defs()) {
            if (!d.kind().equals("regex")) {
                continue;
            }
            List<String> langs = new ArrayList<>();
            for (String g : d.globs()) {
                String ext = g.substring(g.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
                if (!langs.contains(ext)) {
                    langs.add(ext);
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.id());
            m.put("group", d.group());
            m.put("severity", d.severity());
            m.put("langs", langs);
            m.put("regex", d.regex());
            m.put("flags", d.flags());
            m.put("skipComments", Boolean.TRUE.equals(d.skipComments()));
            m.put("max", d.params().get("max"));
            m.put("message", d.message());
            out.add(m);
        }
        return out;
    }

    @Test
    void goldenJson() {
        List<Map<String, Object>> rules = pureRules();
        assertEquals(54, rules.size(), "정규식 규칙 수(5-18 — ㅇ 3차 7 더함) — 늘거나 줄면 이 수와 지시문을 같이 고친다");
        GoldenFiles.assertJson("check/pure-rules.json", rules);
    }

    /**
     * 5-10 — 순수본 {@code pure/tools/code_check.html} 의 {@code var RULES = [ … ];} 가 이 골든과 같다. node 없이 돈다.
     * 규칙 YAML 이 바뀌면 골든과 이것이 같이 빨개진다 — 골든을 portfolio 순수본에 다시 붙이고 {@code sync-pure.sh} 로 끌어오라는 신호
     */
    @Test
    void pureHtmlEmbedsGolden() throws Exception {
        String html = Files.readString(Path.of("pure", "tools", "code_check.html"), StandardCharsets.UTF_8);
        int a = html.indexOf("var RULES = ");
        assertTrue(a >= 0, "순수본에 var RULES 가 없다");
        int s = html.indexOf('[', a);
        int e = html.indexOf("\n];", s);
        assertTrue(e > s, "var RULES 의 끝 ]; 를 못 찾았다");
        JsonNode pure = JsonMapper.builder().enable(JsonReadFeature.ALLOW_TRAILING_COMMA).build().readTree(html.substring(s, e + 2));
        JsonNode golden = GoldenFiles.JSON.readTree(GoldenFiles.DIR.resolve("check/pure-rules.json").toFile());
        assertEquals(golden, pure, "순수본 RULES ≠ golden/check/pure-rules.json");
    }

    @Test
    void portableToJavaScript() {
        List<String> bad = new ArrayList<>();
        for (Map<String, Object> r : pureRules()) {
            String re = (String) r.get("regex");
            for (String t : JAVA_ONLY) {
                if (re.contains(t)) {
                    bad.add(r.get("id") + " — " + t);
                }
            }
        }
        assertEquals(List.of(), bad, "JS RegExp 로 못 옮기는 자바 전용 문법");
    }
}
