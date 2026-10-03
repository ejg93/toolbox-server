package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 7-3 — 생성 한 번: 새 파일 → 둘째 실행은 전부 .gen 옆 파일 · 원본 불변 · 밖을 가리키는 세트 거절 · PK 없는 표 경고 */
class GeneratorTest {

    @TempDir
    Path tmp;

    static GenModel.Options opts() {
        return new GenModel.Options("kr.go.hr", null, List.of("TB"), Map.of(), "oracle");
    }

    @Test
    void createsThenSidecars() throws Exception {
        TemplateSet set = TemplateSet.load(GenTemplatesTest.GEN, "egov35");
        Path out = tmp.resolve("out");
        Files.createDirectories(out);
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        Table noPk = new Table("HR", "LOG", "TABLE", null, GenModelTest.empHist().columns(), null, null, null, null, null, null, null);
        Generator.Result a = Generator.run(set, List.of(GenModelTest.empHist(), noPk), opts(), Map.of(), GenModelTest.TYPES, out, files,
                "UTF-8", "LF", null);
        assertEquals(10, a.files().size());
        assertTrue(a.files().stream().allMatch(f -> f.status().equals("created")), a.files().toString());
        assertTrue(a.warnings().stream().anyMatch(w -> w.contains("LOG") && w.contains("PK 없음")), a.warnings().toString());
        Path ctl = out.resolve("src/main/java/kr/go/hr/emphist/web/EmpHistController.java");
        String before = Files.readString(ctl, StandardCharsets.UTF_8);

        Generator.Result b = Generator.run(set, List.of(GenModelTest.empHist()), opts(), Map.of(), GenModelTest.TYPES, out, files, "UTF-8",
                "LF", null);
        assertTrue(b.files().stream().allMatch(f -> f.status().equals("sidecar") && f.rel().endsWith(".gen")), b.files().toString());
        assertEquals(before, Files.readString(ctl, StandardCharsets.UTF_8), "있는 파일은 안 덮는다");
        assertTrue(Files.exists(out.resolve("src/main/java/kr/go/hr/emphist/web/EmpHistController.java.gen")));
    }

    /** 7-10 — 프로필 인코딩으로 못 쓰는 글자가 든 표는 경고 뒤 건너뛰고, 다른 표는 쓴다(쓰기 단계에서 job 이 깨지지 않게) */
    @Test
    void unencodableTableIsWarnedAndSkipped() throws Exception {
        TemplateSet set = TemplateSet.load(GenTemplatesTest.GEN, "egov35");
        Path out = tmp.resolve("out3");
        Files.createDirectories(out);
        Table e = GenModelTest.empHist();
        Table bad = new Table(e.schema(), "TB_BAD", e.type(), "이모지 😀", e.columns(), e.pk(), e.fks(),
                e.uniques(), e.indexes(), e.rowCount(), e.createdAt(), e.lastDdlAt());
        Generator.Result r = Generator.run(set, List.of(bad, e), opts(), Map.of(), GenModelTest.TYPES, out, new LocalFiles(tmp.resolve("data")),
                "MS949", "CRLF", null);
        assertTrue(r.warnings().stream().anyMatch(w -> w.startsWith("TB_BAD:") && w.endsWith("건너뜀")), r.warnings().toString());
        assertTrue(r.files().stream().noneMatch(f -> f.table().equals("TB_BAD")), r.files().toString());
        assertEquals(10, r.files().size(), "다른 표는 전부 쓴다");
        assertTrue(Files.notExists(out.resolve("src/main/java/kr/go/hr/bad/web/BadController.java")));
    }

    /** 7-11 — MS949 프로필: JSP·XML 의 인코딩 선언이 MS949 이고, 매퍼를 XML 파서로 읽으면 한글 코멘트가 그대로 */
    @Test
    void ms949DeclaresItsEncoding() throws Exception {
        TemplateSet set = TemplateSet.load(GenTemplatesTest.GEN, "egov35");
        Path out = tmp.resolve("out4");
        Files.createDirectories(out);
        Generator.Result r = Generator.run(set, List.of(GenModelTest.empHist()), opts(), Map.of(), GenModelTest.TYPES, out,
                new LocalFiles(tmp.resolve("data")), "MS949", "CRLF", null);
        assertEquals(10, r.files().size(), r.warnings().toString());
        java.nio.charset.Charset ms949 = java.nio.charset.Charset.forName("MS949");
        Path list;
        try (java.util.stream.Stream<Path> s = Files.walk(out)) {
            list = s.filter(p -> p.getFileName().toString().equals("EmpHistList.jsp")).findFirst().orElseThrow();
        }
        String jsp = Files.readString(list, ms949);
        assertTrue(jsp.startsWith("<%@ page contentType=\"text/html; charset=MS949\" pageEncoding=\"MS949\" %>"), jsp.substring(0, 120));
        assertTrue(jsp.contains("<meta charset=\"MS949\">"), "meta");
        Path mapper;
        try (java.util.stream.Stream<Path> s = Files.walk(out)) {
            mapper = s.filter(p -> p.getFileName().toString().endsWith("_SQL_oracle.xml")).findFirst().orElseThrow();
        }
        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbf.setExpandEntityReferences(false);
        org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(mapper.toFile());
        org.w3c.dom.Node first = doc.getFirstChild();
        while (first != null && first.getNodeType() != org.w3c.dom.Node.COMMENT_NODE) {
            first = first.getNextSibling();
        }
        assertTrue(first != null && first.getNodeValue().contains("사원 이력"), "XML 파서가 한글 코멘트를 그대로 읽는다");
        assertEquals("MS949", doc.getXmlEncoding().toUpperCase(java.util.Locale.ROOT));
    }

    @Test
    void refusesSetThatPointsOutside() throws Exception {
        Path gen = tmp.resolve("gen");
        Files.createDirectories(gen.resolve("bad"));
        Files.writeString(gen.resolve("bad/set.yaml"), "name: bad\nfiles:\n  - { template: A.ftl, path: '../../escape-[=Name].txt' }\n");
        Files.writeString(gen.resolve("bad/A.ftl"), "x");
        Path out = tmp.resolve("out2");
        Files.createDirectories(out);
        TemplateSet set = TemplateSet.load(gen, "bad");
        assertThrows(IllegalArgumentException.class, () -> Generator.run(set, List.of(GenModelTest.empHist()), opts(), Map.of(),
                GenModelTest.TYPES, out, new LocalFiles(tmp.resolve("data")), "UTF-8", "LF", null));
        assertTrue(!Files.exists(tmp.resolve("escape-EmpHist.txt")));
    }
}
