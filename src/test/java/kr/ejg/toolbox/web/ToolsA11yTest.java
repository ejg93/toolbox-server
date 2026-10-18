package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import kr.ejg.toolbox.core.check.Finding;
import kr.ejg.toolbox.core.check.RuleSet;
import kr.ejg.toolbox.core.check.Source;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;

/**
 * 5-30 — 우리 화면에 우리 코드 검사 ㅈ(a11y) 묶음이 0건. 사용자가 발주처 JSP 에 돌리는 규칙을 우리 화면에도 그대로 댄다.
 * sql_snippets 는 순수본 그대로라 뺀다(고침은 portfolio 지시문 — PROGRESS 사람 할 것 ④).
 */
class ToolsA11yTest {

    private static final Path DIR = Path.of("src/main/resources/tools");

    @Test
    void toolsPassOwnA11yRules() throws Exception {
        // CheckRulesTest.profile 과 같은 생성 식 — 그 클래스는 core.check 의 package-private 이라 여기서 못 부른다
        Profile p = new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null,
                new Profile.Naming(null, null, null, null, null, true), new Profile.CodeCheck(null, null, null), "egov35", null, null, null);
        RuleSet.Run run = RuleSet.load(p, null).run();
        List<String> bad = new ArrayList<>();
        try (Stream<Path> s = Files.list(DIR)) {
            for (Path f : s.filter(Files::isRegularFile).sorted().toList()) {
                String n = f.getFileName().toString();
                if (n.equals("sql_snippets.html") || !(n.endsWith(".html") || n.endsWith(".js"))) {
                    continue;
                }
                String text = Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n");
                for (Finding x : run.apply(new Source(n, text, "UTF-8", "LF", n.endsWith(".js") ? "js" : "html"))) {
                    if (x.group().equals("a11y")) {
                        bad.add(n + ":" + x.line() + " " + x.rule());
                    }
                }
            }
        }
        for (Finding x : run.finish()) {
            if (x.group().equals("a11y")) {
                bad.add(x.file() + ":" + x.line() + " " + x.rule());
            }
        }
        assertEquals(List.of(), bad, "화면이 우리 a11y 규칙에 걸린다 — 규칙이 틀렸으면 5-31 로, 화면이 틀렸으면 고친다");
    }
}
