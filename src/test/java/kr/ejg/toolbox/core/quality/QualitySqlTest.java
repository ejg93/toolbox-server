package kr.ejg.toolbox.core.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/** 2-6 — 8종 × 방언 5 골든(치환값 고정) + 이름 검사 + 방언 별칭 + [검증] 에 [정제] CASE 붙이기 */
class QualitySqlTest {

    static final List<String> DIALECTS = List.of("oracle", "mysql", "pg", "mssql", "sybase");

    static QualitySql.Rendered render(String id, String dialect) {
        return QualitySql.render(id, dialect, "APP", "TB_ORDER", "ORD_DT", List.of("ORD_NO", "LINE_NO"), List.of("APP", "APP2"));
    }

    @Test
    void goldenFortyAndNoCorePlaceholderLeft() {
        assertEquals(8, QualitySql.kinds().size());
        for (QualitySql.Kind k : QualitySql.kinds()) {
            for (String d : DIALECTS) {
                String sql = render(k.id(), d).sql();
                GoldenFiles.assertText("quality/" + k.id() + "-" + d + ".sql", sql);
                assertFalse(sql.contains("대상컬럼") || sql.contains("스키마명.테이블명") || sql.contains("__SCHEMAS__")
                        || sql.contains("후보키1"), k.id() + "-" + d + " 에 채울 자리가 남았다");
            }
        }
    }

    @Test
    void datecheckGetsDatefixCase() {
        for (String d : DIALECTS) {
            String sql = render("datecheck", d).sql();
            assertFalse(sql.contains("통째로 붙여넣기"), d + " — 자리가 채워졌다");
        }
        assertTrue(render("datecheck", "pg").sql().contains("WHEN ORD_DT ~ '^[0-9]{4}-'"));
    }

    @Test
    void namesMustBeIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> QualitySql.render("format", "pg", "APP", "T; DROP TABLE X", "C", null, null));
        assertThrows(IllegalArgumentException.class, () -> QualitySql.render("format", "pg", "APP", "T", "C' OR '1'='1", null, null));
        assertThrows(IllegalArgumentException.class, () -> QualitySql.render("nopk", "pg", null, null, null, null, List.of("A')--")));
        assertThrows(IllegalArgumentException.class, () -> QualitySql.render("nope", "pg", null, "T", "C", null, null));
        assertThrows(IllegalArgumentException.class, () -> QualitySql.render("format", "db2", null, "T", "C", null, null));
    }

    @Test
    void dialectAliasesAndLeftoverPlaceholdersForHumans() {
        assertEquals("oracle", QualitySql.render("format", "tibero", "A", "T", "C", null, null).dialect());
        assertEquals("mysql", QualitySql.render("format", "mariadb", "A", "T", "C", null, null).dialect());
        String codes = render("codes", "pg").sql();
        assertTrue(codes.contains("업무테이블") && codes.contains("공통코드테이블") && codes.contains("해당그룹"),
                "대사 쿼리의 업무·코드 표 이름은 사람이 고친다");
        assertTrue(codes.startsWith("SELECT ORD_DT AS 코드값"), "코드컬럼 → 고른 컬럼");
        assertFalse(render("format", "pg").notes().isEmpty(), "순수본 메모(개인정보 발견 시 중단 등)가 같이 간다");
    }
}
