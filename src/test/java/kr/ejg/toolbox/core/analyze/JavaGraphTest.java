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
}
