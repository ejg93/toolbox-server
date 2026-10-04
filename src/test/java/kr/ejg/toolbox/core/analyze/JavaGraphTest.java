package kr.ejg.toolbox.core.analyze;

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
        List<Source> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(DIR)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                String rel = DIR.relativize(p).toString().replace('\\', '/');
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
}
