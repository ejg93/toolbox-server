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
        Map<String, List<SqlJoins.Join>> out = new LinkedHashMap<>();
        sql.forEach((k, v) -> out.put(k, SqlJoins.extract(v)));
        GoldenFiles.assertJson("analyze/sql-joins.json", out);
    }
}
