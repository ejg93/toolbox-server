package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 설계 50 — 자원 해제 둘. 픽스처 줄을 직접 단언한다(fixturesGolden 의 「안 걸린 규칙」 은 kind java 를 안 센다) */
class ResourceAstTest {

    static final Path SECURITY = Path.of("src/test/resources/fixtures/check/security");
    static final Path JAVA = Path.of("src/test/resources/fixtures/check/java");
    static final Set<String> ON = Set.of(ResourceAst.RESOURCE_LEAK, ResourceAst.TRY_WITH_RESOURCES);
    static final String LEAK = " resourceLeak";
    static final String TWR = " tryWithResources";
    static final String FIN = "} finally {\n";

    static List<String> hits(String code, Set<String> on) {
        CompilationUnit cu = new JavaSource().parse(code).getResult().orElseThrow();
        return ResourceAst.apply(cu, on).stream().map(h -> h.node().getBegin().map(p -> p.line).orElse(0) + " "
                + h.rule().replace("security.", "").replace("java.", "")).toList();
    }

    static List<String> file(Path dir, String name) throws Exception {
        return hits(Files.readString(dir.resolve(name), StandardCharsets.UTF_8), ON);
    }

    @Test
    void leakFixture() throws Exception {
        assertEquals(List.of("89" + LEAK, "90" + LEAK, "93" + LEAK, "94" + LEAK, "96" + LEAK, "98" + LEAK),
                file(SECURITY, "pos.java"));
        assertEquals(List.of(), file(SECURITY, "neg.java"));
    }

    @Test
    void tryWithResourcesFixture() throws Exception {
        assertEquals(List.of("18" + TWR, "31" + TWR, "49" + TWR, "58" + TWR), file(JAVA, "PosResource.java"));
        assertEquals(List.of(), file(JAVA, "NegResource.java"));
    }

    @Test
    void foreignStatementImportIsNotResource() {
        assertEquals(List.of(), hits("import com.github.javaparser.ast.stmt.Statement;\n"
                + "class A { void f(Node n) { Statement s = n.find(); s.toString(); } }", ON));
        assertEquals(List.of("2" + LEAK), hits("import java.sql.Statement;\n"
                + "class A { void f(Connection c) throws Exception { Statement s = c.createStatement(); s.execute(); } }", ON));
    }

    @Test
    void nestedTypeAndConstructorHandOver() {
        assertEquals(List.of(), hits("class A { void f(String id) { Profile.Connection c = find(id).orElseThrow(); c.url(); } }", ON));
        assertEquals(List.of(), hits("class A { static A open(String u) throws Exception { Connection k;\n"
                + " try { k = DriverManager.getConnection(u); } catch (SQLException e) { throw e; }\n A a = new A(u, k); return a; } }", ON));
        assertEquals(List.of("1" + LEAK), hits("class A { void f(String u) throws Exception {"
                + " java.sql.Connection c = DriverManager.getConnection(u); c.commit(); } }", ON));
    }

    /** R-15 — 메모리 new 를 넓은 형식에 담음 · 래퍼 속 래퍼가 빌린 것 · 보관소 get */
    @Test
    void borrowedThroughWrappersAndLookups() {
        assertEquals(List.of(), hits("class A { void f(byte[] b) { InputStream in = new ByteArrayInputStream(b); Writer w = new StringWriter();"
                + " in.read(); w.flush(); } }", ON));
        assertEquals(List.of(), hits("class A { void f(InputStream inStream, javax.servlet.http.HttpServletResponse response) throws Exception {"
                + " BufferedReader in = new BufferedReader(new InputStreamReader(inStream, \"8859_1\"));"
                + " PrintWriter pw = new PrintWriter(response.getOutputStream()); in.readLine(); pw.flush(); } }", ON));
        assertEquals(List.of(), hits("class A { static final ThreadLocal<OutputStream> L = new ThreadLocal<>(); void f() throws Exception {"
                + " OutputStream out = L.get(); out.flush(); } }", ON));
        assertEquals(List.of("1" + LEAK), hits("class A { void f(java.net.URLConnection conn) throws Exception {"
                + " OutputStreamWriter w = new OutputStreamWriter(conn.getOutputStream()); w.flush(); } }", ON));
    }

    @Test
    void closeOutsideFinallyLeaks() {
        assertEquals(List.of("1" + LEAK), hits("class A { void f(String p) throws Exception {"
                + " InputStream in = new FileInputStream(p); in.read(); in.close(); } }", ON));
    }

    @Test
    void uncoveredCloseExceptionIsNotTwr() {
        assertEquals(List.of(), hits("class A { void f(String u) {\n Connection c = null;\n try {\n"
                + " c = DriverManager.getConnection(u);\n c.commit();\n } catch (IOException e) {\n e.toString();\n"
                + FIN + " c.close();\n }\n } }", ON));
    }

    @Test
    void reassignedInTryIsNotTwr() {
        assertEquals(List.of(), hits("class A { void f(String p) throws Exception {\n InputStream in = null;\n try {\n"
                + " in = new FileInputStream(p);\n in = new FileInputStream(p + 1);\n in.read();\n"
                + FIN + " in.close();\n }\n } }", ON));
    }

    @Test
    void offRulesAreSilent() {
        assertEquals(List.of(), hits("class A { void f(String p) throws Exception { InputStream in = new FileInputStream(p); } }",
                Set.of(ResourceAst.TRY_WITH_RESOURCES)));
    }
}
