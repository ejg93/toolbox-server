package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;
import org.junit.jupiter.api.Test;

/** 1-31a — 테이블 제약 → 검증 어노테이션·javadoc 조각(설계 16 D7·D8) */
class ValidationTest {

    static Column col(String name, String type, Long len, Integer p, Integer s, boolean nullable, String def) {
        return new Column(name, 0, type, null, len, p, s, nullable, def, null, null);
    }

    static Table users(Check... checks) {
        return Table.of("S", "USERS", "TABLE", null)
                .withColumns(List.of(col("USER_ID", "NUMBER", null, 19, 0, false, null), col("LOGIN_ID", "VARCHAR2", 50L, null, null, false, null),
                        col("EMAIL", "VARCHAR2", 200L, null, null, true, null), col("USE_YN", "CHAR", 1L, null, null, false, "'Y'"),
                        col("AMT", "NUMBER", null, 12, 2, false, null), col("QTY", "NUMBER", null, 10, 0, true, null),
                        col("DEPT_ID", "NUMBER", null, 10, 0, true, null), col("STATUS", "VARCHAR2", 10L, null, null, true, null),
                        col("START_DT", "DATE", null, null, null, true, null), col("END_DT", "DATE", null, null, null, true, null),
                        col("RATE", "NUMBER", null, 5, 2, true, null)))
                .withConstraints(new PrimaryKey("PK", List.of("USER_ID")),
                        List.of(new ForeignKey("FK_DEPT", List.of("DEPT_ID"), null, "DEPT", List.of("ID"))),
                        List.of(new UniqueKey("UK_LOGIN", List.of("LOGIN_ID"))))
                .withChecks(List.of(checks));
    }

    static Validation.Result of(Table t, String column, String javaType, String dialect) {
        Column c = t.columns().stream().filter(x -> x.name().equals(column)).findFirst().orElseThrow();
        return Validation.of(t, c, javaType, dialect, Validation.Ns.JAVAX);
    }

    @Test
    void notNullSizeDigitsKeysAndBytes() {
        Table t = users();
        assertEquals(List.of(), of(t, "USER_ID", "Long", "oracle").annotations().stream().filter(a -> a.startsWith("@NotNull")).toList(),
                "PK 는 나중에 채워질 수 있어 NotNull 을 안 붙인다");
        assertEquals(List.of("@Digits(integer = 19, fraction = 0)"), of(t, "USER_ID", "Long", "oracle").annotations());
        Validation.Result login = of(t, "LOGIN_ID", "String", "oracle");
        assertEquals(List.of("@NotBlank", "@Size(max = 50)"), login.annotations());
        assertTrue(login.notes().contains("UNIQUE") && login.notes().stream().anyMatch(n -> n.contains("바이트")), login.notes().toString());
        assertEquals(List.of("@Size(max = 200)"), of(t, "EMAIL", "String", "postgresql").annotations());
        assertTrue(of(t, "EMAIL", "String", "postgresql").notes().isEmpty(), "바이트 안내는 oracle·tibero 만");
        assertEquals(List.of("@Size(max = 1)"), of(t, "USE_YN", "String", null).annotations(), "DEFAULT 가 있으면 NotBlank 없음");
        assertEquals(List.of("@NotNull", "@Digits(integer = 10, fraction = 2)"), of(t, "AMT", "java.math.BigDecimal", null).annotations());
        assertEquals(List.of("FK → DEPT(ID)"), of(t, "DEPT_ID", "Long", null).notes());
        assertEquals(List.of(), of(t, "RATE", "Double", null).annotations(), "double 은 Digits 를 규격이 안 받는다");
        assertEquals(java.util.Set.of("javax.validation.constraints.NotBlank", "javax.validation.constraints.Size"), login.imports());
        Column c = t.columns().get(1);
        assertTrue(Validation.of(t, c, "String", null, Validation.Ns.JAKARTA).imports().stream().allMatch(i -> i.startsWith("jakarta.")));
        assertEquals(Validation.Result.NONE, Validation.of(t, c, "String", null, null), "끔");
        assertEquals(Validation.Ns.JAKARTA, Validation.nsOf("egov5"));
        assertEquals(Validation.Ns.JAVAX, Validation.nsOf("egov4"));
        assertEquals(Validation.Ns.JAVAX, Validation.nsOf(null));
    }

    @Test
    void oracleCheckForms() {
        Table t = users(new Check("CK1", "QTY BETWEEN 0 AND 100"), new Check("CK2", "\"STATUS\" IN ('A', 'B.C', 'D|E')"),
                new Check("SYS_C1", "\"EMAIL\" IS NOT NULL"));
        assertEquals(List.of("@Digits(integer = 10, fraction = 0)", "@Min(0)", "@Max(100)"), of(t, "QTY", "Long", "oracle").annotations());
        Validation.Result st = of(t, "STATUS", "String", "oracle");
        assertEquals(List.of("@Size(max = 10)", "@Pattern(regexp = \"^(A|B\\\\.C|D\\\\|E)$\")"), st.annotations());
        assertTrue(st.notes().contains("허용 값: 'A', 'B.C', 'D|E'"), st.notes().toString());
        assertEquals(List.of("@Size(max = 200)"), of(t, "EMAIL", "String", "postgresql").annotations(), "IS NOT NULL 만인 CHECK 는 무시");
        assertTrue(of(t, "EMAIL", "String", "postgresql").notes().isEmpty());
    }

    /** A-19 — int 를 넘는 경계는 L, long 을 넘으면 글자 꼴 @DecimalMax — 생성 소스가 컴파일된다 */
    @Test
    void wideBoundsCompile() {
        Table t = users(new Check("CK1", "QTY BETWEEN 0 AND 3000000000"));
        assertEquals(List.of("@Digits(integer = 10, fraction = 0)", "@Min(0)", "@Max(3000000000L)"), of(t, "QTY", "Long", "oracle").annotations());
        Table big = users(new Check("CK2", "QTY <= 99999999999999999999"));
        assertTrue(of(big, "QTY", "BigInteger", "oracle").annotations().contains("@DecimalMax(\"99999999999999999999\")"),
                of(big, "QTY", "BigInteger", "oracle").annotations().toString());
    }

    @Test
    void postgresCheckForms() {
        Table t = users(new Check("ck_status", "((status)::text = ANY ((ARRAY['A'::character varying, 'B'::character varying])::text[]))"),
                new Check("ck_qty", "(qty >= 0)"), new Check("ck_amt", "(amt > (0)::numeric)"));
        assertEquals("@Pattern(regexp = \"^(A|B)$\")", of(t, "STATUS", "String", null).annotations().get(1));
        assertEquals(List.of("@Digits(integer = 10, fraction = 0)", "@Min(0)"), of(t, "QTY", "Long", null).annotations());
        assertTrue(of(t, "AMT", "BigDecimal", null).annotations().contains("@DecimalMin(value = \"0\", inclusive = false)"),
                of(t, "AMT", "BigDecimal", null).annotations().toString());
    }

    /** 4-47(A2-06) — 리터럴 안 :: 는 캐스트가 아니다 */
    @Test
    void castInsideLiteralIsKept() {
        Table p = users(new Check("ck_st", "((status)::text = ANY (ARRAY['A'::character varying]))"));
        assertEquals("[@Size(max = 10)]", of(p, "STATUS", "String", null).annotations().toString(), "지킴이 — 지금과 같다");
        Table t = users(new Check("ck_kind", "status IN ('std::string', 'x')"));
        assertEquals("@Pattern(regexp = \"^(std::string|x)$\")", of(t, "STATUS", "String", null).annotations().get(1));
    }

    @Test
    void mssqlAndMariaCheckForms() {
        Table t = users(new Check("CK_Q", "([QTY]>=(0) AND [QTY]<=(100))"), new Check("CK_S", "([STATUS]='Y' OR [STATUS]='N')"),
                new Check("CK_A", "`amt` < 1000"));
        assertEquals(List.of("@Digits(integer = 10, fraction = 0)", "@Min(0)", "@Max(100)"), of(t, "QTY", "Integer", null).annotations());
        assertEquals("@Pattern(regexp = \"^(Y|N)$\")", of(t, "STATUS", "String", null).annotations().get(1));
        assertTrue(of(t, "AMT", "BigDecimal", null).annotations().contains("@DecimalMax(value = \"1000\", inclusive = false)"));
        Table m = users(new Check("q", "`qty` > 0"));
        assertTrue(of(m, "QTY", "Long", null).annotations().contains("@Min(1)"), "정수형 배타 하한은 +1");
    }

    @Test
    void complexChecksBecomeNotesAndTodo() {
        Table t = users(new Check("CK_DT", "START_DT <= END_DT"), new Check("CK_F", "UPPER(STATUS) <> STATUS"));
        for (String c : List.of("START_DT", "END_DT")) {
            Validation.Result r = of(t, c, "LocalDate", null);
            assertEquals(List.of("CHECK: START_DT <= END_DT"), r.notes(), c);
            assertEquals(List.of("CHECK 를 코드로 옮긴다"), r.todos(), c);
        }
        Validation.Result st = of(t, "STATUS", "String", null);
        assertEquals(List.of("@Size(max = 10)"), st.annotations());
        assertEquals(List.of("CHECK: UPPER(STATUS) <> STATUS"), st.notes());
    }

    @Test
    void parseKeepsIdentifiersWithOrAndInside() {
        Object r = Validation.parse("COLOR_OR_SIZE >= 1 AND COLOR_OR_SIZE <= 3");
        assertEquals(new Validation.Range("COLOR_OR_SIZE", new java.math.BigDecimal("1"), true, new java.math.BigDecimal("3"), true), r);
    }

    /** 0-85h — 붙여 넣은 DDL 의 긴 CHECK 리터럴이 교대 반복 재귀로 스택을 넘기지 않는다(소유 수량자) */
    @Test
    void longLiteralDoesNotOverflow() {
        String lit = "'" + "a".repeat(200_000) + "'";
        Table t = new Table(null, "T", "TABLE", null, List.of(new Column("C", 1, "VARCHAR2", null, 10L, null, null, true, null, null, null)),
                null, List.of(), List.of(), List.of(), null, null, null);
        assertEquals(List.of("C"), Validation.columnsIn("C IN (" + lit + ")", t));
        assertTrue(Validation.normalize("(C = (" + lit + "))").startsWith("C = "));
    }

    /** 1-96(A-10) — 긴 ANY(ARRAY[ 줄기도 한 번 훑기 */
    @Test
    void longAnyArrayIsLinear() {
        String in = "x = ANY (ARRAY[".repeat(20_000);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> Validation.normalize(in));
    }

    /** 1-96(A-10) — 컬럼 5,000 · CHECK 200 표의 DTO 가 컬럼 × CHECK × 컬럼으로 안 돈다 */
    @Test
    void manyColumnsWithChecksAreLinear() {
        List<Column> cols = new ArrayList<>();
        List<Check> checks = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            cols.add(new Column("C" + i, i + 1, "INT", null, null, 10, 0, true, null, null, null));
        }
        for (int i = 0; i < 200; i++) {
            checks.add(new Check("CK" + i, "C" + i + " >= 0"));
        }
        Table t = Table.of("S", "WIDE", "TABLE", null).withColumns(cols).withChecks(checks);
        DtoGenerator.Source src = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> DtoGeneratorTest.GEN.generate(t,
                new DtoGenerator.Options(null, DtoGenerator.Style.RECORD, List.of(), Map.of(), Map.of(), null, Validation.Ns.JAVAX)));
        assertTrue(src.text().contains("@Min(0)"), src.text().substring(0, 400));
    }
}
