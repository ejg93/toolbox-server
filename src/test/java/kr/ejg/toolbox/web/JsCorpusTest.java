package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.gen.DdlReader;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.text.Csv;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-7(+ V-5 카멜) — dev_tools JSON·XML 포매터·바이트 계산·카멜 변환, table_builder 불러오기를 실물 표본에 돌린다(순수본 JS = 백엔드본).
 * <ul>
 *   <li>등급 A: 유효 JSON 이 포맷·압축 뒤 다시 파싱해 달라짐 · 16자리 넘는 정수 원문이 사라짐 · XML 포맷 전후 DOM 이 다름 ·
 *       바이트 수가 자바 {@code getBytes} 와 다름(MS949 로 담기는 줄) · 표 불러오기 → 내보내기 → 불러오기 모델이 달라짐 ·
 *       컬럼명 snake → camel → snake 가 안 돌아옴(숫자로 시작하는 조각은 원래 못 돌아와 뺀다)</li>
 *   <li>등급 B: 무효 JSON(n_*)을 받아 줌 · MS949 로 못 담는 글자가 든 줄</li>
 * </ul>
 */
@Tag("corpus")
class JsCorpusTest {

    @TempDir
    static Path tmp;

    static JsonNode s;
    static List<String> korean;
    static List<String> columns;

    @BeforeAll
    static void run() throws Exception {
        CorpusFiles.verify();
        Path out = Path.of("target", "corpus-js").toAbsolutePath();
        Files.createDirectories(out);
        // 실물 컬럼명 — eGov Oracle DDL(공통 + 모듈별)
        Set<String> cols = new LinkedHashSet<>();
        for (Path p : CorpusFiles.files("egov", "*.sql")) {
            if (CorpusFiles.rel(p).contains("/ddl/oracle/")) {
                for (Table t : DdlReader.read(Csv.decode(Files.readAllBytes(p))).tables()) {
                    t.columns().stream().map(Column::name).map(n -> n.toUpperCase(Locale.ROOT)).forEach(cols::add);
                }
            }
        }
        columns = List.copyOf(cols);
        // 실물 한국어 줄 — 공공데이터 CSV 마다 앞 5행
        List<String> kor = new ArrayList<>();
        for (Path p : CorpusFiles.files("csv", "*.csv")) {
            Csv.decode(Files.readAllBytes(p)).lines().skip(1).limit(5).filter(l -> !l.isBlank()).forEach(kor::add);
        }
        korean = kor;
        Files.write(out.resolve("in-columns.txt"), columns, StandardCharsets.UTF_8);
        Files.write(out.resolve("in-korean.txt"), korean, StandardCharsets.UTF_8);
        CorpusNode.Result r = CorpusNode.run("corpus-js.js", "corpus-js", tmp);
        s = new ObjectMapper().readTree(r.out().resolve("summary.json").toFile());
    }

    static List<JsonNode> of(String kind) {
        List<JsonNode> l = new ArrayList<>();
        s.get("files").forEach(n -> {
            if (n.get("kind").asText().equals(kind)) {
                l.add(n);
            }
        });
        return l;
    }

    @Test
    void jsonRoundTripsAndKeepsBigNumbers() throws Exception {
        List<JsonNode> js = of("json");
        List<String> bad = new ArrayList<>();
        List<String> lenient = new ArrayList<>();
        for (JsonNode n : js) {
            String f = n.get("file").asText();
            if (n.get("valid").asBoolean()) {
                if (!n.path("accepted").asBoolean()) {
                    bad.add(f + " 유효한데 거부");
                } else if (!n.path("same").asBoolean()) {
                    bad.add(f + " 다시 파싱하면 달라짐");
                } else if (!n.path("bigKept").asBoolean(true)) {
                    bad.add(f + " 16자리 넘는 수 원문 사라짐");
                }
            } else if (n.path("accepted").asBoolean() && f.contains("/n_")) {
                lenient.add(f);
            }
        }
        CorpusFiles.none("JSON 포매터(파일 " + js.size() + ")", bad, js.size());
        CorpusFiles.conformance("js-json-lenient", lenient); // 무효를 받아 줌 — 원문 보존형 파서가 문자열 이스케이프를 안 잰다
    }

    @Test
    void xmlDomKept() throws Exception {
        List<JsonNode> xs = of("xml");
        List<String> bad = new ArrayList<>();
        for (JsonNode n : xs) {
            if (n.get("valid").asBoolean() && !n.path("same").asBoolean()) {
                bad.add(n.get("file").asText() + " " + n.path("why").asText(""));
            }
        }
        CorpusFiles.none("XML 포매터(파일 " + xs.size() + ")", bad, xs.size());
    }

    @Test
    void byteCountsMatchJava() throws Exception {
        Charset ms949 = Charset.forName("MS949");
        List<String> bad = new ArrayList<>();
        List<String> unmappable = new ArrayList<>();
        JsonNode b = s.get("bytes");
        assertEquals(korean.size(), b.size());
        for (int i = 0; i < korean.size(); i++) {
            String line = korean.get(i);
            int u8 = line.getBytes(StandardCharsets.UTF_8).length;
            if (b.get(i).get(0).asInt() != u8) {
                bad.add("줄 " + i + " UTF-8 " + b.get(i).get(0).asInt() + " ≠ " + u8);
            }
            if (!ms949.newEncoder().canEncode(line)) {
                unmappable.add("줄 " + i);
            } else if (b.get(i).get(1).asInt() != line.getBytes(ms949).length) {
                bad.add("줄 " + i + " CP949 " + b.get(i).get(1).asInt() + " ≠ " + line.getBytes(ms949).length);
            }
        }
        CorpusFiles.none("바이트 계산(줄 " + korean.size() + ")", bad, korean.size());
        CorpusFiles.baseline("js-bytes-unmappable", unmappable, korean.size());
    }

    @Test
    void camelRoundTrips() {
        JsonNode c = s.get("camel");
        List<String> bad = new ArrayList<>();
        int judged = 0;
        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i);
            if (!col.matches("[A-Z][A-Z0-9]*(_[A-Z][A-Z0-9]*)*")) {
                continue; // 숫자로 시작하는 조각·연속 밑줄 — 순수본 규칙상 원래 못 돌아온다
            }
            judged++;
            if (!c.get(i).asText().equals(col)) {
                bad.add(col + " → " + c.get(i).asText());
            }
        }
        CorpusFiles.none("카멜 왕복(컬럼 " + judged + ")", bad, judged);
    }

    @Test
    void tableImportExportStable() {
        List<JsonNode> ts = of("table");
        List<String> bad = new ArrayList<>();
        for (JsonNode n : ts) {
            if (!n.get("ok").asBoolean() || !n.get("same").asBoolean()) {
                bad.add(n.get("file").asText() + " " + n.path("why").asText("모델 다름"));
            }
        }
        CorpusFiles.none("table_builder 왕복(표 " + ts.size() + ")", bad, ts.size());
    }

    @Test
    void summaryGolden() {
        Map<String, Object> g = new LinkedHashMap<>();
        Map<String, Integer> kinds = new TreeMap<>();
        s.get("files").forEach(n -> kinds.merge(n.get("kind").asText() + (n.path("valid").asBoolean(true) ? "" : "(무효)"), 1, Integer::sum));
        g.put("files", kinds);
        g.put("koreanLines", korean.size());
        g.put("columns", columns.size());
        GoldenFiles.assertJson("corpus/js-summary.json", g);
    }
}
