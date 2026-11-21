package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.check.Source;
import org.junit.jupiter.api.Test;

/**
 * 6-14 — JPA 색인. 픽스처: @Table 있음(스키마)·없음(짓기)·@MappedSuperclass·SINGLE_TABLE 자식·JOINED 자식·@Entity(name)·@SecondaryTable·@JoinTable·
 * @CollectionTable·R2DBC @Table·javax·jakarta·와일드카드 import · 저장소(파생·JPQL·네이티브·텍스트 블록·모르는 이름·모르는 엔티티·커스텀·바탕 사슬·
 * 다른 패키지 @Query) · 모듈마다 같은 엔티티 이름
 */
class JpaIndexTest {

    static final Path DIR = Path.of("src/test/resources/fixtures/analyze/jpa");

    static List<Source> sources() throws IOException {
        List<Source> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(DIR)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                String rel = DIR.relativize(p).toString().replace('\\', '/');
                out.add(new Source(rel, Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"), null, null, null));
            }
        }
        return out;
    }

    /** 0-94 — 스택이 넘치는 파일은 건너뛰고 나머지 엔티티는 읽는다 */
    @Test
    void deepFileIsSkipped() {
        List<Source> in = List.of(new Source("Deep.java", "class Deep { int x = " + "(".repeat(6000) + "1" + ")".repeat(6000) + "; }\n", null, null, null),
                new Source("p/User.java", "package p;\nimport javax.persistence.Entity;\n@Entity\npublic class User {\n    Long id;\n}\n", null, null, null));
        JpaIndex x = JpaIndex.scan(in);
        assertEquals(List.of("p.User"), x.entities().stream().map(JpaIndex.Entity::fqcn).toList());
    }

    /** R-C-I2 — 예약어와 겹치는 엔티티 이름(Order)과 연관 경로 조인(join o.items i)의 표 */
    @Test
    void reservedWordEntityAndPathJoin() {
        String h = "package p;\nimport javax.persistence.*;\nimport java.util.List;\n";
        List<Source> in = List.of(
                new Source("p/Order.java", h + "@Entity\npublic class Order {\n    @Id Long id;\n    @OneToMany List<Item> items;\n    Coupon coupon;\n}\n", null, null, null),
                new Source("p/Item.java", h + "@Entity\n@Table(name = \"T88_ITEM\")\npublic class Item {\n    @Id Long id;\n}\n", null, null, null),
                new Source("p/Coupon.java", h + "public class Coupon {\n}\n", null, null, null));
        JpaIndex x = JpaIndex.scan(in);
        String order = x.entities().stream().filter(e -> "Order".equals(e.name())).findFirst().orElseThrow().table();
        JpaIndex.From from = new JpaIndex.From("p", Map.of(), "");
        List<Unresolved> out = new ArrayList<>();
        assertEquals(List.of(order), tables(x.jpql("select o from Order o where o.id = 1", from, "f", 1, "w", out)));
        assertEquals(List.of(), out);
        assertEquals(new java.util.TreeSet<>(List.of(order, "T88_ITEM")),
                new java.util.TreeSet<>(tables(x.jpql("select o from Order o join o.items i where i.id = 1", from, "f", 1, "w", out))));
        assertEquals(List.of(), out);
        assertEquals(List.of(order), tables(x.jpql("select o from Order o join fetch o.coupon c", from, "f", 1, "w", out)));
        assertEquals(List.of("jpql w:o.coupon"), out.stream().map(u -> u.kind() + " " + u.detail()).toList());
        out.clear();
        assertEquals(List.of(order), tables(x.jpql("select o from Order o order by o.id", from, "f", 1, "w", out)));
        assertEquals(List.of(), out);
    }

    private static List<String> tables(List<SqlTables.Ref> refs) {
        return refs.stream().map(SqlTables.Ref::table).toList();
    }

    /** 색인 + 저장소마다 선언 메서드와 상속 메서드 넷(findById·save·deleteById·flush)의 표·글자 */
    @Test
    void golden() throws IOException {
        JpaIndex x = JpaIndex.scan(sources());
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("entities", x.entities());
        g.put("repositories", x.repositories());
        Map<String, Object> refs = new TreeMap<>();
        List<Unresolved> out = new ArrayList<>();
        for (JpaIndex.Repo r : x.repositories()) {
            List<String> methods = new ArrayList<>(List.of("findById", "save", "deleteById", "flush"));
            Files.readAllLines(DIR.resolve(r.file())).stream().map(String::trim).filter(l -> l.matches("^[\\w.<>]+ \\w+\\(.*\\);$"))
                    .map(l -> l.substring(l.indexOf(' ') + 1, l.indexOf('('))).forEach(methods::add);
            for (String m : methods) {
                Map<String, String> letters = new TreeMap<>();
                x.refs(r.fqcn(), m, out).forEach(ref -> letters.put(ref.table(), ref.letters()));
                refs.put(r.simpleName() + "." + m, letters);
            }
        }
        g.put("refs", refs);
        g.put("unresolved", x.unresolved());
        g.put("refsUnresolved", out);
        GoldenFiles.assertJson("analyze/jpa-index.json", g);
    }

    @Test
    void nameRules() {
        assertEquals(EnumSet.of(SqlTables.Crud.R), JpaIndex.crud("existsByEmail"));
        assertEquals(EnumSet.of(SqlTables.Crud.C, SqlTables.Crud.U), JpaIndex.crud("saveAllAndFlush"));
        assertEquals(EnumSet.of(SqlTables.Crud.D), JpaIndex.crud("removeByCode"));
        assertNull(JpaIndex.crud("archive"));
        assertEquals("BBS_MASTER", JpaIndex.snake("BbsMaster"));
        assertEquals("URL_INFO", JpaIndex.snake("URLInfo"));
        assertEquals("TB_X", JpaIndex.norm("\"sch\".\"tb_x\""));
        assertEquals("mod-b/", JpaIndex.module("mod-b/src/main/java/com/ex/b/Notice.java"));
    }

    /** 0-85g — 긴 JPQL 리터럴이 교대 반복 재귀로 스택을 넘기지 않는다(소유 수량자) */
    @Test
    void longLiteralDoesNotOverflow() throws IOException {
        JpaIndex x = JpaIndex.scan(sources());
        String q = "select e from UserMaster e where e.esntlId = '" + "a".repeat(200_000) + "'";
        List<SqlTables.Ref> refs = x.jpql(q, new JpaIndex.From("com.ex.c", Map.of(), "mod-c/"), "f", 1, "w", new ArrayList<>());
        assertEquals(List.of("COMVNUSERMASTER"), refs.stream().map(SqlTables.Ref::table).toList());
    }
}
