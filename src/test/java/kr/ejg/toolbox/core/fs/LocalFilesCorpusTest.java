package kr.ejg.toolbox.core.fs;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.api.io.TempDir;

/**
 * V-7 — 로컬 파일 층(4-1)의 인코딩·줄바꿈 판정을 실물에 — jsp 폴더 일괄·주석 삭제 폴더가 되쓰는 길이다.
 * 표본: 공공데이터 CSV(MS949·UTF-8 BOM) + 표본에 섞인 EUC-KR 코드 파일 + UTF-8 코드 파일.
 * <ul>
 *   <li>등급 A: 엄격히 읽히는 파일을 {@code read} → 같은 글로 {@code write} 하면 원본 바이트와 같지 않다(인코딩·BOM·줄바꿈 중 하나를 바꿈)</li>
 *   <li>등급 B: UTF-8 로도 MS949 로도 온전히 안 읽히는 원본(깨진 바이트) · 줄바꿈이 섞인 원본(다수결로 맞춘다)</li>
 * </ul>
 */
@Tag("corpus")
class LocalFilesCorpusTest {

    @TempDir
    Path tmp;

    static boolean strict(byte[] b, Charset cs) {
        try {
            cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(b));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    @Test
    void readThenWriteKeepsBytes() throws IOException {
        List<Path> fs = new ArrayList<>(CorpusFiles.files("csv", "*.csv"));
        // 표본에 섞인 EUC-KR 코드 파일(V-2 드러남) + UTF-8 코드 파일 몇 백
        for (Path p : CorpusFiles.files("egov", "*.java", "*.jsp", "*.properties", "*.sql")) {
            if (fs.size() >= 1000) {
                break;
            }
            fs.add(p);
        }
        Path root = tmp.resolve("w");
        LocalFiles lf = new LocalFiles(tmp.resolve("data"));
        List<String> bad = new ArrayList<>();
        List<String> b = new ArrayList<>();
        Map<String, Integer> enc = new TreeMap<>();
        for (Path src : fs) {
            byte[] orig = Files.readAllBytes(src);
            String rel = CorpusFiles.rel(src);
            if (!strict(orig, StandardCharsets.UTF_8) && !strict(orig, Charset.forName("MS949"))) {
                b.add(rel + " 깨진 바이트");
                continue;
            }
            String s = new String(orig, StandardCharsets.ISO_8859_1);
            boolean mixed = s.contains("\r\n") && s.replace("\r\n", "").contains("\n");
            if (mixed) {
                b.add(rel + " 줄바꿈 섞임");
                continue;
            }
            Path copy = root.resolve(rel);
            Files.createDirectories(copy.getParent());
            Files.write(copy, orig);
            LocalFiles.Text t = lf.read(copy.toString());
            enc.merge(t.encoding(), 1, Integer::sum);
            lf.write(copy.toString(), root.toString(), t.text(), t.encoding(), t.lineEnding(), tmp.resolve("bk"));
            if (!java.util.Arrays.equals(orig, Files.readAllBytes(copy))) {
                bad.add(rel + " " + t.encoding() + " " + t.lineEnding());
            }
        }
        CorpusFiles.none("읽고 되쓰기(파일 " + fs.size() + ")", bad, fs.size());
        CorpusFiles.baseline("localfiles-b", b, fs.size());
        GoldenFiles.assertJson("corpus/localfiles-encodings.json", enc);
    }
}
