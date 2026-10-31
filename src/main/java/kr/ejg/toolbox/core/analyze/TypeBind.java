package kr.ejg.toolbox.core.analyze;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 제네릭 조상의 타입 변수 묶음(6-37a, 설계 46 §3-13·21) — {@code ArticleService extends CrudService<ArticleDao, Article>} 이면 조상
 * {@code CrudService<D extends CrudDao<T>, T>} 의 D 는 ArticleDao. 이름으로 푼다(심볼 해석기 없음, 6-3). {@link JavaGraph} 가 길이 상한에 닿아 따로 둔다
 */
final class TypeBind {

    /** 조상 사다리 상한 — JavaGraph.MAX_DEPTH 와 같다 */
    private static final int MAX_UP = 12;

    private TypeBind() {
    }

    /** start(그 자리의 묶음 startBind)에서 owner 까지 올라가 owner 의 타입 변수 묶음. 못 닿으면 빈 맵 */
    static Map<String, JavaGraph.Cls> bindFor(JavaGraph.Cls start, Map<String, JavaGraph.Cls> startBind, JavaGraph.Cls owner,
            BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        return until(start, startBind, owner::equals, lookup);
    }

    /** 필드를 선언한 조상의 묶음 — 하위 클래스 자기 메서드가 조상 필드 {@code D dao} 를 부를 때 */
    static Map<String, JavaGraph.Cls> forField(JavaGraph.Cls start, Map<String, JavaGraph.Cls> startBind, String field,
            BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        return until(start, startBind, c -> c.fields.containsKey(field), lookup);
    }

    /** visited 꼬리 — 묶음이 다르면 같은 조상 메서드도 다시 걷는다(한 프로그램의 articleService.get·officeService.get) */
    static String key(Map<String, JavaGraph.Cls> bind) {
        return bind.isEmpty() ? "" : bind.values().stream().map(c -> c.fqcn).sorted().collect(Collectors.joining(",", "|", ""));
    }

    private static Map<String, JavaGraph.Cls> until(JavaGraph.Cls start, Map<String, JavaGraph.Cls> startBind, Predicate<JavaGraph.Cls> stop,
            BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        Map<String, JavaGraph.Cls> bind = startBind;
        JavaGraph.Cls cur = start;
        Set<String> seen = new HashSet<>();
        for (int up = 0; up <= MAX_UP && cur != null && seen.add(cur.fqcn); up++) {
            if (stop.test(cur)) {
                return bind;
            }
            List<JavaGraph.Cls> sup = cur.superName == null ? List.of() : lookup.apply(cur, cur.superName);
            if (sup.isEmpty()) {
                return Map.of();
            }
            bind = next(cur, bind, sup.get(0), lookup);
            cur = sup.get(0);
        }
        return Map.of();
    }

    /** cur 의 extends 타입 인자를 up 의 타입 매개변수에 자리로 짝짓는다 — 타입 변수면 지금 묶음에서, 아니면 이름이 하나로 풀릴 때만 */
    private static Map<String, JavaGraph.Cls> next(JavaGraph.Cls cur, Map<String, JavaGraph.Cls> bind, JavaGraph.Cls up,
            BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        List<String> args = superArgs(cur.decl);
        List<String> params = up.decl.getTypeParameters().stream().map(TypeParameter::getNameAsString).toList();
        Map<String, JavaGraph.Cls> out = new HashMap<>();
        for (int i = 0; i < Math.min(args.size(), params.size()); i++) {
            String arg = args.get(i);
            String param = params.get(i);
            one(bind.containsKey(arg) ? List.of(bind.get(arg)) : lookup.apply(cur, arg)).ifPresent(c -> out.put(param, c));
        }
        return out;
    }

    private static List<String> superArgs(ClassOrInterfaceDeclaration d) {
        if (d.getExtendedTypes().isEmpty()) {
            return List.of();
        }
        return d.getExtendedTypes(0).getTypeArguments().map(a -> a.stream().map(TypeBind::simple).toList()).orElse(List.of());
    }

    private static String simple(Type t) {
        return t.isClassOrInterfaceType() ? t.asClassOrInterfaceType().getNameAsString() : t.asString();
    }

    private static Optional<JavaGraph.Cls> one(List<JavaGraph.Cls> l) {
        return l.size() == 1 ? Optional.of(l.get(0)) : Optional.empty();
    }
}
