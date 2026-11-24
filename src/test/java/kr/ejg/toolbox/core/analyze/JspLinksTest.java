package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** 6-87(C2-07·C2-13) — 한 줄 따옴표 짝이 밀려도 매핑 URL 을 잡는다 · 경로 글자만 이어진 긴 줄도 선형 */
    @Test
    void quotePairsAndLongTokens() {
        Set<String> maps = Set.of("/user/save", "/user/list", "/user/view");
        Source a = new Source("a.jsp", "<form onsubmit=\"return chk('f')\" action=\"/user/save\">\n", null, null, null);
        assertEquals(List.of(new JspLinks.Link("/user/save", "form")), JspLinks.extract(a, maps).links());
        Source b = new Source("b.jsp", "<li class=\"<c:if test='${on}'>on</c:if>\"><a href=\"/user/list\">l</a></li>\n", null, null, null);
        assertEquals(List.of(new JspLinks.Link("/user/list", "link")), JspLinks.extract(b, maps).links());
        Source c = new Source("c.jsp", "<a onclick=\"go('x')\" href='/user/view'>v</a>\n", null, null, null);
        assertEquals(List.of(new JspLinks.Link("/user/view", "link")), JspLinks.extract(c, maps).links());
        String line = "/a.do".repeat(8000);
        JspLinks.Result r = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
                () -> JspLinks.extract(new Source("l.jsp", line + "\n", null, null, null)));
        long sum = r.links().stream().mapToLong(l -> l.url().length()).sum() + r.unresolved().stream().mapToLong(u -> u.detail().length()).sum();
        assertTrue(sum <= 100L * line.length(), String.valueOf(sum));
    }

    /** 6-67 — 루트 매핑 「/」 는 단서(href·action·url …)가 있을 때만 링크. split('/')·문자열 잇기의 '/' 는 아니다 */
    @Test
    void rootMappingNeedsACue() {
        Source script = new Source("a.jsp", "<script>var p = path.split('/'); var q = a + '/' + b;</script>", null, null, null);
        assertEquals(List.of(), JspLinks.extract(script, Set.of("/")).links());
        Source href = new Source("b.jsp", "<a href=\"/\">home</a><a href=\"${ctx}/\">h</a>", null, null, null);
        assertEquals(List.of(new JspLinks.Link("/", "link")), JspLinks.extract(href, Set.of("/")).links());
        // 6-74 — 단서가 창 안에 있어도 따옴표와 맞닿지 않으면 아니다
        Source far = new Source("c.jsp", "<a href=\"x.jsp\">t</a><script>var p = path.split('/');</script>", null, null, null);
        assertEquals(List.of(), JspLinks.extract(far, Set.of("/")).links());
        Source cues = new Source("d.jsp", String.join("\n", "<a href=\"<c:url value='/'/>\">h</a>", "<form action=\"/\">",
                "<script>location.href = '/';", "$.ajax({ url: '/' });", "window.open('/');</script>"), null, null, null);
        assertEquals(List.of("ajax", "form", "link", "popup", "script"), JspLinks.extract(cues, Set.of("/")).links().stream().map(JspLinks.Link::kind).toList());
        // 마무리 리뷰 I1 — spring:url·s:url 값 · 포함 단서의 속성(src=·url=·page=)도 맞닿음이다
        java.util.Map<String, String> roots = new java.util.TreeMap<>();
        for (String s : List.of("<a href=\"<spring:url value='/'/>\">h</a>", "<a href=\"<s:url value='/'/>\">h</a>", "<iframe src=\"/\"></iframe>",
                "<c:import url=\"/\"/>", "<jsp:include page=\"/\"/>")) {
            roots.put(s, String.join(",", JspLinks.extract(new Source("e.jsp", s, null, null, null), Set.of("/")).links().stream().map(JspLinks.Link::kind).toList()));
        }
        assertEquals(java.util.Map.of("<a href=\"<spring:url value='/'/>\">h</a>", "link", "<a href=\"<s:url value='/'/>\">h</a>", "link",
                "<iframe src=\"/\"></iframe>", "include", "<c:import url=\"/\"/>", "include", "<jsp:include page=\"/\"/>", "include"), roots);
    }

    /** 6-85(C2-03) — <script> 몸을 <!-- … //--> 로 감싼 옛 꼴 안 링크도 나온다 */
    @Test
    void wrappedScriptLinks() {
        String w = "<script type=\"text/javascript\">\n<!--\nfunction fn_select() {\n    document.listForm.action = \"<c:url value='/updateSampleView.do'/>\";\n"
                + "    document.listForm.submit();\n}\n//-->\n</script>\n";
        assertEquals(List.of(new JspLinks.Link("/updateSampleView.do", "form")), jsp(w).links());
        assertEquals(List.of(), jsp("<!-- <script>location.href = '/x.do';</script> -->\n").links(), "주석 처리한 script 는 그대로 빈다");
    }

    /** 5-146b(C2-13) — 주석 안 짝 없는 <script · 자기 닫는 태그 · text/template 몸이 죽은 주석 링크를 살리지 않는다 */
    @Test
    void commentedScriptTagsKeepDeadLinksDead() {
        String tail = "<a href=\"/live.do\">x</a>\n<script>\nvar a = 1;\n</script>\n";
        for (String head : List.of("<%-- <script src=\"old.js\"> --%>\n<!-- old\n<a href=\"/dead/three.do\">d</a>\n-->\n",
                "<script src=\"a.js\"/>\n<!-- old\n<a href=\"/dead/two.do\">d</a>\n-->\n",
                "<script type=\"text/template\">\n<!-- old\n<a href=\"/dead/four.do\">d</a>\n-->\n</script>\n")) {
            assertEquals(List.of("/live.do"), jsp(head + tail).links().stream().map(JspLinks.Link::url).toList(), head);
        }
    }

    /** 6-85(C2-08·C2-12) — c:set 은 값이 ${…contextPath} 하나일 때만 컨텍스트 이름 · 닫는 > 없는 c:set 반복도 선형 */
    @Test
    void ctxSetNeedsBareContextPath() {
        JspLinks.Result r = jsp("<c:set var=\"adminPath\" value=\"${pageContext.request.contextPath}/admin\"/>\n<a href=\"${adminPath}/user/list.do\">x</a>\n");
        assertEquals(List.of(), r.links());
        assertEquals(List.of("jspUrl ${}/user/list.do"), r.unresolved().stream().map(u -> u.kind() + " " + u.detail()).toList());
        JspLinks.Result ok = jsp("<c:set var=\"ctxRoot\" value=\"${pageContext.request.contextPath}\"/>\n<a href=\"${ctxRoot}/a.do\">x</a>\n");
        assertEquals(List.of(new JspLinks.Link("/a.do", "link")), ok.links());
        // 6-97(C2-11) — 값이 contextPath 식으로 끝나면 컨텍스트 이름(스크립틀릿·앞 글·함수 감싸기) · 뒤에 경로가 붙으면 아니다
        for (String v : List.of("<%=request.getContextPath()%>",
                "${pageContext.request.scheme}://${pageContext.request.serverName}:${pageContext.request.serverPort}${pageContext.request.contextPath}",
                "${fn:escapeXml(pageContext.request.contextPath)}")) {
            assertEquals(List.of(new JspLinks.Link("/user/list.do", "link")),
                    jsp("<c:set var=\"path\" value=\"" + v + "\"/>\n<a href=\"${path}/user/list.do\">x</a>\n").links(), v);
        }
        JspLinks.Result img = jsp("<c:set var=\"img\" value=\"${pageContext.request.contextPath}/images/\"/>\n<a href=\"${img}/a.do\">x</a>\n");
        assertEquals(List.of(), img.links());
        String many = "<c:set var='a' ".repeat(2000);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> JspLinks.ctxNames(many));
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
