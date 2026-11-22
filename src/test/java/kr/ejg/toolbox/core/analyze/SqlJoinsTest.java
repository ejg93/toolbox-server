package kr.ejg.toolbox.core.analyze;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/** 6-13 — 조인 등식 꼴마다 골든: 명시 JOIN·쉼표 조인·(+)·별칭 없음·서브쿼리·자기 조인·주석 속 등식·스키마·못 푼 별칭·같은 등식 두 번 */
class SqlJoinsTest {

    @Test
    void shapes() {
        Map<String, String> sql = new LinkedHashMap<>();
        sql.put("explicit", "SELECT * FROM A a JOIN B b ON a.ID = b.A_ID");
        sql.put("comma", "SELECT * FROM A a, B b WHERE a.ID = b.A_ID AND a.X = 1");
        sql.put("outerPlus", "SELECT * FROM A a, B b WHERE a.ID = b.A_ID(+) AND a.CD(+) = b.CD");
        sql.put("noAlias", "SELECT * FROM A JOIN B ON A.ID = B.A_ID");
        sql.put("subquery", "SELECT * FROM A a WHERE a.ID IN (SELECT b.A_ID FROM B b WHERE b.CD = a.CD)");
        sql.put("selfJoin", "SELECT * FROM A a JOIN A p ON a.PARENT_ID = p.ID");
        sql.put("comment", "SELECT * FROM A a /* a.X = b.Y */ JOIN B b ON a.ID = b.A_ID -- a.Z = b.W");
        sql.put("schema", "SELECT * FROM S.A a JOIN S.B AS b ON a.ID = b.A_ID");
        sql.put("unknownAlias", "SELECT * FROM A a WHERE a.ID = c.ID");
        sql.put("twice", "SELECT * FROM A a JOIN B b ON a.ID = b.A_ID WHERE b.A_ID = a.ID");
        sql.put("literal", "SELECT * FROM A a, B b WHERE a.ID = 'b.X' AND a.ID = b.ID");
        // 6-60 — SET 대입은 조인 등식이 아니다
        sql.put("updateSet", "UPDATE TB_A A JOIN TB_B B ON A.ID = B.ID SET B.X = A.X");
        sql.put("mergeSet", "MERGE INTO T USING S ON (T.ID = S.ID) WHEN MATCHED THEN UPDATE SET T.X = S.X WHEN NOT MATCHED THEN INSERT (ID) VALUES (S.ID)");
        // 6-66 · 6-68 · R-C-M1 — 별칭 범위(문장·질의 괄호·집합 연산 가지) · WITH 이름 · MERGE USING · 괄호 안 SET · 스키마 바인드
        sql.put("unionBranches", "SELECT a.X FROM T1 a JOIN T2 b ON a.ID = b.ID UNION ALL SELECT a.X FROM T3 a JOIN T4 b ON a.ID = b.ID");
        sql.put("shadowedAlias", "SELECT * FROM T1 a JOIN T2 b ON a.ID = b.ID WHERE a.X IN (SELECT a.X FROM T3 a)");
        sql.put("cteName", "WITH C AS (SELECT ID FROM T1) SELECT * FROM C JOIN T2 ON C.ID = T2.ID");
        sql.put("cteAlias", "WITH C AS (SELECT ID FROM T1) SELECT * FROM C x JOIN T2 y ON x.ID = y.ID");
        sql.put("cteInnerJoin", "WITH C AS (SELECT a.ID FROM T1 a JOIN T3 b ON a.ID = b.ID) SELECT * FROM C");
        sql.put("parenTable", "SELECT * FROM (A a JOIN B b ON a.ID = b.A_ID) JOIN C c ON a.ID = c.A_ID");
        sql.put("twoStatements", "SELECT * FROM T1 a JOIN T2 b ON a.ID = b.ID; SELECT * FROM T3 a JOIN T4 b ON a.ID = b.ID");
        sql.put("mergeUsing", "MERGE INTO TB_T T USING TB_S S ON (T.ID = S.ID) WHEN MATCHED THEN UPDATE SET T.X = S.X");
        sql.put("castCharacterSet", "SELECT * FROM A a JOIN B b ON CAST(a.NM AS CHAR CHARACTER SET utf8) = b.NM AND a.ID = b.ID");
        sql.put("setInParenDml", "WITH U AS (UPDATE A SET X = B.X FROM B WHERE A.ID = B.ID RETURNING A.ID) SELECT * FROM U JOIN C ON U.ID = C.ID");
        sql.put("bindSchema", "SELECT * FROM ${s}.TB_A a JOIN ${s}.TB_B b ON a.ID = b.ID");
        // 마무리 리뷰 M1 — 짝 없는 ) 뒤 SET · SET 안 CASE WHEN · SET 안 서브쿼리 뒤 대입
        sql.put("unbalancedParenSet", "UPDATE A a JOIN B b ON a.ID = b.ID ) SET a.X = b.X WHERE a.K = 1");
        sql.put("mergeCaseSet", "MERGE INTO TB_T T USING TB_S S ON (T.ID = S.ID) WHEN MATCHED THEN UPDATE SET T.CNT = CASE WHEN S.FLAG = 'Y' THEN 1 ELSE 0 END, T.NM = S.NM, T.DT = S.DT");
        sql.put("setSubqueryThenAssign", "UPDATE A a JOIN B b ON a.ID = b.ID SET a.X = (SELECT MAX(c.X) FROM C c WHERE c.ID = a.ID), a.Y = b.Y");
        // 6-71 — 질의 아닌 괄호 안 UNION 의 가지는 바깥 별칭을 본다 · ( 뒤 분기 표시 · 파생 표 별칭은 바깥 같은 별칭을 가린다
        String b = String.valueOf(SqlTables.BRANCH);
        sql.put("parenUnionOuterAlias", "SELECT * FROM T1 a WHERE a.ID IN ((SELECT b.ID FROM T2 b) UNION (SELECT c.ID FROM T3 c WHERE c.X = a.X))");
        sql.put("parenUnionThree", "SELECT * FROM T1 a WHERE a.ID IN ((SELECT b.ID FROM T2 b WHERE b.X = a.X) UNION (SELECT c.ID FROM T3 c WHERE c.X = a.X) UNION (SELECT d.ID FROM T4 d WHERE d.X = a.X))");
        sql.put("parenUnionNoLeak", "SELECT * FROM T1 a WHERE a.ID IN ((SELECT b.ID FROM T2 b) UNION (SELECT c.ID FROM T3 c WHERE c.X = b.X))");
        sql.put("branchQueryParen", "SELECT * FROM T1 a JOIN T2 b ON a.ID = b.ID WHERE a.X IN (" + b + " SELECT a.X FROM T3 a " + b + " SELECT a.X FROM T4 a)");
        sql.put("derivedAliasShadows", "SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE b.X IN (SELECT c.X FROM (SELECT ID, X FROM T3) AS v JOIN T4 c ON v.ID = c.ID)");
        // 0-118 — LATERAL·APPLY 안 상관 등식 · 문장 바닥 괄호 친 UNION 가지
        sql.put("lateral", "SELECT * FROM T1 a JOIN LATERAL (SELECT b.ID FROM T2 b WHERE b.A_ID = a.ID) v ON v.ID = a.ID");
        sql.put("apply", "SELECT * FROM T1 a CROSS APPLY (SELECT b.ID FROM T2 b WHERE b.A_ID = a.ID) v");
        sql.put("parenUnionTop", "(SELECT a.X FROM T1 a JOIN T2 b ON a.ID = b.ID) UNION ALL (SELECT a.X FROM T3 a JOIN T4 b ON a.ID = b.ID)");
        // 6-75 — 겹괄호 파생 표 · LATERAL·APPLY 별칭 가림 · 안 닫힌 괄호 · 파생 표 뒤 쉼표 + 지킴이 다섯
        sql.put("derivedDoubleParen", "SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE b.X IN (SELECT c.X FROM ((SELECT ID, X FROM T3)) v JOIN T4 c ON v.ID = c.ID)");
        sql.put("derivedParenUnion", "SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE b.X IN (SELECT c.X FROM ((SELECT ID FROM T3) UNION (SELECT ID FROM T5)) v JOIN T4 c ON v.ID = c.ID)");
        sql.put("lateralAliasShadows", "SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE EXISTS (SELECT 1 FROM T4 c JOIN LATERAL (SELECT ID FROM T3 x WHERE x.P = c.P) v ON v.ID = c.ID)");
        sql.put("applyAliasShadows", "SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE EXISTS (SELECT 1 FROM T4 c CROSS APPLY (SELECT ID FROM T3 x WHERE x.P = c.P) v WHERE v.ID = c.ID)");
        sql.put("unclosedParenUnion", "SELECT a.X FROM T1 a WHERE (a.Y = 1 UNION SELECT c.X FROM T3 c WHERE c.X = a.X");
        sql.put("commaAfterDerived", "SELECT * FROM (SELECT ID FROM T3) v, T1 a, T2 b WHERE a.ID = b.ID");
        sql.put("commaAroundDerived", "SELECT * FROM T1 a, (SELECT ID FROM T3) v, T2 b WHERE a.ID = b.ID AND v.ID = a.ID");
        sql.put("commaAfterLateral", "SELECT * FROM T1 a, LATERAL (SELECT ID FROM T3 x WHERE x.P = a.P) v, T2 b WHERE a.ID = b.ID");
        sql.put("nestedParenUnion", "SELECT * FROM T1 a WHERE a.ID IN (((SELECT b.ID FROM T2 b WHERE b.X = a.X)) UNION ((SELECT c.ID FROM T3 c WHERE c.X = a.X)))");
        sql.put("mixedParens", "SELECT * FROM ((T1 a JOIN T2 b ON a.ID = b.ID) JOIN (SELECT ID FROM T3) v ON v.ID = a.ID) WHERE a.X IN ((SELECT c.X FROM T4 c) UNION (SELECT d.X FROM T5 d WHERE d.Y = b.Y))");
        sql.put("parenTableParenUnion", "SELECT * FROM (T1 a JOIN T2 b ON a.ID = b.ID) JOIN ((SELECT ID FROM T3) UNION (SELECT ID FROM T4)) u ON u.ID = a.ID");
        sql.put("scalarAlias", "SELECT a.ID, (SELECT MAX(c.X) FROM T3 c WHERE c.ID = a.ID) b FROM T1 a JOIN T2 b ON a.ID = b.ID");
        sql.put("commaDerivedShadows", "SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE b.X IN (SELECT c.X FROM T4 c, (SELECT ID FROM T3) v WHERE v.ID = c.ID)");
        // 6-78 — 분기마다 같은 별칭: 자리 앞의 가장 가까운 정의 + 지킴이(정의 앞에서 쓰는 별칭)
        sql.put("branchSameAlias", b + " SELECT a.ID FROM T_A a JOIN T_B b ON a.BID = b.ID " + b + " SELECT a.ID FROM T_X a JOIN T_C c ON a.CID = c.ID");
        sql.put("aliasUsedBeforeFrom", "SELECT (SELECT MAX(b.X) FROM T2 b WHERE b.ID = a.ID) FROM T1 a");
        Map<String, List<SqlJoins.Join>> out = new LinkedHashMap<>();
        sql.forEach((k, v) -> out.put(k, SqlJoins.extract(v)));
        GoldenFiles.assertJson("analyze/sql-joins.json", out);
    }
}
