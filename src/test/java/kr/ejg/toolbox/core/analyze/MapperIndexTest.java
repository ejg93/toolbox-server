package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.check.Source;
import org.junit.jupiter.api.Test;

/** 6-2 — 매퍼 XML 색인. 픽스처: 방언 둘(같은 ns.id·표 차이)·같은/다른 namespace include·selectKey·CDATA 조각·choose 표·깨진 XML·매퍼 아님 */
class MapperIndexTest {

    static final Path DIR = Path.of("src/test/resources/fixtures/analyze/mapper");

    static List<Source> sources() throws IOException {
        List<Source> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(DIR)) {
            for (Path p : s.sorted().toList()) {
                out.add(new Source(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"), null, null, null));
            }
        }
        return out;
    }

    @Test
    void golden() throws IOException {
        MapperIndex.Index idx = MapperIndex.scan(sources());
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> st = new LinkedHashMap<>();
        idx.statements().forEach((id, s) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tag", s.tag());
            m.put("verb", s.verb());
            Map<String, String> tables = new LinkedHashMap<>();
            s.refs().forEach(r -> tables.put(r.table(), r.letters()));
            m.put("tables", tables);
            m.put("selectKey", s.selectKey());
            m.put("files", s.files());
            m.put("line", s.line());
            st.put(id, m);
        });
        out.put("statements", st);
        out.put("unresolved", idx.unresolved());
        GoldenFiles.assertJson("analyze/mapper-index.json", out);
    }

    /** 6-8(PR #27 리뷰 ⑤) — 여는 표시만 있고 닫는 표시가 없으면 남은 글을 그대로 돌려준다. 예외·미해결 없음 */
    @Test
    void brokenIncludeMarkerKeepsText() {
        String text = "SELECT * FROM T " + MapperIndex.INC_OPEN + "frag";
        MapperIndex.Raw r = new MapperIndex.Raw("Ns", "id", "select", text, List.of(), "a.xml", 1);
        List<Unresolved> unresolved = new ArrayList<>();
        assertEquals(text, MapperIndex.include(r, text, Map.of(), unresolved, new HashSet<>(), 0));
        assertEquals(List.of(), unresolved);
    }
}
