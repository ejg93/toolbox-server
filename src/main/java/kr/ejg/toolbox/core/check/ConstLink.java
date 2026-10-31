package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 메서드·파일 사이 상수(설계 45 §3-3~5) — 비밀번호·키 싱크가 메서드 안에서 못 푼 인자(매개변수·호출 결과·다른 클래스 필드)를
 * 검사 끝에 호출자 인자 상수 · 피호출 반환 상수 · 클래스 필드 상수로 푼다. 값은 판정에만 쓰고 어디에도 안 적는다(규칙 3).
 * {@link TaintLink} 가 하나 가진다
 */
final class ConstLink {

    /** 지역 → 매개변수 사슬을 몇 번까지 따라가나 */
    static final int HOPS = 3;

    enum Kind { PARAM, CALL, FIELD }

    /** 호출 인자가 상수 */
    private record Call(String from, TaintLink.Ref ref, int arg, Set<String> values) {
    }

    /** 호출 인자가 자기 매개변수 — 사슬(a → b → c) */
    private record Pass(String from, String method, TaintLink.Ref ref, int arg, int param) {
    }

    /** 못 푼 싱크 인자 — 줄·발췌는 요약 때 뜬다(AST 를 안 들고 있게). type·name 은 FIELD, ref 는 CALL, index 는 PARAM */
    private record NeedAt(String rule, String file, int line, String excerpt, String from, String method, Kind kind,
            TaintLink.Ref ref, String type, String name, int index) {
    }

    private final Map<String, Rule.Def> on;
    private final boolean active;
    private final List<Call> calls = new ArrayList<>();
    private final List<Pass> passes = new ArrayList<>();
    /** 메서드 id → 반환 상수 */
    private final Map<String, Set<String>> rets = new HashMap<>();
    /** 클래스 id → 그 파일의 필드 상수 */
    private final Map<String, Map<String, Set<String>>> classFields = new HashMap<>();
    private final List<NeedAt> needs = new ArrayList<>();

    ConstLink(Map<String, Rule.Def> on) {
        this.on = on;
        this.active = on.containsKey(TaintConst.PW_RULE) || on.containsKey(TaintConst.KEY);
    }

    boolean enabled() {
        return active;
    }

    void fields(String classId, Map<String, Set<String>> f) {
        if (active && !f.isEmpty()) {
            classFields.put(classId, f);
        }
    }

    static String id(TaintLink.ClassInfo c, CallableDeclaration<?> m) {
        return c.id() + "#" + m.getNameAsString() + "/" + m.getParameters().size();
    }

    /** 메서드 하나 — 상수 인자·매개변수를 넘기는 호출과 상수 반환 */
    void see(TaintLink.ClassInfo c, Taint.Body b, Map<String, Set<String>> cuFields) {
        if (!active || c == null) {
            return;
        }
        String id = id(c, b.m());
        Map<String, Set<String>> l = TaintConst.locals(b.m(), cuFields);
        for (MethodCallExpr m : b.calls()) {
            record(c, id, TaintLink.ref(m, b.types(), c), m.getArguments(), l, b);
        }
        for (ObjectCreationExpr o : b.news()) {
            String ty = o.getType().getNameAsString();
            record(c, id, new TaintLink.Ref(ty, ty, o.getArguments().size()), o.getArguments(), l, b);
        }
        for (ReturnStmt r : b.returns()) {
            r.getExpression().flatMap(e -> TaintConst.resolve(e, l)).map(TaintConst::plain).filter(v -> !v.isEmpty())
                    .ifPresent(v -> merge(rets, id, v));
        }
    }

    private void record(TaintLink.ClassInfo c, String id, TaintLink.Ref ref, List<Expression> args, Map<String, Set<String>> l,
            Taint.Body b) {
        if (ref == null) {
            return;
        }
        for (int j = 0; j < args.size(); j++) {
            Set<String> v = TaintConst.resolve(args.get(j), l).map(TaintConst::plain).orElse(Set.of());
            if (!v.isEmpty()) {
                calls.add(new Call(c.id(), ref, j, v));
                continue;
            }
            int p = param(args.get(j), b, HOPS);
            if (p >= 0) {
                passes.add(new Pass(c.id(), id, ref, j, p));
            }
        }
    }

    /** 못 푼 싱크 인자 하나 — 매개변수 · 호출 결과 · 필드 중 어느 꼴인지 가른다. 셋 다 아니면 버린다 */
    void need(TaintConst.Need n, TaintLink.ClassInfo c, String file, String... raw) {
        CallableDeclaration<?> m = n.at().findAncestor(CallableDeclaration.class).orElse(null);
        if (!active || c == null || m == null) {
            return;
        }
        Taint.Body b = Taint.body(m);
        int line = n.at().getBegin().map(x -> x.line).orElse(1);
        String ex = Finding.excerpt(line - 1 < raw.length ? raw[line - 1] : "");
        String id = id(c, m);
        int p = param(n.arg(), b, HOPS);
        if (p >= 0) {
            needs.add(new NeedAt(n.rule(), file, line, ex, c.id(), id, Kind.PARAM, null, null, null, p));
            return;
        }
        Expression src = origin(n.arg(), b, HOPS);
        if (src instanceof MethodCallExpr call) {
            TaintLink.Ref r = TaintLink.ref(call, b.types(), c);
            if (r != null) {
                needs.add(new NeedAt(n.rule(), file, line, ex, c.id(), id, Kind.CALL, r, null, null, -1));
            }
        } else if (src instanceof FieldAccessExpr f) {
            String type = f.getScope().isThisExpr() ? null : owner(f.getScope(), b);
            if (type != null || f.getScope().isThisExpr()) {
                needs.add(new NeedAt(n.rule(), file, line, ex, c.id(), id, Kind.FIELD, null, type, f.getNameAsString(), -1));
            }
        } else if (src != null && src.isNameExpr()) {
            needs.add(new NeedAt(n.rule(), file, line, ex, c.id(), id, Kind.FIELD, null, null, src.asNameExpr().getNameAsString(), -1));
        }
    }

    /** 식이 이 메서드의 몇 번 매개변수인가 — 지역은 선언·대입 값을 hops 번까지 따라간다. 아니면 -1 */
    private static int param(Expression e, Taint.Body b, int hops) {
        Expression x = strip(e);
        if (!x.isNameExpr()) {
            return -1;
        }
        String n = x.asNameExpr().getNameAsString();
        List<Parameter> ps = b.m().getParameters();
        for (int i = 0; i < ps.size(); i++) {
            if (ps.get(i).getNameAsString().equals(n)) {
                return i;
            }
        }
        if (hops > 0) {
            for (Expression v : values(n, b)) {
                int p = param(v, b, hops - 1);
                if (p >= 0) {
                    return p;
                }
            }
        }
        return -1;
    }

    /** 값이 온 곳 — 호출 · 필드 접근 · 지역이 아닌 이름(필드). 지역은 선언·대입 값을 따라간다. 글자 상수·그 밖은 null */
    private static Expression origin(Expression e, Taint.Body b, int hops) {
        Expression x = strip(e);
        if (x.isMethodCallExpr() || x.isFieldAccessExpr()) {
            return x;
        }
        if (!x.isNameExpr()) {
            return null;
        }
        String n = x.asNameExpr().getNameAsString();
        if (!b.locals().contains(n)) {
            return x;
        }
        for (Expression v : hops > 0 ? values(n, b) : List.<Expression>of()) {
            Expression o = origin(v, b, hops - 1);
            if (o != null) {
                return o;
            }
        }
        return null;
    }

    /** 필드 접근 스코프의 타입 — 지역·매개변수면 선언 타입, 아니면 타입 이름(Config.PW · 패키지.Config.PW) */
    private static String owner(Expression scope, Taint.Body b) {
        if (scope.isNameExpr()) {
            String n = scope.asNameExpr().getNameAsString();
            return b.locals().contains(n) ? b.types().get(n) : n;
        }
        if (scope.isFieldAccessExpr()) {
            String n = scope.asFieldAccessExpr().getNameAsString();
            return Character.isUpperCase(n.charAt(0)) ? n : null;
        }
        return null;
    }

    private static List<Expression> values(String n, Taint.Body b) {
        List<Expression> out = new ArrayList<>();
        b.decls().stream().filter(d -> d.getNameAsString().equals(n)).forEach(d -> d.getInitializer().ifPresent(out::add));
        b.assigns().stream().filter(a -> a.getTarget().isNameExpr() && a.getTarget().asNameExpr().getNameAsString().equals(n))
                .forEach(a -> out.add(a.getValue()));
        return out;
    }

    /** 괄호·형변환 · 상수 꼴 호출(getBytes·toCharArray 등)을 벗긴다 */
    private static Expression strip(Expression e) {
        Expression x = Taint.unwrap(e);
        while (x.isMethodCallExpr() && TaintConst.CONST_SCOPE.contains(x.asMethodCallExpr().getNameAsString())
                && x.asMethodCallExpr().getScope().isPresent()) {
            x = Taint.unwrap(x.asMethodCallExpr().getScope().get());
        }
        return x;
    }

    // ---------------------------------------------------------------- 풀기(§3-5)

    /**
     * 검사 끝 — 매개변수 상수(호출자 인자 + 사슬 고정점) · 반환 상수 · 필드 상수로 못 푼 인자를 푼다.
     * 찾아가는 호출은 못 푼 인자에 닿는 이름만 — 모호 수(TaintLink 의 AMBIGUOUS)를 쓸데없이 늘리지 않게
     */
    List<Finding> finish(BiFunction<TaintLink.Ref, TaintLink.ClassInfo, List<String>> targets,
            BiFunction<String, TaintLink.ClassInfo, List<TaintLink.ClassInfo>> pick, Function<String, TaintLink.ClassInfo> classes,
            Set<String> seen) {
        List<Finding> out = new ArrayList<>();
        Map<String, Set<String>> args = params(targets, classes);
        for (NeedAt n : needs) {
            TaintLink.ClassInfo from = classes.apply(n.from());
            Set<String> v = from == null ? Set.of() : valuesOf(n, from, args, targets, pick);
            Rule.Def d = on.get(n.rule());
            if (d != null && !TaintConst.plain(v).isEmpty() && seen.add(n.file() + "|" + n.rule() + "|" + n.line())) {
                out.add(new Finding(n.file(), n.line(), d.group(), n.rule(), d.severity(), n.excerpt()));
            }
        }
        calls.clear();
        passes.clear();
        rets.clear();
        classFields.clear();
        needs.clear();
        return out;
    }

    /** 매개변수 「메서드 id#자리」 → 상수 — 호출자 인자에서, 사슬은 고정점으로 */
    private Map<String, Set<String>> params(BiFunction<TaintLink.Ref, TaintLink.ClassInfo, List<String>> targets,
            Function<String, TaintLink.ClassInfo> classes) {
        Set<String> wanted = wanted();
        Map<String, Set<String>> args = new HashMap<>();
        for (Call x : calls) {
            TaintLink.ClassInfo from = wanted.contains(sig(x.ref())) ? classes.apply(x.from()) : null;
            if (from != null) {
                targets.apply(x.ref(), from).forEach(t -> merge(args, t + "#" + x.arg(), x.values()));
            }
        }
        boolean more = true;
        for (int r = 0; more && r < TaintLink.ROUNDS; r++) {
            more = false;
            for (Pass x : passes) {
                Set<String> v = args.get(x.method() + "#" + x.param());
                TaintLink.ClassInfo from = v == null ? null : classes.apply(x.from());
                if (from != null) {
                    for (String t : targets.apply(x.ref(), from)) {
                        more |= merge(args, t + "#" + x.arg(), v);
                    }
                }
            }
        }
        return args;
    }

    /** 매개변수 상수가 필요한 이름/인자 수 — PARAM 인 못 푼 인자의 메서드, 그리로 매개변수를 넘기는 메서드(추이) */
    private Set<String> wanted() {
        Set<String> w = new HashSet<>();
        needs.stream().filter(n -> n.kind() == Kind.PARAM).forEach(n -> w.add(tail(n.method())));
        boolean more = true;
        for (int r = 0; more && r < TaintLink.ROUNDS; r++) {
            more = false;
            for (Pass x : passes) {
                if (w.contains(sig(x.ref()))) {
                    more |= w.add(tail(x.method()));
                }
            }
        }
        return w;
    }

    private Set<String> valuesOf(NeedAt n, TaintLink.ClassInfo from, Map<String, Set<String>> args,
            BiFunction<TaintLink.Ref, TaintLink.ClassInfo, List<String>> targets,
            BiFunction<String, TaintLink.ClassInfo, List<TaintLink.ClassInfo>> pick) {
        if (n.kind() == Kind.PARAM) {
            return args.getOrDefault(n.method() + "#" + n.index(), Set.of());
        }
        Set<String> out = new LinkedHashSet<>();
        if (n.kind() == Kind.CALL) {
            targets.apply(n.ref(), from).forEach(t -> out.addAll(rets.getOrDefault(t, Set.of())));
            return out;
        }
        if (n.type() != null) {
            pick.apply(n.type(), from).forEach(c -> out.addAll(fieldsOf(c).getOrDefault(n.name(), Set.of())));
            return out;
        }
        List<TaintLink.ClassInfo> x = List.of(from);
        for (int i = 0; x.size() == 1 && i < TaintLink.SUPER_MAX; i++) { // 자기 클래스 → 상위(하나로 갈릴 때만)
            TaintLink.ClassInfo c = x.get(0);
            Set<String> v = fieldsOf(c).get(n.name());
            if (v != null) {
                return v;
            }
            x = c.superName() == null ? List.of() : pick.apply(c.superName(), c);
        }
        return out;
    }

    private Map<String, Set<String>> fieldsOf(TaintLink.ClassInfo c) {
        return classFields.getOrDefault(c.id(), Map.of());
    }

    private static String sig(TaintLink.Ref r) {
        return r.name() + "/" + r.argc();
    }

    private static String tail(String methodId) {
        return methodId.substring(methodId.lastIndexOf('#') + 1);
    }

    private static boolean merge(Map<String, Set<String>> m, String k, Set<String> v) {
        Set<String> s = m.computeIfAbsent(k, x -> new LinkedHashSet<>());
        boolean changed = false;
        for (String x : v) {
            if (s.size() < TaintConst.VALUES_MAX) {
                changed |= s.add(x);
            }
        }
        return changed;
    }
}
