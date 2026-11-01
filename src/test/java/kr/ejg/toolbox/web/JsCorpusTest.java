package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
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

    /** 4-29 — corpus-js.js 가 CSV 마다 넘기는 앞부분 바이트 수 */
    private static final int HEAD = 4096;
    private static final int HEX_HEAD = 64;
    private static final int DUMP_WIDTH = 16;
    private static final String EUC_KR = "euc-kr";
    private static final String UTF_8 = "utf-8";
    private static final String ASCII = "ascii";
    private static final char BOM = (char) 0xFEFF;

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

    /**
     * 4-29(설계 51) — 인코딩 판별 탭의 순수 함수를 CSV 앞 4,096바이트·첫 줄에 돌려 Java 와 대조.
     * A: Hex·덤프 줄 수·왕복·첫 깨진 UTF-8 자리·판정(파일 전체 엄격 UTF-8 기준, 깨진 바이트 파일 제외). B: 첫 줄 디코딩(WHATWG euc-kr ↔ MS949)
     */
    @Test
    void hexAndVerdictMatchJava() throws Exception {
        Map<String, Path> csv = new TreeMap<>();
        for (Path p : CorpusFiles.files("csv", "*.csv")) {
            csv.put(CorpusFiles.rel(p), p);
        }
        Set<String> broken = new HashSet<>(Files.readAllLines(GoldenFiles.DIR.resolve("corpus").resolve("csv-broken-bytes.txt"), StandardCharsets.UTF_8));
        JsonNode enc = s.get("enc");
        assertEquals(csv.size(), enc == null ? -1 : enc.size(), "summary.json 의 enc — CSV 마다 하나");
        List<String> bad = new ArrayList<>();
        List<String> decodeOff = new ArrayList<>();
        for (JsonNode n : enc) {
            String f = n.get("path").asText();
            byte[] raw = Files.readAllBytes(csv.get(f));
            byte[] head = Arrays.copyOf(raw, Math.min(HEAD, raw.length));
            bad.addAll(hexMismatches(f, n, head));
            if (!broken.contains(f)) {
                String want = verdict(raw);
                if (!want.equals(n.path("enc").asText())) {
                    bad.add(f + " 판정 JS " + n.path("enc").asText() + " / Java " + want);
                } else if (!lineSame(n, head, want)) {
                    decodeOff.add(f);
                }
            }
        }
        CorpusFiles.none("Hex·판정(CSV " + enc.size() + ", 판정 " + (enc.size() - broken.size()) + ")", bad, enc.size());
        if (!decodeOff.isEmpty()) {
            CorpusFiles.baseline("js-enc-decode", decodeOff, enc.size());
        }
    }

    /** Hex 64바이트 · 덤프 줄 수 · 왕복 · 첫 깨진 UTF-8 자리 */
    static List<String> hexMismatches(String f, JsonNode n, byte[] head) {
        List<String> bad = new ArrayList<>();
        String hex = HexFormat.ofDelimiter(" ").withUpperCase().formatHex(head, 0, Math.min(HEX_HEAD, head.length));
        if (!hex.equals(n.path("hex64").asText())) {
            bad.add(f + " hex64");
        }
        if (n.path("dumpLines").asInt() != (head.length + DUMP_WIDTH - 1) / DUMP_WIDTH) {
            bad.add(f + " 덤프 줄 " + n.path("dumpLines").asInt());
        }
        if (!n.path("roundTrip").asBoolean()) {
            bad.add(f + " 왕복");
        }
        int at = firstBadUtf8(head);
        if (n.path("badUtf8").asInt() != at) {
            bad.add(f + " 첫 깨진 UTF-8 JS " + n.path("badUtf8").asInt() + " / Java " + at);
        }
        return bad;
    }

    /** 엄격 UTF-8 디코더가 멈춘 자리(끝에서 잘린 열 포함), 없으면 −1 */
    static int firstBadUtf8(byte[] b) {
        CharsetDecoder d = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(b);
        CoderResult r = d.decode(in, CharBuffer.allocate(b.length + 1), true);
        return r.isError() ? in.position() : -1;
    }

    /** 파일 전체가 엄격 UTF-8 이면 비ASCII 유무로 utf-8·ascii, 아니면 euc-kr(CsvCorpusTest 규칙) */
    static String verdict(byte[] raw) {
        if (firstBadUtf8(raw) != -1) {
            return EUC_KR;
        }
        for (byte x : raw) {
            if (x < 0) {
                return UTF_8;
            }
        }
        return ASCII;
    }

    /** 첫 줄(첫 LF 앞) — MS949 파일은 WHATWG euc-kr ↔ Java MS949, UTF-8 파일은 TextDecoder 가 BOM 을 떼므로 Java 쪽도 뗀다 */
    static boolean lineSame(JsonNode n, byte[] head, String want) {
        int nl = 0;
        while (nl < head.length && head[nl] != '\n') {
            nl++;
        }
        byte[] line = Arrays.copyOf(head, nl);
        if (EUC_KR.equals(want)) {
            return n.path("lineEucKr").asText().equals(new String(line, Charset.forName("MS949")));
        }
        String u = new String(line, StandardCharsets.UTF_8);
        return n.path("lineUtf8").asText().equals(u.isEmpty() || u.charAt(0) != BOM ? u : u.substring(1));
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
