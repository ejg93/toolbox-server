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
