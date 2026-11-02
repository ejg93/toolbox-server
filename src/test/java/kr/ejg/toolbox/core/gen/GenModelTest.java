package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;

/** 7-2 — 표 → 템플릿 모델. 복합 PK·문자열·숫자·날짜·코멘트 있음/없음(논리명)·예약어 컬럼·CLOB */
class GenModelTest {

    static final TypeMapping TYPES = TypeMapping.load();
    static final Map<String, String> VARS = Map.of("rte", "egovframework.rte");

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
        return new GenModel.Options("kr.go.hr", null, List.of("TB"), Map.of("DEPT_NM", "부서명"), dialect);
    }

    @Test
    void golden() {
        GenModel.Result r = GenModel.of(empHist(), opts("oracle"), VARS, TYPES);
        assertEquals(List.of(), r.warnings());
        GoldenFiles.assertJson("gen/model.json", r.model());
    }

    @Test
    void namesAndDialectFile() {
        Map<String, Object> m = GenModel.of(empHist(), opts("oracle"), VARS, TYPES).model();
        assertEquals("EmpHist", m.get("Name"), "TB 를 뗀다");
        assertEquals("emphist", m.get("module"), "module 기본값 = 이름 소문자");
        assertEquals("kr/go/hr/emphist", m.get("packagePath"));
        assertEquals("/emphist", m.get("urlBase"));
        assertEquals("postgres", GenModel.of(empHist(), opts("postgresql"), VARS, TYPES).model().get("dialectFile"));
        assertEquals("maria", GenModel.of(empHist(), opts("mariadb"), VARS, TYPES).model().get("dialectFile"));
        assertEquals("tibero", GenModel.of(empHist(), opts("tibero"), VARS, TYPES).model().get("dialectFile"));
        GenModel.Options withModule = new GenModel.Options("kr.go.hr", "emp.hist", List.of("TB"), Map.of(), "oracle");
        assertEquals("/emp/hist", GenModel.of(empHist(), withModule, VARS, TYPES).model().get("urlBase"));
    }

    /** 7-15 — 표별 재정의: 클래스 이름 그대로 · 뺄 컬럼(대소문자 무시) · PK 는 안 빠지고 경고 */
    @Test
    @SuppressWarnings("unchecked")
    void classNameAndSkipColumnsOverride() {
        GenModel.Options o = new GenModel.Options("kr.go.hr", null, List.of("TB"), Map.of(), "oracle", new Profile.TableGen("History", List.of("note", "EMP_NO")), null);
        GenModel.Result r = GenModel.of(empHist(), o, VARS, TYPES);
        assertEquals(List.of("TB_EMP_HIST: PK 컬럼 EMP_NO 는 뺄 수 없다"), r.warnings());
        assertEquals("History", r.model().get("Name"));
        assertEquals("history", r.model().get("module"), "module 기본값도 재정의 이름에서");
        List<String> cols = ((List<Map<String, Object>>) r.model().get("fields")).stream().map(f -> (String) f.get("column")).toList();
        assertTrue(!cols.contains("NOTE") && cols.contains("EMP_NO") && cols.contains("SALARY"), cols.toString());
        assertEquals(GenModel.of(empHist(), opts("oracle"), VARS, TYPES).model(),
                GenModel.of(empHist(), new GenModel.Options("kr.go.hr", null, List.of("TB"), Map.of("DEPT_NM", "부서명"), "oracle", new Profile.TableGen(" ", null), null), VARS,
                        TYPES).model(), "빈 재정의는 지금과 같다");
    }

    @Test
    void skipsViewsAndTablesWithoutPk() {
        Table view = new Table("HR", "EMP_V", "VIEW", null, empHist().columns(), null, null, null, null, null, null, null);
        GenModel.Result v = GenModel.of(view, opts("oracle"), VARS, TYPES);
        assertTrue(v.model().isEmpty());
        assertTrue(v.warnings().get(0).contains("뷰"), v.warnings().toString());
        Table noPk = new Table("HR", "LOG", "TABLE", null, empHist().columns(), null, null, null, null, null, null, null);
        GenModel.Result n = GenModel.of(noPk, opts("oracle"), VARS, TYPES);
        assertTrue(n.model().isEmpty());
        assertTrue(n.warnings().get(0).contains("PK 없음"), n.warnings().toString());
    }

    /** 7-16b·7-17 — eGov 게시판 꼴 픽스처: 복합 PK · 제목 · CLOB · 여부(_AT CHAR(1)) · 날짜 · 수 · CHECK IN · BLOB · 감사 컬럼 넷 */
    static Table bbs() {
        return new Table("EGOV", "COMTNBBS", "TABLE", "게시물", List.of(
                col("NTT_ID", 1, "NUMBER", null, 20, 0, false, "게시물 ID"),
                col("BBS_ID", 2, "VARCHAR2", 20L, null, null, false, "게시판 ID"),
                col("NTT_SJ", 3, "VARCHAR2", 2000L, null, null, false, "게시물 제목"),
                col("NTT_CN", 4, "CLOB", null, null, null, true, "게시물 내용"),
                col("USE_AT", 5, "CHAR", 1L, null, null, false, "사용 여부"),
                col("NTCE_BGNDE", 6, "DATE", null, null, null, true, "게시 시작일"),
                col("RDCNT", 7, "NUMBER", null, 10, 0, true, "조회 수"),
                col("SNTNC_SE", 8, "VARCHAR2", 2L, null, null, true, "문장 구분"),
                col("ATCH_FILE", 9, "BLOB", null, null, null, true, "첨부 파일"),
                col("FRST_REGIST_PNTTM", 10, "DATE", null, null, null, false, "최초 등록 시점"),
                col("FRST_REGISTER_ID", 11, "VARCHAR2", 20L, null, null, false, "최초 등록자 ID"),
                col("LAST_UPDT_PNTTM", 12, "DATE", null, null, null, true, "최종 수정 시점"),
                col("LAST_UPDUSR_ID", 13, "VARCHAR2", 20L, null, null, true, "최종 수정자 ID")),
                new PrimaryKey("PK_COMTNBBS", List.of("NTT_ID", "BBS_ID")), null, null, null, null, null, null,
                List.of(new Check("CK_SNTNC_SE", "SNTNC_SE IN ('A','B','C')")));
    }

    static GenModel.Options bbsOpts(Profile.TableGen tg, Profile.Audit audit) {
        return new GenModel.Options("kr.go.egov", null, List.of("COMTN"), Map.of(), "oracle", tg, audit);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> field(Map<String, Object> m, String column) {
        return ((List<Map<String, Object>>) m.get("fields")).stream().filter(f -> column.equals(f.get("column"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    static List<Object> columns(Map<String, Object> m, String key) {
        return ((List<Map<String, Object>>) m.get(key)).stream().map(f -> f.get("column")).toList();
    }

    /** 역할·입력 꼴 한 줄 — html · options · list/insert/edit · query · required */
    static String role(Map<String, Object> f) {
        return f.get("html") + " " + f.get("options") + " " + f.get("list") + "/" + f.get("insert") + "/" + f.get("edit") + " " + f.get("query")
                + " " + f.get("required") + " " + f.get("audit");
    }

    @Test
    void bbsGolden() {
        GenModel.Result r = GenModel.of(bbs(), bbsOpts(null, null), VARS, TYPES);
        assertEquals(List.of(), r.warnings());
        GoldenFiles.assertJson("gen/model-bbs.json", r.model());
    }

    /** 설계 52 §3-3 기본 규칙 — 감사 컬럼·BLOB 은 화면·검색에서 빠지고, INSERT·UPDATE 는 감사 컬럼을 DB 시각 식·#{} 로 넣는다 */
    @Test
    void rolesAndInputKinds() {
        Map<String, Object> m = GenModel.of(bbs(), bbsOpts(null, null), VARS, TYPES).model();
        assertEquals("text [] true/true/true LIKE true ", role(field(m, "NTT_SJ")));
        assertEquals("textarea [] true/true/true NONE false ", role(field(m, "NTT_CN")));
        assertEquals("radio [Y, N] true/true/true EQ true ", role(field(m, "USE_AT")));
        assertEquals("datetime [] true/true/true BETWEEN false ", role(field(m, "NTCE_BGNDE")));
        assertEquals("number [] true/true/true NONE false ", role(field(m, "RDCNT")));
        assertEquals("select [A, B, C] true/true/true EQ false ", role(field(m, "SNTNC_SE")));
        assertEquals(" [] false/false/false NONE false ", role(field(m, "ATCH_FILE")));
        assertEquals("number [] true/true/false NONE true ", role(field(m, "NTT_ID")), "PK 는 수정·검색 없음, 등록은 사용자가 친다");
        assertEquals("datetime [] false/false/false NONE true created", role(field(m, "FRST_REGIST_PNTTM")));
        assertEquals("text [] false/false/false NONE true creator", role(field(m, "FRST_REGISTER_ID")));
        assertEquals("datetime [] false/false/false NONE false updated", role(field(m, "LAST_UPDT_PNTTM")));
        assertEquals("text [] false/false/false NONE false updater", role(field(m, "LAST_UPDUSR_ID")));
        assertEquals(List.of(true, false), List.of(field(m, "FRST_REGIST_PNTTM").get("now"), field(m, "FRST_REGISTER_ID").get("now")));
        assertEquals(List.of(true, false), List.of(field(m, "NTT_ID").get("step"), field(m, "RDCNT").get("step")), "BigDecimal 만 step");
        assertEquals(List.of("NTT_ID", "BBS_ID", "NTT_SJ", "NTT_CN", "USE_AT", "NTCE_BGNDE", "RDCNT", "SNTNC_SE", "FRST_REGIST_PNTTM",
                "FRST_REGISTER_ID"), columns(m, "insertCols"), "NULL 허용 LAST_* 는 INSERT 에 없다");
        assertEquals(List.of("NTT_SJ", "NTT_CN", "USE_AT", "NTCE_BGNDE", "RDCNT", "SNTNC_SE", "LAST_UPDT_PNTTM", "LAST_UPDUSR_ID"),
                columns(m, "updateCols"));
        assertEquals(List.of("NTT_SJ", "USE_AT", "NTCE_BGNDE", "SNTNC_SE"), columns(m, "search"));
        assertEquals(columns(m, "insertFields").subList(2, 8), columns(m, "editFields"));
        assertEquals("SYSDATE", m.get("now"));
        assertEquals(List.of(true, true, false), List.of(field(m, "FRST_REGISTER_ID").get("user"), field(m, "LAST_UPDUSR_ID").get("user"),
                field(m, "FRST_REGIST_PNTTM").get("user")), "7-20b 사람 감사 컬럼");
        assertEquals(List.of("FRST_REGISTER_ID"), columns(m, "insertUsers"), "NULL 허용 LAST_UPDUSR_ID 는 INSERT 에 없다");
        assertEquals(List.of("LAST_UPDUSR_ID"), columns(m, "updateUsers"));
        assertEquals(List.of("loginVO", "uniqId"), List.of(m.get("sessionKey"), m.get("sessionProp")));
        assertEquals("NOW()", GenModel.of(bbs(), new GenModel.Options("kr.go.egov", null, List.of(), Map.of(), "mariadb"), VARS, TYPES).model().get("now"));
    }

    /** 프로필 generator.tables.<표>.columns — 적은 칸만 덮고, 모르는 html·query 값은 기본값 + 경고 */
    @Test
    void columnOverride() {
        Profile.TableGen tg = new Profile.TableGen(null, null, Map.of(
                "ntt_cn", new Profile.ColumnGen(true, null, null, "like", "TEXT", null),
                "RDCNT", new Profile.ColumnGen(null, null, null, "EQ", null, null),
                "NTT_SJ", new Profile.ColumnGen(null, null, null, "bogus", "wiki", null)));
        GenModel.Result r = GenModel.of(bbs(), bbsOpts(tg, null), VARS, TYPES);
        assertEquals(List.of("COMTNBBS.NTT_SJ: 모르는 html 값 wiki — 기본값", "COMTNBBS.NTT_SJ: 모르는 query 값 bogus — 기본값"), r.warnings());
        assertEquals("text [] true/true/true LIKE false ", role(field(r.model(), "NTT_CN")));
        assertEquals("number [] true/true/true EQ false ", role(field(r.model(), "RDCNT")));
        assertEquals("text [] true/true/true LIKE true ", role(field(r.model(), "NTT_SJ")));
    }

    /** 프로필 generator.audit — 적은 묶음만 감사 컬럼, eGov 넷은 보통 컬럼이 된다. 표에 없는 이름은 조용히 */
    @Test
    void auditOverride() {
        GenModel.Result r = GenModel.of(bbs(), bbsOpts(null, new Profile.Audit(List.of("REG_DT"), null, null, null)), VARS, TYPES);
        assertEquals(List.of(), r.warnings());
        assertEquals("datetime [] true/true/true BETWEEN true ", role(field(r.model(), "FRST_REGIST_PNTTM")));
        assertEquals("text [] true/true/true LIKE false ", role(field(r.model(), "LAST_UPDUSR_ID")));
        assertEquals(List.of(), columns(r.model(), "insertCols").stream().filter(c -> !columns(r.model(), "insertFields").contains(c)).toList());
        assertEquals(List.of(List.of(), List.of()), List.of(columns(r.model(), "insertUsers"), columns(r.model(), "updateUsers")));
    }

    /** 표 하나 — 첫 컬럼 ID(NUMBER) 가 PK, 나머지는 주어진 컬럼(번호는 2부터) */
    static Table one(Column... cols) {
        List<Column> all = new java.util.ArrayList<>();
        all.add(col("ID", 1, "NUMBER", null, 10, 0, false, "ID"));
        all.addAll(List.of(cols));
        return new Table("T", "TB_ONE", "TABLE", null, all, new PrimaryKey("PK_ONE", List.of("ID")), null, null, null, null, null, null);
    }

    static String htmlQuery(Map<String, Object> m, String column) {
        Map<String, Object> f = field(m, column);
        return f.get("html") + " " + f.get("query");
    }

    /** 7-18a 음성 — 문턱 아래 VARCHAR 는 text·LIKE, 여부 규칙은 CHAR(1) + _AT 셋 다 맞아야 radio */
    @Test
    void negatives() {
        Map<String, Object> m = GenModel.of(one(col("MEMO", 2, "VARCHAR2", 999L, null, null, true, "메모"),
                col("SE_CD", 3, "CHAR", 1L, null, null, true, "구분"), col("USE_AT", 4, "VARCHAR2", 1L, null, null, true, "사용"),
                col("DEL_AT", 5, "CHAR", 2L, null, null, true, "삭제")), opts("oracle"), VARS, TYPES).model();
        assertEquals("text LIKE", htmlQuery(m, "MEMO"));
        assertEquals("text LIKE", htmlQuery(m, "SE_CD"), "이름이 _AT 아님");
        assertEquals("text LIKE", htmlQuery(m, "USE_AT"), "nativeType 이 CHAR 아님");
        assertEquals("text LIKE", htmlQuery(m, "DEL_AT"), "길이 1 아님");
    }

    /** 7-18a — insert·edit 재정의가 있으면 감사 시각도 사용자 값(#{…}), PK 는 edit 재정의에도 수정 칸이 아니다 */
    @Test
    void overrideKeepsUserValue() {
        Profile.TableGen tg = new Profile.TableGen(null, null, Map.of(
                "FRST_REGIST_PNTTM", new Profile.ColumnGen(null, true, null, null, null, null),
                "NTT_ID", new Profile.ColumnGen(null, null, true, null, null, null),
                "FRST_REGISTER_ID", new Profile.ColumnGen(null, true, null, null, null, null)));
        GenModel.Result r = GenModel.of(bbs(), bbsOpts(tg, null), VARS, TYPES);
        assertEquals(List.of(), r.warnings());
        Map<String, Object> stamp = field(r.model(), "FRST_REGIST_PNTTM");
        assertEquals(List.of(false, true), List.of(stamp.get("now"), stamp.get("insert")));
        assertTrue(columns(r.model(), "insertCols").contains("FRST_REGIST_PNTTM"));
        assertEquals(false, field(r.model(), "NTT_ID").get("edit"));
        assertTrue(!columns(r.model(), "editFields").contains("NTT_ID") && !columns(r.model(), "updateCols").contains("NTT_ID"),
                columns(r.model(), "updateCols").toString());
        assertEquals(false, field(r.model(), "FRST_REGISTER_ID").get("user"));
        assertEquals(List.of(), columns(r.model(), "insertUsers"), "재정의한 사람 컬럼은 사용자 값");
    }

    /** 7-20b — userSession 「속성[.프로퍼티]」 을 첫 점에서 가른다. 빈 글은 둘 다 빈 글(Principal 만) */
    @Test
    void userSessionShapes() {
        Map<String, Object> empty = GenModel.of(bbs(), bbsOpts(null, new Profile.Audit(null, null, null, null, "")), VARS, TYPES).model();
        assertEquals(List.of("", ""), List.of(empty.get("sessionKey"), empty.get("sessionProp")));
        Map<String, Object> key = GenModel.of(bbs(), bbsOpts(null, new Profile.Audit(null, null, null, null, "userId")), VARS, TYPES).model();
        assertEquals(List.of("userId", ""), List.of(key.get("sessionKey"), key.get("sessionProp")));
    }

    /** 7-19 — 문턱 1,000 유지. 길이 있는 textarea 도 LIKE, CLOB 은 검색 없음. 제목·이름 접미(_SJ·_NM·_TITLE·_NAME)는 길이 무관 text·LIKE */
    @Test
    void textareaBoundary() {
        Map<String, Object> m = GenModel.of(one(col("A999", 2, "VARCHAR2", 999L, null, null, true, "a"),
                col("B1000", 3, "VARCHAR2", 1000L, null, null, true, "b"), col("C2000", 4, "VARCHAR2", 2000L, null, null, true, "c"),
                col("D4000", 5, "VARCHAR2", 4000L, null, null, true, "d"), col("E_CN", 6, "CLOB", null, null, null, true, "e"),
                col("F_SJ", 7, "VARCHAR2", 2000L, null, null, true, "f"), col("G_NM", 8, "VARCHAR2", 1000L, null, null, true, "g"),
                col("H_TITLE", 9, "VARCHAR2", 4000L, null, null, true, "h"), col("I_NAME", 10, "CLOB", null, null, null, true, "i"),
                col("J_SJ", 11, "CHAR", 1L, null, null, true, "j")), opts("oracle"), VARS, TYPES).model();
        String textLike = "text LIKE";
        String areaLike = "textarea LIKE";
        assertEquals(List.of(textLike, areaLike, areaLike, areaLike, "textarea NONE", textLike, textLike, textLike, textLike, textLike),
                List.of(htmlQuery(m, "A999"), htmlQuery(m, "B1000"), htmlQuery(m, "C2000"), htmlQuery(m, "D4000"), htmlQuery(m, "E_CN"),
                        htmlQuery(m, "F_SJ"), htmlQuery(m, "G_NM"), htmlQuery(m, "H_TITLE"), htmlQuery(m, "I_NAME"), htmlQuery(m, "J_SJ")));
    }

    /** 7-10 — 모르는 방언은 거절(경로 식에 박히지 않게) */
    @Test
    void unknownDialectRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> GenModel.of(empHist(), opts("db2"), VARS, TYPES));
    }
}
