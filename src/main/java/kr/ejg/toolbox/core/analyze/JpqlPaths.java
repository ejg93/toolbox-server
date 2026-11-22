package kr.ejg.toolbox.core.analyze;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** R-C-I2 — JPQL 의 연관 경로 조인({@code JOIN [FETCH] 별칭.속성 [[AS] 별칭]})을 엔티티 필드 형식으로 푼다. JpaIndex 가 메서드 상한이라 따로 둔다 */
final class JpqlPaths {

    /** 조상 사다리 상한 */
    private static final int MAX_UP = 8;

    private JpqlPaths() {
    }

    /** 한 번 훑기의 상태 — 별칭 → 엔티티 */
    private record Walk(List<String> t, Function<String, List<JpaIndex.Entity>> byName, Function<String, JpaIndex.Cls> types,
            BiFunction<String, JpaIndex.From, List<JpaIndex.Cls>> lookup, Consumer<String> read, Consumer<String> miss,
            Map<String, JpaIndex.Entity> alias) {
    }

    /**
     * @param t      JPQL 낱말(JpaIndex.JPQL_TOKEN 이 자른 것 — 식별자·점 이은 이름·괄호·쉼표)
     * @param byName 엔티티 이름 → 엔티티(참조하는 쪽으로 가른 것). 하나일 때만 쓴다
     * @param types  FQCN → 클래스(없으면 null — 필드 없음으로 본다)
     * @param lookup 단순 이름·참조 자리 → 클래스 후보(extends 조상 찾기 — 하나일 때만 오른다)
     * @param read   푼 경로의 대상 표(부르는 쪽이 R 을 더한다)
     * @param miss   별칭은 아는데 속성·대상 엔티티를 못 찾은 경로(「별칭.속성」 글 그대로)
     */
    static void resolve(List<String> t, Function<String, List<JpaIndex.Entity>> byName, Function<String, JpaIndex.Cls> types,
            BiFunction<String, JpaIndex.From, List<JpaIndex.Cls>> lookup, Consumer<String> read, Consumer<String> miss) {
        Walk w = new Walk(t, byName, types, lookup, read, miss, new HashMap<>());
        boolean inFrom = false;
        for (int i = 0; i < t.size(); i++) {
            String u = t.get(i).toUpperCase(Locale.ROOT);
            switch (u) {
                case "FROM" -> {
                    inFrom = true;
                    at(w, i + 1);
                }
                case "JOIN" -> {
                    inFrom = false;
                    at(w, i + 1 < t.size() && "FETCH".equalsIgnoreCase(t.get(i + 1)) ? i + 2 : i + 1);
                }
                case "," -> at(w, inFrom ? i + 1 : t.size());
                default -> inFrom = stillFrom(inFrom, u, byName.apply(t.get(i)).isEmpty());
            }
        }
    }

    /** FROM 목록이 이어지나 — 괄호·멈춤 낱말(엔티티 이름 아님)에서 끝난다, AS 는 잇는다 */
    private static boolean stillFrom(boolean inFrom, String u, boolean notEntity) {
        boolean stop = "(".equals(u) || ")".equals(u) || JpaIndex.JPQL_STOP.contains(u) && notEntity;
        return stop ? inFrom && "AS".equals(u) : inFrom;
    }

    /** j 자리 낱말 하나 — 엔티티 이름이면 별칭을 적고, 「별칭.속성」 이면 대상 표를 더한다 */
    private static void at(Walk w, int j) {
        if (j >= w.t().size()) {
            return;
        }
        String id = w.t().get(j);
        if ("(".equals(id) || ",".equals(id) || ")".equals(id)) {
            return;
        }
        String[] seg = id.split("\\.", -1);
        String last = seg[seg.length - 1];
        if (seg.length == 1 || !last.isEmpty() && Character.isUpperCase(last.charAt(0))) {
            List<JpaIndex.Entity> hit = w.byName().apply(last);
            Optional<JpaIndex.Entity> only = hit.size() == 1 ? Optional.of(hit.get(0)) : Optional.empty();
            only.ifPresent(e -> alias(w, j + 1, e));
        } else if (seg.length == 2 && w.alias().containsKey(seg[0].toLowerCase(Locale.ROOT))) {
            pathAt(w, j, seg[1], w.alias().get(seg[0].toLowerCase(Locale.ROOT)));
        }
    }

    /** 경로 {@code 별칭.속성} — 속성 형식의 대상 엔티티가 하나면 R, 아니면 miss */
    private static void pathAt(Walk w, int j, String field, JpaIndex.Entity owner) {
        Optional<JpaIndex.Entity> target = field(w, owner, field).map(FieldDeclaration::getCommonType)
                .map(JpqlPaths::targetName).map(w.byName()).filter(l -> l.size() == 1).map(l -> l.get(0));
        if (target.isEmpty()) {
            w.miss().accept(w.t().get(j));
            return;
        }
        w.read().accept(target.get().table());
        alias(w, j + 1, target.get());
    }

    /** 속성 선언 — 소유 엔티티 클래스에서 extends 조상으로(@MappedSuperclass·엔티티 조상). 조상을 하나로 못 정하면 거기서 끝(6-64) */
    private static Optional<FieldDeclaration> field(Walk w, JpaIndex.Entity owner, String name) {
        JpaIndex.Cls c = w.types().apply(owner.fqcn());
        for (int up = 0; c != null && c.decl != null && up <= MAX_UP; up++) {
            Optional<FieldDeclaration> f = c.decl.getFieldByName(name);
            if (f.isPresent()) {
                return f;
            }
            c = parent(w, c);
        }
        return Optional.empty();
    }

    private static JpaIndex.Cls parent(Walk w, JpaIndex.Cls c) {
        if (c.decl.getExtendedTypes().isEmpty()) {
            return null;
        }
        ClassOrInterfaceType ext = c.decl.getExtendedTypes(0);
        List<JpaIndex.Cls> up = w.lookup().apply(ext.getName().getIdentifier(), c.from());
        return up.size() == 1 && named(c, ext, up.get(0)) ? up.get(0) : null;
    }

    /**
     * c 의 extends 글이 u 를 가리키나 — 스코프 붙은 이름 · 명시 import · 같은 패키지 · 별 import(6-73). 이름만 같은 다른 패키지 클래스로 안 오른다.
     * 중첩 조상(q.Outer.Base)은 스코프 머리를 import 로 풀거나 별 import 패키지·바깥 클래스 뒤에 이어 댄다(마무리 리뷰 I2)
     */
    static boolean named(JpaIndex.Cls c, ClassOrInterfaceType ext, JpaIndex.Cls u) {
        if (ext.getScope().isPresent()) {
            String q = ext.getNameWithScope();
            int dot = q.indexOf('.');
            String head = c.imports.get(q.substring(0, dot));
            return q.equals(u.fqcn) || (c.pkg + "." + q).equals(u.fqcn) || head != null && (head + q.substring(dot)).equals(u.fqcn) || starred(c, q, u);
        }
        String imp = c.imports.get(ext.getName().getIdentifier());
        if (imp != null) {
            return imp.equals(u.fqcn);
        }
        return u.pkg.equals(c.pkg) || starred(c, u.name, u);
    }

    /** 별 import 패키지(또는 바깥 클래스) 뒤에 q 를 이으면 u 인가 */
    private static boolean starred(JpaIndex.Cls c, String q, JpaIndex.Cls u) {
        return c.stars.stream().anyMatch(s -> (s + "." + q).equals(u.fqcn));
    }

    /** 형식의 대상 이름 — 타입 인자가 있으면 마지막 것(List&lt;Item&gt;·Map&lt;K, Item&gt;), 없으면 형식 이름 */
    private static String targetName(Type type) {
        if (type instanceof ClassOrInterfaceType c) {
            Optional<Type> arg = c.getTypeArguments().filter(a -> !a.isEmpty()).map(a -> a.get(a.size() - 1));
            if (arg.isPresent()) {
                return arg.get() instanceof ClassOrInterfaceType a ? a.getNameAsString() : arg.get().asString();
            }
            return c.getNameAsString();
        }
        return type.asString();
    }

    /** k 자리(AS 건너뜀)가 별칭이면 적는다 */
    private static void alias(Walk w, int k, JpaIndex.Entity e) {
        int a = k < w.t().size() && "AS".equalsIgnoreCase(w.t().get(k)) ? k + 1 : k;
        if (a >= w.t().size()) {
            return;
        }
        String name = w.t().get(a);
        boolean ident = !name.isEmpty() && Character.isJavaIdentifierStart(name.charAt(0)) && !name.contains(".");
        if (ident && !JpaIndex.JPQL_STOP.contains(name.toUpperCase(Locale.ROOT))) {
            w.alias().put(name.toLowerCase(Locale.ROOT), e);
        }
    }
}
