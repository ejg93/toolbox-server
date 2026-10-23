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

    /** 6-33 — ${} 이름(조각 포함·방언 파일 합집합·정렬) · 동적 표지(문장 안 태그 · 끼운 조각의 태그, 정적 조각은 아님) */
    @Test
    void dollarsAndDynamic() {
        String a = """
                <mapper namespace="D">
                  <sql id="dynFrag"><if test="x != null">AND A = #{x}</if></sql>
                  <sql id="statFrag">FROM T ${tbl}</sql>
                  <select id="sorted">SELECT * FROM T ORDER BY ${sortCol} ${ sortDir }</select>
                  <select id="withIf">SELECT * FROM T <where><if test="a">A = #{a}</if></where></select>
                  <select id="incDyn">SELECT * FROM T WHERE 1=1 <include refid="dynFrag"/></select>
                  <select id="incStat">SELECT * <include refid="statFrag"/></select>
                  <select id="plain"><![CDATA[SELECT A FROM T WHERE B < #{b}]]></select>
                </mapper>""";
        String b = """
                <mapper namespace="D">
                  <select id="sorted">SELECT * FROM T ORDER BY ${extra.col}</select>
                </mapper>""";
        MapperIndex.Index idx = MapperIndex.scan(List.of(new Source("D_SQL_oracle.xml", a, null, null, null),
                new Source("D_SQL_mysql.xml", b, null, null, null)));
        assertEquals(List.of("extra.col", "sortCol", "sortDir"), idx.get("D.sorted").dollars());
        assertEquals(List.of("tbl"), idx.get("D.incStat").dollars(), "조각 안 ${}");
        assertEquals(List.of(), idx.get("D.plain").dollars());
        Map<String, Boolean> dyn = new LinkedHashMap<>();
        for (String id : List.of("sorted", "withIf", "incDyn", "incStat", "plain")) {
            dyn.put(id, idx.get("D." + id).dynamic());
        }
        assertEquals(Map.of("sorted", false, "withIf", true, "incDyn", true, "incStat", false, "plain", false), dyn);
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
        MapperIndex.Raw r = new MapperIndex.Raw("Ns", "id", "select", text, List.of(), "a.xml", 1, false);
        List<Unresolved> unresolved = new ArrayList<>();
        assertEquals(text, MapperIndex.include(r, text, Map.of(), unresolved, new HashSet<>(), 0));
        assertEquals(List.of(), unresolved);
    }
}
