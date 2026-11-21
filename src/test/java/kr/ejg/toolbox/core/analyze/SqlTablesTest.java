package kr.ejg.toolbox.core.analyze;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/** 6-1 — SQL 한 문장 → 테이블·CRUD. 케이스는 eGov 매퍼 실측 꼴(설계 8) */
class SqlTablesTest {

    static final String B = String.valueOf(SqlTables.BRANCH);

    /** 이름 · 태그 · SQL */
    static final String[][] CASES = {
        {"oraclePaging", "select", "SELECT * FROM ( SELECT rownum rn, TB.* FROM ( SELECT a.NTT_ID, b.USER_NM FROM COMTNBBS a "
                + "LEFT OUTER JOIN COMVNUSERMASTER b ON a.FRST_REGISTER_ID = b.ESNTL_ID WHERE a.BBS_ID = #{bbsId} ORDER BY a.SORT_ORDR DESC ) TB ) "
                + "WHERE rn BETWEEN #{firstIndex} + 1 AND #{firstIndex} + #{recordCountPerPage}"},
        {"mysqlPaging", "select", "SELECT a.NTT_ID FROM COMTNBBS a WHERE a.USE_AT = 'Y' ORDER BY a.SORT_ORDR DESC LIMIT #{recordCountPerPage} OFFSET #{firstIndex}"},
        {"joinSubquery", "select", "SELECT a.NTT_ID, c.COMMENT_CO FROM COMTNBBS a LEFT OUTER JOIN (SELECT NTT_ID, BBS_ID, COUNT(1) AS COMMENT_CO "
                + "FROM COMTNCOMMENT WHERE USE_AT = 'Y' GROUP BY NTT_ID, BBS_ID) c ON a.NTT_ID = c.NTT_ID"},
        {"commaListAlias", "select", "SELECT a.X FROM COMTNBBSMASTER a, COMTNBBSUSE b, COMTCCMMNDETAILCODE WHERE a.BBS_ID = b.BBS_ID"},
        {"asAlias", "select", "SELECT T.X FROM COMTNBLOG AS T INNER JOIN COMTNBLOGUSER AS U ON T.BLOG_ID = U.BLOG_ID"},
        {"insertValues", "insert", "INSERT INTO COMTNBBS (NTT_ID, BBS_ID, NTT_SJ) VALUES (#{nttId}, #{bbsId}, #{nttSj})"},
        {"insertSelect", "insert", "INSERT INTO COMTSSYSLOGSUMMARY SELECT TO_CHAR(b.OCCRRNC_DE,'YYYYMMDD'), b.SVC_NM FROM COMTNSYSLOG b WHERE b.X = 1"},
        {"selectKeyBody", "select", "SELECT NVL(MAX(SORT_ORDR),0)+1 AS NTT_NO FROM COMTNBBS WHERE BBS_ID = #{bbsId}"},
        {"updateWithSubquery", "update", "UPDATE COMTNBBS SET NTT_SJ = #{nttSj}, INQIRE_CO = (SELECT MAX(INQIRE_CO) FROM COMTNBBSMASTER) "
                + "WHERE BBS_ID = #{bbsId} AND NTT_ID = #{nttId}"},
        {"updateAsDelete", "update", "UPDATE COMTNBBS SET USE_AT = 'N', LAST_UPDUSR_ID = #{lastUpdusrId} WHERE BBS_ID = #{bbsId}"},
        {"updateFrom", "update", "UPDATE T1 SET X = S.X FROM COMTNSRC S WHERE T1.ID = S.ID"},
        {"deleteFrom", "delete", "DELETE FROM COMTNADBK WHERE (EMPLYR_ID = #{emplyrId}) OR ADBK_ID IN (SELECT ADBK_ID FROM COMTNADBKUSER)"},
        {"deleteNoFrom", "delete", "DELETE COMTNADBK WHERE ADBK_ID = #{adbkId}"},
        {"merge", "update", "MERGE INTO COMTNTARGET T USING COMTNSOURCE S ON (T.ID = S.ID) WHEN MATCHED THEN UPDATE SET T.X = S.X "
                + "WHEN NOT MATCHED THEN INSERT (ID, X) VALUES (S.ID, S.X)"},
        {"truncate", "delete", "TRUNCATE TABLE COMTNTMP"},
        {"onDuplicateKey", "insert", "INSERT INTO COMTNCNT (ID, CO) VALUES (#{id}, 1) ON DUPLICATE KEY UPDATE CO = CO + 1"},
        {"ifBoundary", "select", "SELECT A.X FROM\nCOMTNBBS A\n\nWHERE 1=1\n\nAND A.Y = #{y}\n"},
        {"chooseBranches", "select", "SELECT * FROM \n" + B + " comthtrsmrcvmntrngloginfo \n\n" + B + " comtczip \n\n" + B
                + " whitelist_table_required \n\n LIMIT 1"},
        {"dynamicTable", "select", "SELECT * FROM ${tableName} WHERE X = #{x}"},
        {"dual", "select", "SELECT SYSDATE FROM DUAL"},
        {"cubridDbRoot", "select", "SELECT SYS_DATETIME FROM db_root"},
        {"schemaPrefix", "select", "SELECT * FROM COM.COMTNBBS A JOIN \"COM\".\"COMTNCOMMENT\" C ON A.ID = C.ID"},
        {"quotedIdentifier", "select", "SELECT * FROM `comtnbbs` b WHERE b.X = 'FROM NOT_A_TABLE'"},
        {"commentFrom", "select", "/* FROM GHOST */ SELECT X -- FROM GHOST2\n FROM COMTNREAL"},
        {"tagVerbMismatch", "delete", "UPDATE COMTNNOTETRNSMIT SET DELETE_AT = 'Y' WHERE NOTE_ID = #{noteId}"},
        {"verbFromTag", "select", "WITH X AS (SELECT 1 FROM COMTNCTE) SELECT * FROM X"},
        {"forUpdate", "select", "SELECT X FROM COMTNLOCK WHERE ID = #{id} FOR UPDATE"},
        {"insertAll", "insert", "INSERT ALL INTO COMTNA (X) VALUES (1) INTO COMTNB (X) VALUES (2) SELECT * FROM DUAL"},
        // 6-36a(설계 46) — CTE 이름·괄호 켜기·문장 경계·식별자 꼴
        {"cte", "select", "WITH C AS (SELECT 1 FROM COMTNA) SELECT * FROM C JOIN COMTNB B ON 1=1"},
        {"cteColsRecursive", "select", "WITH RECURSIVE C(N) AS (SELECT 1 FROM COMTNA UNION ALL SELECT N+1 FROM C) SELECT * FROM C"},
        {"cteInsert", "insert", "WITH C AS (SELECT X FROM COMTNA) INSERT INTO COMTNB SELECT * FROM C"},
        {"cteNested", "select", "WITH O AS (WITH I AS (SELECT 1 FROM COMTNA) SELECT * FROM I) SELECT * FROM O"},
        {"cteNotMaterialized", "select", "WITH C AS NOT MATERIALIZED (SELECT 1 FROM COMTNA), D AS MATERIALIZED (SELECT 2 FROM COMTNB) "
                + "SELECT * FROM C, D"},
        {"extractFrom", "select", "SELECT EXTRACT(DAY FROM D) FROM COMTNA"},
        {"substringFrom", "select", "SELECT SUBSTRING(X FROM 2 FOR 3) FROM COMTNA"},
        {"convertUsing", "select", "SELECT CONVERT(X USING UTF8MB4) FROM COMTNA"},
        {"isDistinctFrom", "select", "SELECT A IS DISTINCT FROM B FROM COMTNA"},
        {"inSubquery", "select", "SELECT * FROM COMTNA WHERE X IN (SELECT Y FROM COMTNB) AND EXISTS (SELECT 1 FROM COMTNC)"},
        {"withNolockWord", "select", "SELECT * FROM COMTNA WITH (NOLOCK)"},
        {"multiStatement", "delete", "DELETE FROM COMTNA; INSERT INTO COMTNB VALUES (1)"},
        {"goBoundary", "update", "UPDATE COMTNA SET X=1\nGO\nUPDATE COMTNB SET X=1"},
        {"plsqlBlock", "update", "BEGIN SELECT X INTO V_X FROM COMTNA; UPDATE COMTNB SET Y = V_X; END;"},
        {"copyNoVerb", null, "COPY COMTNA (A) FROM STDIN"},
        {"bracketIdent", "select", "SELECT * FROM [dbo].[COMTNA] A"},
        {"doubledQuote", "select", "SELECT \"\"\"x\"\"\" FROM COMTNA"},
        {"unbalancedParen", "select", "SELECT * FROM COMTNA WHERE 1=1 )"},
        // 6-36b(설계 46) — 대상 동사·표 함수·괄호 친 표·점 사슬
        {"multiTableDelete", "delete", "DELETE COMTNA, COMTNB FROM COMTNA JOIN COMTNB ON COMTNA.ID = COMTNB.ID"},
        {"deleteModifiers", "delete", "DELETE LOW_PRIORITY QUICK IGNORE FROM COMTNA WHERE 1=1"},
        {"mergeNoInto", "update", "MERGE COMTNT T USING COMTNS S ON (T.ID = S.ID) WHEN MATCHED THEN UPDATE SET T.X = S.X"},
        {"crossApply", "select", "SELECT * FROM COMTNA A CROSS APPLY COMTNB B OUTER APPLY FN(A.ID) F"},
        {"tableFunction", "select", "SELECT * FROM JSON_TABLE(J, '$[*]' COLUMNS(A INT PATH '$.a')) JT JOIN COMTNA ON 1=1"},
        {"unnest", "select", "SELECT * FROM UNNEST(ARRAY[1, 2]) AS U(X) JOIN COMTNA ON 1=1"},
        {"nolockHint", "select", "SELECT * FROM COMTNA (NOLOCK)"},
        {"parenTable", "select", "SELECT * FROM (COMTNA A) JOIN ((COMTNB B INNER JOIN COMTNC C ON 1=1)) ON 1=1"},
        {"parenSubqueryStillClosed", "select", "SELECT * FROM (SELECT 1 FROM COMTNA) X"},
        {"usingColumnsNotTable", "select", "SELECT * FROM COMTNA JOIN COMTNB USING (ID)"},
        {"leadingDots", "select", "SELECT * FROM ..[COMTNA]"},
        {"doubleDot", "select", "SELECT * FROM DB..COMTNA"},
        {"oracleTableFn", "select", "SELECT * FROM TABLE(CAST(X AS T)) JOIN COMTNA ON 1=1"},
        {"insertIgnore", "insert", "INSERT IGNORE INTO COMTNA (X) VALUES (1)"},
        {"ignoreIndex", "select", "SELECT * FROM COMTNA IGNORE INDEX (IX) JOIN COMTNB ON 1=1"},
        // R-11(설계 46) — V-42a 재채점이 드러낸 이번 번들의 퇴행 셋
        {"indexHintForJoin", "select", "SELECT * FROM COMTNA IGNORE INDEX FOR JOIN (I2)"},
        {"dmlCte", "select", "WITH D AS (DELETE FROM COMTNA RETURNING *) SELECT * FROM D"},
        {"updateTop", "update", "UPDATE TOP (10) PERCENT COMTNA SET X = 1"},
        // 마무리 리뷰 — 열 이름 GO 는 문장 경계가 아니다(뒤에 동사가 오거나 글 끝일 때만)
        {"goColumn", "select", "SELECT GO, NM FROM COMTNA"},
        // 6-38(설계 47) — 더미 사슬 끝 · CYCLE 의 USING · 대상 자리 표 함수 · PL/SQL 블록 동사 · 층마다 동사(DML CTE)
        {"dummyChain", "select", "SELECT 1 FROM SYS.DUAL"},
        {"cycleUsing", "select", "WITH RECURSIVE C AS (SELECT ID FROM COMTNA UNION ALL SELECT ID FROM C) CYCLE ID SET IS_CYCLE USING PATH SELECT * FROM C"},
        {"updateTableFunction", "update", "UPDATE OPENQUERY(LNK, 'SELECT 1') SET X = 1"},
        {"deleteTableFunction", "delete", "DELETE FROM OPENQUERY(LNK, 'SELECT 1')"},
        {"insertIntoColumnsStillTable", "insert", "INSERT INTO COMTNA (X) SELECT Y FROM COMTNB"},
        {"declareBlock", "update", "DECLARE V NUMBER; BEGIN UPDATE COMTNB SET Y = 1; END;"},
        {"dmlCteUpdate", "update", "WITH U AS (UPDATE COMTNA SET X = 1 RETURNING *) UPDATE COMTNB SET Y = 1"},
        // 6-44(C-TBL-COMMA) — 닫힌 뒤 쉼표가 표 목록 안이면 다음 표
        {"commaAfterSubquery", "select", "SELECT * FROM A, (SELECT X FROM B) V, C"},
        {"commaAfterOn", "select", "SELECT * FROM A JOIN B ON A.X = B.X, C"},
        {"commaAfterDblink", "select", "SELECT * FROM A@LNK, B"},
        {"commaAfterPartition", "select", "SELECT * FROM T1 PARTITION (P1), T2"},
        {"commaAfterTableFn", "select", "SELECT * FROM T1, TABLE(F(T1.X)) V, T2"},
        {"commaAfterHint", "select", "SELECT * FROM T1 WITH (NOLOCK), T2"},
        {"commaInParenTable", "select", "SELECT * FROM (A JOIN B ON A.X = B.X, C)"},
        {"commaAfterUsing", "delete", "DELETE FROM A USING B JOIN C ON B.X = C.X, D WHERE A.X = B.X"},
        {"orderByCommas", "select", "SELECT A FROM T ORDER BY A, B"},
        {"groupByCommas", "select", "SELECT A, B FROM T GROUP BY A, B"},
        {"unionSelectList", "select", "SELECT A FROM T UNION SELECT B, C FROM U"},
        {"mergeSetCommas", "update", "MERGE INTO T USING S ON (T.A = S.A) WHEN MATCHED THEN UPDATE SET T.B = S.B, T.C = S.C"},
        {"updateSetFromCommas", "update", "UPDATE A SET X = 1, Y = 2 FROM B, C WHERE A.K = B.K"},
        {"limitCommas", "select", "SELECT A FROM T LIMIT 10, 20"},
        // 6-45(C-TBL-DELALIAS) — 대상 자리 이름이 FROM 의 별칭이면 U·D 는 진짜 표로
        {"deleteAlias", "delete", "DELETE A FROM TB_A A JOIN TB_B B ON A.X = B.X"},
        {"updateAliasMssql", "update", "UPDATE A SET A.X = 1 FROM TB_A A WHERE A.Y = 2"},
        {"deleteTwoAliases", "delete", "DELETE A, B FROM TB_A A JOIN TB_B B ON A.X = B.X"},
        {"updateFromKeepsTarget", "update", "UPDATE T1 SET X = S.X FROM COMTNSRC S"},
        {"innerAliasIsNotTarget", "delete", "DELETE FROM A WHERE X IN (SELECT B.X FROM TB_B A2)"},
        // V-65a — 6-44 표 목록이 잘못 켜지던 꼴(실물 sqlfluff): ORDER BY … USING 연산자 · 괄호 안 DML 의 OUTPUT 목록
        {"orderByUsingOperator", "select", "SELECT * FROM T ORDER BY A USING <, B USING >"},
        {"outputInDerivedDelete", "select", "SELECT * FROM (DELETE TBL6 OUTPUT DELETED.ID, DELETED.CATEGORY_ID WHERE X < 1) AS D INNER JOIN TBL7 T ON D.CATEGORY_ID = T.ID"},
        // 6-46 — 세미콜론 없는 T-SQL 배치 낱말이 표 목록을 끝낸다 · 권한 문장은 통째로 뺀다
        {"tsqlBatchDeclare", "select", "SELECT A FROM T DECLARE @X INT, @Y INT"},
        {"tsqlBatchExec", "select", "SELECT A FROM T EXEC P @A, @B"},
        {"revokeFromUsers", "select", "REVOKE SELECT ON T FROM U1, U2"},
        {"grantThenSelect", "select", "GRANT SELECT ON T TO U1; SELECT A FROM B"},
        // 6-48 — ; 없는 권한 문장은 GO·받는 쪽 뒤 문장 동사에서 끝난다 · MySQL IF( 함수는 절 끝이 아니다
        {"grantNoSemicolon", "select", "GRANT SELECT, INSERT ON T TO U1 SELECT A FROM B"},
        {"grantThenGo", "select", "GRANT SELECT ON T TO U1 GO SELECT A FROM C"},
        {"ifFunctionInJoin", "select", "SELECT * FROM A JOIN B ON IF(A.X > 0, A.X, 0) = B.Y, C"},
        // 6-47 — 리터럴 끝: 역슬래시 따옴표 · LIKE ESCAPE 관용 · PG 달러 인용 · Oracle q 인용 뒤 표가 안 빠진다
        {"backslashQuote", "select", "SELECT * FROM A WHERE X = 'it\\'s' AND Y IN (SELECT ID FROM B)"},
        {"likeEscapeIdiom", "select", "SELECT * FROM A WHERE X LIKE '%\\' ESCAPE '\\' AND Y IN (SELECT ID FROM B)"},
        // 6-58 — 따옴표 친 예약어 이름 · 이름에 붙은 ${} · MySQL # 주석
        {"quotedKeywordTable", "select", "SELECT * FROM `order` o JOIN `user` u ON o.ID = u.ID"},
        {"bracketKeywordTable", "select", "SELECT * FROM [ORDER] A JOIN S.\"GROUP\" G ON A.ID = G.ID"},
        {"bindSuffixTable", "select", "SELECT * FROM TB_LOG_${ym}"},
        {"hashComment", "select", "SELECT * FROM T1 # FROM T9\nWHERE X = 1"},
        {"dollarQuote", "select", "SELECT $$it's$$ FROM A JOIN B ON A.ID = B.ID"},
        {"oracleQQuote", "select", "SELECT q'[it's]' FROM A, B"},
    };

    @Test
    void golden() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] c : CASES) {
            SqlTables.Result r = SqlTables.extract(c[2], c[1]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c[0]);
            m.put("tag", c[1]);
            m.put("sql", c[2].replace(B, "<branch>"));
            m.put("verb", r.verb());
            Map<String, String> tables = new LinkedHashMap<>();
            r.refs().forEach(x -> tables.put(x.table(), x.letters()));
            m.put("tables", tables);
            m.put("unresolved", r.unresolved());
            out.add(m);
        }
        GoldenFiles.assertJson("analyze/sql-tables.json", out);
    }
}
