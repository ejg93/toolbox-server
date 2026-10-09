package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.check.Source;
import org.junit.jupiter.api.Test;

/**
 * 6-6 — JSP 가 부르는 .do URL. 픽스처: c:url 따옴표 둘·form action contextPath·location.href 안 c:url·ajax url·
 * javascript:fn('/x.do')·c:import·쿼리 꼬리·경로 중간 EL(jspUrl)·EL 접두(jspUrl)·상대 c:url(버림)·주석 안(무시)·같은 URL 두 번(하나)
 */
class JspLinksTest {

    static final Path DIR = Path.of("src/test/resources/fixtures/analyze/jsp");

    @Test
    void golden() throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        try (Stream<Path> s = Files.list(DIR)) {
            for (Path p : s.sorted().toList()) {
                String rel = p.getFileName().toString();
                JspLinks.Result r = JspLinks.extract(new Source(rel, Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"), null,
                        null, null));
                out.put(rel, Map.of("urls", r.urls(), "unresolved", r.unresolved()));
            }
        }
        GoldenFiles.assertJson("analyze/jsp-links.json", out);
    }

    @Test
    void emptyText() {
        JspLinks.Result r = JspLinks.extract(new Source("x.jsp", null, null, null, null));
        assertEquals(List.of(), r.urls());
    }

    /** 6-27 — 부르는 꼴(단서): 가장 가까운 단서 하나, 같은 URL 의 다른 꼴은 전부, 변수에 담는 c:url var 는 기타 */
    @Test
    void kinds() {
        String jsp = String.join("\n",
                "<a href=\"<c:url value='/a.do'/>\">목록</a>",
                "<form:form action=\"<c:url value='/b.do'/>\" method=\"post\">",
                "<script>",
                "function pop() { window.open(\"<c:url value='/c.do'/>\", \"pop\"); }",
                "$.ajax({ type: 'post', url: \"<c:url value='/d.do'/>\", data: x });",
                "function go() { location.href = \"<c:url value='/e.do'/>\"; }",
                "</script>",
                "<a href=\"/z.do\">z</a>",
                "<c:url var=\"x\" value=\"/f.do\"/>",
                "<c:import url=\"/g.do\"/>",
                "<script>function again() { window.open(\"<c:url value='/a.do'/>\"); }</script>");
        JspLinks.Result r = JspLinks.extract(new Source("k.jsp", jsp, null, null, null));
        Map<String, String> got = new java.util.TreeMap<>();
        r.links().forEach(l -> got.merge(l.url(), l.kind(), (x, y) -> x + "," + y));
        assertEquals(Map.of("/a.do", "link,popup", "/b.do", "form", "/c.do", "popup", "/d.do", "ajax", "/e.do", "script",
                "/f.do", "other", "/g.do", "link", "/z.do", "link"), got);
        assertEquals(List.of("/a.do", "/b.do", "/c.do", "/d.do", "/e.do", "/f.do", "/g.do", "/z.do"), r.urls(), "URL 은 중복 없이 정렬");
    }
}
