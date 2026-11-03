package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;

/**
 * 7-5 — 저장소 템플릿 세트 셋(egov35·egov4·egov5, 7-12)을 7-2 픽스처 표로 그려 골든과 견주고, 그 결과를 이 도구 엔진으로 다시 읽는다
 * (Java 구문 · 매퍼 문장 여섯 · 표 하나에 C·R·U·D). 방언 다섯의 매퍼는 따로 골든.
 */
class GenTemplatesTest {

    static final Path GEN = Path.of("templates/gen");

    static final String EGOV35 = "egov35";
    static final String ORACLE = "oracle";
    static final Set<String> CRUD = Set.of("C", "R", "U", "D");

    static Map<String, String> render(String setName, String dialect) throws Exception {
        return render(setName, dialect, GenModelTest.empHist());
    }

    static Map<String, String> render(String setName, String dialect, Table table) throws Exception {
        return render(setName, dialect, table, null);
    }

    /** audit — null 이면 EGOV(Options 가 orDefault) */
    static Map<String, String> render(String setName, String dialect, Table table, Profile.Audit audit) throws Exception {
        TemplateSet set = TemplateSet.load(GEN, setName);
        Templates t = new Templates(set);
        GenModel.Options o = new GenModel.Options("kr.go.hr", null, List.of("TB"), Map.of("DEPT_NM", "부서명"), dialect, null, audit);
        Map<String, Object> model = GenModel.of(table, o, set.vars(), GenModelTest.TYPES).model();
        Map<String, String> out = new LinkedHashMap<>();
        for (TemplateSet.FileSpec f : set.files()) {
            out.put(t.renderPath(f.path(), model), t.render(f.template(), model));
        }
        return out;
    }

    /** EGOV 감사 넷 + 세션 글 — 감사 묶음이 살아 있어야 컨트롤러 hasUser 가 켜진다 */
    static Profile.Audit egovSession(String s) {
        Profile.Audit e = Profile.Audit.EGOV;
        return new Profile.Audit(e.created(), e.creator(), e.updated(), e.updater(), s);
    }

    /** 경로가 suffix 로 끝나는 생성물 하나 */
    static String file(Map<String, String> out, String suffix) {
        return out.entrySet().stream().filter(e -> e.getKey().endsWith(suffix)).findFirst().orElseThrow(() -> new AssertionError(suffix + " 없음: " + out.keySet()))
                .getValue();
    }

    static String base(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Test
    void setsGolden() throws Exception {
        for (String set : List.of(EGOV35, "egov4", "egov5")) {
            Map<String, String> files = render(set, ORACLE);
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
            Map<String, String> files = render(EGOV35, d, GenModelTest.bbs());
            String mapper = files.entrySet().stream().filter(e -> e.getKey().endsWith(".xml")).findFirst().orElseThrow().getValue();
            GoldenFiles.assertText("gen/mapper-" + d + ".xml", mapper);
        }
    }

    /** 7-17 — eGov 게시판 꼴(역할·입력 꼴·검색 셋·감사 컬럼)을 egov35·oracle 로 — 바뀌는 여섯만 골든(7-20c 컨트롤러의 로그인 ID) */
    @Test
    void bbsGolden() throws Exception {
        Map<String, String> files = render(EGOV35, ORACLE, GenModelTest.bbs());
        Set<String> golden = Set.of("ComtnbbsList.jsp", "ComtnbbsRegist.jsp", "ComtnbbsUpdt.jsp", "ComtnbbsVO.java", "Comtnbbs_SQL_oracle.xml",
                "ComtnbbsController.java");
        int n = 0;
        for (Map.Entry<String, String> e : files.entrySet()) {
            String b = base(e.getKey());
            if (golden.contains(b)) {
                GoldenFiles.assertText("gen/bbs/" + b, e.getValue());
                n++;
            }
        }
        assertEquals(golden.size(), n, files.keySet().toString());
        assertEquals(CRUD, roundTrip(EGOV35 + " bbs", files, "Comtnbbs", "COMTNBBS"));
    }

    /** 7-22 — userSession 빈 글은 Principal 만, 점 없는 글은 세션 값 그대로(toString) */
    @Test
    void controllerSessionShapes() throws Exception {
        String principal = file(render(EGOV35, ORACLE, GenModelTest.bbs(), egovSession("")), "Controller.java");
        assertTrue(principal.contains("getUserPrincipal()") && principal.contains("HttpServletRequest"), principal);
        for (String no : List.of("HttpSession", "getAttribute(", "BeanWrapperImpl")) {
            assertFalse(principal.contains(no), no);
        }
        String plain = file(render(EGOV35, ORACLE, GenModelTest.bbs(), egovSession("userId")), "Controller.java");
        assertTrue(plain.contains("getAttribute(\"userId\")") && plain.contains("user.toString()"), plain);
        assertFalse(plain.contains("BeanWrapperImpl") || plain.contains("getPropertyValue"), plain);
    }

    /** 7-22 — 길이 있는 textarea 컬럼: 등록은 textarea, 목록 검색은 한 줄 입력칸, 매퍼는 LIKE */
    @Test
    void sizedTextareaRendersSearch() throws Exception {
        Map<String, String> out = render(EGOV35, ORACLE, GenModelTest.one(GenModelTest.col("C2000", 2, "VARCHAR2", 2000L, null, null, true, "설명")));
        String list = file(out, "List.jsp");
        assertTrue(list.contains("<form:input path=\"c2000\"") && list.contains("<label for=\"c2000\""), list);
        String regist = file(out, "Regist.jsp");
        assertTrue(regist.contains("<form:textarea path=\"c2000\""), regist);
        String mapper = file(out, "_SQL_oracle.xml");
        assertTrue(mapper.contains("C2000 LIKE") && mapper.contains("c2000 != null"), mapper);
    }

    /** 생성물이 이 도구 엔진을 통과한다 — 구문·매퍼 문장 여섯·표 하나에 CRUD 넷 */
    @Test
    void roundTripThroughOwnEngine() throws Exception {
        for (String set : List.of(EGOV35, "egov4", "egov5")) {
            assertEquals(CRUD, roundTrip(set, render(set, ORACLE), "EmpHist", "TB_EMP_HIST"), set + " — 표 하나에 C·R·U·D");
        }
    }

    /** 구문·매퍼 문장 여섯을 단언하고 표 하나에 붙은 CRUD 글자를 돌려준다 */
    static Set<String> roundTrip(String set, Map<String, String> files, String ns, String table) {
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
        assertEquals(Set.of(ns + ".select" + ns + "List", ns + ".select" + ns + "ListTotCnt", ns + ".select" + ns + "Detail",
                ns + ".insert" + ns, ns + ".update" + ns, ns + ".delete" + ns), idx.statements().keySet(), set);
        Set<String> letters = new TreeSet<>();
        for (MapperIndex.Statement s : idx.statements().values()) {
            for (SqlTables.Ref r : s.refs()) {
                assertEquals(table, r.table(), set);
                for (char ch : r.letters().toCharArray()) {
                    letters.add(String.valueOf(ch));
                }
            }
        }
        return letters;
    }
}
