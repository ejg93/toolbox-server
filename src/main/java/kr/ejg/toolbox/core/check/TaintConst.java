package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 상수 전파(설계 42 §3-6) — 하드코딩 비밀번호·키, 약한 알고리즘 이름. 필드 초기값 + 메서드 안 대입의 고정점으로 식의 글자 값을 풀고,
 * 그 값으로 그 규칙의 정규식이 보던 꼴의 글을 합성해 묻는다(정규식 판정이 둘로 갈리지 않게). {@link Taint} 가 부른다
 */
final class TaintConst {

    static final String PW_RULE = "security.hardcodedPassword";
    static final String KEY = "security.hardcodedKey";
    static final String HASH = "security.weakHash";
    static final String CIPHER = "security.weakCipher";
    static final Set<String> CONST_RULES = Set.of(PW_RULE, KEY, HASH, CIPHER);
    /** getInstance 의 스코프(단순 이름) → 규칙 */
    static final Map<String, String> ALGORITHMS = Map.of("MessageDigest", HASH, "Cipher", CIPHER, "KeyGenerator", CIPHER,
            "SecretKeyFactory", CIPHER);
    /** 모드가 없는 getInstance 스코프 */
    static final Set<String> KEY_MAKERS = Set.of("KeyGenerator", "SecretKeyFactory");
    /** 자격 증명 싱크 → 비밀번호 인자 자리 */
    static final Map<String, Integer> PASSWORD_CALLS = Map.of("getConnection", 2, "setPassword", 0, "PasswordAuthentication", 1,
            "login", 1, "setCredentials", 1, "UsernamePasswordCredentials", 1, "connect", 2);
    /** 키 싱크 → 키·IV 인자 자리 */
    static final Map<String, Integer> KEY_CALLS = Map.of("SecretKeySpec", 0, "IvParameterSpec", 0, "PBEKeySpec", 0,
            "GCMParameterSpec", 1);
    /** 상수 스코프면 값이 상수로 남는 호출 */
    static final Set<String> CONST_SCOPE = Set.of("getBytes", "toCharArray", "trim", "toUpperCase", "toLowerCase", "intern",
            "strip");
    /** (키, 기본값) — 둘째 인자가 그 값 */
    static final Set<String> DEFAULTED = Set.of("getProperty", "getOrDefault");
    /** 바이트·문자 배열 리터럴의 자리 값 */
    static final String BYTES = "<bytes>";
    static final int VALUES_MAX = 8;
    static final String UPPER = "toUpperCase";
    static final String LOWER = "toLowerCase";

    private final Map<String, Rule.Def> on;

    TaintConst(Map<String, Rule.Def> on) {
        this.on = on;
    }

    /** 클래스 필드의 상수 초기값 — 이름 → 값들 */
    static Map<String, Set<String>> fieldConstants(CompilationUnit cu) {
        Map<String, Set<String>> l = new HashMap<>();
        for (FieldDeclaration f : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator v : f.getVariables()) {
                v.getInitializer().flatMap(e -> resolve(e, l)).ifPresent(s -> merge(l, v.getNameAsString(), s));
            }
        }
        return l;
    }

    /** 필드 선언의 비밀번호 이름 = 상수 */
    List<Taint.Hit> fieldSecrets(CompilationUnit cu, Map<String, Set<String>> fields) {
        List<Taint.Hit> out = new ArrayList<>();
        for (FieldDeclaration f : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator v : f.getVariables()) {
                v.getInitializer().ifPresent(e -> namedSecret(v.getNameAsString(), e, fields, v, out));
            }
        }
        return out;
    }

    List<Taint.Hit> constants(CallableDeclaration<?> m, Map<String, Set<String>> fields) {
        if (on.keySet().stream().noneMatch(CONST_RULES::contains)) {
            return List.of();
        }
        Map<String, Set<String>> l = new HashMap<>(fields);
        List<VariableDeclarator> decls = Taint.outsideLambda(m, VariableDeclarator.class);
        List<AssignExpr> assigns = Taint.outsideLambda(m, AssignExpr.class);
        for (int i = 0; i < Taint.ROUNDS; i++) {
            int before = size(l);
            decls.forEach(d -> d.getInitializer().flatMap(e -> resolve(e, l)).ifPresent(s -> merge(l, d.getNameAsString(), s)));
            assigns.stream().filter(a -> a.getTarget().isNameExpr())
                    .forEach(a -> resolve(a.getValue(), l).ifPresent(s -> merge(l, a.getTarget().asNameExpr().getNameAsString(), s)));
            if (size(l) == before) {
                break;
            }
        }
        List<Taint.Hit> out = new ArrayList<>();
        decls.forEach(d -> d.getInitializer().ifPresent(e -> namedSecret(d.getNameAsString(), e, l, d, out)));
        for (AssignExpr a : assigns) {
            target(a.getTarget()).ifPresent(n -> namedSecret(n, a.getValue(), l, a, out));
        }
        for (MethodCallExpr c : Taint.outsideLambda(m, MethodCallExpr.class)) {
            algorithm(c, l, out);
            secretArg(c.getNameAsString(), c.getArguments(), l, c, PW_RULE, PASSWORD_CALLS, out);
        }
        for (ObjectCreationExpr o : Taint.outsideLambda(m, ObjectCreationExpr.class)) {
            String n = o.getType().getNameAsString();
            secretArg(n, o.getArguments(), l, o, PW_RULE, PASSWORD_CALLS, out);
            secretArg(n, o.getArguments(), l, o, KEY, KEY_CALLS, out);
        }
        return out;
    }

    private static Optional<String> target(Expression e) {
        if (e.isNameExpr()) {
            return Optional.of(e.asNameExpr().getNameAsString());
        }
        return e.isFieldAccessExpr() ? Optional.of(e.asFieldAccessExpr().getNameAsString()) : Optional.empty();
    }

    /** 이름 = 상수 — 비밀번호 규칙의 정규식에 「이름 = "값"」 을 묻는다(이름 판정은 정규식이 한다) */
    private void namedSecret(String name, Expression value, Map<String, Set<String>> l, Node at, List<Taint.Hit> out) {
        Rule.Def d = on.get(PW_RULE);
        if (d == null) {
            return;
        }
        for (String v : resolve(value, l).orElse(Set.of())) {
            if (d.pattern().matcher(name + " = \"" + quoteless(v) + "\"").find()) {
                out.add(new Taint.Hit(PW_RULE, at));
                return;
            }
        }
    }

    /** 자격 증명·키 싱크의 인자 자리가 상수 */
    private void secretArg(String name, List<Expression> args, Map<String, Set<String>> l, Node at, String rule,
            Map<String, Integer> calls, List<Taint.Hit> out) {
        Integer i = calls.get(name);
        Rule.Def d = on.get(rule);
        if (i == null || d == null || i >= args.size()) {
            return;
        }
        boolean constant = resolve(args.get(i), l).map(s -> s.stream().anyMatch(v -> !v.isEmpty())).orElse(false);
        String probe = KEY.equals(rule) ? "SecretKeySpec(\"" : "password = \"x\"";
        if (constant && d.pattern().matcher(probe).find()) {
            out.add(new Taint.Hit(rule, at));
        }
    }

    /** getInstance(상수) — 해석한 이름으로 「Cipher.getInstance("값")」 을 그 규칙의 정규식에 묻는다(5-16 ECB 판정 유지) */
    private void algorithm(MethodCallExpr c, Map<String, Set<String>> l, List<Taint.Hit> out) {
        if (!"getInstance".equals(c.getNameAsString()) || c.getArguments().isEmpty() || c.getScope().isEmpty()) {
            return;
        }
        String scope = Taint.simple(c.getScope().get().toString());
        String rule = ALGORITHMS.get(scope);
        Rule.Def d = rule == null ? null : on.get(rule);
        if (d == null) {
            return;
        }
        String cls = HASH.equals(rule) ? "MessageDigest" : "Cipher";
        // 키 생성기·키 팩토리는 모드가 없다 — 알고리즘 이름만 본다(모드 없는 AES = ECB 판정은 Cipher 에만, 5-53 Benchmark crypto FP)
        String mode = KEY_MAKERS.contains(scope) ? "/CBC/PKCS5Padding" : "";
        for (String v : resolve(c.getArgument(0), l).orElse(Set.of())) {
            String name = v.contains("/") ? v : v + mode;
            if (d.pattern().matcher(cls + ".getInstance(\"" + quoteless(name) + "\")").find()) {
                out.add(new Taint.Hit(rule, c));
                return;
            }
        }
    }

    /** 식의 상수 값들 — 상수가 아니면 비어 있음. 값은 VALUES_MAX 개까지 */
    static Optional<Set<String>> resolve(Expression e, Map<String, Set<String>> l) {
        if (e.isStringLiteralExpr()) {
            return Optional.of(Set.of(e.asStringLiteralExpr().getValue()));
        }
        if (e.isCharLiteralExpr() || e.isIntegerLiteralExpr()) {
            return Optional.of(Set.of(e.toString()));
        }
        if (e.isNameExpr()) {
            return Optional.ofNullable(l.get(e.asNameExpr().getNameAsString()));
        }
        if (e.isBinaryExpr()) {
            return concat(e.asBinaryExpr(), l);
        }
        if (e.isMethodCallExpr()) {
            return resolveCall(e.asMethodCallExpr(), l);
        }
        return resolveWrapped(e, l);
    }

    private static Optional<Set<String>> resolveWrapped(Expression e, Map<String, Set<String>> l) {
        if (e instanceof EnclosedExpr x) {
            return resolve(x.getInner(), l);
        }
        if (e instanceof CastExpr x) {
            return resolve(x.getExpression(), l);
        }
        if (e instanceof UnaryExpr x) {
            return resolve(x.getExpression(), l);
        }
        if (e instanceof ArrayCreationExpr a) {
            return a.getInitializer().flatMap(i -> resolve(i, l));
        }
        if (e instanceof ArrayInitializerExpr a) {
            boolean all = !a.getValues().isEmpty() && a.getValues().stream().allMatch(v -> resolve(v, l).isPresent());
            return all ? Optional.of(Set.of(BYTES)) : Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<Set<String>> concat(BinaryExpr b, Map<String, Set<String>> l) {
        if (b.getOperator() != BinaryExpr.Operator.PLUS) {
            return Optional.empty();
        }
        Optional<Set<String>> x = resolve(b.getLeft(), l);
        Optional<Set<String>> y = resolve(b.getRight(), l);
        if (x.isEmpty() || y.isEmpty()) {
            return Optional.empty();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String p : x.get()) {
            for (String q : y.get()) {
                if (out.size() < VALUES_MAX) {
                    out.add(p + q);
                }
            }
        }
        return Optional.of(out);
    }

    private static Optional<Set<String>> resolveCall(MethodCallExpr c, Map<String, Set<String>> l) {
        String n = c.getNameAsString();
        if (DEFAULTED.contains(n) && c.getArguments().size() == 2) {
            return resolve(c.getArgument(1), l); // 「기본값이 약하다」 — 설정이 없을 때 쓰는 값
        }
        if (!CONST_SCOPE.contains(n) || c.getScope().isEmpty()) {
            return Optional.empty();
        }
        Optional<Set<String>> s = resolve(c.getScope().get(), l);
        if (UPPER.equals(n)) {
            return s.map(v -> map(v, x -> x.toUpperCase(Locale.ROOT)));
        }
        return LOWER.equals(n) ? s.map(v -> map(v, x -> x.toLowerCase(Locale.ROOT))) : s;
    }

    private static Set<String> map(Set<String> s, java.util.function.UnaryOperator<String> f) {
        Set<String> out = new LinkedHashSet<>();
        s.forEach(x -> out.add(f.apply(x)));
        return out;
    }

    private static void merge(Map<String, Set<String>> l, String name, Set<String> values) {
        Set<String> s = l.computeIfAbsent(name, k -> new LinkedHashSet<>());
        for (String v : values) {
            if (s.size() < VALUES_MAX) {
                s.add(v);
            }
        }
    }

    private static int size(Map<String, Set<String>> l) {
        return l.values().stream().mapToInt(Set::size).sum();
    }

    private static String quoteless(String v) {
        return v.replace("\"", "");
    }
}
