package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.check.Source;
import org.junit.jupiter.api.Test;

/**
 * 6-3 — Java 호출 그래프. 픽스처: 컨트롤러 둘(같은 URL·params 둘·클래스 머리)·서비스 인터페이스·구현 둘(@Service 이름으로 가름·@Autowired 는 모호)·
 * 프레임워크 타입 필드·DAO(리터럴·접두·변수·상수·식)·추상 DAO·Mapper 인터페이스·SqlSessionTemplate·뷰 꼴·javadoc 꼴·순환 호출·깨진 파일
 */
class JavaGraphTest {

    static final String STATEMENT = "statement";
    static final String LEGACY_LIST = "legacy.code.list";
    static final String LIST = "list";
    static final String CODE_SERVICE = "CodeService";
    static final String USER_CTL = "web/UserController.java";

    static final String SPRING_IMPORTS = "import org.springframework.stereotype.*;\nimport org.springframework.beans.factory.annotation.Autowired;\nimport org.springframework.web.bind.annotation.*;\n";

    static final String FOO_FIND = "foo.find";

    static final String PUB = "    public ";

    static final String CODE_DO = "/code.do";

    static final String VIEW = "view";

    static final String PKG_WEB = "package web;\n";
    static final String PKG_SVC = "package svc;\n";
    static final String DEPT_JAVA = "web/DeptController.java";

    static final Path DIR = Path.of("src/test/resources/fixtures/analyze/java");

    static List<Source> sources() throws IOException {
        return sources(DIR, "");
    }

    /** 폴더의 파일 전부 — 상대 경로 앞에 prefix */
    static List<Source> sources(Path dir, String prefix) throws IOException {
        List<Source> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                String rel = prefix + dir.relativize(p).toString().replace('\\', '/');
                out.add(new Source(rel, Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"), null, null, null));
            }
        }
        return out;
    }

    @Test
    void golden() throws IOException {
        GoldenFiles.assertJson("analyze/java-graph.json", JavaGraph.scan(sources(), null));
    }

    /** 6-15 — JPA 픽스처: 저장소 파생·상속 메서드 → jpa 문장, 커스텀 구현 본문 먼저, EntityManager → 「클래스.메서드#em」, 미해결 namedQuery·criteria·jpaType */
    @Test
    void jpaGolden() throws IOException {
        List<Source> src = JpaIndexTest.sources();
        JpaIndex jpa = JpaIndex.scan(src);
        JavaGraph.Graph g = JavaGraph.scan(src, null, jpa);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("graph", g);
        java.util.Map<String, Object> recorded = new java.util.TreeMap<>();
        g.programs().forEach(p -> p.statements().forEach(st -> jpa.recorded(st.id()).ifPresent(refs -> {
            java.util.Map<String, String> letters = new java.util.TreeMap<>();
            refs.forEach(r -> letters.put(r.table(), r.letters()));
            recorded.put(st.id(), letters);
        })));
        out.put("recorded", recorded);
        GoldenFiles.assertJson("analyze/jpa-graph.json", out);
    }

    /**
     * 6-16 — QueryDSL 픽스처(엔티티는 jpa 폴더 것): selectFrom·join(경로, Q)·update(지역 변수)·delete(static import)+서브쿼리 R·모르는 Q(querydsl)·
     * new QX("별칭")·leftJoin·QuerydslRepositorySupport 의 이름 없는 from·delete(MyBatis statement 미해결이 안 난다). 골든엔 qdsl 폴더 것만
     */
    @Test
    void qdslGolden() throws IOException {
        List<Source> src = new ArrayList<>(JpaIndexTest.sources());
        src.addAll(sources(Path.of("src/test/resources/fixtures/analyze/qdsl"), "qdsl/"));
        JpaIndex jpa = JpaIndex.scan(src);
        JavaGraph.Graph g = JavaGraph.scan(src, null, jpa);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("programs", g.programs().stream().filter(p -> p.file().startsWith("qdsl/")).toList());
        out.put("unresolved", g.unresolved().stream().filter(u -> u.file().startsWith("qdsl/")).toList());
        java.util.Map<String, Object> recorded = new java.util.TreeMap<>();
        g.programs().forEach(p -> p.statements().forEach(st -> jpa.recorded(st.id()).filter(r -> st.id().endsWith("#qdsl")).ifPresent(refs -> {
            java.util.Map<String, String> letters = new java.util.TreeMap<>();
            refs.forEach(r -> letters.put(r.table(), r.letters()));
            recorded.put(st.id(), letters);
        })));
        out.put("recorded", recorded);
        GoldenFiles.assertJson("analyze/qdsl-graph.json", out);
    }

    /**
     * 6-35 — JDBC 픽스처: JdbcClient 리터럴(이름 바인딩)·텍스트 블록 INSERT·같은 클래스 상수 연결·JdbcTemplate(this.x·람다 인자)·
     * NamedParameterJdbcTemplate · 이어 붙인 동적 SQL(jdbc 미해결) · RestTemplate.execute(타입 문턱 — 안 잡음). JPA 색인은 비어 있다
     */
    @Test
    void jdbcGolden() throws IOException {
        List<Source> src = sources(Path.of("src/test/resources/fixtures/analyze/jdbc"), "jdbc/");
        JpaIndex jpa = JpaIndex.scan(src);
        JavaGraph.Graph g = JavaGraph.scan(src, null, jpa);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("programs", g.programs());
        out.put("unresolved", g.unresolved());
        java.util.Map<String, Object> recorded = new java.util.TreeMap<>();
        g.programs().forEach(p -> p.statements().forEach(st -> jpa.recorded(st.id()).ifPresent(refs -> {
            java.util.Map<String, String> letters = new java.util.TreeMap<>();
            refs.forEach(r -> letters.put(r.table(), r.letters()));
            recorded.put(st.id(), letters);
        })));
        out.put("recorded", recorded);
        assertTrue(jpa.isEmpty(), "엔티티·저장소가 없어도 JDBC 싱크는 돈다");
        GoldenFiles.assertJson("analyze/jdbc-graph.json", out);
    }

    /**
     * 6-37a(설계 46) — 제네릭 조상 서비스 {@code CrudService<D, T>} 의 필드 {@code D dao} 를 하위 클래스의 타입 인자로 묶는다: 조상 메서드(get·save)·
     * 하위 클래스 자기 메서드(hit)·두 단 조상(TreeService → CrudService)·한 프로그램에서 서로 다른 묶음 둘(both — visited 키)·namespace 밖 DAO(PlainDao — 0).
     * DAO 이름은 기본 이름 규칙({@code (DAO|Mapper)$})에 안 맞는다 — 매퍼 인터페이스는 매퍼 XML namespace 로 안다(6-37b)
     */
    @Test
    void genericGolden() throws IOException {
        JavaGraph.Graph g = JavaGraph.scan(sources(Path.of("src/test/resources/fixtures/analyze/generic"), "generic/"), null, JpaIndex.empty(), null,
                java.util.Set.of("kr.ejg.fx.generic.dao.ArticleDao", "kr.ejg.fx.generic.dao.OfficeDao"));
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("graph", g);
        GoldenFiles.assertJson("analyze/generic-graph.json", out);
        assertTrue(g.unresolved().stream().noneMatch(u -> u.kind().equals("ambiguous")), "묶음이 모호를 만들지 않는다");
    }

    /** 6-25 — tick 이 던지면 그래프가 바로 멈춘다(두 번째 파일에서). 예외는 그대로 올라온다 */
    @Test
    void scanStopsOnTick() throws IOException {
        int[] n = {0};
        assertThrows(IllegalStateException.class, () -> JavaGraph.scan(sources(), null, JpaIndex.empty(), () -> {
            if (++n[0] > 1) {
                throw new IllegalStateException("tick");
            }
        }));
        assertEquals(2, n[0]);
        assertEquals(JavaGraph.scan(sources(), null).programs().size(),
                JavaGraph.scan(sources(), null, JpaIndex.empty(), () -> { }).programs().size(), "tick 이 안 던지면 같다");
    }

    /** 6-31 — 뷰 이름 꼴이 아닌 글(URL 조각)은 view 로 안 잡고 미해결 viewShape. json(@ResponseBody) 프로그램은 view 가 없고 viewShape 도 안 낸다 */
    @Test
    void viewShapeIsNotAView() {
        String java = String.join("\n",
                "package web;",
                "import org.springframework.stereotype.Controller;",
                "import org.springframework.web.bind.annotation.*;",
                "@Controller",
                "public class Q {",
                "    @RequestMapping(\"/q/frag.do\")",
                "    public String frag(String id) { return \"&qestnrId=\" + id; }",
                "    @RequestMapping(\"/q/err.do\")",
                "    @ResponseBody",
                "    public String err() { return \"{\\\"error\\\":1}\"; }",
                "    @RequestMapping(\"/q/ok.do\")",
                "    public String ok() { return \"q/list\"; }",
                "}");
        JavaGraph.Graph g = JavaGraph.scan(List.of(new Source("web/Q.java", java, null, null, null)), null);
        java.util.Map<String, JavaGraph.Program> byUrl = new java.util.TreeMap<>();
        g.programs().forEach(p -> byUrl.put(p.url(), p));
        assertEquals(List.of(), byUrl.get("/q/frag.do").views(), "URL 조각은 view 가 아니다");
        assertEquals(VIEW, byUrl.get("/q/frag.do").kind());
        assertEquals(List.of(), byUrl.get("/q/err.do").views(), "json 프로그램은 view 없음");
        assertEquals("json", byUrl.get("/q/err.do").kind());
        assertEquals(List.of(new JavaGraph.View(VIEW, "q/list")), byUrl.get("/q/ok.do").views());
        assertEquals(List.of("Q.frag"), g.unresolved().stream().filter(u -> u.kind().equals("viewShape")).map(Unresolved::detail).toList(),
                "viewShape 는 frag 하나 — json 인 err 는 안 낸다");
    }

    /** 0-92b(설계 69 §3-9) — 파일 하나의 스택 넘침은 미해결 parse 로 적고 그 파일만 건너뛴다. 분석 전체가 실패하지 않는다 */
    @Test
    void deepFileIsSkippedNotFatal() {
        String ctl = String.join("\n",
                "package web;",
                "import org.springframework.stereotype.Controller;",
                "import org.springframework.web.bind.annotation.*;",
                "@Controller",
                "public class Ok {",
                "    @RequestMapping(\"/ok.do\")",
                "    public String ok() { return \"ok/list\"; }",
                "}");
        String deep = "class Deep { int x = " + "(".repeat(6000) + "1" + ")".repeat(6000) + "; }\n";
        JavaGraph.Graph g = JavaGraph.scan(List.of(new Source("web/Ok.java", ctl, null, null, null), new Source("Deep.java", deep, null, null, null)), null);
        assertEquals(1, g.programs().size());
        assertTrue(g.unresolved().stream().anyMatch(u -> u.kind().equals("parse") && u.file().equals("Deep.java")), g.unresolved().toString());
    }

    /** 6-40(설계 69 §3-5) — DAO 싱크 첫 인자가 SQL 글(공백·연산자)이면 문장 id·미해결 detail 로 안 담는다(규칙 3). 미해결 statement 하나 */
    /** 6-42(설계 70 §3-15) — Stmt 생성자가 id 꼴을 잰다. 예외 글에 본문을 싣지 않는다(규칙 3) */
    @Test
    void stmtRejectsSqlText() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new JavaGraph.Stmt("UPDATE TB_X SET A = 1", "literal"));
        assertFalse(e.getMessage().contains("UPDATE"), e.getMessage());
        for (String ok : List.of("a.b.Dao.find#jdbc", "ns.selectList", "com.x.Repo.findById")) {
            assertEquals(ok, new JavaGraph.Stmt(ok, "jdbc").id());
        }
        for (String bad : List.of("a#b", "")) {
            assertThrows(IllegalArgumentException.class, () -> new JavaGraph.Stmt(bad, "literal"), bad);
        }
    }

    @Test
    void sqlTextIsNotAStatementId() {
        String ctl = String.join("\n",
                "package web;",
                "import org.springframework.stereotype.Controller;",
                "import org.springframework.web.bind.annotation.*;",
                "import dao.LegacyDao;",
                "@Controller",
                "public class L {",
                "    private LegacyDao dao;",
                "    @RequestMapping(\"/l/save.do\")",
                "    public String save() { dao.save(); return \"l/ok\"; }",
                "}");
        String dao = String.join("\n",
                "package dao;",
                "import org.springframework.stereotype.Repository;",
                "@Repository",
                "public class LegacyDao {",
                "    public void save() {",
                "        update(\"UPDATE TB_X SET NM = 'ZZLIT9' WHERE ID = 1\");",
                "        int n = 1;",
                "        delete(\"DELETE FROM TB_X WHERE ID = \" + n);",
                "        insert(\"Legacy.insertOne\");",
                "    }",
                "}");
        JavaGraph.Graph g = JavaGraph.scan(List.of(new Source("web/L.java", ctl, null, null, null),
                new Source("dao/LegacyDao.java", dao, null, null, null)), null);
        assertEquals(List.of("Legacy.insertOne"), g.programs().get(0).statements().stream().map(JavaGraph.Stmt::id).toList());
        String all = g.programs().toString() + g.unresolved() + g.daoStatements();
        assertTrue(!all.contains("ZZLIT9") && !all.contains("TB_X"), all);
        assertTrue(g.unresolved().stream().anyMatch(u -> u.kind().equals(STATEMENT) && u.detail().equals("LegacyDao.save")),
                g.unresolved().toString());
    }

    /** 6-43 ③ — 문장 id 꼴이 아닌 드문 식별자(find€)는 담지 않고 statement 미해결 하나 — 예외로 멈추지 않는다 */
    @Test
    void oddIdentifierIsNotedNotFatal() {
        String ctl = "package x;\nimport org.springframework.stereotype.Controller;\nimport org.springframework.web.bind.annotation.GetMapping;\n"
                + "@Controller\npublic class OddController {\n    private OddMapper m;\n\n    @GetMapping(\"/odd\")\n"
                + "    public String odd() {\n        m.find\u20ac();\n        m.list();\n        return \"v\";\n    }\n}\n";
        String mapper = "package x;\nimport org.apache.ibatis.annotations.Mapper;\n@Mapper\npublic interface OddMapper {\n    Object find\u20ac();\n\n    Object list();\n}\n";
        JavaGraph.Graph g = JavaGraph.scan(List.of(new Source("x/OddController.java", ctl, null, null, null),
                new Source("x/OddMapper.java", mapper, null, null, null)), null);
        assertEquals(1, g.unresolved().stream().filter(u -> u.kind().equals(STATEMENT)).count(), g.unresolved().toString());
        assertTrue(g.programs().stream().anyMatch(p -> p.statements().stream().anyMatch(st -> st.id().equals("x.OddMapper.list"))), g.programs().toString());
    }

    /** R-C-I6 — 다른 모듈의 같은 이름 인터페이스 구현이 섞이지 않는다(구현 색인 열쇠가 FQCN) */
    @Test
    void sameNameServicesInTwoModules() {
        List<Source> in = new ArrayList<>();
        for (String m : List.of("a", "b")) {
            String pkg = "package app." + m + ";\nimport org.springframework.stereotype.*;\nimport org.springframework.beans.factory.annotation.Autowired;\n"
                    + "import org.springframework.web.bind.annotation.*;\n";
            in.add(new Source(m + "/UserController.java", pkg + "@Controller\npublic class UserController {\n    @Autowired UserService userService;\n"
                    + "    @RequestMapping(\"/" + m + "/user/save.do\")\n    public String save() { userService.save(); return \"x\"; }\n}\n", null, null, null));
            in.add(new Source(m + "/UserService.java", pkg + "public interface UserService {\n    void save();\n}\n", null, null, null));
            in.add(new Source(m + "/UserServiceImpl.java", pkg + "@Service\npublic class UserServiceImpl implements UserService {\n"
                    + "    @Autowired UserDAO dao;\n    public void save() { dao.save(); }\n}\n", null, null, null));
            in.add(new Source(m + "/UserDAO.java", pkg + "@Repository\npublic class UserDAO {\n    public void save() { insert(\""
                    + m.toUpperCase(java.util.Locale.ROOT) + ".insertUser\"); }\n}\n", null, null, null));
        }
        JavaGraph.Graph g = JavaGraph.scan(in, null);
        assertEquals(List.of("A.insertUser"), stmts(g, "/a/user/save.do"));
        assertFalse(g.unresolved().stream().anyMatch(u -> u.kind().equals("ambiguous")), g.unresolved().toString());
    }

    /** R-C-I7 ①② · R-C-I8 — this.필드 · 지역 SqlSession · iBATIS SqlMapClient 위 문장 실행 */
    @Test
    void sessionCallShapes() {
        String ctl = "package p;\nimport org.springframework.stereotype.Controller;\nimport org.springframework.beans.factory.annotation.Autowired;\n"
                + "import org.springframework.web.bind.annotation.*;\n@Controller\npublic class ShapeController {\n    @Autowired ShapeDAO dao;\n"
                + "    @RequestMapping(\"/s/a.do\")\n    public String a() { dao.a(); return \"x\"; }\n"
                + "    @RequestMapping(\"/s/b.do\")\n    public String b() { dao.b(null); return \"x\"; }\n"
                + "    @RequestMapping(\"/s/c.do\")\n    public String c() throws Exception { dao.c(); return \"x\"; }\n}\n";
        String dao = "package p; public class ShapeDAO { private org.mybatis.spring.SqlSessionTemplate sqlSession; private com.ibatis.sqlmap.client.SqlMapClient client;\n"
                + "  public Object a() { return this.sqlSession.selectList(\"S.listA\"); }\n"
                + "  public Object b(org.apache.ibatis.session.SqlSessionFactory f) { org.apache.ibatis.session.SqlSession s = f.openSession(); return s.selectOne(\"S.oneB\"); }\n"
                + "  public Object c() throws Exception { return client.queryForList(\"S.listC\", null); } }\n";
        JavaGraph.Graph g = JavaGraph.scan(List.of(new Source("p/ShapeController.java", ctl, null, null, null), new Source("p/ShapeDAO.java", dao, null, null, null)), null);
        assertEquals(List.of("S.listA"), stmts(g, "/s/a.do"));
        assertEquals(List.of("S.oneB"), stmts(g, "/s/b.do"));
        assertEquals(List.of("S.listC"), stmts(g, "/s/c.do"));
    }

    /** R-C-I7 ③ — 조상이 선언한 매핑 메서드도 하위 컨트롤러의 프로그램. 하위가 같은 이름·인자 수로 덮으면 조상 것은 안 나온다 */
    @Test
    void inheritedMappingIsAProgram() {
        String imp = "import org.springframework.stereotype.*;\nimport org.springframework.beans.factory.annotation.Autowired;\nimport org.springframework.web.bind.annotation.*;\nimport dao.InhDAO;\n";
        Source base = new Source("web/BaseController.java", PKG_WEB + imp + "public abstract class BaseController {\n    @Autowired InhDAO dao;\n"
                + "    @GetMapping(\"/list.do\")\n    public String list() { dao.list(); return \"dept/list\"; }\n}\n", null, null, null);
        Source dao = new Source("dao/InhDAO.java", "package dao;\nimport org.springframework.stereotype.Repository;\n@Repository\npublic class InhDAO {\n"
                + "    public void list() { selectList(\"I.list\"); }\n    public void own() { selectList(\"I.own\"); }\n}\n", null, null, null);
        Source dept = new Source(DEPT_JAVA, PKG_WEB + imp + "@Controller\n@RequestMapping(\"/dept\")\npublic class DeptController extends BaseController {\n}\n",
                null, null, null);
        JavaGraph.Graph g = JavaGraph.scan(List.of(base, dao, dept), null);
        assertEquals(1, g.programs().size(), g.programs().toString());
        JavaGraph.Program p = g.programs().get(0);
        assertEquals(List.of("DeptController", "/dept/list.do", "web/BaseController.java"), List.of(p.className(), p.url(), p.file()));
        assertEquals(List.of("I.list"), stmts(g, "/dept/list.do"));
        Source own = new Source(DEPT_JAVA, PKG_WEB + imp + "@Controller\n@RequestMapping(\"/dept\")\npublic class DeptController extends BaseController {\n"
                + "    @GetMapping(\"/list.do\")\n    public String list() { dao.own(); return \"dept/list\"; }\n}\n", null, null, null);
        JavaGraph.Graph g2 = JavaGraph.scan(List.of(base, dao, own), null);
        assertEquals(List.of(DEPT_JAVA), g2.programs().stream().map(JavaGraph.Program::file).toList());
        assertEquals(List.of("I.own"), stmts(g2, "/dept/list.do"));
        // V-67a — 같은 클래스의 오버로드(이름·인자 수 같은 GET 화면 · POST 저장)는 둘 다 프로그램(조상 재정의만 하나로)
        Source both = new Source(DEPT_JAVA, PKG_WEB + imp + "@Controller\n@RequestMapping(\"/dept\")\npublic class DeptController extends BaseController {\n"
                + "    @GetMapping(\"/update/{id}\")\n    public String update(Model m, int id) { return \"dept/form\"; }\n"
                + "    @PostMapping(\"/update/{id}\")\n    public String update(int id, String nm) { dao.own(); return \"redirect:/dept\"; }\n}\n", null, null, null);
        JavaGraph.Graph g3 = JavaGraph.scan(List.of(base, dao, both), null);
        assertEquals(List.of("BaseController.list", "DeptController.update", "DeptController.update"),
                g3.programs().stream().map(x -> (x.file().contains("Base") ? "BaseController" : x.className()) + "." + x.method()).sorted().toList());
    }

    /** R-C-I7 ④ — 조상의 템플릿 메서드 안 맨 호출은 불린 객체(하위 클래스)의 재정의로 간다 */
    @Test
    void templateMethodReachesOverride() {
        String imp = SPRING_IMPORTS;
        JavaGraph.Graph g = JavaGraph.scan(List.of(
                new Source("svc/Tmpl.java", "package svc;\npublic abstract class Tmpl {\n    public void run() { step(); }\n    protected abstract void step();\n}\n", null, null, null),
                new Source("svc/TmplImpl.java", PKG_SVC + imp + "import dao.TmDAO;\n@Service\npublic class TmplImpl extends Tmpl {\n    @Autowired TmDAO dao;\n"
                        + "    protected void step() { dao.step(); }\n}\n", null, null, null),
                new Source("web/TmController.java", PKG_WEB + imp + "import svc.TmplImpl;\n@Controller\npublic class TmController {\n    @Autowired TmplImpl tm;\n"
                        + "    @RequestMapping(\"/tm/run.do\")\n    public String run() { tm.run(); return \"x\"; }\n}\n", null, null, null),
                new Source("dao/TmDAO.java", "package dao;\nimport org.springframework.stereotype.Repository;\n@Repository\npublic class TmDAO {\n"
                        + "    public void step() { update(\"T.step\"); }\n}\n", null, null, null)), null);
        assertEquals(List.of("T.step"), stmts(g, "/tm/run.do"));
        // 마무리 리뷰 I2 — 하위 클래스의 인자 수 다른 같은 이름(오버로드)은 조상의 인자 수 같은 메서드를 가리지 않는다
        JavaGraph.Graph g2 = JavaGraph.scan(List.of(
                new Source("svc/Tmpl.java", PKG_SVC + imp + "import dao.TmDAO;\npublic abstract class Tmpl {\n    @Autowired TmDAO dao;\n"
                        + "    public void run() { step(\"x\"); }\n    protected void step(String s) { dao.step(); }\n}\n", null, null, null),
                new Source("svc/TmplImpl.java", PKG_SVC + imp + "@Service\npublic class TmplImpl extends Tmpl {\n"
                        + "    public void step(String s, int n) { }\n}\n", null, null, null),
                new Source("web/TmController.java", PKG_WEB + imp + "import svc.TmplImpl;\n@Controller\npublic class TmController {\n    @Autowired TmplImpl tm;\n"
                        + "    @RequestMapping(\"/tm/run.do\")\n    public String run() { tm.run(); return \"x\"; }\n}\n", null, null, null),
                new Source("dao/TmDAO.java", "package dao;\nimport org.springframework.stereotype.Repository;\n@Repository\npublic class TmDAO {\n"
                        + "    public void step() { update(\"T.step\"); }\n}\n", null, null, null)), null);
        assertEquals(List.of("T.step"), stmts(g2, "/tm/run.do"));
    }

    /** R-C-I7 ⑤ — 필드 선언의 타입 인자(GenSvc&lt;GenDAO&gt;)가 그 형식 안 타입 변수 필드(D dao)를 푼다 */
    @Test
    void fieldTypeArgumentBindsTheDao() {
        String imp = SPRING_IMPORTS;
        JavaGraph.Graph g = JavaGraph.scan(List.of(
                new Source("svc/GenSvc.java", PKG_SVC + imp + "import dao.GenBase;\n@Service\npublic class GenSvc<D extends GenBase> {\n    @Autowired protected D dao;\n"
                        + "    public void save() { dao.save(); }\n}\n", null, null, null),
                new Source("dao/GenBase.java", "package dao;\npublic abstract class GenBase {\n}\n", null, null, null),
                new Source("dao/GenDAO.java", "package dao;\nimport org.springframework.stereotype.Repository;\n@Repository\npublic class GenDAO extends GenBase {\n"
                        + "    public void save() { insert(\"G.save\"); }\n}\n", null, null, null),
                new Source("web/GenController.java", PKG_WEB + imp + "import svc.GenSvc;\nimport dao.GenDAO;\n@Controller\npublic class GenController {\n"
                        + "    @Autowired GenSvc<GenDAO> svc;\n    @RequestMapping(\"/tm/gen.do\")\n    public String gen() { svc.save(); return \"x\"; }\n}\n", null, null, null)), null);
        assertEquals(List.of("G.save"), stmts(g, "/tm/gen.do"));
    }

    /** url 프로그램의 문장 id 차례 */
    /** 6-92(C2-14·C2-15) — 완전 이름 ModelAndView 도 화면 · 이름 빈 redirect 는 뷰가 아니다(viewDynamic) */
    @Test
    void qualifiedModelAndViewAndBareRedirect() {
        String imp = "import org.springframework.stereotype.*;\nimport org.springframework.web.bind.annotation.*;\n";
        JavaGraph.Graph g = JavaGraph.scan(List.of(new Source("web/VController.java", PKG_WEB + imp + "@Controller\npublic class VController {\n"
                + "    @RequestMapping(\"/a/list.do\")\n    public org.springframework.web.servlet.ModelAndView list() {\n"
                + "        return new org.springframework.web.servlet.ModelAndView(\"a/list\");\n    }\n"
                + "    @RequestMapping(\"/a/go.do\")\n    public String go(String next) {\n        return \"redirect:\" + next;\n    }\n"
                + "    @RequestMapping(\"/a/back.do\")\n    public String back() {\n        return \"redirect:/a.do\";\n    }\n}\n", null, null, null)), null);
        java.util.Map<String, JavaGraph.Program> by = new java.util.TreeMap<>();
        g.programs().forEach(p -> by.put(p.url(), p));
        assertEquals(VIEW, by.get("/a/list.do").kind());
        assertEquals(List.of(new JavaGraph.View(VIEW, "a/list")), by.get("/a/list.do").views());
        assertEquals(List.of(), by.get("/a/go.do").views());
        assertTrue(g.unresolved().stream().anyMatch(u -> "viewDynamic".equals(u.kind()) && "VController.go".equals(u.detail())), g.unresolved().toString());
        assertEquals(1, by.get("/a/back.do").views().size(), by.get("/a/back.do").views().toString());
    }

    /** 6-97(C2-12) — 리터럴·변수 가지도 이름 빈 redirect:·forward:(질의만 남는 것 포함)는 뷰가 아니다 — viewDynamic */
    @Test
    void bareRedirectThroughVariablesIsNotAView() {
        String imp = "import org.springframework.stereotype.*;\nimport org.springframework.web.bind.annotation.*;\n";
        JavaGraph.Graph g = JavaGraph.scan(List.of(new Source("web/WController.java", PKG_WEB + imp + "@Controller\npublic class WController {\n"
                + "    @RequestMapping(\"/w/a.do\")\n    public String a(String t) {\n        String view = \"redirect:\";\n        view += t;\n        return view;\n    }\n"
                + "    @RequestMapping(\"/w/b.do\")\n    public String b(String t) {\n        return \"redirect:?err=\" + t;\n    }\n"
                + "    @RequestMapping(\"/w/c.do\")\n    public String c(boolean ok) {\n        String v = \"forward:\";\n        if (ok) {\n            v = v + \"/a.do\";\n"
                + "        }\n        return v;\n    }\n}\n", null, null, null)), null);
        java.util.Map<String, JavaGraph.Program> by = new java.util.TreeMap<>();
        g.programs().forEach(p -> by.put(p.url(), p));
        for (String u : List.of("/w/a.do", "/w/b.do", "/w/c.do")) {
            assertEquals(List.of(), by.get(u).views(), u);
            String name = "WController." + u.charAt(3);
            assertTrue(g.unresolved().stream().anyMatch(x -> "viewDynamic".equals(x.kind()) && name.equals(x.detail())), u + " " + g.unresolved());
        }
    }

    /** 6-91(C2-11) — 곁 등록부 id 가 오버로드를 가른다 — 부르지 않은 오버로드의 표가 안 섞인다 · 오버로드 없으면 지금 꼴 */
    @Test
    void overloadsKeepSeparateSideIds() {
        String imp = SPRING_IMPORTS;
        List<Source> src = List.of(
                new Source("web/ODao.java", PKG_WEB + "import org.springframework.jdbc.core.JdbcTemplate;\n@org.springframework.stereotype.Repository\npublic class ODao {\n"
                        + "    JdbcTemplate jdbc;\n    public Object find(long id) { return jdbc.queryForList(\"SELECT * FROM T_A\"); }\n"
                        + "    public Object find(long id, String s) { return jdbc.queryForList(\"SELECT * FROM T_B\"); }\n"
                        + "    public Object one() { return jdbc.queryForList(\"SELECT * FROM T_C\"); }\n}\n", null, null, null),
                new Source("web/OController.java", PKG_WEB + imp + "@Controller\npublic class OController {\n    @Autowired ODao dao;\n"
                        + "    @RequestMapping(\"/o.do\")\n    public String go() { dao.find(1L); dao.one(); return \"x\"; }\n}\n", null, null, null));
        JpaIndex jpa = JpaIndex.scan(src);
        JavaGraph.Graph g = JavaGraph.scan(src, null, jpa);
        assertEquals(List.of("web.ODao.find/1#jdbc", "web.ODao.one#jdbc"), stmts(g, "/o.do"));
        assertEquals(List.of("T_A"), jpa.recorded("web.ODao.find/1#jdbc").orElseThrow().stream().map(SqlTables.Ref::table).toList());
    }

    /** 6-90(C2-09) — 조상이 선언한 필드의 타입은 선언한 클래스의 패키지·import 로 푼다 */
    @Test
    void fieldTypeResolvesWhereItIsDeclared() {
        String imp = SPRING_IMPORTS;
        String dao = "public class CmmDAO {\n    public Object load() { return selectList(\"%s.load\"); }\n}\n";
        JavaGraph.Graph g = JavaGraph.scan(List.of(
                new Source("a/CmmDAO.java", "package a;\n" + dao.formatted("A"), null, null, null),
                new Source("b/CmmDAO.java", "package b;\n" + dao.formatted("B"), null, null, null),
                new Source("a/BaseSvc.java", "package a;\n" + imp + "public abstract class BaseSvc {\n    @Autowired CmmDAO dao;\n}\n", null, null, null),
                new Source("b/FooSvc.java", "package b;\n" + imp + "@Service\npublic class FooSvc extends a.BaseSvc {\n    public void run() { dao.load(); }\n}\n", null, null, null),
                new Source("web/FooController.java", PKG_WEB + imp + "import b.FooSvc;\n@Controller\npublic class FooController {\n    @Autowired FooSvc svc;\n"
                        + "    @RequestMapping(\"/foo.do\")\n    public String go() { svc.run(); return \"x\"; }\n}\n", null, null, null)), null);
        assertEquals(List.of("A.load"), stmts(g, "/foo.do"));
    }

    /** 6-90(C2-10) — 후보가 하나여도 명시 import 가 다른 FQCN(프로젝트 밖)이면 이름만 같은 프로젝트 클래스로 안 푼다 · 같은 패키지·중첩 import 는 그대로 */
    @Test
    void importedOutsideClassIsNotTheProjectOne() {
        String imp = SPRING_IMPORTS;
        Source legacy = new Source("app/legacy/CodeService.java", "package app.legacy;\n@org.springframework.stereotype.Service\npublic class CodeService {\n"
                + "    @org.springframework.beans.factory.annotation.Autowired CodeDAO dao;\n    public Object list() { return dao.list(); }\n}\n", null, null, null);
        Source codeDao = new Source("app/legacy/CodeDAO.java", "package app.legacy;\npublic class CodeDAO {\n    public Object list() { return selectList(\"legacy.code.list\"); }\n}\n",
                null, null, null);
        java.util.function.Function<String, Source> ctl = head -> new Source("web/CodeController.java", PKG_WEB + imp + head + "@Controller\npublic class CodeController {\n"
                + "    @Autowired CodeService code;\n    @RequestMapping(\"/code.do\")\n    public String go() { code.list(); return \"x\"; }\n}\n", null, null, null);
        assertEquals(List.of(), stmts(JavaGraph.scan(List.of(legacy, codeDao, ctl.apply("import com.vendor.common.CodeService;\n")), null), CODE_DO));
        assertEquals(List.of(LEGACY_LIST), stmts(JavaGraph.scan(List.of(legacy, codeDao, ctl.apply("import app.legacy.CodeService;\n")), null), CODE_DO));
        Source nested = new Source("app/legacy/Outer.java", "package app.legacy;\npublic class Outer {\n    @org.springframework.stereotype.Service\n"
                + "    public static class CodeService {\n        @org.springframework.beans.factory.annotation.Autowired CodeDAO dao;\n"
                + "        public Object list() { return dao.list(); }\n    }\n}\n", null, null, null);
        assertEquals(List.of(LEGACY_LIST), stmts(JavaGraph.scan(List.of(nested, codeDao, ctl.apply("import app.legacy.Outer.CodeService;\n")), null), CODE_DO));
        Source same = new Source("web/CodeService.java", PKG_WEB + "@org.springframework.stereotype.Service\npublic class CodeService {\n"
                + "    public Object list() { return new Object(); }\n}\n", null, null, null);
        assertEquals(List.of(), stmts(JavaGraph.scan(List.of(same, ctl.apply("")), null), CODE_DO), "같은 패키지 — 풀린다(문장은 없다)");
    }

    /** 6-93a(M8·C2-09·C2-10) — 명시 import 가 답: 하위 패키지 import · 후보 여럿 + 프로젝트 밖 import · 중첩 import · 완전 이름 필드 */
    @Test
    void explicitImportDecidesTheType() {
        String imp = SPRING_IMPORTS;
        String svcBody = "@org.springframework.stereotype.Repository\npublic class %s {\n    public Object %s() { return selectList(\"%s\"); }\n}\n";
        java.util.function.BiFunction<String, String, Source> ctl = (head, field) -> new Source("web/CodeController.java", PKG_WEB + imp + head
                + "@Controller\npublic class CodeController {\n    @Autowired " + field + " code;\n    @RequestMapping(\"/code.do\")\n"
                + "    public String go() { code.list(); return \"x\"; }\n}\n", null, null, null);
        Source page = new Source("a/Page.java", "package a;\n" + svcBody.formatted("Page", LIST, "a.page.load"), null, null, null);
        assertEquals(List.of(), stmts(JavaGraph.scan(List.of(page, ctl.apply("import a.sub.Page;\n", "Page")), null), CODE_DO), "하위 패키지 import");
        assertEquals(List.of(), stmts(JavaGraph.scan(List.of(page, ctl.apply("import com.vendor.Page;\n", "Page")), null), CODE_DO));
        Source legacy = new Source("app/legacy/CodeService.java", "package app.legacy;\n" + svcBody.formatted(CODE_SERVICE, LIST, LEGACY_LIST),
                null, null, null);
        Source other = new Source("app/other/CodeService.java", "package app.other;\n" + svcBody.formatted(CODE_SERVICE, LIST, "other.code.list"),
                null, null, null);
        JavaGraph.Graph two = JavaGraph.scan(List.of(legacy, other, ctl.apply("import com.vendor.common.CodeService;\n", CODE_SERVICE)), null);
        assertEquals(List.of(), stmts(two, CODE_DO), "후보 둘 + 프로젝트 밖 import");
        assertTrue(two.unresolved().stream().noneMatch(u -> u.kind().equals("ambiguous")), two.unresolved().toString());
        Source outer = new Source("a/Outer.java", "package a;\npublic class Outer {\n    @org.springframework.stereotype.Repository\n"
                + "    public static class Inner {\n        public Object list() { return selectList(\"a.inner.list\"); }\n    }\n}\n", null, null, null);
        Source bInner = new Source("b/Inner.java", "package b;\n" + svcBody.formatted("Inner", LIST, "b.inner.list"), null, null, null);
        assertEquals(List.of("a.inner.list"), stmts(JavaGraph.scan(List.of(outer, bInner, ctl.apply("import a.Outer.Inner;\n", "Inner")), null), CODE_DO));
        assertEquals(List.of("a.inner.list"), stmts(JavaGraph.scan(List.of(outer, ctl.apply("import a.Outer.Inner;\n", "Inner")), null), CODE_DO),
                "중첩 import 후보 하나");
        assertEquals(List.of(LEGACY_LIST), stmts(JavaGraph.scan(List.of(legacy, other,
                ctl.apply("import com.vendor.common.CodeService;\n", "app.legacy.CodeService")), null), CODE_DO), "완전 이름 필드");
        assertEquals(List.of(LEGACY_LIST), stmts(JavaGraph.scan(List.of(legacy, ctl.apply("import app.legacy.*;\n", CODE_SERVICE)), null), CODE_DO),
                "별 import");
    }

    /** 6-88(C2-05) — 매핑 주석이 인터페이스·추상 조상 메서드에 있는 컨트롤러도 프로그램 · 하위 주석이 있으면 그것 · URL 이 둘로 안 는다 */
    @Test
    void mappingDeclaredOnInterfaceOrAbstractParent() {
        String imp = SPRING_IMPORTS;
        Source dao = new Source("dao/UserDAO.java", "package dao;\nimport org.springframework.stereotype.Repository;\n@Repository\npublic class UserDAO {\n"
                + "    public Object find() { return selectList(\"user.find\"); }\n}\n", null, null, null);
        Source api = new Source("web/UserApi.java", PKG_WEB + imp + "public interface UserApi {\n    @GetMapping(\"/users\")\n    Object list();\n}\n", null, null, null);
        Source impl = new Source(USER_CTL, PKG_WEB + imp + "import dao.UserDAO;\n@RestController\npublic class UserController implements UserApi {\n"
                + "    @Autowired UserDAO dao;\n    public Object list() { return dao.find(); }\n}\n", null, null, null);
        JavaGraph.Graph g = JavaGraph.scan(List.of(dao, api, impl), null);
        assertEquals(List.of("GET /users web/UserController.java"), g.programs().stream().map(p -> p.verb() + " " + p.url() + " " + p.file()).toList());
        assertEquals(List.of("user.find"), stmts(g, "/users"));
        Source abs = new Source("web/BaseUsers.java", PKG_WEB + imp + "public abstract class BaseUsers {\n    @GetMapping(\"/users\")\n    public abstract Object list();\n}\n",
                null, null, null);
        Source sub = new Source(USER_CTL, PKG_WEB + imp + "import dao.UserDAO;\n@RestController\npublic class UserController extends BaseUsers {\n"
                + "    @Autowired UserDAO dao;\n    public Object list() { return dao.find(); }\n}\n", null, null, null);
        JavaGraph.Graph g2 = JavaGraph.scan(List.of(dao, abs, sub), null);
        assertEquals(List.of("GET /users"), g2.programs().stream().map(p -> p.verb() + " " + p.url()).toList());
        assertEquals(List.of("user.find"), stmts(g2, "/users"));
        Source own = new Source(USER_CTL, PKG_WEB + imp + "import dao.UserDAO;\n@RestController\npublic class UserController implements UserApi {\n"
                + "    @Autowired UserDAO dao;\n    @GetMapping(\"/mine\")\n    public Object list() { return dao.find(); }\n}\n", null, null, null);
        assertEquals(List.of("GET /mine"), JavaGraph.scan(List.of(dao, api, own), null).programs().stream().map(p -> p.verb() + " " + p.url()).toList());
    }

    /** 6-86(C2-04) — 공통 DAO 가 문장 id 를 매개변수로 넘기는 꼴: 호출 스택으로 거슬러 리터럴을 읽는다 · 방문 열쇠가 문장마다 갈린다 */
    @Test
    void statementIdPassedThroughACommonDao() {
        String imp = SPRING_IMPORTS;
        String list = "java.util.List<Object>";
        Source abs = new Source("dao/AbstractDAO.java", "package dao;\nimport org.apache.ibatis.session.SqlSession;\npublic abstract class AbstractDAO {\n"
                + "    protected SqlSession sqlSession;\n    public " + list + " selectList(String queryId, Object p) { return sqlSession.selectList(queryId, p); }\n}\n",
                null, null, null);
        Source base = new Source("dao/BaseDAO.java", "package dao;\npublic abstract class BaseDAO extends AbstractDAO {\n"
                + PUB + list + " list(String id) { return selectList(id, null); }\n}\n", null, null, null);
        Source foo = new Source("dao/FooDAO.java", "package dao;\nimport org.springframework.stereotype.Repository;\n@Repository\npublic class FooDAO extends BaseDAO {\n"
                + PUB + list + " find(Object vo) { return selectList(\"foo.find\", vo); }\n"
                + PUB + list + " find2(Object vo) { return selectList(\"foo.find2\", vo); }\n"
                + PUB + list + " deep() { return list(\"foo.deep\"); }\n"
                + PUB + list + " bad(Object vo) { return selectList(makeId(), vo); }\n"
                + "    String makeId() { return \"x\"; }\n}\n", null, null, null);
        Source ctl = new Source("web/FooController.java", PKG_WEB + imp + "import dao.FooDAO;\n@Controller\npublic class FooController {\n    @Autowired FooDAO dao;\n"
                + "    @RequestMapping(\"/foo/list.do\")\n    public String list() { dao.find(null); return \"x\"; }\n"
                + "    @RequestMapping(\"/foo/both.do\")\n    public String both() { dao.find(null); dao.find2(null); return \"x\"; }\n"
                + "    @RequestMapping(\"/foo/deep.do\")\n    public String deep() { dao.deep(); return \"x\"; }\n"
                + "    @RequestMapping(\"/foo/bad.do\")\n    public String bad() { dao.bad(null); return \"x\"; }\n}\n", null, null, null);
        JavaGraph.Graph g = JavaGraph.scan(List.of(abs, base, foo, ctl), null);
        assertEquals(List.of(FOO_FIND), stmts(g, "/foo/list.do"));
        assertEquals(List.of("literal"), g.programs().stream().filter(p -> "/foo/list.do".equals(p.url())).findFirst().orElseThrow()
                .statements().stream().map(JavaGraph.Stmt::resolution).toList());
        assertEquals(List.of(FOO_FIND, "foo.find2"), stmts(g, "/foo/both.do").stream().sorted().toList());
        assertEquals(List.of("foo.deep"), stmts(g, "/foo/deep.do"));
        assertEquals(List.of(), stmts(g, "/foo/bad.do"));
        assertTrue(g.unresolved().stream().anyMatch(u -> STATEMENT.equals(u.kind()) && "dao/FooDAO.java".equals(u.file()) && "FooDAO.bad".equals(u.detail())),
                g.unresolved().toString());
        assertTrue(g.daoStatements().stream().map(JavaGraph.Stmt::id).toList().containsAll(List.of(FOO_FIND, "foo.find2")), g.daoStatements().toString());
        Source getter = new Source("dao/AbstractDAO.java", "package dao;\npublic abstract class AbstractDAO {\n    protected Object getSqlSession() { return null; }\n"
                + PUB + list + " selectList(String queryId, Object p) { return getSqlSession().selectList(queryId, p); }\n}\n", null, null, null);
        assertEquals(List.of(FOO_FIND), stmts(JavaGraph.scan(List.of(getter, base, foo, ctl), null), "/foo/list.do"));
    }

    /** 6-93b(M12·C2-01·05·06·07) — 방문 열쇠는 String 매개변수 자리의 문장 id: 가운데 층 · 위임 · 재대입 · 같은 이름 지역 변수 · id 둘 · 긴 id */
    @Test
    void statementIdsThroughLayersAndWrappers() {
        String imp = SPRING_IMPORTS;
        String list = "java.util.List<Object>";
        Source abs = new Source("dao/AbstractDAO.java", "package dao;\nimport org.apache.ibatis.session.SqlSession;\npublic abstract class AbstractDAO {\n"
                + "    protected SqlSession sqlSession;\n    public " + list + " selectList(String queryId, Object p) { return sqlSession.selectList(queryId, p); }\n}\n",
                null, null, null);
        Source dao = new Source("dao/CommonDAO.java", "package dao;\nimport org.springframework.stereotype.Repository;\n@Repository\n"
                + "public class CommonDAO extends AbstractDAO {\n"
                + PUB + list + " getList(String queryId, Object p) { return sqlSession.selectList(queryId, p); }\n"
                + PUB + list + " ns(String queryId, Object p) { queryId = \"app.\" + queryId; return sqlSession.selectList(queryId, p); }\n"
                + PUB + list + " a(Object vo) { String queryId = \"foo.a\"; return selectList(queryId, vo); }\n"
                + PUB + list + " b(Object vo) { String queryId = \"foo.b\"; return selectList(queryId, vo); }\n"
                + PUB + list + " page(String listId, String countId, Object p) { selectList(countId, p); return selectList(listId, p); }\n"
                + "    public int update(Object vo) { return sqlSession.update(\"u.update\", vo); }\n}\n", null, null, null);
        Source svc = new Source("svc/CommonService.java", PKG_SVC + imp + "import dao.CommonDAO;\n@Service\npublic class CommonService {\n"
                + "    @Autowired CommonDAO commonDAO;\n" + PUB + list + " getList(String queryId, Object p) { return commonDAO.getList(queryId, p); }\n}\n",
                null, null, null);
        Source biz = new Source("svc/Biz.java", PKG_SVC + imp + "import dao.CommonDAO;\n@Service\npublic class Biz {\n    @Autowired CommonDAO dao;\n"
                + "    public int update(Object vo) { return dao.update(vo); }\n}\n", null, null, null);
        Source up = new Source("svc/Svc.java", PKG_SVC + imp + "@Service\npublic class Svc {\n    @Autowired Biz biz;\n"
                + "    public int update(Object vo) { return biz.update(vo); }\n}\n", null, null, null);
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 260; i++) {
            many.append(" s.update(v").append(i).append(");");
        }
        String longId = "p" + "a".repeat(102);
        Source ctl = new Source("web/LayerController.java", PKG_WEB + imp + "import dao.CommonDAO;\nimport svc.*;\n@Controller\npublic class LayerController {\n"
                + "    @Autowired CommonService svc;\n    @Autowired CommonDAO dao;\n    @Autowired Svc s;\n"
                + "    @RequestMapping(\"/mid.do\")\n    public String mid() { svc.getList(\"a.list\", null); svc.getList(\"b.list\", null); return \"x\"; }\n"
                + "    @RequestMapping(\"/del.do\")\n    public String del() {" + many + " return \"x\"; }\n"
                + "    @RequestMapping(\"/re.do\")\n    public String re() { dao.ns(\"selectUser\", null); return \"x\"; }\n"
                + "    @RequestMapping(\"/ab.do\")\n    public String ab() { dao.a(null); dao.b(null); return \"x\"; }\n"
                + "    @RequestMapping(\"/pg.do\")\n    public String pg() { dao.page(\"a.list\", \"cmm.count\", null); dao.page(\"b.list\", \"cmm.count\", null); return \"x\"; }\n"
                + "    @RequestMapping(\"/long.do\")\n    public String lg() { dao.getList(\"" + longId + ".selectA\", null); dao.getList(\"" + longId
                + ".selectB\", null); return \"x\"; }\n}\n", null, null, null);
        JavaGraph.Graph g = JavaGraph.scan(List.of(abs, dao, svc, biz, up, ctl), null);
        assertEquals(List.of("a.list", "b.list"), stmts(g, "/mid.do").stream().sorted().toList());
        assertEquals(List.of("u.update"), stmts(g, "/del.do"));
        assertTrue(g.unresolved().stream().noneMatch(u -> "depth".equals(u.kind())), g.unresolved().toString());
        assertEquals(List.of(), stmts(g, "/re.do"));
        assertTrue(g.unresolved().stream().anyMatch(u -> STATEMENT.equals(u.kind()) && u.detail().endsWith(".ns")), g.unresolved().toString());
        assertEquals(List.of("foo.a", "foo.b"), stmts(g, "/ab.do").stream().sorted().toList());
        assertEquals(List.of("a.list", "b.list", "cmm.count"), stmts(g, "/pg.do").stream().distinct().sorted().toList());
        assertEquals(List.of(longId + ".selectA", longId + ".selectB"), stmts(g, "/long.do").stream().sorted().toList());
    }

    /** 6-104a(리뷰 M1) — 한 메서드가 감싸개를 못 닿은 id(접두·변수)로 두 번 부르면 둘 다 걷는다(방문 열쇠에 인자 식 자리) */
    @Test
    void wrapperCallsWithUnreachedIdsAreWalkedEach() {
        String imp = SPRING_IMPORTS;
        String list = "java.util.List<Object>";
        Source dao = new Source("dao/CommonDAO.java", "package dao;\nimport org.apache.ibatis.session.SqlSession;\nimport org.springframework.stereotype.Repository;\n"
                + "@Repository\npublic class CommonDAO {\n    protected SqlSession sqlSession;\n"
                + PUB + list + " selectList(String queryId, Object p) { return sqlSession.selectList(queryId, p); }\n}\n", null, null, null);
        Source ctl = new Source("web/TwoController.java", PKG_WEB + imp + "import dao.CommonDAO;\n@Controller\npublic class TwoController {\n"
                + "    @Autowired CommonDAO dao;\n"
                + "    @RequestMapping(\"/two.do\")\n    public String two(String t) {\n        dao.selectList(\"user.\" + t, null);\n"
                + "        dao.selectList(\"dept.\" + t, null);\n        return \"x\";\n    }\n"
                + "    @RequestMapping(\"/un.do\")\n    public String un(String a, String b) {\n        dao.selectList(a, null);\n"
                + "        dao.selectList(b, null);\n        return \"x\";\n    }\n}\n", null, null, null);
        JavaGraph.Graph g = JavaGraph.scan(List.of(dao, ctl), null);
        assertEquals(List.of("dept.", "user."), stmts(g, "/two.do").stream().sorted().toList());
        assertEquals(2, g.unresolved().stream().filter(u -> "prefix".equals(u.kind())).count(), g.unresolved().toString());
        assertEquals(2, g.unresolved().stream().filter(u -> STATEMENT.equals(u.kind()) && u.file().endsWith("TwoController.java")).map(Unresolved::line).distinct().count(),
                g.unresolved().toString());
        assertTrue(g.unresolved().stream().noneMatch(u -> "depth".equals(u.kind())), g.unresolved().toString());
    }

    /** 6-104b(리뷰 M4) — 저장소가 엔티티와 같은 이름의 DTO 를 import 해도 Q 타입(QMember)은 엔티티 Member 다 · EntityManager 길은 6-96 그대로 */
    @Test
    void qTypeIgnoresUnrelatedImportOfTheEntityName() {
        String imp = SPRING_IMPORTS;
        Source entity = new Source("domain/Member.java", "package domain;\nimport javax.persistence.*;\n@Entity\n@Table(name = \"TB_MEMBER\")\npublic class Member {\n"
                + "    @Id Long id;\n}\n", null, null, null);
        Source dto = new Source("app/dto/Member.java", "package app.dto;\npublic class Member {\n    String name;\n}\n", null, null, null);
        Source repo = new Source("repo/MemberQueryRepo.java", "package repo;\nimport app.dto.Member;\nimport domain.QMember;\n"
                + "import com.querydsl.jpa.impl.JPAQueryFactory;\nimport org.springframework.stereotype.Repository;\n@Repository\npublic class MemberQueryRepo {\n"
                + "    private JPAQueryFactory queryFactory;\n    public java.util.List<?> list() { return queryFactory.selectFrom(QMember.member).fetch(); }\n}\n", null, null, null);
        Source emRepo = new Source("repo/MemberEmRepo.java", "package repo;\nimport app.dto.Member;\nimport javax.persistence.EntityManager;\n"
                + "import org.springframework.stereotype.Repository;\n@Repository\npublic class MemberEmRepo {\n    private EntityManager em;\n"
                + "    public void save() { em.persist(new Member()); }\n}\n", null, null, null);
        Source ctl = new Source("web/MController.java", PKG_WEB + imp + "import repo.*;\n@Controller\npublic class MController {\n"
                + "    @Autowired MemberQueryRepo repo;\n    @Autowired MemberEmRepo em;\n"
                + "    @RequestMapping(\"/m.do\")\n    public String m() { repo.list(); return \"x\"; }\n"
                + "    @RequestMapping(\"/e.do\")\n    public String e() { em.save(); return \"x\"; }\n}\n", null, null, null);
        List<Source> src = List.of(entity, dto, repo, emRepo, ctl);
        JpaIndex jpa = JpaIndex.scan(src);
        JavaGraph.Graph g = JavaGraph.scan(src, null, jpa);
        assertEquals(List.of("repo.MemberQueryRepo.list#qdsl"), stmts(g, "/m.do"), g.unresolved().toString());
        assertTrue(g.unresolved().stream().noneMatch(u -> "querydsl".equals(u.kind())), g.unresolved().toString());
        assertTrue(g.unresolved().stream().anyMatch(u -> "jpaType".equals(u.kind()) && u.file().endsWith("MemberEmRepo.java")), "EntityManager 길은 6-96 그대로 " + g.unresolved());
    }

    /** 6-103(리뷰 I4) — 색인 밖 인터페이스(완전 이름·명시 import 필드)는 그 완전 이름을 implements 한 구현으로 — 패키지 뿌리가 같을 때만 */
    @Test
    void outsideInterfaceResolvesByFqn() {
        String imp = SPRING_IMPORTS;
        String sess = "import org.apache.ibatis.session.SqlSession;\n";
        java.util.function.BiFunction<String, String, Source> s = (path, body) -> new Source(path, body, null, null, null);
        List<Source> src = List.of(
                s.apply("com/acme/shop/dao/Dao.java", "package com.acme.shop.dao;\n" + sess + "import org.springframework.stereotype.Repository;\n@Repository\n"
                        + "public class Dao {\n    SqlSession sqlSession;\n    public void one() { sqlSession.selectOne(\"old.one\"); }\n"
                        + "    public void two() { sqlSession.selectOne(\"v1.two\"); }\n    public void p1() { sqlSession.selectOne(\"p.one\"); }\n"
                        + "    public void p2() { sqlSession.selectOne(\"p.two\"); }\n}\n"),
                s.apply("com/acme/shop/facade/CustomerFacade.java", "package com.acme.shop.facade;\npublic interface CustomerFacade {\n    void run();\n}\n"),
                s.apply("com/acme/shop/facade/CustomerFacadeImpl.java", "package com.acme.shop.facade;\n" + imp + "import com.acme.shop.dao.Dao;\n@Service\n"
                        + "public class CustomerFacadeImpl implements CustomerFacade {\n    @Autowired Dao dao;\n    public void run() { dao.one(); }\n}\n"),
                s.apply("com/acme/shop/store/CustomerFacadeImpl.java", "package com.acme.shop.store;\n" + imp + "import com.acme.shop.dao.Dao;\n"
                        + "import com.acme.api.facade.v1.CustomerFacade;\n@Service(\"customerFacadev1\")\n"
                        + "public class CustomerFacadeImpl implements CustomerFacade {\n    @Autowired Dao dao;\n    public void run() { dao.two(); }\n}\n"),
                s.apply("com/acme/shop/store/ProductFacadeImpl.java", "package com.acme.shop.store;\n" + imp + "import com.acme.shop.dao.Dao;\n"
                        + "@Service(\"productV1\")\npublic class ProductFacadeImpl implements com.acme.api.facade.v1.ProductFacade {\n"
                        + "    @Autowired Dao dao;\n    public void run() { dao.p1(); }\n}\n"),
                s.apply("com/acme/shop/store/ProductFacadeV2Impl.java", "package com.acme.shop.store;\n" + imp + "import com.acme.shop.dao.Dao;\n"
                        + "import com.acme.api.facade.v1.ProductFacade;\n@Service(\"productV2\")\npublic class ProductFacadeV2Impl implements ProductFacade {\n"
                        + "    @Autowired Dao dao;\n    public void run() { dao.p2(); }\n}\n"),
                s.apply("com/acme/shop/vo/Vo.java", "package com.acme.shop.vo;\n" + imp + "import java.io.Serializable;\nimport com.acme.shop.dao.Dao;\n"
                        + "@Component\npublic class Vo implements Serializable {\n    @Autowired Dao dao;\n    public String toString() { dao.two(); return \"\"; }\n}\n"),
                s.apply("web/SolImpl.java", PKG_WEB + imp + "import com.acme.shop.dao.Dao;\n@Service\npublic class SolImpl implements com.acme.api.Sol {\n"
                        + "    @Autowired Dao dao;\n    public void run() { dao.two(); }\n}\n"),
                s.apply("com/acme/shop/web/FController.java", "package com.acme.shop.web;\n" + imp + "import com.acme.api.facade.v1.ProductFacade;\n"
                        + "import java.io.Serializable;\n@Controller\npublic class FController {\n"
                        + "    @Autowired private com.acme.api.facade.v1.CustomerFacade fq;\n    @Autowired ProductFacade both;\n"
                        + "    @javax.annotation.Resource(name = \"productV2\") ProductFacade named;\n    @Autowired Serializable ser;\n"
                        + "    @RequestMapping(\"/fq.do\")\n    public String fq() { fq.run(); return \"x\"; }\n"
                        + "    @RequestMapping(\"/both.do\")\n    public String both() { both.run(); return \"x\"; }\n"
                        + "    @RequestMapping(\"/named.do\")\n    public String named() { named.run(); return \"x\"; }\n"
                        + "    @RequestMapping(\"/ser.do\")\n    public String ser() { return ser.toString(); }\n}\n"),
                s.apply("com/acme/shop/web/IController.java", "package com.acme.shop.web;\n" + imp + "import com.acme.api.facade.v1.CustomerFacade;\n"
                        + "@Controller\npublic class IController {\n    @Autowired CustomerFacade f;\n"
                        + "    @RequestMapping(\"/imp.do\")\n    public String imp() { f.run(); return \"x\"; }\n}\n"),
                s.apply("com/acme/shop/web/OController.java", "package com.acme.shop.web;\n" + imp + "import com.acme.shop.facade.CustomerFacade;\n"
                        + "@Controller\npublic class OController {\n    @Autowired CustomerFacade f;\n"
                        + "    @RequestMapping(\"/in.do\")\n    public String in() { f.run(); return \"x\"; }\n}\n"),
                s.apply("web/SController.java", PKG_WEB + imp + "@Controller\npublic class SController {\n    @Autowired com.acme.api.Sol sol;\n"
                        + "    @RequestMapping(\"/sol.do\")\n    public String sol() { sol.run(); return \"x\"; }\n}\n"));
        JavaGraph.Graph g = JavaGraph.scan(src, null);
        assertEquals(List.of("v1.two"), stmts(g, "/fq.do"), "완전 이름 필드");
        assertEquals(List.of("v1.two"), stmts(g, "/imp.do"), "명시 import 필드");
        assertEquals(List.of("old.one"), stmts(g, "/in.do"), "색인 안 인터페이스는 그대로");
        assertEquals(List.of("p.one", "p.two"), stmts(g, "/both.do").stream().sorted().toList(), "구현 둘");
        assertEquals(1, g.unresolved().stream().filter(u -> "ambiguous".equals(u.kind()) && u.detail().equals("FController.both")).count(), g.unresolved().toString());
        assertEquals(List.of("p.two"), stmts(g, "/named.do"), "@Resource 빈 이름");
        assertEquals(List.of(), stmts(g, "/ser.do"), "뿌리가 다른 인터페이스(java.io.Serializable)는 안 건다");
        assertEquals(List.of(), stmts(g, "/sol.do"), "패키지 한 마디 클래스는 안 건다");
    }

    /** 6-94(C2-04) — 공통 DAO 가 색인 밖 프레임워크 메서드를 덮고 super. 로 넘기면 그 super 호출이 싱크 */
    @Test
    void daoOverridesFrameworkSinkWithSuper() {
        String imp = SPRING_IMPORTS;
        String list = "java.util.List<Object>";
        Source common = new Source("dao/CommonDAO.java", "package dao;\nimport egovframework.rte.psl.dataaccess.EgovAbstractMapper;\n"
                + "public class CommonDAO extends EgovAbstractMapper {\n"
                + PUB + list + " selectList(String queryId, Object p) { return super.selectList(queryId, p); }\n"
                + "    public int insert(String queryId, Object p) { return super.insert(queryId, p); }\n}\n", null, null, null);
        Source user = new Source("dao/UserDAO.java", "package dao;\nimport org.springframework.stereotype.Repository;\n@Repository\npublic class UserDAO extends CommonDAO {\n"
                + PUB + list + " list(Object vo) { return selectList(\"user.list\", vo); }\n"
                + "    public int add(Object vo) { return insert(\"user.add\", vo); }\n}\n", null, null, null);
        Source ctl = new Source(USER_CTL, PKG_WEB + imp + "import dao.UserDAO;\n@Controller\npublic class UserController {\n    @Autowired UserDAO dao;\n"
                + "    @RequestMapping(\"/u/list.do\")\n    public String list() { dao.list(null); dao.add(null); return \"x\"; }\n}\n", null, null, null);
        assertEquals(List.of("user.add", "user.list"), stmts(JavaGraph.scan(List.of(common, user, ctl), null), "/u/list.do").stream().sorted().toList());
        // 지킴이 — 조상이 색인 안이고 같은 이름 메서드가 있으면 그 메서드를 걷는다
        Source base = new Source("dao/BaseDAO.java", "package dao;\nimport org.apache.ibatis.session.SqlSession;\npublic class BaseDAO {\n"
                + "    protected SqlSession sqlSession;\n" + PUB + list + " selectList(String queryId, Object p) { return sqlSession.selectList(queryId, p); }\n"
                + "    public int insert(String queryId, Object p) { return sqlSession.insert(queryId, p); }\n}\n", null, null, null);
        Source mid = new Source("dao/CommonDAO.java", "package dao;\npublic class CommonDAO extends BaseDAO {\n"
                + PUB + list + " selectList(String queryId, Object p) { return super.selectList(queryId, p); }\n"
                + "    public int insert(String queryId, Object p) { return super.insert(queryId, p); }\n}\n", null, null, null);
        assertEquals(List.of("user.add", "user.list"), stmts(JavaGraph.scan(List.of(base, mid, user, ctl), null), "/u/list.do").stream().sorted().toList());
    }

    /** 6-95(C2-02) — 타입 수준 @RequestMapping 이 인터페이스·추상 조상에 있으면 그 머리를 붙인다 · c 에 머리가 있으면 c 것 */
    @Test
    void typeLevelMappingOnInterfaceOrParent() {
        String imp = SPRING_IMPORTS;
        java.util.function.Function<JavaGraph.Graph, List<String>> urls = g -> g.programs().stream().map(JavaGraph.Program::url).sorted().toList();
        Source api = new Source("web/UserApi.java", PKG_WEB + imp + "@RequestMapping(\"/api/v1/users\")\npublic interface UserApi {\n"
                + "    @GetMapping(\"/list\")\n    Object list();\n}\n", null, null, null);
        Source impl = new Source(USER_CTL, PKG_WEB + imp + "@RestController\npublic class UserController implements UserApi {\n"
                + "    public Object list() { return null; }\n}\n", null, null, null);
        assertEquals(List.of("/api/v1/users/list"), urls.apply(JavaGraph.scan(List.of(api, impl), null)));
        Source base = new Source("web/BaseCtl.java", PKG_WEB + imp + "@RequestMapping(\"/base\")\npublic abstract class BaseCtl {\n"
                + "    @GetMapping(\"/list\")\n    public abstract Object list();\n    @GetMapping(\"/own\")\n    public Object own() { return null; }\n}\n",
                null, null, null);
        Source sub = new Source("web/SubCtl.java", PKG_WEB + imp + "@RestController\npublic class SubCtl extends BaseCtl {\n"
                + "    public Object list() { return null; }\n}\n", null, null, null);
        assertEquals(List.of("/base/list", "/base/own"), urls.apply(JavaGraph.scan(List.of(base, sub), null)));
        Source subOwn = new Source("web/SubCtl.java", PKG_WEB + imp + "@RestController\n@RequestMapping(\"/sub\")\npublic class SubCtl extends BaseCtl {\n"
                + "    public Object list() { return null; }\n}\n", null, null, null);
        assertEquals(List.of("/sub/list", "/sub/own"), urls.apply(JavaGraph.scan(List.of(base, subOwn), null)), "c 에 머리가 있으면 c 것");
    }

    private static List<String> stmts(JavaGraph.Graph g, String url) {
        return g.programs().stream().filter(p -> url.equals(p.url())).findFirst().orElseThrow(() -> new AssertionError(url + " 없음 " + g.programs()))
                .statements().stream().map(JavaGraph.Stmt::id).toList();
    }
}
