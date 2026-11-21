package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** R-C-I3 · C-JSP-QUAD — 컨텍스트 접두(이름 · c:set · 공백 든 EL) · 스크립트 주석 안 URL · 긴 글의 시간 */
    @Test
    void contextPrefixScriptCommentsAndTime() {
        assertEquals(List.of(new JspLinks.Link("/a.do", "link")), jsp("<a href=\"${ctx}/a.do\">x</a>").links());
        assertEquals(List.of(), jsp("<a href=\"${ctx}/a.do\">x</a>").unresolved());
        assertEquals(List.of("/b.do"), jsp("<c:set var=\"home\" value=\"${pageContext.request.contextPath}\"/>\n<a href=\"${home}/b.do\">x</a>").urls());
        assertEquals(List.of("/c.do"), jsp("<a href=\"${ pageContext.request.contextPath }/c.do\">x</a>").urls());
        JspLinks.Result el = jsp("<a href=\"${board.url}/d.do\">x</a>");
        assertEquals(List.of(), el.links());
        assertEquals(List.of("jspUrl"), el.unresolved().stream().map(Unresolved::kind).toList());
        assertEquals(List.of("/live.do"), jsp("<script>\n// location.href='/old/removed.do';\n/* window.open('/old/pop.do') */\nlocation.href='/live.do';\n</script>").urls());
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            jsp("}".repeat(100_000) + "/x.do");
            assertEquals(List.of(), jsp("${a}/x.do\n".repeat(20_000)).links());
        });
    }

    /** R-C-I3 — .do 아닌 URL 은 컨트롤러 매핑과 글자까지 같을 때만 링크 */
    @Test
    void nonDoUrlsMatchMappings() {
        Source s = new Source("a.jsp", "<a href=\"/user/list\">l</a><a href=\"${ctx}/user/view?id=1\">v</a><img src=\"/img/logo\"/><form action='/user/save'>",
                null, null, null);
        JspLinks.Result r = JspLinks.extract(s, Set.of("/user/list", "/user/view", "/user/save"));
        assertEquals(List.of(new JspLinks.Link("/user/list", "link"), new JspLinks.Link("/user/save", "form"), new JspLinks.Link("/user/view", "link")),
                r.links());
        assertEquals(List.of(), JspLinks.extract(s).links());
    }

    private static JspLinks.Result jsp(String text) {
        return JspLinks.extract(new Source("t.jsp", text, null, null, null));
    }

    @Test
    void emptyText() {
        JspLinks.Result r = JspLinks.extract(new Source("x.jsp", null, null, null, null));
        assertEquals(List.of(), r.urls());
    }

    /** 6-27 — 부르는 꼴(단서): 가장 가까운 단서 하나, 같은 URL 의 다른 꼴은 전부, 변수에 담는 c:url var 는 기타. 6-30 — c:import·jsp:include·iframe 은 포함 */
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
                "<jsp:include page=\"<c:url value='/h.do'/>\"/>",
                "<iframe src=\"/i.do\" title=\"x\"></iframe>",
                "<IFRAME SRC=\"/j.do\"></IFRAME>",
                "<script>function again() { window.open(\"<c:url value='/a.do'/>\"); }</script>");
        JspLinks.Result r = JspLinks.extract(new Source("k.jsp", jsp, null, null, null));
        Map<String, String> got = new java.util.TreeMap<>();
        r.links().forEach(l -> got.merge(l.url(), l.kind(), (x, y) -> x + "," + y));
        assertEquals(Map.ofEntries(Map.entry("/a.do", "link,popup"), Map.entry("/b.do", "form"), Map.entry("/c.do", "popup"),
                Map.entry("/d.do", "ajax"), Map.entry("/e.do", "script"), Map.entry("/f.do", "other"), Map.entry("/g.do", "include"),
                Map.entry("/h.do", "include"), Map.entry("/i.do", "include"), Map.entry("/j.do", "include"), Map.entry("/z.do", "link")), got);
        assertEquals(List.of("/a.do", "/b.do", "/c.do", "/d.do", "/e.do", "/f.do", "/g.do", "/h.do", "/i.do", "/j.do", "/z.do"), r.urls(), "URL 은 중복 없이 정렬");
    }

    /** 6-27 실측(egov 손 대조) — window.opener 는 팝업 단서가 아니다(window.open( 만) */
    @Test
    void openerIsNotPopup() {
        JspLinks.Result r = JspLinks.extract(new Source("o.jsp", "<script>\nopener = window.opener;\nvar urlGo = \"<c:url value='/o.do' />\";\n</script>",
                null, null, null));
        assertEquals(List.of(new JspLinks.Link("/o.do", "other")), r.links());
    }

    /** PR 리뷰 — 낱말 안의 action·href·ajax 는 단서가 아니다(속성·대입 꼴만) */
    @Test
    void cueWordsNeedAttributeShape() {
        JspLinks.Result r = JspLinks.extract(new Source("w.jsp", "<input value=\"action\"> <span class=\"hrefs ajaxy\">x</span>\n"
                + "<script>go('<c:url value='/w.do'/>');</script>", null, null, null));
        assertEquals(List.of(new JspLinks.Link("/w.do", "other")), r.links());
    }
}
