package kr.ejg.toolbox.core.fs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.text.LineDiff;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-6 — 폴더 비교(4-2)를 실물 두 판(eGov 공통컴포넌트 v5.0.5 → v5.0.6)에 돌린다.
 * <ul>
 *   <li>등급 A: 다른 파일마다 줄 비교 왕복 — ops 의 「= 와 -」 가 a, 「= 와 +」 가 b 와 같다</li>
 *   <li>분류 수(같음·다름·한쪽만)와 tooBig 수는 골든</li>
 * </ul>
 */
@Tag("corpus")
class FolderDiffCorpusTest {

    @TempDir
    Path tmp;

    static List<String> lines(String text) {
        String t = text.replace("\r\n", "\n");
        if (t.isEmpty()) {
            return List.of();
        }
        if (t.endsWith("\n")) {
            t = t.substring(0, t.length() - 1);
        }
        return List.of(t.split("\n", -1));
    }

    @Test
    void twoReleasesRoundTrip() throws IOException {
        CorpusFiles.verify();
        LocalFiles fs = new LocalFiles(tmp.resolve("data"));
        Path a = CorpusFiles.root().resolve("egov-prev");
        Path b = CorpusFiles.root().resolve("egov");
        FolderDiff.Result r = FolderDiff.compare(fs, a.toString(), b.toString(), List.of("*.java", "*.jsp", "*.xml"));
        Map<String, Integer> count = new TreeMap<>();
        List<String> bad = new ArrayList<>();
        int diffs = 0;
        for (FolderDiff.Item it : r.items()) {
            count.merge(it.status().name(), 1, Integer::sum);
            if (it.status() != FolderDiff.Status.DIFF) {
                continue;
            }
            diffs++;
            String ta = fs.read(a.resolve(it.rel()).toString()).text();
            String tb = fs.read(b.resolve(it.rel()).toString()).text();
            LineDiff.Result d = LineDiff.diff(ta, tb);
            if (d.tooBig()) {
                count.merge("tooBig", 1, Integer::sum);
                continue;
            }
            List<String> ra = d.ops().stream().filter(o -> !o.op().equals("+")).map(LineDiff.Op::line).toList();
            List<String> rb = d.ops().stream().filter(o -> !o.op().equals("-")).map(LineDiff.Op::line).toList();
            if (!ra.equals(lines(ta)) || !rb.equals(lines(tb))) {
                bad.add(it.rel());
            }
        }
        CorpusFiles.none("줄 비교 왕복(다른 파일 " + diffs + ")", bad, diffs);
        count.put("truncated", r.truncated() ? 1 : 0);
        GoldenFiles.assertJson("corpus/folderdiff-egov.json", count);
    }
}
