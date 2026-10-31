package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 설계 48 — XXE·인증서 검증 끔 판정. 픽스처 줄을 직접 단언한다(fixturesGolden 의 「안 걸린 규칙」 은 kind java 를 안 센다) */
class SecurityAstTest {

    static final Path DIR = Path.of("src/test/resources/fixtures/check/security");
    static final Set<String> ON = Set.of(SecurityAst.XXE, SecurityAst.TRUST_ALL);
    static final String X = " xxe";
    static final String T = " trustAll";

    static List<String> hits(String code) {
        CompilationUnit cu = new JavaSource().parse(code).getResult().orElseThrow();
        return SecurityAst.apply(cu, ON).stream()
                .map(h -> h.node().getBegin().map(p -> p.line).orElse(0) + " " + h.rule().replace("security.", "")).toList();
    }

    static List<String> file(String name) throws Exception {
        return hits(Files.readString(DIR.resolve(name), StandardCharsets.UTF_8));
    }

    @Test
    void positiveFixture() throws Exception {
        assertEquals(List.of("43" + X, "44" + X, "45" + X, "46" + X, "47" + X, "48" + X, "53" + X,
                "60" + T, "61" + T, "62" + T, "64" + T, "65" + T, "69" + T), file("pos.java"));
    }

    @Test
    void negativeFixture() throws Exception {
        assertEquals(List.of(), file("neg.java"));
    }

    @Test
    void staticBlockAndChain() {
        assertEquals(List.of("1" + X, "3" + X), hits("class A { static { F = TransformerFactory.newInstance(); }\n"
                + " void f() { DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument(); }\n"
                + " void g(java.io.InputStream in) { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in); } }"));
    }

    @Test
    void hopNeedsGuardInHelper() {
        assertEquals(List.of("1" + X), hits("class A { void f() { DocumentBuilderFactory d = DocumentBuilderFactory.newInstance(); h(d); }\n"
                + " void h(DocumentBuilderFactory d) { d.setNamespaceAware(true); } }"));
    }

    @Test
    void offRulesAreSilent() {
        CompilationUnit cu = new JavaSource().parse("class A { void f() { SAXParserFactory.newInstance(); } }").getResult().orElseThrow();
        assertEquals(List.of(), SecurityAst.apply(cu, Set.of(SecurityAst.TRUST_ALL)));
    }
}
