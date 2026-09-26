package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 1-9 — 골든 스냅샷 테이블 셋 × 모양 셋, 타입 매핑, 이름 규칙, 생성 소스가 실제로 컴파일되는지 */
class DtoGeneratorTest {

    static final TypeMapping TYPES = TypeMapping.load();
    static final DtoGenerator GEN = new DtoGenerator(TYPES);

    @TempDir
    Path tmp;

    static Table table(String golden, String name) throws Exception {
        for (Schema s : GoldenFiles.schemas("meta/" + golden + ".json")) {
            for (Table t : s.tables()) {
                if (t.name().equalsIgnoreCase(name)) {
                    return t;
                }
            }
        }
        throw new IllegalArgumentException(name);
    }

    static DtoGenerator.Source gen(String golden, String name, String style, String dialect) throws Exception {
        return GEN.generate(table(golden, name), new DtoGenerator.Options("com.example.dto", DtoGenerator.Style.of(style), List.of("TB"),
                Map.of("EMAIL", "이메일", "LOGIN_ID", "(쓰이지 않음 — 코멘트가 먼저)"), Map.of(), dialect));
    }

    @ParameterizedTest
    @CsvSource({
        "postgres-vendor, products, record, postgresql", "postgres-vendor, products, bean, postgresql",
        "postgres-vendor, products, egovVo, postgresql", "postgres-vendor, users, record, postgresql",
        "postgres-vendor, users, bean, postgresql", "postgres-vendor, users, egovVo, postgresql",
        "oracle, ORDER_ITEMS, record, oracle", "oracle, ORDER_ITEMS, bean, oracle", "oracle, ORDER_ITEMS, egovVo, oracle"})
    void goldenAndCompiles(String golden, String name, String style, String dialect) throws Exception {
        DtoGenerator.Source src = gen(golden, name, style, dialect);
        GoldenFiles.assertText("gen/" + name.toLowerCase(java.util.Locale.ROOT) + "-" + style.toLowerCase(java.util.Locale.ROOT) + ".java",
                src.text());
        compile(src);
    }

    void compile(DtoGenerator.Source src) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        Assumptions.assumeTrue(javac != null, "테스트 JVM 이 JRE 라 컴파일러가 없다 — 이 케이스만 건너뛴다(행의 사다리)");
        Path file = tmp.resolve(src.className() + ".java");
        Files.writeString(file, src.text(), StandardCharsets.UTF_8);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int rc = javac.run(null, null, new PrintStream(err, true, StandardCharsets.UTF_8), "-encoding", "UTF-8", "-d",
                tmp.resolve("out").toString(), file.toString());
        assertEquals(0, rc, src.className() + " 컴파일 실패" + System.lineSeparator() + err.toString(StandardCharsets.UTF_8)
                + System.lineSeparator() + src.text());
    }

    static Column col(String nativeType, Integer jdbc, Integer precision, Integer scale) {
        return new Column("C", 1, nativeType, jdbc, null, precision, scale, true, null, null, null);
    }

    @Test
    void typeMapping() {
        assertEquals("Long", TYPES.javaType(col("NUMBER", Types.NUMERIC, 19, 0), "oracle"));
        assertEquals("java.math.BigDecimal", TYPES.javaType(col("NUMBER", Types.NUMERIC, 14, 2), "oracle"));
        assertEquals("Long", TYPES.javaType(col("NUMBER", Types.NUMERIC, 10, 0), "oracle"), "정밀도 10 은 int 를 넘는다");
        assertEquals("Integer", TYPES.javaType(col("NUMBER", Types.NUMERIC, 5, 0), "oracle"));
        assertEquals("java.math.BigDecimal", TYPES.javaType(col("NUMBER", Types.NUMERIC, null, null), "oracle"), "정밀도 없는 NUMBER");
        assertEquals("String", TYPES.javaType(col("VARCHAR2", Types.VARCHAR, null, null), "oracle"));
        assertEquals("java.time.LocalDateTime", TYPES.javaType(col("DATE", Types.TIMESTAMP, null, null), "oracle"), "Oracle DATE 는 시각이 있다");
        assertEquals("java.time.LocalDate", TYPES.javaType(col("date", Types.DATE, null, null), "postgresql"));
        assertEquals("java.time.LocalDateTime", TYPES.javaType(col("timestamp(6) with time zone", null, null, null), null));
        assertEquals("byte[]", TYPES.javaType(col("bytea", Types.BINARY, null, null), "postgresql"));
        assertEquals("Boolean", TYPES.javaType(col("bit", Types.BIT, null, null), "mssql"));
        assertEquals("String", TYPES.javaType(col("CHAR", Types.CHAR, null, null), "oracle"), "_YN CHAR(1) 도 String");
        assertEquals("Integer", TYPES.javaType(col("mystery", Types.INTEGER, null, null), null), "native 을 모르면 jdbcType");
        assertNull(TYPES.javaType(col("geometry", Types.OTHER, null, null), null), "끝내 모르면 null → Object + TODO");
        assertEquals("oracle", TypeMapping.dialectOf("Oracle Database 23ai Free Release 23.0.0.0.0"));
        assertEquals("tibero", TypeMapping.dialectOf("Tibero 7"));
    }

    @Test
    void namingRules() {
        assertEquals("custMstNo", DtoGenerator.fieldName("CUST_MST_NO"));
        assertEquals("a_b", DtoGenerator.fieldName("A__B"), "순수본 toCamel: 「__b」 는 「_」 하나만 먹는다");
        assertEquals("class_", DtoGenerator.fieldName("CLASS"), "자바 키워드");
        assertEquals("_1st", DtoGenerator.fieldName("1ST"));
        assertEquals("CustMst", DtoGenerator.className("TB_CUST_MST", List.of("TB")));
        assertEquals("Tb", DtoGenerator.className("TB", List.of("TB")), "전부 떼지면 원래 이름");
        assertEquals("OrderItems", DtoGenerator.className("order_items", List.of()));
        assertEquals("a *&#47; b", DtoGenerator.doc("a */ b"));
    }

    @Test
    void quotedTableNameCannotCloseJavadoc() throws Exception {
        DdlReader.Result r = DdlReader.read("CREATE TABLE \"a*/b\" (ID INT)");
        DtoGenerator.Source s = GEN.generate(r.tables().get(0), new DtoGenerator.Options(null, DtoGenerator.Style.RECORD, List.of(), Map.of(),
                Map.of(), null));
        assertTrue(s.text().contains("테이블: a*&#47;b"), s.text());
        compile(s);
    }

    @Test
    void unknownTypeAndDdlNotesBecomeTodoButStillCompile() throws Exception {
        DdlReader.Result r = DdlReader.read("CREATE TABLE TB_GEO (ID BIGINT PRIMARY KEY, SHAPE geometry, 9x oops, CLASS VARCHAR(10))");
        Table t = r.tables().get(0);
        for (String style : List.of("record", "bean", "egovVo")) {
            DtoGenerator.Source s = GEN.generate(t, new DtoGenerator.Options(null, DtoGenerator.Style.of(style), List.of("TB"), Map.of(),
                    r.notes().get("TB_GEO"), null));
            assertTrue(s.text().contains("TODO 타입 확인: geometry"), s.text());
            assertTrue(s.text().contains("TODO 못 읽음: 9x oops"), s.text());
            assertTrue(s.text().contains("class_"), s.text());
            compile(s);
        }
    }
}
