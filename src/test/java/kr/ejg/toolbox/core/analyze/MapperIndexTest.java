package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.check.Source;
import org.junit.jupiter.api.Test;

/** 6-2 — 매퍼 XML 색인. 픽스처: 방언 둘(같은 ns.id·표 차이)·같은/다른 namespace include·selectKey·CDATA 조각·choose 표·깨진 XML·매퍼 아님 */
class MapperIndexTest {

    static final Path DIR = Path.of("src/test/resources/fixtures/analyze/mapper");

    static List<Source> sources() throws IOException {
        List<Source> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(DIR)) {
            for (Path p : s.sorted().toList()) {
                out.add(new Source(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"), null, null, null));
            }
        }
        return out;
    }

    /** 6-33 — ${} 이름(조각 포함·방언 파일 합집합·정렬) · 동적 표지(문장 안 태그 · 끼운 조각의 태그, 정적 조각은 아님) */
    @Test
    void dollarsAndDynamic() {
        String a = """
                <mapper namespace="D">
                  <sql id="dynFrag"><if test="x != null">AND A = #{x}</if></sql>
                  <sql id="statFrag">FROM T ${tbl}</sql>
                  <select id="sorted">SELECT * FROM T ORDER BY ${sortCol} ${ sortDir }</select>
                  <select id="withIf">SELECT * FROM T <where><if test="a">A = #{a}</if></where></select>
                  <select id="incDyn">SELECT * FROM T WHERE 1=1 <include refid="dynFrag"/></select>
                  <select id="incStat">SELECT * <include refid="statFrag"/></select>
                  <select id="plain"><![CDATA[SELECT A FROM T WHERE B < #{b}]]></select>
                </mapper>""";
        String b = """
                <mapper namespace="D">
                  <select id="sorted">SELECT * FROM T ORDER BY ${extra.col}</select>
                </mapper>""";
        MapperIndex.Index idx = MapperIndex.scan(List.of(new Source("D_SQL_oracle.xml", a, null, null, null),
                new Source("D_SQL_mysql.xml", b, null, null, null)));
        assertEquals(List.of("extra.col", "sortCol", "sortDir"), idx.get("D.sorted").dollars());
        assertEquals(List.of("tbl"), idx.get("D.incStat").dollars(), "조각 안 ${}");
        assertEquals(List.of(), idx.get("D.plain").dollars());
        Map<String, Boolean> dyn = new LinkedHashMap<>();
        for (String id : List.of("sorted", "withIf", "incDyn", "incStat", "plain")) {
            dyn.put(id, idx.get("D." + id).dynamic());
        }
        assertEquals(Map.of("sorted", false, "withIf", true, "incDyn", true, "incStat", false, "plain", false), dyn);
    }

    @Test
    void golden() throws IOException {
        MapperIndex.Index idx = MapperIndex.scan(sources());
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> st = new LinkedHashMap<>();
        idx.statements().forEach((id, s) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tag", s.tag());
            m.put("verb", s.verb());
            Map<String, String> tables = new LinkedHashMap<>();
            s.refs().forEach(r -> tables.put(r.table(), r.letters()));
            m.put("tables", tables);
            m.put("selectKey", s.selectKey());
            m.put("files", s.files());
            m.put("line", s.line());
            st.put(id, m);
        });
        out.put("statements", st);
        out.put("unresolved", idx.unresolved());
        GoldenFiles.assertJson("analyze/mapper-index.json", out);
    }

    /** 6-37b — namespace 집합은 문장 키(ns.id)의 마지막 점 앞. 프로그램 분석이 이 FQCN 의 인터페이스를 매퍼로 본다 */
    @Test
    void namespacesAreStatementKeyPrefixes() throws IOException {
        assertEquals(java.util.Set.of("Sample"), MapperIndex.scan(sources()).namespaces());
    }

    /** R-C-I8 — iBATIS sqlMap: 열쇠는 id 그대로 · #이름#·$이름$ 을 MyBatis 꼴로 · prepend · isNotEmpty 는 동적 */
    @Test
    void ibatisSqlMap() {
        String xml = "<sqlMap namespace=\"Old\"><sql id=\"cols\">ID, NM</sql>\n"
                + "<select id=\"oldDAO.selectOld\" resultClass=\"map\">SELECT <include refid=\"cols\"/> FROM T88_OLD WHERE ID = #id# "
                + "<isNotEmpty prepend=\"AND\" property=\"nm\">NM = #nm:VARCHAR#</isNotEmpty> ORDER BY $sort$</select>\n"
                + "<statement id=\"oldDAO.touch\">UPDATE T88_OLD SET X = $val$ WHERE Y IN (SELECT Y FROM T88_REF)</statement></sqlMap>\n";
        MapperIndex.Index idx = MapperIndex.scan(List.of(new Source("old.xml", xml, null, null, null)));
        MapperIndex.Statement sel = idx.get("oldDAO.selectOld");
        assertEquals(Map.of("T88_OLD", "R"), letters(sel));
        assertEquals(List.of("sort"), sel.dollars());
        org.junit.jupiter.api.Assertions.assertTrue(sel.dynamic());
        MapperIndex.Statement touch = idx.get("oldDAO.touch");
        assertEquals(Map.of("T88_OLD", "U", "T88_REF", "R"), letters(touch));
        assertEquals(List.of("val"), touch.dollars());
        assertEquals(List.of(), idx.unresolved().stream().filter(u -> "missing".equals(u.kind())).toList());
    }

    /** 6-69 — 파일 이름·databaseId 가 MySQL 계열이 아님을 드러내면 # 는 연산자(뒤 표가 산다). MySQL 이름이면 지금처럼 줄 주석 */
    @Test
    void hashOperatorByDialectHint() {
        String q = "SELECT a # b FROM T89_A";
        MapperIndex.Index idx = MapperIndex.scan(List.of(
                mapper("T89_SQL_postgres.xml", "Pg", null, q),
                mapper("T89_SQL_mysql.xml", "My", null, q),
                mapper("T89_SQL.xml", "Ora", "oracle", q)));
        assertEquals(Map.of("T89_A", "R"), letters(idx.get("Pg.x")));
        assertEquals(Map.of(), letters(idx.get("My.x")));
        assertEquals(Map.of("T89_A", "R"), letters(idx.get("Ora.x")));
    }

    /** 6-72 — 파일 이름은 _ - . 로 끊긴 토막이 방언 낱말과 같을 때만. db2 는 databaseId 에서만 */
    @Test
    void hashHintNeedsAWholeWord() {
        String q = "SELECT a # b FROM T90_A";
        MapperIndex.Index idx = MapperIndex.scan(List.of(
                mapper("OrderMapper_db2.xml", "N1", null, q),
                mapper("OracleSyncMapper.xml", "N2", null, q),
                mapper("T90_SQL_postgresql.xml", "N3", null, q),
                mapper("T90-oracle.xml", "N4", null, q),
                mapper("T90.tibero.xml", "N5", null, q),
                mapper("T90.xml", "N6", "db2", q)));
        Map<String, Map<String, String>> said = new java.util.TreeMap<>();
        for (String ns : List.of("N1", "N2", "N3", "N4", "N5", "N6")) {
            said.put(ns, letters(idx.get(ns + ".x")));
        }
        Map<String, String> r = Map.of("T90_A", "R");
        assertEquals(Map.of("N1", Map.of(), "N2", Map.of(), "N3", r, "N4", r, "N5", r, "N6", r), said);
    }

    /** 6-76(사용자 (가)) — 토막 끝의 판 번호(숫자 + 글자 하나까지)는 떼고 방언 낱말과 맞댄다. db2 는 파일 이름에서 안 본다 */
    @Test
    void hashHintTakesVersionSuffix() {
        String q = "SELECT a # b FROM T90_A";
        List<String> files = List.of("T90_SQL_oracle11g.xml", "sql-map-tibero6.xml", "T90.postgres14.xml", "T90_mssql2019.xml", "T90_oracle2.backup.xml",
                "T90_db2.xml", "T90_sqlserver2008r2.xml");
        List<Source> src = new java.util.ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            src.add(mapper(files.get(i), "V" + i, null, q));
        }
        MapperIndex.Index idx = MapperIndex.scan(src);
        Map<String, Map<String, String>> said = new java.util.TreeMap<>();
        for (int i = 0; i < files.size(); i++) {
            said.put(files.get(i), letters(idx.get("V" + i + ".x")));
        }
        Map<String, String> r = Map.of("T90_A", "R");
        assertEquals(Map.of("T90_SQL_oracle11g.xml", r, "sql-map-tibero6.xml", r, "T90.postgres14.xml", r, "T90_mssql2019.xml", r,
                "T90_oracle2.backup.xml", r, "T90_db2.xml", Map.of(), "T90_sqlserver2008r2.xml", Map.of()), said);
    }

    /** 0-118 — MySQL 낱말이 어디든 있으면 주석 · 폴더 이름만으로는 방언을 안 짐작한다 */
    @Test
    void hashHintNegatives() {
        String q = "SELECT a # b FROM T90_A";
        MapperIndex.Index idx = MapperIndex.scan(List.of(
                mapper("T90_SQL_maria.xml", "M1", null, q),
                mapper("T90_SQL_oracle_mysql.xml", "M2", null, q),
                mapper("T90_SQL_oracle.xml", "M3", "mariadb", q),
                mapper("oracle/T90_SQL.xml", "M4", null, q)));
        Map<String, Map<String, String>> said = new java.util.TreeMap<>();
        for (String ns : List.of("M1", "M2", "M3", "M4")) {
            said.put(ns, letters(idx.get(ns + ".x")));
        }
        assertEquals(Map.of("M1", Map.of(), "M2", Map.of(), "M3", Map.of(), "M4", Map.of()), said);
    }

    /** 6-84(C1-07·C1-13·C1-14) — 방언별 조각은 databaseId 로 갈린다 · iBATIS selectKey 의 $이름$ 은 바인드 · selectKey 안 include 의 표 */
    @Test
    void dialectFragmentsAndSelectKey() {
        String frag = "<mapper namespace=\"N\">\n<sql id=\"src\" databaseId=\"oracle\">T_ORA</sql>\n<sql id=\"src\" databaseId=\"mysql\">T_MY</sql>\n"
                + "<select id=\"q\" databaseId=\"oracle\">SELECT * FROM <include refid=\"src\"/></select>\n"
                + "<select id=\"q\" databaseId=\"mysql\">SELECT * FROM <include refid=\"src\"/></select>\n</mapper>\n";
        MapperIndex.Index a = MapperIndex.scan(List.of(new Source("N_SQL.xml", frag, null, null, null)));
        assertEquals(Map.of("T_MY", "R", "T_ORA", "R"), letters(a.get("N.q")));
        String ib = "<sqlMap namespace=\"B\"><insert id=\"ins\"><selectKey keyProperty=\"id\" resultClass=\"int\">SELECT MAX(ID) FROM $tableName$</selectKey>"
                + "INSERT INTO $tableName$ (ID) VALUES (#id#)</insert></sqlMap>\n";
        MapperIndex.Index b = MapperIndex.scan(List.of(new Source("B_SQL.xml", ib, null, null, null)));
        assertEquals(Map.of(), letters(b.get("ins")));
        org.junit.jupiter.api.Assertions.assertTrue(b.unresolved().stream().anyMatch(u -> "table".equals(u.kind())), b.unresolved().toString());
        String inc = "<mapper namespace=\"K\">\n<sql id=\"t\">T_SEQ</sql>\n<insert id=\"i\"><selectKey keyProperty=\"id\" resultType=\"int\" order=\"BEFORE\">"
                + "SELECT MAX(ID) FROM <include refid=\"t\"/></selectKey>INSERT INTO T1 (ID) VALUES (#{id})</insert>\n</mapper>\n";
        MapperIndex.Index c = MapperIndex.scan(List.of(new Source("K_SQL.xml", inc, null, null, null)));
        assertEquals(Map.of("T1", "C", "T_SEQ", "R"), letters(c.get("K.i")));
    }

    private static Source mapper(String file, String ns, String databaseId, String q) {
        String db = databaseId == null ? "" : " databaseId=\"" + databaseId + "\"";
        return new Source(file, "<mapper namespace=\"" + ns + "\"><select id=\"x\"" + db + ">" + q + "</select></mapper>", null, null, null);
    }

    /** C-MAP-INCLUDE — 자기를 끼우는 조각은 끊고 missing 하나. 펼치기가 거듭제곱으로 안 커진다 */
    @Test
    void selfIncludeIsCutAndNoted() {
        String xml = "<mapper namespace=\"N\"><sql id=\"a\">" + "<include refid=\"a\"/>".repeat(30)
                + "X</sql><select id=\"s\">SELECT 1 FROM T <include refid=\"a\"/></select></mapper>";
        MapperIndex.Index idx = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                () -> MapperIndex.scan(List.of(new Source("n.xml", xml, null, null, null))));
        assertEquals(Map.of("T", "R"), letters(idx.get("N.s")));
        assertEquals(true, idx.unresolved().stream().anyMatch(u -> "missing".equals(u.kind()) && "include N.a".equals(u.detail())), idx.unresolved().toString());
    }

    /** C-MAP-INCLUDE — 훑기가 취소를 듣는다(tick 이 던지면 그 예외로 끝난다) */
    @Test
    void scanHonoursTick() {
        List<Source> xml = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            xml.add(new Source("m" + i + ".xml", "<mapper namespace=\"M" + i + "\"><select id=\"s\">SELECT 1 FROM T</select></mapper>", null, null, null));
        }
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        int cancelAt = 3;
        Runnable tick = () -> {
            if (n.incrementAndGet() >= cancelAt) {
                throw new IllegalStateException("취소");
            }
        };
        IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> MapperIndex.scan(xml, tick));
        assertEquals("취소", e.getMessage());
    }

    private static Map<String, String> letters(MapperIndex.Statement s) {
        Map<String, String> m = new java.util.TreeMap<>();
        s.refs().forEach(r -> m.put(r.table(), r.letters()));
        return m;
    }

    /** 6-8(PR #27 리뷰 ⑤) — 여는 표시만 있고 닫는 표시가 없으면 남은 글을 그대로 돌려준다. 예외·미해결 없음 */
    @Test
    void brokenIncludeMarkerKeepsText() {
        String text = "SELECT * FROM T " + MapperIndex.INC_OPEN + "frag";
        MapperIndex.Raw r = new MapperIndex.Raw("Ns", "id", "select", text, List.of(), "a.xml", 1, false, null);
        List<Unresolved> unresolved = new ArrayList<>();
        assertEquals(text, MapperIndex.include(r, text, Map.of(), unresolved, new HashSet<>(), 0));
        assertEquals(List.of(), unresolved);
    }
}
