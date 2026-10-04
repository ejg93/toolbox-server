package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.analyze.MapperIndex;
import kr.ejg.toolbox.core.analyze.SqlTables;
import kr.ejg.toolbox.core.check.JavaSource;
import kr.ejg.toolbox.core.check.Source;
import org.junit.jupiter.api.Test;

/**
 * 7-5 — 저장소 템플릿 세트 셋(egov35·egov4·egov5, 7-12)을 7-2 픽스처 표로 그려 골든과 견주고, 그 결과를 이 도구 엔진으로 다시 읽는다
 * (Java 구문 · 매퍼 문장 여섯 · 표 하나에 C·R·U·D). 방언 다섯의 매퍼는 따로 골든.
 */
class GenTemplatesTest {

    static final Path GEN = Path.of("templates/gen");

    static Map<String, String> render(String setName, String dialect) throws Exception {
        TemplateSet set = TemplateSet.load(GEN, setName);
        Templates t = new Templates(set);
        GenModel.Options o = new GenModel.Options("kr.go.hr", null, List.of("TB"), Map.of("DEPT_NM", "부서명"), dialect);
        Map<String, Object> model = GenModel.of(GenModelTest.empHist(), o, set.vars(), GenModelTest.TYPES).model();
        Map<String, String> out = new LinkedHashMap<>();
        for (TemplateSet.FileSpec f : set.files()) {
            out.put(t.renderPath(f.path(), model), t.render(f.template(), model));
        }
        return out;
    }

    static String base(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Test
    void setsGolden() throws Exception {
        for (String set : List.of("egov35", "egov4", "egov5")) {
            Map<String, String> files = render(set, "oracle");
            assertEquals(10, files.size(), set);
            for (Map.Entry<String, String> e : files.entrySet()) {
                GoldenFiles.assertText("gen/" + set + "/" + base(e.getKey()), e.getValue());
            }
            GoldenFiles.assertJson("gen/" + set + "/paths.json", new ArrayList<>(files.keySet()));
        }
    }

    @Test
    void mapperPerDialect() throws Exception {
        for (String d : List.of("oracle", "tibero", "postgresql", "mariadb", "mssql")) {
            Map<String, String> files = render("egov35", d);
            String mapper = files.entrySet().stream().filter(e -> e.getKey().endsWith(".xml")).findFirst().orElseThrow().getValue();
            GoldenFiles.assertText("gen/mapper-" + d + ".xml", mapper);
        }
    }

    /** 생성물이 이 도구 엔진을 통과한다 — 구문·매퍼 문장 여섯·표 하나에 CRUD 넷 */
    @Test
    void roundTripThroughOwnEngine() throws Exception {
        for (String set : List.of("egov35", "egov4", "egov5")) {
            Map<String, String> files = render(set, "oracle");
            JavaSource parser = new JavaSource();
            List<Source> xml = new ArrayList<>();
            for (Map.Entry<String, String> e : files.entrySet()) {
                if (e.getKey().endsWith(".java")) {
                    assertTrue(JavaSource.ok(parser.parse(e.getValue())), set + " 구문: " + e.getKey());
                } else if (e.getKey().endsWith(".xml")) {
                    xml.add(new Source(e.getKey(), e.getValue(), "UTF-8", "LF", null));
                }
            }
            MapperIndex.Index idx = MapperIndex.scan(xml);
            assertEquals(List.of(), idx.unresolved(), set + " 매퍼 미해결");
            assertEquals(Set.of("EmpHist.selectEmpHistList", "EmpHist.selectEmpHistListTotCnt", "EmpHist.selectEmpHistDetail",
                    "EmpHist.insertEmpHist", "EmpHist.updateEmpHist", "EmpHist.deleteEmpHist"), idx.statements().keySet(), set);
            Set<String> letters = new TreeSet<>();
            for (MapperIndex.Statement s : idx.statements().values()) {
                for (SqlTables.Ref r : s.refs()) {
                    assertEquals("TB_EMP_HIST", r.table(), set);
                    for (char ch : r.letters().toCharArray()) {
                        letters.add(String.valueOf(ch));
                    }
                }
            }
            assertEquals(Set.of("C", "R", "U", "D"), letters, set + " — 표 하나에 C·R·U·D");
        }
    }
}
