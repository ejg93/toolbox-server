package kr.ejg.toolbox.core.analyze;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** R-C-I2 — JPQL 의 연관 경로 조인({@code JOIN [FETCH] 별칭.속성 [[AS] 별칭]})을 엔티티 필드 형식으로 푼다. JpaIndex 가 메서드 상한이라 따로 둔다 */
final class JpqlPaths {

    private JpqlPaths() {
    }

    /** 한 번 훑기의 상태 — 별칭 → 엔티티 */
    private record Walk(List<String> t, Function<String, List<JpaIndex.Entity>> byName, Function<JpaIndex.Entity, ClassOrInterfaceDeclaration> decl,
            Map<String, EnumSet<SqlTables.Crud>> refs, Consumer<String> miss, Map<String, JpaIndex.Entity> alias) {
    }

    /**
     * @param t      JPQL 낱말(JpaIndex.JPQL_TOKEN 이 자른 것 — 식별자·점 이은 이름·괄호·쉼표)
     * @param byName 엔티티 이름 → 엔티티(참조하는 쪽으로 가른 것). 하나일 때만 쓴다
     * @param decl   엔티티 → 클래스 선언(없으면 null — 필드 없음으로 본다)
     * @param refs   표 → 글자. 푼 경로의 대상 표에 R 을 더한다
     * @param miss   별칭은 아는데 속성·대상 엔티티를 못 찾은 경로(「별칭.속성」 글 그대로)
     */
    static void resolve(List<String> t, Function<String, List<JpaIndex.Entity>> byName, Function<JpaIndex.Entity, ClassOrInterfaceDeclaration> decl,
            Map<String, EnumSet<SqlTables.Crud>> refs, Consumer<String> miss) {
        Walk w = new Walk(t, byName, decl, refs, miss, new HashMap<>());
        boolean inFrom = false;
        for (int i = 0; i < t.size(); i++) {
            String u = t.get(i).toUpperCase(Locale.ROOT);
            if ("FROM".equals(u)) {
                inFrom = true;
                at(w, i + 1);
            } else if ("JOIN".equals(u)) {
                inFrom = false;
                at(w, i + 1 < t.size() && "FETCH".equalsIgnoreCase(t.get(i + 1)) ? i + 2 : i + 1);
            } else if (",".equals(u)) {
                if (inFrom) {
                    at(w, i + 1);
                }
            } else if ("(".equals(u) || ")".equals(u) || JpaIndex.JPQL_STOP.contains(u) && byName.apply(t.get(i)).isEmpty()) {
                inFrom = inFrom && "AS".equals(u);
            }
        }
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
            if (hit.size() == 1) {
                alias(w, j + 1, hit.get(0));
            }
        } else if (seg.length == 2 && w.alias().containsKey(seg[0].toLowerCase(Locale.ROOT))) {
            pathAt(w, j, seg[1], w.alias().get(seg[0].toLowerCase(Locale.ROOT)));
        }
    }

    /** 경로 {@code 별칭.속성} — 속성 형식의 대상 엔티티가 하나면 R, 아니면 miss */
    private static void pathAt(Walk w, int j, String field, JpaIndex.Entity owner) {
        ClassOrInterfaceDeclaration d = w.decl().apply(owner);
        Optional<JpaIndex.Entity> target = Optional.ofNullable(d).flatMap(c -> c.getFieldByName(field)).map(FieldDeclaration::getCommonType)
                .map(JpqlPaths::targetName).map(w.byName()).filter(l -> l.size() == 1).map(l -> l.get(0));
        if (target.isEmpty()) {
            w.miss().accept(w.t().get(j));
            return;
        }
        w.refs().computeIfAbsent(target.get().table(), k -> EnumSet.noneOf(SqlTables.Crud.class)).add(SqlTables.Crud.R);
        alias(w, j + 1, target.get());
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
