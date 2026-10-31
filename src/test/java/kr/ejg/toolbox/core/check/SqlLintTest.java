package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/** 5-57b(설계 47 §3-3~8) — 매퍼 SQL 판정 다섯. 사례마다 기대 종류를 단언하고, 위치까지 골든으로 든다 */
class SqlLintTest {

    static final String N = "NULL_COMPARE";
    static final String X = "CROSS_JOIN";
    static final String P = "PAGING_NO_ORDER";
    static final String I = "INSERT_NO_COLUMNS";
    static final String S = "NOT_IN_SUBQUERY";
    static final String NONE = "";
    static final String WHOLE = "whole";
    static final String PART = "partial";

    /** 이름 · whole/partial · SQL · 기대 종류(쉼표, 위치 순) */
    static final String[][] CASES = {
        {"nullEq", WHOLE, "SELECT ID FROM T WHERE A = NULL", N},
        {"nullNe", WHOLE, "SELECT ID FROM T WHERE A <> NULL AND B != NULL AND C = null", N + "," + N + "," + N},
        {"setNullNotCond", WHOLE, "UPDATE T SET A = NULL WHERE B = NULL", N},
        {"isNull", WHOLE, "SELECT ID FROM T WHERE A IS NULL AND B IS NOT NULL", NONE},
        {"bindNotNull", WHOLE, "SELECT ID FROM T WHERE A = #{a}", NONE},
        {"upsertSetNull", WHOLE, "INSERT INTO T (A) VALUES (1) ON DUPLICATE KEY UPDATE A = NULL", NONE}, // 마무리 리뷰 — ON 이 켠 조건을 UPDATE 가 끈다
        {"caseWhenNull", WHOLE, "SELECT CASE WHEN A = NULL THEN 1 ELSE 0 END FROM T", N},
        {"nullAfterSubquery", WHOLE, "SELECT ID FROM T WHERE ID IN (SELECT ID FROM U) AND D = NULL", N},
        {"notInSub", WHOLE, "SELECT ID FROM T WHERE ID NOT IN (SELECT ID FROM U)", S},
        {"notInList", WHOLE, "SELECT ID FROM T WHERE ID NOT IN (1, 2)", NONE},
        {"inSub", WHOLE, "SELECT ID FROM T WHERE ID IN (SELECT ID FROM U)", NONE},
        {"insertValues", WHOLE, "INSERT INTO T VALUES (1)", I},
        {"insertSelect", WHOLE, "INSERT INTO T SELECT * FROM U", I},
        {"insertIgnore", WHOLE, "INSERT IGNORE INTO S.T SELECT * FROM U", I},
        {"insertAfterSelectKey", WHOLE, "SELECT SEQ.NEXTVAL FROM DUAL INSERT INTO T VALUES (#{id})", I},
        {"insertCols", WHOLE, "INSERT INTO T (A) VALUES (1)", NONE},
        {"insertSet", WHOLE, "INSERT INTO T SET A = #{a}", NONE},
        {"insertAll", WHOLE, "INSERT ALL INTO A (X) VALUES (1) SELECT * FROM DUAL", NONE},
        {"limitBind", WHOLE, "SELECT ID FROM T LIMIT #{a}, #{b}", P},
        {"limitOne", WHOLE, "SELECT ID FROM T LIMIT 1", NONE},
        {"offset", WHOLE, "SELECT ID FROM T OFFSET 10 ROWS", P},
        {"rownumPaging", WHOLE, "SELECT * FROM (SELECT ROWNUM RN, A.* FROM T A) WHERE RN BETWEEN 1 AND 10", P},
        {"rownumOne", WHOLE, "SELECT ID FROM T WHERE ROWNUM = 1", NONE},
        {"oraclePagingOrdered", WHOLE, "SELECT * FROM (SELECT ROWNUM RN, TB.* FROM (SELECT A FROM T ORDER BY A) TB) WHERE RN BETWEEN 1 AND 10", NONE},
        {"top", WHOLE, "SELECT TOP 10 * FROM T", P},
        {"topOne", WHOLE, "SELECT TOP 1 * FROM T", NONE},
        {"topOneParen", WHOLE, "SELECT TOP (1) * FROM T", NONE},
        {"fetchFirst", WHOLE, "SELECT ID FROM T FETCH FIRST 10 ROWS ONLY", P},
        {"withLimit", WHOLE, "WITH C AS (SELECT A FROM T) SELECT * FROM C LIMIT 5", P},
        {"deleteLimit", WHOLE, "DELETE FROM T WHERE A = 1 LIMIT 10", NONE},
        {"partialPaging", PART, "SELECT ID FROM T LIMIT 10", NONE},
        {"commaNoLink", WHOLE, "SELECT a.X FROM TA a, TB b WHERE a.ID = #{id}", X},
        {"commaLinked", WHOLE, "SELECT a.X FROM TA a, TB b WHERE a.ID = b.A_ID", NONE},
        {"commaThreeOneLoose", WHOLE, "SELECT a.X FROM TA a, TB b, TC c WHERE a.ID = b.A_ID", X},
        {"commaPlus", WHOLE, "SELECT a.X FROM TA a, TB b WHERE a.ID = b.A_ID(+)", NONE},
        {"commaSchemaAs", WHOLE, "SELECT * FROM S.TA AS a, S.TB AS b WHERE b.ID = a.ID", NONE},
        {"joinNoOn", WHOLE, "SELECT a.X FROM TA a JOIN TB b WHERE a.ID = #{id}", X},
        {"joinNoOnButWhereLink", WHOLE, "SELECT a.X FROM TA a JOIN TB b WHERE a.ID = b.A_ID", NONE},
        {"joinOn", WHOLE, "SELECT * FROM TA a JOIN TB b ON a.ID = b.ID", NONE},
        {"leftJoinOnThenJoinNoOn", WHOLE, "SELECT * FROM TA a LEFT JOIN TB b ON a.ID = b.ID JOIN TC c WHERE a.X = 1", X},
        {"joinOnConstant", WHOLE, "SELECT * FROM TA a LEFT JOIN TB b ON 1 = 2 LEFT JOIN TC c", X},
        {"crossJoinExplicit", WHOLE, "SELECT * FROM TA a CROSS JOIN TB b", NONE},
        {"naturalJoin", WHOLE, "SELECT * FROM TA NATURAL JOIN TB", NONE},
        {"usingJoin", WHOLE, "SELECT * FROM TA a JOIN TB b USING (ID)", NONE},
        {"subqueryItem", WHOLE, "SELECT * FROM (SELECT ID FROM TA) a, TB b", NONE},
        {"partialCross", PART, "FROM TA a, TB b", NONE},
        {"extractFromNotGroup", WHOLE, "SELECT EXTRACT(DAY FROM D) FROM T", NONE},
        // R-12(설계 47 §3-21) — egov 실측 오탐 둘: 함수로 감싼 등식 · 별칭 없는 열 등식(어느 표 열인지 모른다 → 문장 판정 안 함)
        {"castEquality", WHOLE, "SELECT * FROM TA A, TB B WHERE CAST(A.ID AS VARCHAR) = B.ID", NONE},
        {"functionBothSides", WHOLE, "SELECT * FROM TA a, TB b WHERE UPPER(a.N) = UPPER(b.N)", NONE},
        {"unqualifiedColumn", WHOLE, "SELECT * FROM TA atd, TB mas WHERE atd.APPLCNT_ID = ESNTL_ID AND atd.ID = #{id}", NONE},
        {"pseudoColumnStillLoose", WHOLE, "SELECT * FROM TA a, TB b WHERE a.DT = SYSDATE", X},
        {"numberStillLoose", WHOLE, "SELECT * FROM TA a, TB b WHERE a.ID = 1", X},
    };

    @Test
    void golden() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        for (String[] c : CASES) {
            List<SqlLint.Hit> hits = SqlLint.check(c[2], PART.equals(c[1]));
            List<String> kinds = hits.stream().map(h -> h.kind().name()).toList();
            List<String> want = c[3].isEmpty() ? List.of() : Arrays.asList(c[3].split(","));
            if (!want.equals(kinds)) {
                wrong.add(c[0] + " 기대 " + want + " 실제 " + kinds);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c[0]);
            m.put("partial", PART.equals(c[1]));
            m.put("sql", c[2]);
            m.put("hits", hits.stream().map(h -> h.kind().rule() + "@" + h.at()).toList());
            out.add(m);
        }
        assertEquals(List.of(), wrong);
        GoldenFiles.assertJson("check/sql-lint.json", out);
    }
}
