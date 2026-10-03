package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Test;

/** 7-2 — 표 → 템플릿 모델. 복합 PK·문자열·숫자·날짜·코멘트 있음/없음(논리명)·예약어 컬럼·CLOB */
class GenModelTest {

    static final TypeMapping TYPES = TypeMapping.load();

    static Column col(String name, int ord, String type, Long len, Integer prec, Integer scale, boolean nullable, String comment) {
        return new Column(name, ord, type, null, len, prec, scale, nullable, null, comment, null);
    }

    /** V-16·7-5 도 쓰는 픽스처 표 */
    static Table empHist() {
        return new Table("HR", "TB_EMP_HIST", "TABLE", "사원 이력", List.of(
                col("EMP_NO", 1, "NUMBER", null, 6, 0, false, "사원 번호"),
                col("START_DATE", 2, "DATE", null, null, null, false, "시작일"),
                col("JOB_ID", 3, "VARCHAR2", 10L, null, null, false, "직무"),
                col("DEPT_NM", 4, "VARCHAR2", 30L, null, null, true, null),
                col("SALARY", 5, "NUMBER", null, 8, 2, true, "급여"),
                col("CLASS", 6, "VARCHAR2", 5L, null, null, true, "등급"),
                col("NOTE", 7, "CLOB", null, null, null, true, null)),
                new PrimaryKey("PK_EMP_HIST", List.of("EMP_NO", "START_DATE")), null, null, null, null, null, null);
    }

    static GenModel.Options opts(String dialect) {
        return new GenModel.Options("kr.go.hr", null, List.of("TB"), Map.of("DEPT_NM", "부서명"), dialect, Map.of("rte", "egovframework.rte"));
    }

    @Test
    void golden() {
        GenModel.Result r = GenModel.of(empHist(), opts("oracle"), TYPES);
        assertEquals(List.of(), r.warnings());
        GoldenFiles.assertJson("gen/model.json", r.model());
    }

    @Test
    void namesAndDialectFile() {
        Map<String, Object> m = GenModel.of(empHist(), opts("oracle"), TYPES).model();
        assertEquals("EmpHist", m.get("Name"), "TB 를 뗀다");
        assertEquals("emphist", m.get("module"), "module 기본값 = 이름 소문자");
        assertEquals("kr/go/hr/emphist", m.get("packagePath"));
        assertEquals("/emphist", m.get("urlBase"));
        assertEquals("postgres", GenModel.of(empHist(), opts("postgresql"), TYPES).model().get("dialectFile"));
        assertEquals("maria", GenModel.of(empHist(), opts("mariadb"), TYPES).model().get("dialectFile"));
        assertEquals("tibero", GenModel.of(empHist(), opts("tibero"), TYPES).model().get("dialectFile"));
        GenModel.Options withModule = new GenModel.Options("kr.go.hr", "emp.hist", List.of("TB"), Map.of(), "oracle", Map.of());
        assertEquals("/emp/hist", GenModel.of(empHist(), withModule, TYPES).model().get("urlBase"));
    }

    @Test
    void skipsViewsAndTablesWithoutPk() {
        Table view = new Table("HR", "EMP_V", "VIEW", null, empHist().columns(), null, null, null, null, null, null, null);
        GenModel.Result v = GenModel.of(view, opts("oracle"), TYPES);
        assertTrue(v.model().isEmpty());
        assertTrue(v.warnings().get(0).contains("뷰"), v.warnings().toString());
        Table noPk = new Table("HR", "LOG", "TABLE", null, empHist().columns(), null, null, null, null, null, null, null);
        GenModel.Result n = GenModel.of(noPk, opts("oracle"), TYPES);
        assertTrue(n.model().isEmpty());
        assertTrue(n.warnings().get(0).contains("PK 없음"), n.warnings().toString());
    }
}
