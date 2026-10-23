package kr.ejg.toolbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 9-5 — 반입 심의 자료({@code docs/import-review.md})가 코드와 어긋나면 빨강. 문서가 부르는 시험·모듈·라우트·클래스가 실물에 있고,
 * 지운 라우트는 운영 코드에 없다.
 */
class ImportReviewDocTest {

    static final Path DOC = Path.of("docs/import-review.md");

    static String doc() throws Exception {
        return Files.readString(DOC, StandardCharsets.UTF_8);
    }

    /** ArchitectureTest 의 시험 메서드 이름이 전부 문서에 있다 — 새 게이트를 더하면 문서도 같이 */
    @Test
    void namesEveryArchitectureRule() throws Exception {
        String d = doc();
        List<String> missing = new ArrayList<>();
        for (Method m : ArchitectureTest.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Test.class) && !d.contains(m.getName())) {
                missing.add(m.getName());
            }
        }
        assertEquals(List.of(), missing, "docs/import-review.md 에 없는 ArchitectureTest 규칙");
    }

    /** 8절 모듈 목록 = scripts/package.sh 의 JLINK_MODULES 한 줄(원본은 스크립트) */
    @Test
    void jlinkModulesMatchPackageScript() throws Exception {
        Matcher s = Pattern.compile("(?m)^JLINK_MODULES=(\\S+)$").matcher(Files.readString(Path.of("scripts/package.sh"), StandardCharsets.UTF_8));
        assertTrue(s.find(), "package.sh 에 JLINK_MODULES= 줄이 없다");
        Matcher d = Pattern.compile("모듈 `([a-z.,]+)`").matcher(doc());
        assertTrue(d.find(), "문서 8절에 모듈 목록(백틱)이 없다");
        assertEquals(s.group(1), d.group(1), "문서의 jlink 모듈과 package.sh 가 다르다");
    }

    /** 문서가 부르는 라우트·클래스·표식이 실물에 있다 */
    @Test
    void namedThingsExist() throws Exception {
        String d = doc();
        String main = mainSources();
        for (String s : List.of("/api/fs/result", "/api/cleanup/apply", "/api/generate", "ReadOnlyJdbc", "SqlProbe", "결과_출처.txt", "GIT_REF", "SVN_REV",
                "openBrowser", "LocalOnly")) {
            assertTrue(d.contains(s), "문서에 " + s + " 가 없다");
            assertTrue(main.contains(s), "운영 코드에 " + s + " 가 없다 — 문서가 낡았다");
        }
    }

    /** 지운 것은 운영 코드에 없다 — 원본 되쓰기·임의 SQL 실행 라우트 */
    @Test
    void removedRoutesAreGone() throws Exception {
        String main = mainSources();
        for (String gone : List.of("/api/fs/write", "/api/sql/run", "/api/sql/export")) {
            assertTrue(!main.contains(gone), "운영 코드에 " + gone + " 가 남아 있다(9-1b·9-3d)");
        }
    }

    static String mainSources() throws Exception {
        StringBuilder b = new StringBuilder();
        try (Stream<Path> s = Files.walk(Path.of("src/main"))) {
            for (Path p : s.filter(Files::isRegularFile).filter(p -> p.toString().matches(".*\\.(java|js|html)$")).toList()) {
                b.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return b.toString();
    }
}
