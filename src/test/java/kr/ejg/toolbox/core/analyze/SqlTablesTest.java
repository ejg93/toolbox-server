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
