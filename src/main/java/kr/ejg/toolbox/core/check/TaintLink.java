package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 메서드 사이·파일 사이 오염(설계 43 §3-2~8) — 파일마다 메서드 요약(매개변수·호출 결과·필드 씨앗이 닿는 싱크·호출 인자·필드 쓰기·반환)을
 * 모으고, 검사가 끝날 때 호출을 이름으로 이어 고정점으로 퍼뜨린다. 요약은 오염을 더하기만 한다(씻지 않는다). 모호한 이름은 건너뛴다.
 * 지적은 싱크 줄(피호출 파일). {@link JavaRules} 가 검사 한 번마다 하나 가진다
 */
final class TaintLink {

    static final int SEEDS_MAX = 32;
    static final int ROUNDS = 50;
    static final int SUPER_MAX = 12;
    private static final Set<String> PRIMITIVE = Set.of("int", "long", "short", "byte", "boolean", "double", "float", "char");
    /** 상수 필드(대문자 이름)는 씨앗으로 안 친다 — 오염 값이 들 일이 없고 씨앗만 먹는다 */
    private static final Pattern CONSTANT = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    /** 이름이 둘 이상인데 못 가른 해석 · 씨앗 상한으로 버린 씨앗 — corpus 시험이 이력에 적는 수(로그 아님) */
    private static final AtomicInteger AMBIGUOUS = new AtomicInteger();
    private static final AtomicInteger DROPPED = new AtomicInteger();

    /** 피호출 — type 은 단순 이름, null 이면 자기 클래스(스코프 없음·this). 생성자는 name = 타입 이름 */
    record Ref(String type, String name, int argc) {
    }

    /** 씨앗이 이 호출의 arg 번 인자에 닿는다 */
    record Site(Ref ref, int arg) {
    }

    /** 씨앗이 닿는 싱크 — 발췌는 싱크 줄 원문(규칙 3, 160자) */
    record SinkAt(String rule, int line, String excerpt) {
    }

    record Facts(List<SinkAt> sinks, List<Site> calls, Set<String> fieldWrites, boolean returns) {
        boolean empty() {
            return sinks.isEmpty() && calls.isEmpty() && fieldWrites.isEmpty() && !returns;
        }
    }

    /** 메서드 하나 — src 는 출처 켠 실행(싱크는 직접 지적이라 뺀다), 나머지는 씨앗별. 필드 키는 「클래스.필드」 */
    record Summary(String file, String cls, String id, Facts src, Map<Integer, Facts> params, Map<Ref, Facts> callVars,
            Map<String, Facts> fields) {
    }

    /** bodies = 몸 있는 메서드·생성자의 「이름/인자 수」 */
    record ClassInfo(String id, String name, String pkg, String file, String superName, List<String> interfaces,
            boolean abstractOrInterface, Map<String, String> fieldTypes, Set<String> bodies, Map<String, String> imports) {
    }

    /** 보류한 설정 값 알고리즘 판정 — 줄·발췌는 요약 때 뜬다(AST 를 안 들고 있게) */
    private record Later(String file, int line, String excerpt, TaintConst.Pending p) {
    }

    private record State(Set<String> params, Set<String> fields, Set<String> returns, Set<Facts> done, List<Finding> out) {
    }

    private final Taint taint;
    private final Map<String, Rule.Def> on;
    private final PropertiesIndex props;
    private final List<Later> later = new ArrayList<>();
    private final Map<String, ClassInfo> classes = new HashMap<>();
    private final Map<String, List<ClassInfo>> byName = new HashMap<>();
    /** 메서드 id(「클래스 id#이름/인자 수」) → 요약들 */
    private final Map<String, List<Summary>> summaries = new LinkedHashMap<>();
    /** 이미 낸 지적 「파일|규칙|줄」 — 직접 지적도 여기 */
    private final Set<String> seen = new HashSet<>();
    private final Map<String, List<String>> targetCache = new HashMap<>();
    private final Map<String, List<ClassInfo>> subtypes = new HashMap<>();

    TaintLink(Taint taint, Map<String, Rule.Def> on, PropertiesIndex props) {
        this.taint = taint;
        this.on = on;
        this.props = props;
    }

    /** [모호, 버린 씨앗] — reset 이면 0 으로 되돌린다(시험용) */
    static int[] counts(boolean reset) {
        int[] c = {AMBIGUOUS.get(), DROPPED.get()};
        if (reset) {
            AMBIGUOUS.set(0);
            DROPPED.set(0);
        }
        return c;
    }

    /** 파일 하나 — 클래스·요약을 모으고 직접 지적(출처 켠 실행 + 상수 판정)을 돌려준다. emit 이면 직접 지적을 낸 것으로 친다 */
    List<Taint.Hit> summarize(CompilationUnit cu, Source s, boolean emit) {
        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        Map<String, String> imports = new HashMap<>();
        for (ImportDeclaration i : cu.getImports()) {
            if (!i.isAsterisk() && !i.isStatic()) {
                String n = i.getNameAsString();
                imports.put(n.substring(n.lastIndexOf('.') + 1), n);
            }
        }
        for (TypeDeclaration<?> t : cu.findAll(TypeDeclaration.class)) {
            ClassInfo c = info(t, s.rel(), pkg, imports);
            classes.put(c.id(), c);
            byName.computeIfAbsent(c.name(), k -> new ArrayList<>()).add(c);
        }
        String[] raw = s.text().split("\n", -1);
        List<TaintConst.Pending> ps = new ArrayList<>();
        List<Taint.Hit> hits = taint.scan(cu, (b, f) -> method(s.rel(), raw, b, f), ps);
        for (TaintConst.Pending p : ps) {
            int line = p.at().getBegin().map(x -> x.line).orElse(1);
            later.add(new Later(s.rel(), line, Finding.excerpt(line - 1 < raw.length ? raw[line - 1] : ""), p.detached()));
        }
        if (emit) {
            hits.forEach(h -> seen.add(s.rel() + "|" + h.rule() + "|" + h.node().getBegin().map(p -> p.line).orElse(1)));
        }
        return hits;
    }

    private static ClassInfo info(TypeDeclaration<?> t, String file, String pkg, Map<String, String> imports) {
        String sup = null;
        List<String> ifs = new ArrayList<>();
        boolean abs = false;
        if (t instanceof ClassOrInterfaceDeclaration c) {
            abs = c.isInterface() || c.isAbstract();
            List<String> ext = c.getExtendedTypes().stream().map(x -> x.getNameAsString()).toList();
            if (c.isInterface()) {
                ifs.addAll(ext);
            } else if (!ext.isEmpty()) {
                sup = ext.get(0);
            }
            c.getImplementedTypes().forEach(x -> ifs.add(x.getNameAsString()));
        }
        Map<String, String> types = new HashMap<>();
        for (FieldDeclaration f : t.getFields()) {
            f.getVariables().forEach(v -> types.put(v.getNameAsString(), Taint.type(v.getTypeAsString())));
        }
        Set<String> bodies = new HashSet<>();
        for (MethodDeclaration m : t.getMethods()) {
            if (m.getBody().isPresent()) {
                bodies.add(m.getNameAsString() + "/" + m.getParameters().size());
            }
        }
        for (ConstructorDeclaration k : t.getConstructors()) {
            bodies.add(t.getNameAsString() + "/" + k.getParameters().size());
        }
        return new ClassInfo(file + "!" + typeId(t), t.getNameAsString(), pkg, file, sup, ifs, abs, types, bodies, imports);
    }

    /** 클래스 id 의 이름 몫 — 바깥 클래스 사슬까지(한 파일에 같은 단순 이름 중첩 클래스 둘이 안 겹치게, 마무리 리뷰) */
    private static String typeId(TypeDeclaration<?> t) {
        return t.getFullyQualifiedName().orElse(t.getNameAsString());
    }

    // ---------------------------------------------------------------- 요약(§3-4)

    private void method(String file, String[] raw, Taint.Body b, Taint.Facts src) {
        ClassInfo c = b.m().findAncestor(TypeDeclaration.class).map(t -> classes.get(file + "!" + typeId((TypeDeclaration<?>) t)))
                .orElse(null);
        if (c == null) {
            return;
        }
        Map<String, String> types = b.types();
        Facts s0 = facts(b, src, raw, types, c);
        int[] budget = {SEEDS_MAX};
        List<Parameter> ps = b.m().getParameters();
        Map<Integer, Facts> params = new HashMap<>();
        for (int i = 0; i < ps.size(); i++) {
            String ty = Taint.type(ps.get(i).getTypeAsString());
            Facts f = PRIMITIVE.contains(ty) || Taint.NOT_INPUT.contains(ty) ? null
                    : seed(budget, b, Set.of(ps.get(i).getNameAsString()), raw, types, c);
            if (f != null) {
                params.put(i, f);
            }
        }
        Map<Ref, Facts> callVars = new HashMap<>();
        callInits(b, types, c).forEach((ref, vars) -> {
            Facts f = seed(budget, b, vars, raw, types, c);
            if (f != null) {
                callVars.put(ref, f);
            }
        });
        Map<String, Facts> fields = new HashMap<>();
        for (String k : fieldReads(b)) {
            Facts f = seed(budget, b, aliases(b, k), raw, types, c);
            if (f != null) {
                fields.put(k, f);
            }
        }
        if (s0.empty() && params.isEmpty() && callVars.isEmpty() && fields.isEmpty()) {
            return;
        }
        String id = c.id() + "#" + b.m().getNameAsString() + "/" + ps.size();
        summaries.computeIfAbsent(id, k -> new ArrayList<>()).add(new Summary(file, c.id(), id,
                new Facts(List.of(), s0.calls(), s0.fieldWrites(), s0.returns()), params, callVars, fields));
    }

    private Facts seed(int[] budget, Taint.Body b, Set<String> seed, String[] raw, Map<String, String> types, ClassInfo c) {
        if (budget[0] <= 0) {
            DROPPED.incrementAndGet();
            return null;
        }
        budget[0]--;
        Facts f = facts(b, taint.run(b, seed, false), raw, types, c);
        return f.empty() ? null : f;
    }

    /** 한 번 돈 결과 → 싱크(줄·발췌)·오염 인자가 든 호출·오염 값을 쓴 필드 키·오염 반환 */
    private static Facts facts(Taint.Body b, Taint.Facts r, String[] raw, Map<String, String> types, ClassInfo c) {
        Set<String> t = r.tainted();
        List<SinkAt> sinks = new ArrayList<>();
        for (Taint.Hit h : r.sinks()) {
            int line = h.node().getBegin().map(p -> p.line).orElse(1);
            sinks.add(new SinkAt(h.rule(), line, Finding.excerpt(line - 1 < raw.length ? raw[line - 1] : "")));
        }
        List<Site> calls = new ArrayList<>();
        Set<String> writes = new HashSet<>();
        for (MethodCallExpr m : b.calls()) {
            sites(ref(m, types, c), m.getArguments(), t, calls);
            if (Taint.MUTATORS.contains(m.getNameAsString()) && m.getScope().isPresent()
                    && m.getArguments().stream().anyMatch(a -> Sanitizers.reaches(a, t))) {
                writes.add(fieldKey(m.getScope().get(), b));
            }
        }
        for (ObjectCreationExpr o : b.news()) {
            String ty = o.getType().getNameAsString();
            sites(new Ref(ty, ty, o.getArguments().size()), o.getArguments(), t, calls);
        }
        for (AssignExpr a : b.assigns()) {
            if (Sanitizers.reaches(a.getValue(), t)) {
                writes.add(fieldKey(a.getTarget(), b));
            }
        }
        writes.remove(null);
        boolean ret = b.returns().stream().anyMatch(x -> x.getExpression().map(e -> Sanitizers.reaches(e, t)).orElse(false));
        return new Facts(sinks, calls, writes, ret);
    }

    private static void sites(Ref ref, List<Expression> args, Set<String> t, List<Site> out) {
        if (ref == null) {
            return;
        }
        for (int j = 0; j < args.size(); j++) {
            if (Sanitizers.reaches(args.get(j), t)) { // 정화 값은 넘기지 않는다 — 범주는 메서드 경계를 못 넘는다(설계 44 §3-2)
                out.add(new Site(ref, j));
            }
        }
    }

    /** 필드 키 「클래스.필드」 — bare 필드(지역에 가리지 않은) · this.f · X.f(대문자 스코프) · 그 배열 요소. 아니면 null */
    private static String fieldKey(Expression e, Taint.Body b) {
        if (e.isArrayAccessExpr()) {
            return fieldKey(e.asArrayAccessExpr().getName(), b);
        }
        if (e.isNameExpr()) {
            String n = e.asNameExpr().getNameAsString();
            return b.fields().contains(n) && !b.locals().contains(n) ? b.cls() + "." + n : null;
        }
        if (!e.isFieldAccessExpr()) {
            return null;
        }
        Expression scope = e.asFieldAccessExpr().getScope();
        String f = e.asFieldAccessExpr().getNameAsString();
        if (scope.isThisExpr()) {
            return b.cls() + "." + f;
        }
        boolean type = scope.isNameExpr() && Character.isUpperCase(scope.asNameExpr().getNameAsString().charAt(0))
                && !b.locals().contains(scope.asNameExpr().getNameAsString());
        return type ? scope.asNameExpr().getNameAsString() + "." + f : null;
    }

    /** 메서드가 읽는 필드 키 — 상수 이름은 뺀다 */
    private static Set<String> fieldReads(Taint.Body b) {
        Set<String> out = new LinkedHashSet<>();
        for (FieldAccessExpr f : Taint.outsideLambda(b.m(), FieldAccessExpr.class)) {
            String k = CONSTANT.matcher(f.getNameAsString()).matches() || assigned(f) ? null : fieldKey(f, b);
            if (k != null) {
                out.add(k);
            }
        }
        for (NameExpr n : Taint.outsideLambda(b.m(), NameExpr.class)) {
            String k = CONSTANT.matcher(n.getNameAsString()).matches() || assigned(n) ? null : fieldKey(n, b);
            if (k != null) {
                out.add(k);
            }
        }
        return out;
    }

    /** 대입의 대상(쓰기만) — 읽는 필드 씨앗에서 뺀다(마무리 리뷰: 씨앗 상한을 먹는다) */
    private static boolean assigned(Expression e) {
        return e.getParentNode().filter(p -> p instanceof AssignExpr a && a.getTarget().getRange().equals(e.getRange())).isPresent();
    }

    /** 필드 키 씨앗 — 자기 클래스 필드면 this.f·bare f 로도 읽힌다 */
    private static Set<String> aliases(Taint.Body b, String key) {
        Set<String> a = new HashSet<>();
        a.add(key);
        String own = b.cls() + ".";
        if (key.startsWith(own)) {
            String f = key.substring(own.length());
            a.add("this." + f);
            if (!b.locals().contains(f)) {
                a.add(f);
            }
        }
        return a;
    }

    /** x = scope.m(args) — 피호출 → 받는 지역 변수들. 요청 출처 호출은 이미 오염이라 뺀다 */
    private static Map<Ref, Set<String>> callInits(Taint.Body b, Map<String, String> types, ClassInfo c) {
        Map<Ref, Set<String>> out = new LinkedHashMap<>();
        List<Map.Entry<String, Expression>> inits = new ArrayList<>();
        b.decls().forEach(d -> d.getInitializer().ifPresent(e -> inits.add(Map.entry(d.getNameAsString(), e))));
        b.assigns().stream().filter(a -> a.getTarget().isNameExpr())
                .forEach(a -> inits.add(Map.entry(a.getTarget().asNameExpr().getNameAsString(), a.getValue())));
        for (Map.Entry<String, Expression> e : inits) {
            Expression v = Taint.unwrap(e.getValue());
            Ref r = v.isMethodCallExpr() && !Taint.SOURCES.contains(v.asMethodCallExpr().getNameAsString())
                    ? ref(v.asMethodCallExpr(), types, c) : null;
            if (r != null) {
                out.computeIfAbsent(r, k -> new LinkedHashSet<>()).add(e.getKey());
            }
        }
        return out;
    }

    /** 호출 → 피호출(§3-5). 스코프 타입을 모르면 null — 해석 안 함 */
    private static Ref ref(MethodCallExpr m, Map<String, String> types, ClassInfo c) {
        int n = m.getArguments().size();
        if (m.getScope().isEmpty()) {
            return new Ref(null, m.getNameAsString(), n);
        }
        Expression s = Taint.unwrap(m.getScope().get());
        if (s.isThisExpr()) {
            return new Ref(null, m.getNameAsString(), n);
        }
        String ty = scopeType(s, types, c);
        return ty == null ? null : new Ref(ty, m.getNameAsString(), n);
    }

    private static String scopeType(Expression s, Map<String, String> types, ClassInfo c) {
        if (s.isSuperExpr()) {
            return c.superName();
        }
        if (s.isObjectCreationExpr()) {
            return s.asObjectCreationExpr().getType().getNameAsString();
        }
        if (s.isNameExpr()) {
            String v = s.asNameExpr().getNameAsString();
            String ty = types.getOrDefault(v, c.fieldTypes().get(v));
            return ty != null ? ty : Character.isUpperCase(v.charAt(0)) ? v : null;
        }
        if (s.isFieldAccessExpr()) {
            FieldAccessExpr f = s.asFieldAccessExpr();
            if (f.getScope().isThisExpr()) {
                return c.fieldTypes().get(f.getNameAsString());
            }
            return Character.isUpperCase(f.getNameAsString().charAt(0)) ? f.getNameAsString() : null; // 패키지로 쓴 정적 호출
        }
        return null;
    }

    // ---------------------------------------------------------------- 고정점(§3-7)

    /** 호출을 이어 퍼뜨린 지적 — 파일·줄·규칙. 뒤에 상태를 비운다 */
    List<Finding> finish() {
        for (ClassInfo c : classes.values()) {
            if (c.superName() != null) {
                subtypes.computeIfAbsent(c.superName(), k -> new ArrayList<>()).add(c);
            }
            c.interfaces().forEach(i -> subtypes.computeIfAbsent(i, k -> new ArrayList<>()).add(c));
        }
        State st = new State(new HashSet<>(), new HashSet<>(), new HashSet<>(), Collections.newSetFromMap(new IdentityHashMap<>()),
                new ArrayList<>());
        boolean more = true;
        for (int round = 0; more && round < ROUNDS; round++) {
            more = false;
            for (List<Summary> l : summaries.values()) {
                for (Summary s : l) {
                    more |= step(s, st);
                }
            }
        }
        TaintConst k = new TaintConst(on);
        for (Later x : later) {
            Rule.Def d = on.get(x.p().rule());
            if (d != null && k.weak(x.p(), props) && seen.add(x.file() + "|" + x.p().rule() + "|" + x.line())) {
                st.out().add(new Finding(x.file(), x.line(), d.group(), x.p().rule(), d.severity(), x.excerpt()));
            }
        }
        later.clear();
        classes.clear();
        byName.clear();
        summaries.clear();
        seen.clear();
        targetCache.clear();
        subtypes.clear();
        return st.out();
    }

    private boolean step(Summary s, State st) {
        ClassInfo c = classes.get(s.cls());
        boolean any = fire(s, c, s.src(), true, st);
        for (Map.Entry<Integer, Facts> e : s.params().entrySet()) {
            if (st.params().contains(s.id() + "#" + e.getKey())) {
                any |= fire(s, c, e.getValue(), false, st); // 매개변수에 기댄 반환은 호출자가 이미 오염으로 본다(§3-2)
            }
        }
        for (Map.Entry<String, Facts> e : s.fields().entrySet()) {
            if (st.fields().contains(e.getKey())) {
                any |= fire(s, c, e.getValue(), true, st);
            }
        }
        for (Map.Entry<Ref, Facts> e : s.callVars().entrySet()) {
            if (targets(e.getKey(), c).stream().anyMatch(st.returns()::contains)) {
                any |= fire(s, c, e.getValue(), true, st);
            }
        }
        return any;
    }

    /** 처음 켜진 씨앗 — 호출 인자·필드 쓰기·반환을 퍼뜨리고 싱크를 낸다 */
    private boolean fire(Summary s, ClassInfo c, Facts f, boolean ret, State st) {
        if (!st.done().add(f)) {
            return false;
        }
        for (Site x : f.calls()) {
            targets(x.ref(), c).forEach(m -> st.params().add(m + "#" + x.arg()));
        }
        st.fields().addAll(f.fieldWrites());
        if (ret && f.returns()) {
            st.returns().add(s.id());
        }
        for (SinkAt k : f.sinks()) {
            Rule.Def d = on.get(k.rule());
            if (d != null && seen.add(s.file() + "|" + k.rule() + "|" + k.line())) {
                st.out().add(new Finding(s.file(), k.line(), d.group(), k.rule(), d.severity(), k.excerpt()));
            }
        }
        return true;
    }

    /** 피호출 메서드 id 들 — 몸 있는 것만, 인터페이스·추상은 하위 타입, 없으면 상위로 */
    private List<String> targets(Ref r, ClassInfo from) {
        return targetCache.computeIfAbsent(from.id() + "|" + r, k -> {
            String sig = r.name() + "/" + r.argc();
            Set<String> out = new LinkedHashSet<>();
            for (ClassInfo c : r.type() == null ? List.of(from) : pick(r.type(), from)) {
                boolean has = c.bodies().contains(sig);
                if (has) {
                    out.add(c.id() + "#" + sig);
                }
                if (!has || c.abstractOrInterface()) {
                    subs(c.name()).stream().filter(x -> x.bodies().contains(sig)).forEach(x -> out.add(x.id() + "#" + sig));
                }
                if (out.isEmpty()) {
                    up(c, sig, out);
                }
            }
            return new ArrayList<>(out);
        });
    }

    private void up(ClassInfo c, String sig, Set<String> out) {
        Optional<ClassInfo> x = Optional.of(c);
        for (int i = 0; x.isPresent() && x.get().superName() != null && i < SUPER_MAX; i++) {
            x = only(pick(x.get().superName(), x.get()));
            if (x.isPresent() && x.get().bodies().contains(sig)) {
                out.add(x.get().id() + "#" + sig);
                return;
            }
        }
    }

    /** 단순 이름 → 클래스 — 하나뿐 · 같은 파일 · import · 같은 패키지 하나. 못 가르면 건너뛴다(§3-5) */
    private List<ClassInfo> pick(String name, ClassInfo from) {
        List<ClassInfo> all = byName.getOrDefault(name, List.of());
        if (all.isEmpty() || only(all).isPresent()) {
            return all;
        }
        List<ClassInfo> f = all.stream().filter(c -> c.file().equals(from.file())).toList();
        String fq = from.imports().get(name);
        if (f.isEmpty()) {
            f = all.stream().filter(c -> fq != null && fq.equals(c.pkg() + "." + c.name())).toList();
        }
        if (f.isEmpty()) {
            f = all.stream().filter(c -> c.pkg().equals(from.pkg())).toList();
        }
        Optional<ClassInfo> one = only(f); // 같은 파일·같은 FQCN(모듈 복제)도 둘 이상이면 건너뛴다(마무리 리뷰)
        if (one.isPresent()) {
            return List.of(one.get());
        }
        AMBIGUOUS.incrementAndGet();
        return List.of();
    }

    private static Optional<ClassInfo> only(List<ClassInfo> l) {
        return l.size() == 1 ? Optional.of(l.get(0)) : Optional.empty();
    }

    /** 이 이름을 extends·implements 하는 클래스(추이) — 단순 이름으로 */
    private List<ClassInfo> subs(String name) {
        List<ClassInfo> out = new ArrayList<>();
        Set<String> names = new HashSet<>(List.of(name));
        Deque<String> q = new ArrayDeque<>(names);
        while (!q.isEmpty()) {
            for (ClassInfo c : subtypes.getOrDefault(q.poll(), List.of())) {
                out.add(c);
                if (names.add(c.name())) {
                    q.add(c.name());
                }
            }
        }
        return out;
    }
}
