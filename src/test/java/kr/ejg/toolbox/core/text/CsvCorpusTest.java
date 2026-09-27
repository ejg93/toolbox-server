package kr.ejg.toolbox.core.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * V-4 — {@link Csv} 를 공공데이터포털 실물 CSV(대부분 CP949)에 돌린다.
 * <ul>
 *   <li>등급 A: decode·parse 예외 · 치환 문자(U+FFFD — 인코딩을 잘못 골랐다) · 값 행 0</li>
 *   <li>등급 B: 머리와 열 수가 다른 행이 있는 파일(끝 쉼표·따옴표 안 줄바꿈 등)</li>
 * </ul>
 */
@Tag("corpus")
class CsvCorpusTest {

    static List<Path> files() throws IOException {
        return CorpusFiles.files("csv", "*.csv");
    }

    @Test
    void decodesAndParses() throws IOException {
        List<Path> fs = files();
        List<String> bad = new ArrayList<>();
        for (Path p : fs) {
            try {
                byte[] b = Files.readAllBytes(p);
                String t = Csv.decode(b);
                // 엄격히 읽히는 인코딩이 있으면 decode 가 그것과 같아야 한다. 원본에 U+FFFD 가 실제로 든 파일(원본 오염)도 이 비교로 통과한다
                String strict = strict(b, java.nio.charset.StandardCharsets.UTF_8);
                if (strict == null) {
                    strict = strict(b, java.nio.charset.Charset.forName("MS949"));
                }
                if (strict != null && !strict.equals(t)) {
                    bad.add(CorpusFiles.rel(p) + " 인코딩 오판");
                } else if (Csv.parse(t).size() < 2) {
                    bad.add(CorpusFiles.rel(p) + " 값 행 0");
                }
            } catch (RuntimeException e) {
                bad.add(CorpusFiles.rel(p) + " " + e.getClass().getSimpleName());
            }
        }
        CorpusFiles.none("Csv(파일 " + fs.size() + ")", bad, fs.size());
    }

    /** 엄격 디코딩 — 안 되면 null */
    static String strict(byte[] b, java.nio.charset.Charset cs) {
        try {
            return cs.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(b)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return null;
        }
    }

    /** UTF-8 로도 MS949 로도 온전히 안 읽히는 파일(원본의 깨진 바이트) — 치환 문자가 나오는 것이 맞다 */
    @Test
    void brokenBytesBaseline() throws IOException {
        List<Path> fs = files();
        List<String> b = new ArrayList<>();
        for (Path p : fs) {
            byte[] raw = Files.readAllBytes(p);
            if (strict(raw, java.nio.charset.StandardCharsets.UTF_8) == null && strict(raw, java.nio.charset.Charset.forName("MS949")) == null) {
                b.add(CorpusFiles.rel(p));
            }
        }
        CorpusFiles.baseline("csv-broken-bytes", b, fs.size());
    }

    @Test
    void ragged() throws IOException {
        List<Path> fs = files();
        List<String> b = new ArrayList<>();
        for (Path p : fs) {
            List<List<String>> rows = Csv.parse(Csv.decode(Files.readAllBytes(p)));
            int head = rows.get(0).size();
            if (rows.stream().anyMatch(r -> r.size() != head)) {
                b.add(CorpusFiles.rel(p));
            }
        }
        CorpusFiles.baseline("csv-ragged", b, fs.size());
    }

    /** 인코딩·구분자·행·열 합계 — 값 없이 수만 */
    @Test
    void summaryGolden() throws IOException {
        Map<String, Integer> by = new TreeMap<>();
        for (Path p : files()) {
            byte[] b = Files.readAllBytes(p);
            String t = Csv.decode(b);
            boolean utf8 = new String(b, java.nio.charset.StandardCharsets.UTF_8).equals(t);
            by.merge(utf8 ? (t.startsWith(String.valueOf((char) 0xFEFF)) ? "UTF-8 BOM" : "UTF-8") : "MS949", 1, Integer::sum);
            by.merge("구분자 " + (Csv.detectDelim(t) == '\t' ? "TAB" : String.valueOf(Csv.detectDelim(t))), 1, Integer::sum);
        }
        GoldenFiles.assertJson("corpus/csv-summary.json", by);
    }
}
