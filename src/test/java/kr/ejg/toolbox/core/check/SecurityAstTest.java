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
                "60" + T, "61" + T, "62" + T, "64" + T, "65" + T, "69" + T,
                "75" + X, "77" + X, "79" + X, "81" + T, "82" + T,
                "104" + X, "109" + X, "113" + X, "114" + X, "118" + X, "138" + X), file("pos.java"));
    }

    /** 5-65 — 파일이 Cyberneko 를 import 하면 인자 있는 SAXBuilder(HTML 파서를 준다)는 조용, 인자 없는 것은 그대로 */
    @Test
    void nekoBuilderWithArgsIsSilent() {
        assertEquals(List.of("2" + X), hits("import org.codelibs.nekohtml.parsers.SAXParser; class A { void f() {"
                + " SAXBuilder a = new SAXBuilder(new XMLReaderSAX2Factory(true, SAXParser.class.getName()), null, null);\n"
                + " SAXBuilder b = new SAXBuilder(); } }"));
    }

    /** 5-65 — Cyberneko 제외는 jdom SAXBuilder 만(설계 59 §3-4). 같은 파일의 dom4j SAXReader 는 인자가 있어도 그대로 지적 */
    @Test
    void nekoImportDoesNotSilenceSaxReader() {
        assertEquals(List.of("1" + X), hits("import org.codelibs.nekohtml.parsers.SAXParser; class A { void f(XMLReader r) {"
                + " SAXReader a = new SAXReader(r); } }"));
    }

    /** 5-65 — setEntityResolver 만으로는 막음이 아니다(사용자 2026-11-08) — 그 팩토리는 그대로 지적 */
    @Test
    void entityResolverAloneIsNotGuard() {
        assertEquals(List.of("1" + X), hits("class A { void f() throws Exception { DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();"
                + " DocumentBuilder b = f.newDocumentBuilder(); b.setEntityResolver((p, s) -> null); } }"));
    }

    /** 5-64 ③ — 매개변수에 대입한 팩토리는 그 메서드 범위 — 다른 메서드의 같은 이름 지역 막음을 안 받는다 */
    @Test
    void parameterIsMethodScope() {
        assertEquals(List.of("1" + X), hits("class A { void p(DocumentBuilderFactory f) { f = DocumentBuilderFactory.newInstance(); }\n"
                + " void q() { DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();"
                + " f.setFeature(\"http://apache.org/xml/features/disallow-doctype-decl\", true); } }"));
    }

    /** 5-61a — 막음은 변수 단위: 한 메서드에 팩토리 둘이면 안 막은 것만 */
    @Test
    void twoFactoriesGuardedSeparately() {
        assertEquals(List.of("2" + X), hits("class A { void f() { DocumentBuilderFactory a = DocumentBuilderFactory.newInstance(); "
                + "a.setFeature(\"http://apache.org/xml/features/disallow-doctype-decl\", true);\n"
                + " DocumentBuilderFactory b = DocumentBuilderFactory.newInstance(); } }"));
    }

    /** 마무리 리뷰 — 팩토리에서 이은 객체(spf.newSAXParser().getXMLReader())의 막음도 그 팩토리의 막음(OWASP 꼴). 다른 팩토리에서 이은 것은 아니다 */
    @Test
    void derivedObjectGuardCounts() {
        assertEquals(List.of("3" + X), hits("class A { void f() throws Exception { SAXParserFactory spf = SAXParserFactory.newInstance();\n"
                + " XMLReader r = spf.newSAXParser().getXMLReader(); r.setFeature(\"http://xml.org/sax/features/external-general-entities\", false);\n"
                + " SAXParserFactory g = SAXParserFactory.newInstance(); SAXParserFactory h = SAXParserFactory.newInstance(); XMLReader q = h.newSAXParser().getXMLReader();"
                + " q.setFeature(\"http://xml.org/sax/features/external-general-entities\", false); h.toString(); } }"));
    }

    /** 5-61a — 필드 팩토리는 클래스 범위에서 그 변수의 막음(static 블록 포함) */
    @Test
    void fieldGuardedInStaticBlock() {
        assertEquals(List.of("3" + X), hits("class A { static final DocumentBuilderFactory F = DocumentBuilderFactory.newInstance();\n"
                + " static { F.setFeature(\"http://apache.org/xml/features/disallow-doctype-decl\", true); }\n"
                + " static final DocumentBuilderFactory G = DocumentBuilderFactory.newInstance(); }"));
    }

    /** 5-61a — 변환기가 무인자 newTransformer + transform 첫 인자가 전부 DOMSource 면 조용. transform 이 없으면 지적 그대로 */
    @Test
    void domOnlyTransformerIsSilent() {
        assertEquals(List.of("3" + X), hits("class A { void f(Document d, Writer w) throws Exception { DOMSource s = new DOMSource(d); "
                + "TransformerFactory tf = TransformerFactory.newInstance(); tf.newTransformer().transform(s, new StreamResult(w)); }\n"
                + " void g(Document d, StreamResult r) throws Exception { TransformerFactory t1 = TransformerFactory.newInstance(); t1.newTransformer().transform(new DOMSource(d), r); }\n"
                + " void h() { TransformerFactory t2 = TransformerFactory.newInstance(); t2.newTransformer(); } }"));
    }

    /** 5-61a — dom4j SAXReader·jdom SAXBuilder 의 new 도 앵커. jdom setExpandEntities(false) 는 막음 */
    @Test
    void dom4jJdomAreAnchors() {
        assertEquals(List.of("1" + X, "4" + X), hits("class A { void f(java.io.File x) throws Exception { SAXReader r = new SAXReader();\n"
                + " SAXBuilder b = new SAXBuilder(); b.setExpandEntities(false);\n"
                + " }\n void g(java.io.File x) throws Exception { new SAXReader().read(x); } }"));
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
