package kr.ejg.toolbox.web;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-3 — jsp_formatter({@code formatJsp}·{@code compareDoc}, 순수본 = 백엔드본)를 실물 표본 JSP 전부에 돌린다.
 * <ul>
 *   <li>등급 A: 토큰이 사라지거나 늘었다({@code struct}) · 두 번 포맷하면 또 바뀐다(멱등 아님) · 스크립트 오류.
 *       멱등은 순수본이 적은 구조적 한계 ②(스크립틀릿으로 태그를 반쪽씩 여닫는 레거시)가 있는 파일({@code unbalanced > 0})에선 B</li>
 *   <li>등급 B: 인라인 간격 변화({@code gapChg})·pre/textarea 이동({@code preChg})·토큰 한도 초과({@code tooBig})</li>
 * </ul>
 */
@Tag("corpus")
@org.junit.jupiter.api.Disabled("V-3a — formatJsp 가 속성값 안 태그·스크립틀릿을 쪼갠다(실물 JSP 482/1,326). 순수본(portfolio) 수정 전")
class JspFmtCorpusTest {

    @TempDir
    static Path tmp;

    static List<JsonNode> rows;

    @BeforeAll
    static void run() throws Exception {
        rows = CorpusNode.run("corpus-jspfmt.js", "corpus-jspfmt", tmp).files();
    }

    static String f(JsonNode r) {
        return r.get("file").asText();
    }

    @Test
    void noTokenLostAndIdempotent() {
        List<String> bad = new ArrayList<>();
        for (JsonNode r : rows) {
            if (r.has("error")) {
                bad.add(f(r) + " 오류");
            } else if (!r.get("tooBig").asBoolean() && r.get("struct").asInt() > 0) {
                bad.add(f(r) + " struct " + r.get("struct").asInt());
            } else if (!r.get("idem").asBoolean() && r.get("unbalanced").asInt() == 0) {
                bad.add(f(r) + " 멱등 아님");
            }
        }
        CorpusFiles.none("jsp_formatter", bad, rows.size());
    }

    @Test
    void layoutChangesBaseline() throws java.io.IOException {
        List<String> b = new ArrayList<>();
        for (JsonNode r : rows) {
            if (r.has("error")) {
                continue;
            }
            boolean nonIdemButUnbalanced = !r.get("idem").asBoolean() && r.get("unbalanced").asInt() > 0;
            if (r.get("gapChg").asInt() > 0 || r.get("preChg").asInt() > 0 || r.get("tooBig").asBoolean() || nonIdemButUnbalanced) {
                b.add(f(r));
            }
        }
        CorpusFiles.baseline("jspfmt", b, rows.size());
    }

    /** 출처별 첫 JSP 의 수만 — 코드 본문은 골든에 안 싣는다 */
    @Test
    void pickedSummaryGolden() {
        Map<String, Map<String, Object>> picked = new TreeMap<>();
        for (JsonNode r : rows) {
            String src = f(r).substring(0, f(r).indexOf('/'));
            if (r.has("error") || picked.containsKey(src)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            for (String k : new String[] {"file", "tokens", "struct", "gapChg", "preChg", "unbalanced", "idem", "before", "after"}) {
                m.put(k, r.get(k).isNumber() ? (Object) r.get(k).asInt() : r.get(k).isBoolean() ? r.get(k).asBoolean() : r.get(k).asText());
            }
            picked.put(src, m);
        }
        GoldenFiles.assertJson("corpus/jspfmt-picked.json", picked);
    }
}
