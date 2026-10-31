package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals("view", byUrl.get("/q/frag.do").kind());
        assertEquals(List.of(), byUrl.get("/q/err.do").views(), "json 프로그램은 view 없음");
        assertEquals("json", byUrl.get("/q/err.do").kind());
        assertEquals(List.of(new JavaGraph.View("view", "q/list")), byUrl.get("/q/ok.do").views());
        assertEquals(List.of("Q.frag"), g.unresolved().stream().filter(u -> u.kind().equals("viewShape")).map(Unresolved::detail).toList(),
                "viewShape 는 frag 하나 — json 인 err 는 안 낸다");
    }
}
