package kr.ejg.toolbox.core.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.dict.Dictionaries;
import kr.ejg.toolbox.core.logical.Audit.Finding;
import kr.ejg.toolbox.core.logical.Audit.Rule;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Test;

/**
 * 3-7 — 규칙 다섯 각각 양성·음성. 사전은 3-2 샘플(행안부 + 기관), 사용자 사전만 한 줄 더한다.
 * TB_NEG 는 전부 지키는 테이블, TB_QPX 는 규칙마다 걸리는 컬럼을 하나씩 둔다.
 */
class AuditTest {

    static final Set<Rule> ALL = EnumSet.allOf(Rule.class);

    static Column col(String name, int ord, String type, long len, String comment) {
        return new Column(name, ord, type, type.equals("CHAR") ? 1 : 12, len, null, null, true, null, comment, null);
    }

    static Table table(String name, String comment, Column... cols) {
        return Table.of("S", name, "TABLE", comment).withColumns(Arrays.asList(cols));
    }

    static List<Schema> fixture() {
        return List.of(new Schema("S", null, List.of(
                table("TB_NEG", "없음", col("USE_YN", 1, "CHAR", 1, "사용 여부")),
                table("TB_QPX", "양성",
                        col("USE_YN", 1, "VARCHAR", 10, "사용구분"),
                        col("USE_QWZX", 2, "VARCHAR", 10, null),
                        col("ZQV_YN", 3, "CHAR", 1, "테스트여부")))));
    }

    static List<Finding> run(Set<Rule> rules) throws Exception {
        Dictionaries d = LogicalRunSampleTest.sampleDicts().withUser(Map.of("ZQV", "테스트", "USE", "사용"));
        return Audit.run(fixture(), d, LogicalRunSampleTest.domains, List.of("TB"), true, rules);
    }

    static boolean has(List<Finding> f, String table, String column, Rule rule) {
        return f.stream().anyMatch(x -> x.table().equals(table) && java.util.Objects.equals(x.column(), column) && x.rule() == rule);
    }

    static String detail(List<Finding> f, String table, String column, Rule rule) {
        return f.stream().filter(x -> x.table().equals(table) && java.util.Objects.equals(x.column(), column) && x.rule() == rule)
                .findFirst().orElseThrow().detail();
    }

    @Test
    void negativeTableHasNoColumnFinding() throws Exception {
        List<Finding> f = run(ALL);
        for (Rule r : Rule.values()) {
            assertFalse(has(f, "TB_NEG", "USE_YN", r), r + " — 코멘트 있음·공백만 다름·전부 매칭·공통표준단어·여부C1");
        }
    }

    @Test
    void noComment() throws Exception {
        List<Finding> f = run(ALL);
        assertTrue(has(f, "TB_QPX", "USE_QWZX", Rule.NO_COMMENT));
        assertFalse(has(f, "TB_QPX", "USE_YN", Rule.NO_COMMENT));
    }

    @Test
    void commentMismatchIsOffByDefault() throws Exception {
        assertTrue(has(run(ALL), "TB_QPX", "USE_YN", Rule.COMMENT_MISMATCH));
        assertEquals("코멘트 「사용구분」 / 조립 「사용여부」", detail(run(ALL), "TB_QPX", "USE_YN", Rule.COMMENT_MISMATCH));
        assertFalse(has(run(Audit.DEFAULT_RULES), "TB_QPX", "USE_YN", Rule.COMMENT_MISMATCH), "기본 꺼짐");
        assertFalse(has(run(ALL), "TB_QPX", "USE_QWZX", Rule.COMMENT_MISMATCH), "코멘트 없으면 NO_COMMENT 만");
    }

    @Test
    void unmatchedToken() throws Exception {
        List<Finding> f = run(ALL);
        assertEquals("QWZX", detail(f, "TB_QPX", "USE_QWZX", Rule.UNMATCHED_TOKEN));
        assertFalse(has(f, "TB_QPX", "USE_YN", Rule.UNMATCHED_TOKEN));
    }

    @Test
    void nonStandardAbbr() throws Exception {
        List<Finding> f = run(ALL);
        assertEquals("ZQV", detail(f, "TB_QPX", "ZQV_YN", Rule.NON_STANDARD_ABBR), "사용자 사전에만 있다");
        assertFalse(has(f, "TB_QPX", "USE_YN", Rule.NON_STANDARD_ABBR), "USE 는 사용자 사전에도 있지만 공통표준단어라 아니다");
    }

    @Test
    void domainSpec() throws Exception {
        List<Finding> f = run(ALL);
        assertEquals("«여부» 계열인데 VARCHAR(10) — 행안부 여부C1", detail(f, "TB_QPX", "USE_YN", Rule.DOMAIN_SPEC));
        assertFalse(has(f, "TB_QPX", "ZQV_YN", Rule.DOMAIN_SPEC), "CHAR(1) 은 여부C1 과 맞다");
    }

    @Test
    void tableRowsTooAndOrder() throws Exception {
        List<Finding> f = run(ALL);
        assertTrue(has(f, "TB_QPX", null, Rule.UNMATCHED_TOKEN), "테이블명 QPX 는 사전에 없다 — 무시토큰 TB 는 뺀다");
        assertFalse(f.stream().anyMatch(x -> x.column() == null && x.rule() == Rule.NON_STANDARD_ABBR && x.detail().contains("TB")));
        assertEquals("TB_NEG", f.get(0).table(), "스냅샷 순서");
    }

    @Test
    void goldenOnPostgresVendorSnapshot() throws Exception {
        List<Schema> pg = GoldenFiles.schemas("meta/postgres-vendor.json");
        List<Finding> f = Audit.run(pg, LogicalRunSampleTest.sampleDicts(), LogicalRunSampleTest.domains, List.of("TB"), true, ALL);
        GoldenFiles.assertJson("logical/audit-pg.json", f);
    }
}
