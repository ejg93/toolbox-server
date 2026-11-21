package kr.ejg.toolbox.core.analyze;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import java.util.ArrayList;
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

    /** 호출 자리의 묶음 — 자기 호출(맨·this·super)이면 c 의 묶음에서, 다른 객체면 그 객체에서 빈 묶음으로. JavaGraph.walk 의 가지를 여기로 옮겼다 */
    static Map<String, JavaGraph.Cls> bindFor(boolean self, JavaGraph.Cls c, Map<String, JavaGraph.Cls> bind, JavaGraph.Cls t, JavaGraph.Cls owner,
            JavaGraph.Field via, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        return self ? bindFor(c, bind, owner, lookup) : bindFor(t, fieldBind(c, bind, t, via, lookup), owner, lookup);
    }

    /** 필드 선언의 타입 인자를 그 형식의 타입 매개변수에 자리로 — via 가 없거나 인자가 없으면 빈 묶음. 인자가 타입 변수면 지금 묶음에서, 아니면 이름이 하나로 풀릴 때만 */
    private static Map<String, JavaGraph.Cls> fieldBind(JavaGraph.Cls c, Map<String, JavaGraph.Cls> bind, JavaGraph.Cls t, JavaGraph.Field via,
            BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        if (via == null || via.args().isEmpty()) {
            return Map.of();
        }
        List<String> params = t.decl.getTypeParameters().stream().map(TypeParameter::getNameAsString).toList();
        Map<String, JavaGraph.Cls> out = new HashMap<>();
        for (int i = 0; i < Math.min(via.args().size(), params.size()); i++) {
            String arg = via.args().get(i);
            String param = params.get(i);
            one(bind.containsKey(arg) ? List.of(bind.get(arg)) : lookup.apply(c, arg)).ifPresent(x -> out.put(param, x));
        }
        return out;
    }

    /** 타입 인자의 단순 이름 — 없으면 빈 목록 */
    static List<String> args(ClassOrInterfaceType ct) {
        return ct.getTypeArguments().map(a -> a.stream().map(TypeBind::simple).toList()).orElse(List.of());
    }

    /** 필드를 선언한 조상의 묶음 — 하위 클래스 자기 메서드가 조상 필드 {@code D dao} 를 부를 때 */
    static Map<String, JavaGraph.Cls> forField(JavaGraph.Cls start, Map<String, JavaGraph.Cls> startBind, String field,
            BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        return until(start, startBind, c -> c.fields.containsKey(field), lookup);
    }

    /** R-C-I7 — c 의 메서드와 물려받은 조상 메서드(가까운 쪽이 같은 이름·인자 수로 덮은 것은 뺀다). 값 = 그 메서드를 선언한 클래스. 사다리 상한 MAX_UP */
    static List<Map.Entry<JavaGraph.Cls, MethodDeclaration>> handlers(JavaGraph.Cls c, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        List<Map.Entry<JavaGraph.Cls, MethodDeclaration>> out = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> seen = new HashSet<>();
        JavaGraph.Cls cur = c;
        for (int up = 0; up <= MAX_UP && cur != null && seen.add(cur.fqcn); up++) {
            for (MethodDeclaration m : cur.decl.getMethods()) {
                if (keys.add(m.getNameAsString() + "/" + m.getParameters().size())) {
                    out.add(Map.entry(cur, m));
                }
            }
            cur = parent(cur, lookup);
        }
        return out;
    }

    /** 첫 조상 — 없으면 null(사다리 끝) */
    private static JavaGraph.Cls parent(JavaGraph.Cls c, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        List<JavaGraph.Cls> sup = c.superName == null ? List.of() : lookup.apply(c, c.superName);
        return sup.isEmpty() ? null : sup.get(0);
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
