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
import kr.ejg.toolbox.core.check.SpringMappings;

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
            Set<String> here = new HashSet<>(); // 같은 클래스의 오버로드는 다 낸다 — 가까운 쪽이 덮은 조상 메서드만 뺀다(V-67a)
            for (MethodDeclaration m : cur.decl.getMethods()) {
                String k = m.getNameAsString() + "/" + m.getParameters().size();
                here.add(k);
                if (!keys.contains(k)) {
                    out.add(Map.entry(cur, m));
                }
            }
            keys.addAll(here);
            cur = parent(cur, lookup);
        }
        return out;
    }

    /**
     * 6-88(C2-05) — 처리기 m 의 매핑. m 에 매핑 주석이 없으면 같은 이름·인자 수로 ① 조상 사다리(MAX_UP) ② c 와 조상이 구현한 인터페이스(ifaces —
     * 색인에 있는 FQCN)의 메서드 가운데 매핑 주석이 있는 첫 것을 읽는다. 클래스 머리는 c, 줄은 m(몸·이름도 m)
     */
    static List<SpringMappings.Mapping> mappings(JavaGraph.Cls c, MethodDeclaration m, Set<String> ifaces,
            java.util.function.Function<String, List<JavaGraph.Cls>> byName, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        List<SpringMappings.Mapping> own = SpringMappings.of(c.decl, m);
        if (!own.isEmpty()) {
            return own;
        }
        int at = m.getBegin().map(p -> p.line).orElse(1);
        return elsewhere(c, m, ifaces, byName, lookup).map(x -> SpringMappings.of(c.decl, x).stream()
                .map(mp -> new SpringMappings.Mapping(mp.verb(), mp.url(), mp.params(), at)).toList()).orElse(own);
    }

    private static Optional<MethodDeclaration> elsewhere(JavaGraph.Cls c, MethodDeclaration m, Set<String> ifaces,
            java.util.function.Function<String, List<JavaGraph.Cls>> byName, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        String name = m.getNameAsString();
        int argc = m.getParameters().size();
        Set<String> seen = new HashSet<>();
        JavaGraph.Cls cur = parent(c, lookup);
        for (int up = 0; up <= MAX_UP && cur != null && seen.add(cur.fqcn); up++) {
            Optional<MethodDeclaration> hit = annotated(cur, name, argc);
            if (hit.isPresent()) {
                return hit;
            }
            cur = parent(cur, lookup);
        }
        for (String f : ifaces) {
            for (JavaGraph.Cls ic : byName.apply(f.substring(f.lastIndexOf('.') + 1))) {
                Optional<MethodDeclaration> hit = ic.fqcn.equals(f) ? annotated(ic, name, argc) : Optional.empty();
                if (hit.isPresent()) {
                    return hit;
                }
            }
        }
        return Optional.empty();
    }

    /** cls 에 선언된 같은 이름·인자 수 메서드 가운데 매핑 주석이 있는 첫 것 */
    private static Optional<MethodDeclaration> annotated(JavaGraph.Cls cls, String name, int argc) {
        return cls.methods.getOrDefault(name, List.of()).stream().filter(x -> x.getParameters().size() == argc
                && x.getAnnotations().stream().anyMatch(a -> SpringMappings.ANNOTATIONS.contains(a.getName().getIdentifier()))).findFirst();
    }

    /** 6-90(C2-09) — c 에서 조상으로 올라 그 필드를 선언한 클래스, 없으면 c(필드 타입은 선언한 쪽 패키지·import 로 푼다) */
    static JavaGraph.Cls declarer(JavaGraph.Cls c, String field, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        Set<String> seen = new HashSet<>();
        JavaGraph.Cls cur = c;
        for (int up = 0; up <= MAX_UP && cur != null && seen.add(cur.fqcn); up++) {
            if (cur.fields.containsKey(field)) {
                return cur;
            }
            cur = parent(cur, lookup);
        }
        return c;
    }

    /**
     * 6-90(C2-10) — from 의 명시 import 가 그 단순 이름을 다른 FQCN 으로 가리키나(프로젝트 밖 클래스). 중첩 클래스 import({@code a.Outer.Inner})는
     * 색인 FQCN({@code a.Inner} — 패키지 + 단순 이름)과 패키지 머리·이름 꼬리로 맞춘다. import 가 없으면(같은 패키지·별 import) 거짓
     */
    static boolean importsOther(JavaGraph.Cls from, String simple, JavaGraph.Cls cand) {
        String imp = from.imports.get(simple);
        return imp != null && !imp.equals(cand.fqcn) && !imp.equals(cand.fqn); // 6-93a — 어림(패키지 머리 + 이름 꼬리)을 버린다
    }

    /** 6-93a — 완전 이름으로 적은 필드 타입(scope.type) — 후보 가운데 완전 이름이 그것이거나 그것으로 끝나는(Outer.Inner) 것만. import 는 안 본다 */
    static List<JavaGraph.Cls> scoped(List<JavaGraph.Cls> cands, String scope, String type) {
        String full = scope + "." + type;
        return cands.stream().filter(x -> full.equals(x.fqn) || full.equals(x.fqcn) || x.fqn != null && x.fqn.endsWith("." + full)).toList();
    }

    /** 첫 조상 — 없으면 null(사다리 끝) */
    static JavaGraph.Cls parent(JavaGraph.Cls c, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
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
