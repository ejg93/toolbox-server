package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    static final String CODE_REPO = "CodeRepository";
    static final String CODE = "Code";

    static final String T_ORDER = "T_ORDER";

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

    /** 6-64 — @MappedSuperclass 조상이 선언한 연관 경로도 푼다. 조상에도 없는 속성은 지금처럼 jpql 미해결 */
    @Test
    void inheritedAssociationPath() {
        String h = "package p;\nimport javax.persistence.*;\n";
        List<Source> in = List.of(
                new Source("p/Base.java", h + "@MappedSuperclass\npublic abstract class Base {\n    @ManyToOne Lang language;\n}\n", null, null, null),
                new Source("p/Desc.java", h + "@Entity\n@Table(name = \"T89_DESC\")\npublic class Desc extends Base {\n    @Id Long id;\n}\n", null, null, null),
                new Source("p/Lang.java", h + "@Entity\n@Table(name = \"T89_LANG\")\npublic class Lang {\n    @Id Long id;\n}\n", null, null, null));
        JpaIndex x = JpaIndex.scan(in);
        JpaIndex.From from = new JpaIndex.From("p", Map.of(), "");
        List<Unresolved> out = new ArrayList<>();
        assertEquals(new java.util.TreeSet<>(List.of("T89_DESC", "T89_LANG")),
                new java.util.TreeSet<>(tables(x.jpql("select d from Desc d join d.language l where l.id = 1", from, "f", 1, "w", out))));
        assertEquals(List.of(), out);
        assertEquals(List.of("T89_DESC"), tables(x.jpql("select d from Desc d join d.nope n", from, "f", 1, "w", out)));
        assertEquals(List.of("jpql w:d.nope"), out.stream().map(u -> u.kind() + " " + u.detail()).toList());
    }

    /** 6-73 — 조상은 자식의 extends 글이 가리킬 때만 오른다. 프로젝트 밖 조상을 import 한 엔티티는 이름만 같은 프로젝트 클래스로 안 오른다 */
    @Test
    void ancestorMustBeNamedByTheChild() {
        String lang = "T90_LANG";
        java.util.function.UnaryOperator<String> h = pkg -> "package " + pkg + ";\nimport javax.persistence.*;\n";
        String body = " extends %s {\n    @Id Long id;\n}\n";
        List<Source> in = List.of(
                new Source("q/BaseEntity.java", h.apply("q") + "import p.Lang;\n@MappedSuperclass\npublic abstract class BaseEntity {\n    @ManyToOne Lang language;\n}\n", null, null, null),
                new Source("p/Lang.java", h.apply("p") + "@Entity\n@Table(name = \"T90_LANG\")\npublic class Lang {\n    @Id Long id;\n}\n", null, null, null),
                new Source("p/Vendor.java", h.apply("p") + "import com.vendor.BaseEntity;\n@Entity\n@Table(name = \"T90_VENDOR\")\npublic class Vendor" + body.formatted("BaseEntity"), null, null, null),
                new Source("p/Star.java", h.apply("p") + "import q.*;\n@Entity\n@Table(name = \"T90_STAR\")\npublic class Star" + body.formatted("BaseEntity"), null, null, null),
                new Source("p/Named.java", h.apply("p") + "import q.BaseEntity;\n@Entity\n@Table(name = \"T90_NAMED\")\npublic class Named" + body.formatted("BaseEntity"), null, null, null),
                new Source("p/Full.java", h.apply("p") + "@Entity\n@Table(name = \"T90_FULL\")\npublic class Full" + body.formatted("q.BaseEntity"), null, null, null));
        JpaIndex x = JpaIndex.scan(in);
        JpaIndex.From from = new JpaIndex.From("p", Map.of(), "");
        Map<String, Object> said = new TreeMap<>();
        for (String e : List.of("Vendor", "Star", "Named", "Full")) {
            List<Unresolved> out = new ArrayList<>();
            List<String> t = new java.util.TreeSet<>(tables(x.jpql("select e from " + e + " e join e.language l", from, "f", 1, "w", out))).stream().toList();
            said.put(e, List.of(t, out.stream().map(u -> u.kind() + " " + u.detail()).toList()));
        }
        assertEquals(Map.of(
                "Vendor", List.of(List.of("T90_VENDOR"), List.of("jpql w:e.language")),
                "Star", List.of(List.of(lang, "T90_STAR"), List.of()),
                "Named", List.of(List.of(lang, "T90_NAMED"), List.of()),
                "Full", List.of(List.of("T90_FULL", lang), List.of())), said);
    }

    /** 6-96(C2-14) — 순환 상속(A↔B · 자기 상속)도 끝나고 엔티티 표는 지은 이름 */
    @Test
    void cyclicInheritanceEnds() {
        String h = "package c;\nimport javax.persistence.*;\n";
        List<Source> in = List.of(new Source("c/A.java", h + "@Entity\npublic class A extends B {\n    @Id Long id;\n}\n", null, null, null),
                new Source("c/B.java", h + "@Entity\npublic class B extends A {\n    @Id Long id;\n}\n", null, null, null),
                new Source("c/Selfish.java", h + "@Entity\npublic class Selfish extends Selfish {\n    @Id Long id;\n}\n", null, null, null));
        JpaIndex x = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> JpaIndex.scan(in));
        assertEquals(3, x.entities().size());
        assertTrue(x.entities().stream().allMatch(e -> e.guessed() && List.of("A", "B", "SELFISH").contains(e.table())), x.entities().toString());
    }

    /** 6-96(C2-08) — 후보가 하나여도 명시 import 가 다른 FQCN 이면 그 프로젝트 클래스로 안 푼다 · 그 FQCN 이면 풀린다 */
    @Test
    void singleCandidateRespectsExplicitImport() {
        String h = "package legacy;\nimport javax.persistence.*;\n";
        List<Source> in = List.of(new Source("legacy/Code.java", h + "@Entity\n@Table(name = \"T92_CODE\")\npublic class Code {\n    @Id Long id;\n}\n", null, null, null),
                new Source("legacy/CodeRepository.java", "package legacy;\nimport org.springframework.data.jpa.repository.JpaRepository;\n"
                        + "public interface CodeRepository extends JpaRepository<Code, Long> {\n}\n", null, null, null));
        JpaIndex x = JpaIndex.scan(in);
        JpaIndex.From vendor = new JpaIndex.From("web", Map.of(CODE_REPO, "com.vendor.common.CodeRepository", CODE, "com.vendor.domain.Code"), "");
        assertEquals(List.of(), x.repos(CODE_REPO, vendor));
        assertEquals(List.of(), x.entitiesNamed(CODE, vendor));
        JpaIndex.From own = new JpaIndex.From("web", Map.of(CODE_REPO, "legacy.CodeRepository", CODE, "legacy.Code"), "");
        assertEquals(1, x.repos(CODE_REPO, own).size());
        assertEquals(1, x.entitiesNamed(CODE, own).size());
        assertEquals(1, x.repos(CODE_REPO, new JpaIndex.From("legacy", Map.of(), "")).size(), "같은 패키지");
    }

    /** 6-89(C2-06) — JOIN 대상 뒤 쉼표도 다음 엔티티를 연다 · 괄호 안 쉼표는 아니다 */
    @Test
    void commaAfterJoin() {
        java.util.function.BiFunction<String, String, Source> e = (n, t) -> new Source("p/" + n + ".java",
                "package p;\nimport javax.persistence.*;\n@Entity\n@Table(name = \"" + t + "\")\npublic class " + n + " {\n    @Id Long id;\n}\n", null, null, null);
        JpaIndex x = JpaIndex.scan(List.of(e.apply("Order", T_ORDER), e.apply("Item", "T_ITEM"), e.apply("Product", "T_PRODUCT"),
                e.apply("Customer", "T_CUSTOMER"), e.apply("Address", "T_ADDRESS")));
        JpaIndex.From from = new JpaIndex.From("p", Map.of(), "");
        java.util.function.Function<String, List<String>> t = q -> new java.util.TreeSet<>(tables(x.jpql(q, from, "f", 1, "w", new ArrayList<>()))).stream().toList();
        assertEquals(List.of(T_ORDER, "T_PRODUCT"), t.apply("select o from Order o join o.items i, Product p where p.id = o.id"));
        assertEquals(List.of("T_ADDRESS", "T_CUSTOMER", T_ORDER), t.apply("select o from Order o join o.items i, Customer c join c.addresses a, Address d"));
        assertEquals(List.of(T_ORDER, "T_PRODUCT"), t.apply("select o from Order o, Product p join o.items i"));
        assertEquals(List.of(T_ORDER), t.apply("select o from Order o join o.items i where o.id in (1, 2)"));
    }

    /** 6-77 — 표 짓기의 조상·저장소 사슬·커스텀 저장소도 자식의 extends 글이 가리킬 때만(JpqlPaths.named 와 같은 판정) */
    @Test
    void ancestorsMustBeNamedEverywhere() {
        java.util.function.UnaryOperator<String> h = pkg -> "package " + pkg + ";\nimport javax.persistence.*;\n";
        String jr = "import org.springframework.data.jpa.repository.JpaRepository;\n";
        List<Source> in = List.of(
                new Source("q/BaseEntity.java", h.apply("q") + "@Entity\n@Table(name = \"T91_BASE\")\npublic class BaseEntity {\n    @Id Long id;\n}\n", null, null, null),
                new Source("p/Vendor.java", h.apply("p") + "import com.vendor.BaseEntity;\n@Entity\npublic class Vendor extends BaseEntity {\n}\n", null, null, null),
                new Source("p/Star.java", h.apply("p") + "import q.*;\n@Entity\npublic class Star extends BaseEntity {\n}\n", null, null, null),
                new Source("p/Full.java", h.apply("p") + "@Entity\npublic class Full extends q.BaseEntity {\n}\n", null, null, null),
                new Source("q/BaseRepo.java", "package q;\n" + jr + "public interface BaseRepo<T> extends JpaRepository<T, Long> {\n}\n", null, null, null),
                new Source("p/VendorRepo.java", "package p;\nimport com.vendor.BaseRepo;\npublic interface VendorRepo extends BaseRepo<Vendor> {\n}\n", null, null, null),
                new Source("p/StarRepo.java", "package p;\nimport q.BaseRepo;\npublic interface StarRepo extends BaseRepo<Star> {\n}\n", null, null, null));
        JpaIndex x = JpaIndex.scan(in);
        Map<String, JpaIndex.Entity> by = new TreeMap<>();
        x.entities().forEach(e -> by.put(e.fqcn(), e));
        assertTrue(by.get("p.Vendor").guessed(), by.get("p.Vendor").toString());
        assertTrue(x.unresolved().stream().anyMatch(u -> "entityName".equals(u.kind()) && "Vendor".equals(u.detail())), x.unresolved().toString());
        assertEquals("T91_BASE", by.get("p.Star").table());
        assertEquals("T91_BASE", by.get("p.Full").table());
        assertEquals(List.of("p.StarRepo p.Star"), x.repositories().stream().filter(r -> r.fqcn().startsWith("p."))
                .map(r -> r.fqcn() + " " + r.entity()).toList(), "q.BaseRepo 자체(제네릭 바탕)는 지금처럼 저장소 — 이 행과 무관");
    }

    /** 마무리 리뷰 I2 — 중첩 클래스 조상: 바깥 클래스 import · 별 import 로 부른 Outer.Base · Outer 의 별 import. 프로젝트 밖 Outer 는 안 오른다 */
    @Test
    void nestedAncestorNamedThroughItsOuter() {
        java.util.function.UnaryOperator<String> h = pkg -> "package " + pkg + ";\nimport javax.persistence.*;\n";
        String lang = "T90_LANG";
        String up = "true 0";
        String body = " extends %s {\n    @Id Long id;\n}\n";
        List<Source> in = new ArrayList<>(List.of(
                new Source("q/Outer.java", h.apply("q") + "import p.Lang;\npublic class Outer {\n    @MappedSuperclass\n    public abstract static class Base {\n        @ManyToOne Lang language;\n    }\n}\n", null, null, null),
                new Source("p/Lang.java", h.apply("p") + "@Entity\n@Table(name = \"" + lang + "\")\npublic class Lang {\n    @Id Long id;\n}\n", null, null, null)));
        Map<String, String[]> kids = new LinkedHashMap<>();
        kids.put("OuterImport", new String[] {"import q.Outer;\n", "Outer.Base"});
        kids.put("PkgStar", new String[] {"import q.*;\n", "Outer.Base"});
        kids.put("OuterStar", new String[] {"import q.Outer.*;\n", "Base"});
        kids.put("Direct", new String[] {"import q.Outer.Base;\n", "Base"});
        kids.put("Foreign", new String[] {"import com.vendor.Outer;\n", "Outer.Base"});
        kids.forEach((n, v) -> in.add(new Source("p/" + n + ".java", h.apply("p") + v[0] + "@Entity\n@Table(name = \"T90_" + n.toUpperCase(java.util.Locale.ROOT)
                + "\")\npublic class " + n + body.formatted(v[1]), null, null, null)));
        JpaIndex x = JpaIndex.scan(in);
        JpaIndex.From from = new JpaIndex.From("p", Map.of(), "");
        Map<String, Object> said = new TreeMap<>();
        for (String e : kids.keySet()) {
            List<Unresolved> out = new ArrayList<>();
            said.put(e, new java.util.TreeSet<>(tables(x.jpql("select e from " + e + " e join e.language l", from, "f", 1, "w", out))).contains(lang)
                    + " " + out.size());
        }
        assertEquals(Map.of("OuterImport", up, "PkgStar", up, "OuterStar", up, "Direct", up, "Foreign", "false 1"), said);
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
