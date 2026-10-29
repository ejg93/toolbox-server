package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ast.CompilationUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 5-51a(설계 42 §3-2~5) — 오염 추적 엔진. 규칙 id·줄만 본다 */
class TaintTest {

    static final Set<String> ALL = Set.of("security.osCommand", "security.pathTraversal", "security.openRedirect",
            "security.responseSplitting", "security.ssrf", "security.sqlInjection", "security.xssServlet", "security.ldapInjection",
            "security.xpathInjection", "security.trustBoundary", "security.cookieSecureFlag");

    static List<String> hits(String body) {
        return hitsIn("class A { String adminPath; void f(javax.servlet.http.HttpServletRequest request, "
                + "javax.servlet.http.HttpServletResponse response, java.util.Map<String, String> map, boolean cond) throws Exception {\n"
                + body + "\n} }");
    }

    static List<String> hitsIn(String code) {
        CompilationUnit cu = new JavaSource().parse(code).getResult().orElseThrow();
        return new Taint(ALL).scan(cu).stream()
                .map(h -> h.node().getBegin().map(p -> p.line).orElse(0) + " " + h.rule().replace("security.", "")).toList();
    }

    @Test
    void direct() {
        assertEquals(List.of("2 pathTraversal"), hits("new java.io.File(request.getParameter(\"p\"));"));
    }

    @Test
    void throughVariable() {
        assertEquals(List.of("3 pathTraversal"), hits("String p = request.getHeader(\"x\");\nnew java.io.FileInputStream(p);"));
    }

    @Test
    void cookieArray() {
        assertEquals(List.of("4 pathTraversal"),
                hits("javax.servlet.http.Cookie[] cs = request.getCookies();\nString v = cs[0].getValue();\nnew java.io.File(v);"));
    }

    @Test
    void stringBuilderMutator() {
        assertEquals(List.of("4 osCommand"), hits("StringBuilder sb = new StringBuilder(\"ls \");\nsb.append(request.getParameter(\"d\"));\n"
                + "Runtime.getRuntime().exec(sb.toString());"));
    }

    @Test
    void constantIsClean() {
        assertEquals(List.of(), hits("String p = \"fixed\";\nnew java.io.File(p);"));
    }

    @Test
    void sanitizerCleans() {
        assertEquals(List.of(), hits("int n = Integer.parseInt(request.getParameter(\"n\"));\nnew java.io.File(\"f\" + n);"));
    }

    @Test
    void allowListLookupIsClean() {
        assertEquals(List.of(), hits("String v = map.get(request.getParameter(\"k\"));\nnew java.io.File(v);"));
    }

    @Test
    void controllerParameterIsSource() {
        assertEquals(List.of("3 pathTraversal"), hitsIn("@Controller class C {\n@GetMapping public String f(String name, Model m) {\n"
                + "new java.io.File(name); new java.io.File(m.toString()); return \"v\"; } }"));
    }

    @Test
    void plainMethodParameterIsNotSource() {
        assertEquals(List.of(), hitsIn("class S { void f(String name) { new java.io.File(name); } }"));
    }

    @Test
    void bindingAnnotationIsSource() {
        assertEquals(List.of("1 osCommand"), hitsIn("class S { void f(@RequestParam String c) { Runtime.getRuntime().exec(c); } }"));
    }

    @Test
    void conditionalEitherBranch() {
        assertEquals(List.of("3 pathTraversal"), hits("String bar = cond ? \"safe\" : request.getParameter(\"p\");\nnew java.io.File(bar);"));
    }

    @Test
    void branchAssignmentIsFlowInsensitive() {
        assertEquals(List.of("3 pathTraversal"),
                hits("String bar;\nif (cond) bar = \"a\"; else bar = request.getParameter(\"p\"); new java.io.File(bar);"));
    }

    @Test
    void laterAssignmentStillTaints() {
        assertEquals(List.of("3 pathTraversal"), hits("String a = \"x\";\nnew java.io.File(a);\na = request.getParameter(\"p\");"));
    }

    @Test
    void writerVariableAndDirect() {
        assertEquals(List.of("3 xssServlet", "4 xssServlet"), hits("java.io.PrintWriter w = response.getWriter();\n"
                + "w.print(request.getParameter(\"m\"));\nresponse.getWriter().format(\"%s\", request.getParameter(\"m\"));\nw.print(\"ok\");"));
    }

    @Test
    void sendErrorMessage() {
        assertEquals(List.of("2 xssServlet"), hits("response.sendError(404, \"no \" + request.getParameter(\"q\"));"));
    }

    @Test
    void sessionAttribute() {
        assertEquals(List.of("2 trustBoundary"), hits("request.getSession().setAttribute(\"u\", request.getParameter(\"u\"));"));
    }

    @Test
    void redirectConcat() {
        assertEquals(List.of("1 openRedirect"),
                hitsIn("class C { String f(@RequestParam String next) { return \"redirect:\" + next; } String g() { return \"redirect:\" + adminPath; } }"));
    }

    @Test
    void sqlSinksAndScope() {
        assertEquals(List.of("3 sqlInjection"), hits("String p = request.getParameter(\"p\");\n"
                + "stmt.executeQuery(\"SELECT * FROM t WHERE a='\" + p + \"'\");\njdbcTemplate.update(\"UPDATE t SET a=?\", p);\ndao.update(p);"));
    }

    @Test
    void ldapAndXpath() {
        assertEquals(List.of("3 ldapInjection", "4 xpathInjection"), hits("String f = request.getParameter(\"f\");\n"
                + "ctx.search(\"ou=people\", f, sc);\nxpath.evaluate(f, doc);\nctx.search(\"ou=people\", \"(uid=admin)\", sc);"));
    }

    @Test
    void cookieFlags() {
        assertEquals(List.of("2 cookieSecureFlag"), hits("c.setSecure(false);\nc.setSecure(true);\nc.setHttpOnly(Boolean.TRUE);"));
    }

    @Test
    void headerAndCookieValue() {
        assertEquals(List.of("3 responseSplitting"),
                hits("String lang = request.getParameter(\"l\");\nresponse.addCookie(new javax.servlet.http.Cookie(\"lang\", lang));"));
    }

    @Test
    void lambdaBodyIsSkipped() {
        assertEquals(List.of(), hits("Runnable r = () -> new java.io.File(request.getParameter(\"p\"));"));
    }

    @Test
    void pathsGetNeedsPathsScope() {
        assertEquals(List.of("2 pathTraversal"), hits("java.nio.file.Paths.get(\"/a\", request.getParameter(\"p\"));\nlist.get(request.getParameter(\"i\"));"));
    }

    @Test
    void wantsOnlyTaintRules() {
        assertEquals(true, Taint.wants(Set.of("security.ssrf")));
        assertEquals(false, Taint.wants(Set.of("java.unusedImport")));
    }
}
