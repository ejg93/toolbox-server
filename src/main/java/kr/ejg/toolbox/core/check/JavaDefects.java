package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 설계 49 — 예외·동시성 결함 다섯. JavaParser AST 만 본다(타입 해석 없음 — 형식은 선언 글로).
 * <ul>
 *   <li>{@code java.finallyReturn} — finally 안 return·break·continue(finally 안 루프·switch 의 탈출, 람다·안쪽 클래스 몸은 아님)</li>
 *   <li>{@code java.finallyThrow} — finally 안 throw(finally 안 try 블록에 있고 그 try 에 catch 가 있으면 아님)</li>
 *   <li>{@code java.lostCause} — catch 몸이 매개변수를 한 번도 안 쓰고 {@code throw new X(…)}</li>
 *   <li>{@code java.threadLocalRemove} — ThreadLocal 필드에 scope 가 그 이름인 {@code set} 은 있고 {@code remove} 는 없다</li>
 *   <li>{@code java.staticDateFormat} — static SimpleDateFormat·DateFormat·DecimalFormat·MessageFormat·NumberFormat·ChoiceFormat 필드</li>
 *   <li>설계 58 — {@code java.catchThrowable}(다시 안 던지는 Throwable·Error catch) · {@code java.logAndThrow}(catch 의 로그 + throw) ·
 *   {@code java.mappingPrivate}(private·static 메서드의 매핑) · {@code java.floatEquals}(float·double 의 ==·!=)</li>
 * </ul>
 */
final class JavaDefects {

    static final String FINALLY_RETURN = "java.finallyReturn";
    static final String FINALLY_THROW = "java.finallyThrow";
    static final String LOST_CAUSE = "java.lostCause";
    static final String THREAD_LOCAL = "java.threadLocalRemove";
    static final String STATIC_DATE_FORMAT = "java.staticDateFormat";
    static final String CATCH_THROWABLE = "java.catchThrowable";
    static final String LOG_AND_THROW = "java.logAndThrow";
    static final String MAPPING_PRIVATE = "java.mappingPrivate";
    static final String FLOAT_EQUALS = "java.floatEquals";
    private static final Set<String> DATE_FORMATS = Set.of("SimpleDateFormat", "DateFormat", "DecimalFormat", "MessageFormat", "NumberFormat",
            "ChoiceFormat");
    private static final Set<String> BROAD_CATCH = Set.of("Throwable", "Error");
    /** JavaRules 와 같다. 가시성을 열면 DeclarationOrder(7-22 전례) */
    private static final Set<String> LOG_METHODS = Set.of("error", "warn", "info", "debug", "trace", "fatal");
    /** JavaRules 와 같다 */
    private static final Pattern LOGGER = Pattern.compile("(?i)^(log|logger|.*log|.*logger)$");
    private static final String PRINT_STACK = "printStackTrace";
    private static final String THREAD_LOCAL_TYPE = "ThreadLocal";
    private static final String SET = "set";
    private static final String REMOVE = "remove";

    private JavaDefects() {
    }

    /** 켜진 규칙(on)의 판정만 — 줄·규칙 순서, 같은 노드·같은 규칙은 한 건 */
    static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on) {
        List<Taint.Hit> out = new ArrayList<>();
        if (on.contains(FINALLY_RETURN) || on.contains(FINALLY_THROW)) {
            finallies(cu, on, out);
        }
        if (on.contains(LOST_CAUSE)) {
            lostCause(cu, out);
        }
        if (on.contains(THREAD_LOCAL)) {
            threadLocals(cu, out);
        }
        if (on.contains(STATIC_DATE_FORMAT)) {
            staticDateFormats(cu, out);
        }
        if (on.contains(CATCH_THROWABLE)) {
            catchThrowables(cu, out);
        }
        if (on.contains(LOG_AND_THROW)) {
            logAndThrows(cu, out);
        }
        if (on.contains(MAPPING_PRIVATE)) {
            mappingPrivates(cu, out);
        }
        if (on.contains(FLOAT_EQUALS)) {
            floatEquals(cu, out);
        }
        out.sort(Comparator.comparingInt((Taint.Hit h) -> line(h.node())).thenComparing(Taint.Hit::rule));
        return out;
    }

    private static void add(List<Taint.Hit> out, String rule, Node n) {
        if (out.stream().noneMatch(h -> h.rule().equals(rule) && same(h.node(), n))) {
            out.add(new Taint.Hit(rule, n));
        }
    }

    private static void finallies(CompilationUnit cu, Set<String> on, List<Taint.Hit> out) {
        for (TryStmt t : cu.findAll(TryStmt.class)) {
            if (t.getFinallyBlock().isEmpty()) {
                continue;
            }
            BlockStmt f = t.getFinallyBlock().get();
            if (on.contains(FINALLY_RETURN)) {
                finallyJumps(f, out);
            }
            if (on.contains(FINALLY_THROW)) {
                finallyThrows(f, out);
            }
        }
    }

    private static void finallyJumps(BlockStmt f, List<Taint.Hit> out) {
        for (Statement s : f.findAll(Statement.class)) {
            boolean jump = s instanceof ReturnStmt || s instanceof BreakStmt || s instanceof ContinueStmt;
            if (jump && !barrier(path(s, f)) && !loopBetween(s, path(s, f))) {
                add(out, FINALLY_RETURN, s);
            }
        }
    }

    private static void finallyThrows(BlockStmt f, List<Taint.Hit> out) {
        for (ThrowStmt s : f.findAll(ThrowStmt.class)) {
            List<Node> p = path(s, f);
            if (!barrier(p) && !caughtInside(s, p)) {
                add(out, FINALLY_THROW, s);
            }
        }
    }

    /** s 에서 위로 f 직전까지의 조상 — f 는 빼고, 가까운 것부터 */
    private static List<Node> path(Node s, Node f) {
        List<Node> out = new ArrayList<>();
        for (Node cur = s.getParentNode().orElse(null); cur != null && !same(cur, f); cur = cur.getParentNode().orElse(null)) {
            out.add(cur);
        }
        return out;
    }

    /** 람다·안쪽 클래스(익명·지역)의 몸 — 그 자리의 문맥 밖 */
    private static boolean barrier(List<Node> path) {
        return path.stream().anyMatch(n -> n instanceof LambdaExpr || n instanceof BodyDeclaration);
    }

    /** 5-61b — 안쪽 catch 몸도 문맥 밖(중첩 catch 의 throw 는 안쪽 catch 가 판정). finally 판정은 barrier 그대로 */
    private static boolean catchBarrier(List<Node> path) {
        return barrier(path) || path.stream().anyMatch(CatchClause.class::isInstance);
    }

    /** finally 안 루프(break 는 switch 도)의 탈출 — finally 를 벗어나지 않는다 */
    private static boolean loopBetween(Statement s, List<Node> path) {
        boolean brk = s instanceof BreakStmt;
        return path.stream().anyMatch(n -> n instanceof ForStmt || n instanceof ForEachStmt || n instanceof WhileStmt
                || n instanceof DoStmt || brk && (n instanceof SwitchStmt || n instanceof SwitchEntry));
    }

    /** finally 안 다른 try 의 try 블록에 있고 그 try 에 catch 가 있다 — 그 자리에서 잡힌 것으로 친다 */
    private static boolean caughtInside(ThrowStmt s, List<Node> path) {
        Node child = s;
        for (Node n : path) {
            if (n instanceof TryStmt t && !t.getCatchClauses().isEmpty() && same(t.getTryBlock(), child)) {
                return true;
            }
            child = n;
        }
        return false;
    }

    private static void lostCause(CompilationUnit cu, List<Taint.Hit> out) {
        for (CatchClause cc : cu.findAll(CatchClause.class)) {
            String v = cc.getParameter().getNameAsString();
            BlockStmt b = cc.getBody();
            if (b.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals(v))
                    || b.findAll(MethodReferenceExpr.class).stream().anyMatch(r -> r.getScope().toString().equals(v))) { // 5-61b — e::addSuppressed 의 scope 는 TypeExpr
                continue;
            }
            for (ThrowStmt s : b.findAll(ThrowStmt.class)) {
                if (s.getExpression() instanceof ObjectCreationExpr && !catchBarrier(path(s, b))) {
                    add(out, LOST_CAUSE, s);
                }
            }
        }
    }

    private static void threadLocals(CompilationUnit cu, List<Taint.Hit> out) {
        List<MethodCallExpr> calls = cu.findAll(MethodCallExpr.class);
        for (FieldDeclaration f : cu.findAll(FieldDeclaration.class)) {
            if (!simpleType(f.getElementType()).endsWith(THREAD_LOCAL_TYPE)) {
                continue;
            }
            for (VariableDeclarator v : f.getVariables()) {
                String n = v.getNameAsString();
                boolean passed = calls.stream().anyMatch(c -> c.getArguments().stream()
                        .anyMatch(a -> a instanceof NameExpr x && x.getNameAsString().equals(n))); // 5-61b — 넘겨 지우는 꼴(clear(TL))
                if (scopeCall(calls, n, SET) && !scopeCall(calls, n, REMOVE) && !passed) {
                    add(out, THREAD_LOCAL, v);
                }
            }
        }
    }

    /** scope 의 마지막 이름이 n 이고 메서드 이름이 name 인 호출이 있나(scope 없는 호출은 다른 객체라 안 센다) */
    private static boolean scopeCall(List<MethodCallExpr> calls, String n, String name) {
        return calls.stream().anyMatch(c -> c.getNameAsString().equals(name)
                && c.getScope().map(JavaDefects::lastName).filter(n::equals).isPresent());
    }

    private static void staticDateFormats(CompilationUnit cu, List<Taint.Hit> out) {
        for (FieldDeclaration f : cu.findAll(FieldDeclaration.class)) {
            if (f.isStatic() && DATE_FORMATS.contains(simpleType(f.getElementType()))) {
                f.getVariables().forEach(v -> add(out, STATIC_DATE_FORMAT, v));
            }
        }
    }

    /** 5-61b — catch 매개변수 형식(union 포함)이 Throwable·Error 이고 몸에서 그 이름을 다시 던지지 않는다 */
    private static void catchThrowables(CompilationUnit cu, List<Taint.Hit> out) {
        for (CatchClause cc : cu.findAll(CatchClause.class)) {
            Parameter p = cc.getParameter();
            List<Type> types = p.getType() instanceof UnionType u ? new ArrayList<>(u.getElements()) : List.of(p.getType());
            boolean broad = types.stream().map(JavaDefects::simpleType).anyMatch(BROAD_CATCH::contains);
            if (broad && cc.getBody().findAll(ThrowStmt.class).stream().noneMatch(s -> rethrows(s, p.getNameAsString(), cc.getBody()))) {
                add(out, CATCH_THROWABLE, p);
            }
        }
    }

    private static boolean rethrows(ThrowStmt s, String name, BlockStmt body) {
        return !catchBarrier(path(s, body)) && s.getExpression() instanceof NameExpr n && n.getNameAsString().equals(name);
    }

    /** 5-61b — catch 몸(람다·안쪽 클래스·안쪽 catch 밖)에 로거 호출 또는 e.printStackTrace() 와 throw 가 같이 있다 — 첫 throw 자리 */
    private static void logAndThrows(CompilationUnit cu, List<Taint.Hit> out) {
        for (CatchClause cc : cu.findAll(CatchClause.class)) {
            BlockStmt b = cc.getBody();
            String param = cc.getParameter().getNameAsString();
            boolean logged = b.findAll(MethodCallExpr.class).stream().anyMatch(m -> !catchBarrier(path(m, b)) && logs(m, param));
            b.findAll(ThrowStmt.class).stream().filter(s -> !catchBarrier(path(s, b))).findFirst()
                    .filter(s -> logged).ifPresent(s -> add(out, LOG_AND_THROW, s));
        }
    }

    private static boolean logs(MethodCallExpr m, String param) {
        String name = m.getNameAsString();
        String scope = m.getScope().map(JavaDefects::lastName).orElse("");
        boolean logsParam = m.getArguments().stream().anyMatch(a -> a.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals(param)));
        return LOG_METHODS.contains(name) && LOGGER.matcher(scope).matches() && logsParam || PRINT_STACK.equals(name) && scope.equals(param);
    }

    /** 5-61b — private·static 메서드의 매핑 애너테이션(SpringMappings.ANNOTATIONS) */
    private static void mappingPrivates(CompilationUnit cu, List<Taint.Hit> out) {
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            if (m.isPrivate() || m.isStatic()) {
                m.getAnnotations().stream().filter(a -> SpringMappings.ANNOTATIONS.contains(a.getName().getIdentifier())).findFirst()
                        .ifPresent(a -> add(out, MAPPING_PRIVATE, a));
            }
        }
    }

    /** 5-61b — ==·!= 의 한쪽이 double 리터럴·float/double(래퍼 포함) 선언 형식·그 형 변환. 같은 글끼리(x != x — NaN 검사)·null 은 뺀다 */
    private static void floatEquals(CompilationUnit cu, List<Taint.Hit> out) {
        for (BinaryExpr b : cu.findAll(BinaryExpr.class)) {
            boolean eq = b.getOperator() == BinaryExpr.Operator.EQUALS || b.getOperator() == BinaryExpr.Operator.NOT_EQUALS;
            if (eq && !b.getLeft().toString().equals(b.getRight().toString()) && !b.getLeft().isNullLiteralExpr() && !b.getRight().isNullLiteralExpr()
                    && (floating(b.getLeft()) || floating(b.getRight()))) {
                add(out, FLOAT_EQUALS, b);
            }
        }
    }

    private static boolean floating(Expression e) {
        Expression x = e instanceof EnclosedExpr en ? en.getInner() : e;
        return x instanceof DoubleLiteralExpr || x instanceof CastExpr c && ValueAst.FLOATING.contains(simpleType(c.getType()))
                || ValueAst.FLOATING.contains(ValueAst.declaredType(x));
    }

    private static boolean same(Node a, Node b) {
        return a.getRange().equals(b.getRange());
    }

    /** 선언 글에서 {@code <…>}·{@code []} 를 떼고 마지막 {@code .} 뒤 */
    private static String simpleType(Type t) {
        String s = t.asString();
        int lt = s.indexOf('<');
        if (lt >= 0) {
            s = s.substring(0, lt);
        }
        s = s.replace("[]", "").trim();
        return s.substring(s.lastIndexOf('.') + 1);
    }

    private static String lastName(Expression e) {
        if (e instanceof NameExpr n) {
            return n.getNameAsString();
        }
        if (e instanceof FieldAccessExpr f) {
            return f.getNameAsString();
        }
        return "";
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(1);
    }
}
