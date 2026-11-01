package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 설계 50 — 자원 해제 둘. JavaParser AST 만 본다(타입 해석 없음 — 형식은 선언 글의 단순 이름으로).
 * <ul>
 *   <li>{@code security.resourceLeak} — 자원(JDBC·스트림·POI·SqlSession) 지역 변수를 만들고 finally 안 close·closer 유틸·
 *   return·필드 대입·래퍼 넘김·컬렉션 넣기·람다 포획 어느 것도 없다(close 가 finally 밖에만 있으면 예외 때 샌다)</li>
 *   <li>{@code java.tryWithResources} — finally 에 close 문장만 있는 try. 판정이 곧 코드 정리 고침의 전제다
 *   ({@link #plan} — {@code ResourceFix} 가 같은 판정을 받는다)</li>
 * </ul>
 */
final class ResourceAst {

    static final String RESOURCE_LEAK = "security.resourceLeak";
    static final String TRY_WITH_RESOURCES = "java.tryWithResources";
    private static final Set<String> EXACT = Set.of("Connection", "Statement", "PreparedStatement", "CallableStatement",
            "ResultSet", "Scanner", "Formatter", "Socket", "ServerSocket", "ZipFile", "JarFile", "RandomAccessFile",
            "FileChannel", "SocketChannel", "ServerSocketChannel", "Workbook", "XSSFWorkbook", "HSSFWorkbook",
            "SXSSFWorkbook", "SqlSession");
    private static final List<String> SUFFIXES = List.of("InputStream", "OutputStream", "Reader", "Writer");
    /** 메모리(자원 아님) · close 가 없거나 AutoCloseable 이 아닌 이름 겹침(SAX·StAX) */
    private static final Set<String> MEMORY = Set.of("StringWriter", "StringReader", "CharArrayReader", "CharArrayWriter",
            "ByteArrayInputStream", "ByteArrayOutputStream", "XMLReader", "XMLStreamReader", "XMLStreamWriter",
            "XMLEventReader", "XMLEventWriter");
    private static final Set<String> JDBC = Set.of("Connection", "Statement", "PreparedStatement", "CallableStatement",
            "ResultSet");
    private static final Set<String> IO_EXACT = Set.of("ZipFile", "JarFile", "RandomAccessFile", "Socket", "ServerSocket");
    private static final List<String> KNOWN_PACKAGES = List.of("java.sql", "javax.sql", "java.io", "java.nio.channels",
            "java.util", "java.util.zip", "java.util.jar", "java.net", "org.apache.poi", "org.apache.ibatis");
    private static final Set<String> CLOSERS = Set.of("close", "closeQuietly", "closeSilently", "closeStream",
            "closeConnection", "closeStatement", "closeResultSet", "closeAll", "release", "dispose", "shutdown", "free");
    private static final Set<String> TRANSFER = Set.of("add", "put", "offer", "push", "set", "register");
    private static final List<String> TRANSFER_PREFIXES = List.of("add", "put", "set", "register");
    private static final Set<String> SERVLET_OWNERS = Set.of("request", "response", "req", "resp");
    private static final Set<String> SERVLET_GETTERS = Set.of("getOutputStream", "getWriter", "getInputStream", "getReader");
    private static final List<String> SERVLET_TYPES = List.of("ServletRequest", "ServletResponse");
    private static final String CLOSE = "close";
    private static final String CLOSE_QUIETLY = "closeQuietly";
    private static final String SQL_EXCEPTION = "SQLException";
    private static final String IO_EXCEPTION = "IOException";
    private static final Set<String> BROAD = Set.of("Exception", "Throwable");
    private static final String SYSTEM = "System";
    /** ThreadLocal·Map 의 get — 보관소에서 꺼낸 것은 소유가 아니다(R-15) */
    private static final String LOOKUP = "get";
    private static final String CHANNEL = "Channel";
    private static final String WORKBOOK = "Workbook";
    private static final String UNKNOWN = "";
    private static final int MAX_NAMES = 3;
    private static final int ONE = 1;

    /** try-with-resources 로 옮길 자원 하나 — 선언 문장 · 꼴 B 면 try 안 대입 문장 · 초기화식 */
    record Res(String name, Type type, ExpressionStmt decl, Optional<ExpressionStmt> assign, Expression init) {
    }

    /** 고칠 수 있는 try — 자원은 머리 순서(꼴 A 선언 순서 뒤 꼴 B 대입 순서) */
    record Twr(TryStmt tryStmt, List<Res> resources) {
    }

    private ResourceAst() {
    }

    /** 켜진 규칙(on)의 판정만 — 줄·규칙 순서, 같은 노드·같은 규칙은 한 건 */
    static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on) {
        List<Taint.Hit> out = new ArrayList<>();
        if (on.contains(RESOURCE_LEAK)) {
            for (VariableDeclarator v : cu.findAll(VariableDeclarator.class)) {
                candidate(v, cu).filter(body -> !released(v, body)).ifPresent(b -> add(out, RESOURCE_LEAK, v));
            }
        }
        if (on.contains(TRY_WITH_RESOURCES)) {
            for (TryStmt t : cu.findAll(TryStmt.class)) {
                plan(t, cu).ifPresent(p -> add(out, TRY_WITH_RESOURCES, t));
            }
        }
        out.sort(Comparator.comparingInt((Taint.Hit h) -> line(h.node())).thenComparing(Taint.Hit::rule));
        return out;
    }

    private static void add(List<Taint.Hit> out, String rule, Node n) {
        if (out.stream().noneMatch(h -> h.rule().equals(rule) && same(h.node(), n))) {
            out.add(new Taint.Hit(rule, n));
        }
    }

    // ---------------------------------------------------------------- 자원 형식·만든 자리

    /** 단순 형식 이름이 자원이다 — 메모리 제외, 정확 이름·접미 넷, 다른 패키지 단일 import 면 이름 충돌 */
    private static boolean isResource(String simple, CompilationUnit cu) {
        if (simple.isEmpty() || MEMORY.contains(simple)) {
            return false;
        }
        boolean named = EXACT.contains(simple) || SUFFIXES.stream().anyMatch(simple::endsWith);
        return named && !foreign(simple, cu);
    }

    private static boolean foreign(String simple, CompilationUnit cu) {
        for (ImportDeclaration i : cu.getImports()) {
            if (!i.isAsterisk() && !i.isStatic() && simple.equals(i.getName().getIdentifier())) {
                String pkg = i.getName().getQualifier().map(Name::asString).orElse(UNKNOWN);
                return KNOWN_PACKAGES.stream().noneMatch(pkg::startsWith);
            }
        }
        return false;
    }

    /** 선언 형식 — var 는 new 의 형식, 배열은 빈 글 */
    private static String typeOf(VariableDeclarator v) {
        Type t = v.getType();
        if (t.isVarType()) {
            return v.getInitializer().map(ResourceAst::strip).filter(Expression::isObjectCreationExpr)
                    .map(e -> simpleType(e.asObjectCreationExpr().getType())).orElse(UNKNOWN);
        }
        return t.isArrayType() || foreignQualifier(t.asString()) ? UNKNOWN : simpleType(t);
    }

    /** {@code Profile.Connection} 처럼 알려진 패키지 밖 한정 이름 — 사용자 형식의 중첩 형식 */
    private static boolean foreignQualifier(String type) {
        int lt = type.indexOf('<');
        String base = lt < 0 ? type : type.substring(0, lt);
        int dot = base.lastIndexOf('.');
        return dot >= 0 && KNOWN_PACKAGES.stream().noneMatch(base.substring(0, dot)::startsWith);
    }

    /** 소유하는 만든 자리 — new(익명 몸 없음, 메모리 형식 아님, 첫 인자가 빌린 것 아님) 또는 호출(컨테이너 소유·보관소 get 아님) */
    private static boolean creation(Expression init, CompilationUnit cu) {
        Expression x = strip(init);
        if (x instanceof ObjectCreationExpr o) {
            return o.getAnonymousClassBody().isEmpty() && !MEMORY.contains(simpleType(o.getType()))
                    && (o.getArguments().isEmpty() || !borrowed(o.getArgument(0), o, cu));
        }
        return x instanceof MethodCallExpr m && !servletOwned(m) && !LOOKUP.equals(m.getNameAsString());
    }

    /**
     * 래퍼 첫 인자가 남의 것 — 메모리(new·이름)·System.in/out/err·컨테이너 스트림·보관소 get·지역 변수 아닌 자원 이름(매개변수·필드·this.x).
     * {@code new W(new V(a))} 는 안쪽 첫 인자로 내려간다(R-15)
     */
    private static boolean borrowed(Expression arg, Node at, CompilationUnit cu) {
        Expression a = strip(arg);
        if (a instanceof ObjectCreationExpr inner) {
            return MEMORY.contains(simpleType(inner.getType())) || inner.getArguments().isNonEmpty() && borrowed(inner.getArgument(0), at, cu);
        }
        if (a instanceof MethodCallExpr m) {
            return servletOwned(m) || LOOKUP.equals(m.getNameAsString());
        }
        if (a instanceof FieldAccessExpr f && f.getScope() instanceof NameExpr s && SYSTEM.equals(s.getNameAsString())) {
            return true;
        }
        return borrowedName(a, at, cu);
    }

    /** 이름·this.x — 메모리 형식이거나, 지역 변수 아닌 자원(매개변수·필드) */
    private static boolean borrowedName(Expression a, Node at, CompilationUnit cu) {
        boolean thisField = a instanceof FieldAccessExpr f && f.getScope() instanceof ThisExpr;
        if (!thisField && !(a instanceof NameExpr)) {
            return false;
        }
        String x = a instanceof NameExpr n ? n.getNameAsString() : ((FieldAccessExpr) a).getNameAsString();
        boolean local = !thisField && enclosingBody(at).map(b -> declaresLocal(b, x)).orElse(false);
        String type = ValueAst.declaredType(a);
        return MEMORY.contains(type) || !local && isResource(type, cu);
    }

    /** request·response 의 스트림 — 컨테이너가 닫는다 */
    private static boolean servletOwned(MethodCallExpr m) {
        if (!SERVLET_GETTERS.contains(m.getNameAsString())) {
            return false;
        }
        Optional<Expression> scope = m.getScope().map(ResourceAst::strip).filter(Expression::isNameExpr);
        return scope.map(s -> SERVLET_OWNERS.contains(s.asNameExpr().getNameAsString())
                || SERVLET_TYPES.stream().anyMatch(ValueAst.declaredType(s)::endsWith)).orElse(false);
    }

    private static boolean declaresLocal(Node body, String x) {
        return body.findAll(VariableDeclarator.class).stream().anyMatch(d -> d.getNameAsString().equals(x));
    }

    // ---------------------------------------------------------------- resourceLeak

    /** 지역 변수 자원 후보면 그 몸 — 초기화식이 만든 자리이거나, null·없음이고 몸 안 첫 대입이 만든 자리 */
    private static Optional<Node> candidate(VariableDeclarator v, CompilationUnit cu) {
        boolean local = v.getParentNode().filter(p -> p instanceof VariableDeclarationExpr)
                .flatMap(Node::getParentNode).filter(p -> p instanceof ExpressionStmt).isPresent();
        if (!local || !isResource(typeOf(v), cu)) {
            return Optional.empty();
        }
        Optional<Node> body = enclosingBody(v);
        Optional<Expression> init = v.getInitializer().map(ResourceAst::strip);
        if (init.isPresent() && !init.get().isNullLiteralExpr()) {
            return init.filter(e -> creation(e, cu)).flatMap(e -> body);
        }
        return body.filter(b -> firstAssign(b, v.getNameAsString()).filter(a -> creation(a.getValue(), cu)).isPresent());
    }

    private static Optional<Node> enclosingBody(Node n) {
        for (Node a = n.getParentNode().orElse(null); a != null; a = a.getParentNode().orElse(null)) {
            if (a instanceof CallableDeclaration || a instanceof InitializerDeclaration || a instanceof LambdaExpr) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    private static Optional<AssignExpr> firstAssign(Node body, String x) {
        return body.findAll(AssignExpr.class).stream().filter(a -> a.getOperator() == AssignExpr.Operator.ASSIGN
                && a.getTarget() instanceof NameExpr t && t.getNameAsString().equals(x)).findFirst();
    }

    /** 이름 하나라도 해제·넘김 자리에 나오면 해제 */
    private static boolean released(VariableDeclarator v, Node body) {
        String x = v.getNameAsString();
        return body.findAll(NameExpr.class).stream().filter(n -> n.getNameAsString().equals(x))
                .anyMatch(n -> releasedAt(n, v, body));
    }

    private static boolean releasedAt(NameExpr n, VariableDeclarator v, Node body) {
        Node p = n.getParentNode().orElse(null);
        boolean scope = p instanceof MethodCallExpr m && m.getScope().filter(s -> same(s, n)).isPresent();
        boolean returned = !scope && n.findAncestor(ReturnStmt.class).isPresent();
        return handedOver(n, p, scope, body) || p instanceof TryStmt || returned || captured(n, v, body);
    }

    /** close·closer 유틸·new 인자·컬렉션 넣기·필드 대입 자리 */
    private static boolean handedOver(NameExpr n, Node p, boolean scope, Node body) {
        if (p instanceof MethodCallExpr m) {
            String name = m.getNameAsString();
            return closer(name) && (!scope || inFinally(m)) || !scope && handsTo(name);
        }
        if (p instanceof ObjectCreationExpr) {
            return true; // 래퍼·생성자에 넘김 — 받은 객체가 소유한다(래퍼 변수는 따로 후보)
        }
        if (p instanceof AssignExpr a && same(a.getValue(), n)) {
            return a.getTarget() instanceof FieldAccessExpr
                    || a.getTarget() instanceof NameExpr t && !declaresLocal(body, t.getNameAsString());
        }
        return false;
    }

    private static boolean closer(String name) {
        return CLOSERS.contains(name) || name.startsWith(CLOSE);
    }

    private static boolean handsTo(String name) {
        return TRANSFER.contains(name) || TRANSFER_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private static boolean inFinally(Node n) {
        return n.findAncestor(BlockStmt.class).map(b -> b.getParentNode().filter(p -> p instanceof TryStmt t
                && t.getFinallyBlock().filter(f -> same(f, b)).isPresent()).isPresent() || inFinally(b)).orElse(false);
    }

    /** 몸 안의 람다·익명 클래스가 선언 밖에서 이름을 잡는다 */
    private static boolean captured(NameExpr n, VariableDeclarator v, Node body) {
        for (Node a = n.getParentNode().orElse(null); a != null && !same(a, body); a = a.getParentNode().orElse(null)) {
            boolean closure = a instanceof LambdaExpr || a instanceof ObjectCreationExpr o && o.getAnonymousClassBody().isPresent();
            if (closure && !a.isAncestorOf(v)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- tryWithResources

    /** finally 에 close 문장만 있는 try 를 try-with-resources 로 옮길 수 있으면 그 계획 — 판정 = 고침 전제 */
    static Optional<Twr> plan(TryStmt t, CompilationUnit cu) {
        return Planner.of(t, cu);
    }

    /** try-with-resources 판정 — 메서드 수 한계(30) 때문에 따로 센다 */
    private static final class Planner {

        private Planner() {
        }

        static Optional<Twr> of(TryStmt t, CompilationUnit cu) {
            if (t.getResources().isNonEmpty() || t.getFinallyBlock().isEmpty()
                    || !(t.getParentNode().orElse(null) instanceof BlockStmt blk)) {
                return Optional.empty();
            }
            Optional<List<String>> names = closeNames(t.getFinallyBlock().get());
            if (names.isEmpty()) {
                return Optional.empty();
            }
            List<Res> a = new ArrayList<>();
            List<Res> b = new ArrayList<>();
            for (String n : names.get()) {
                Optional<Res> r = resource(t, blk, n, names.get(), cu);
                if (r.isEmpty()) {
                    return Optional.empty();
                }
                (r.get().assign().isPresent() ? b : a).add(r.get());
            }
            a.sort(Comparator.comparingInt(r -> line(r.decl())));
            b.sort(Comparator.comparingInt(r -> line(r.assign().get())));
            a.addAll(b);
            return t.getTryBlock().getStatements().size() > b.size() && assignsFirst(t, b) ? Optional.of(new Twr(t, a)) : Optional.empty();
        }

        /** finally 문장마다 close 하나 — 이름 1~3, 겹침 없음. 다른 문장이 있으면 빈 값 */
        private static Optional<List<String>> closeNames(BlockStmt fin) {
            Set<String> names = new LinkedHashSet<>();
            for (Statement s : fin.getStatements()) {
                Optional<String> n = closeName(s);
                if (n.isEmpty() || !names.add(n.get())) {
                    return Optional.empty();
                }
            }
            return names.isEmpty() || names.size() > MAX_NAMES ? Optional.empty() : Optional.of(List.copyOf(names));
        }

        /** {@code n.close();} · {@code closeQuietly(n);} · {@code try { n.close(); } catch …} · 그것을 감싼 {@code if (n != null)} */
        private static Optional<String> closeName(Statement s) {
            if (s instanceof IfStmt i && i.getElseStmt().isEmpty()) {
                Optional<String> guard = nullGuard(i.getCondition());
                Statement inner = single(i.getThenStmt());
                return guard.filter(g -> !(inner instanceof IfStmt) && closeName(inner).filter(g::equals).isPresent());
            }
            if (s instanceof TryStmt t && t.getResources().isEmpty() && t.getFinallyBlock().isEmpty()
                    && t.getCatchClauses().isNonEmpty() && t.getTryBlock().getStatements().size() == ONE) {
                return closeCall(t.getTryBlock().getStatement(0)).filter(c -> CLOSE.equals(c.getNameAsString())).flatMap(Planner::target);
            }
            return closeCall(s).flatMap(Planner::target);
        }

        private static Optional<MethodCallExpr> closeCall(Statement s) {
            if (s instanceof ExpressionStmt es && es.getExpression() instanceof MethodCallExpr m) {
                return Optional.of(m);
            }
            return Optional.empty();
        }

        /** {@code n.close()} 의 n · {@code X.closeQuietly(n)} 의 n */
        private static Optional<String> target(MethodCallExpr m) {
            if (CLOSE.equals(m.getNameAsString()) && m.getArguments().isEmpty()) {
                return m.getScope().filter(Expression::isNameExpr).map(e -> e.asNameExpr().getNameAsString());
            }
            if (CLOSE_QUIETLY.equals(m.getNameAsString()) && m.getArguments().size() == ONE) {
                return Optional.of(m.getArgument(0)).filter(Expression::isNameExpr).map(e -> e.asNameExpr().getNameAsString());
            }
            return Optional.empty();
        }

        private static Optional<String> nullGuard(Expression c) {
            if (strip(c) instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.NOT_EQUALS) {
                Expression l = strip(b.getLeft());
                Expression r = strip(b.getRight());
                Expression name = r.isNullLiteralExpr() ? l : r;
                if ((l.isNullLiteralExpr() || r.isNullLiteralExpr()) && name.isNameExpr()) {
                    return Optional.of(name.asNameExpr().getNameAsString());
                }
            }
            return Optional.empty();
        }

        private static Statement single(Statement s) {
            return s instanceof BlockStmt b && b.getStatements().size() == ONE ? b.getStatement(0) : s;
        }

        /** 이름 n 의 자원 — 같은 블록 앞 선언(꼴 B {@code = null} + try 안 대입 · 꼴 A 바로 앞 만든 자리), 밖에서 안 씀, close 예외를 받음 */
        private static Optional<Res> resource(TryStmt t, BlockStmt blk, String n, List<String> names, CompilationUnit cu) {
            int at = index(blk, t);
            for (int j = at - 1; j >= 0; j--) {
                Statement s = blk.getStatement(j);
                Optional<VariableDeclarator> d = declaration(s).filter(v -> v.getNameAsString().equals(n));
                if (d.isPresent()) {
                    return resourceAt(t, blk, j, d.get(), names, cu);
                }
            }
            return Optional.empty();
        }

        private static Optional<Res> resourceAt(TryStmt t, BlockStmt blk, int j, VariableDeclarator v, List<String> names,
                                                CompilationUnit cu) {
            String type = v.getType().isVarType() ? UNKNOWN : typeOf(v);
            if (!isResource(type, cu) || !covered(t, closeException(type))) {
                return Optional.empty();
            }
            ExpressionStmt decl = (ExpressionStmt) blk.getStatement(j);
            Optional<Expression> init = v.getInitializer().map(ResourceAst::strip).filter(e -> !e.isNullLiteralExpr());
            if (init.isPresent()) {
                // 꼴 A 에 catch 가 있으면 옮긴 초기화의 예외가 그 catch 로 들어간다(전에는 메서드 밖으로 — 마무리 리뷰)
                boolean adjacent = t.getCatchClauses().isEmpty() && blk.getStatements().subList(j + 1, index(blk, t)).stream()
                        .allMatch(s -> declaration(s).filter(d -> names.contains(d.getNameAsString())).isPresent());
                return adjacent && creation(init.get(), cu) && !usedOutside(t, blk, j, v.getNameAsString(), Optional.empty())
                        ? Optional.of(new Res(v.getNameAsString(), v.getType(), decl, Optional.empty(), init.get())) : Optional.empty();
            }
            Optional<ExpressionStmt> assign = t.getTryBlock().getStatements().stream()
                    .filter(s -> assignment(s, v.getNameAsString()).filter(a -> creation(a.getValue(), cu)).isPresent())
                    .map(s -> (ExpressionStmt) s).findFirst();
            return assign.filter(a -> !usedOutside(t, blk, j, v.getNameAsString(), assign))
                    .map(a -> new Res(v.getNameAsString(), v.getType(), decl, assign, ((AssignExpr) a.getExpression()).getValue()));
        }

        /** 꼴 B 대입은 try 블록 첫 문장들 */
        private static boolean assignsFirst(TryStmt t, List<Res> b) {
            List<Statement> head = t.getTryBlock().getStatements().subList(0, b.size());
            return b.stream().allMatch(r -> head.stream().anyMatch(s -> same(s, r.assign().get())));
        }

        private static Optional<VariableDeclarator> declaration(Statement s) {
            if (s instanceof ExpressionStmt es && es.getExpression() instanceof VariableDeclarationExpr d
                    && d.getVariables().size() == ONE) {
                return Optional.of(d.getVariable(0));
            }
            return Optional.empty();
        }

        private static Optional<AssignExpr> assignment(Statement s, String n) {
            if (s instanceof ExpressionStmt es && es.getExpression() instanceof AssignExpr a
                    && a.getOperator() == AssignExpr.Operator.ASSIGN && a.getTarget() instanceof NameExpr t
                    && t.getNameAsString().equals(n)) {
                return Optional.of(a);
            }
            return Optional.empty();
        }

        /** 선언과 try 사이 · catch · try 뒤 형제 문장에 이름이 나오거나, try 안에서 (꼴 B 대입 말고) 다시 대입한다 */
        private static boolean usedOutside(TryStmt t, BlockStmt blk, int decl, String n, Optional<ExpressionStmt> assign) {
            int at = index(blk, t);
            List<Node> outside = new ArrayList<>(blk.getStatements().subList(decl + 1, at));
            outside.addAll(blk.getStatements().subList(at + 1, blk.getStatements().size()));
            outside.addAll(t.getCatchClauses());
            boolean named = outside.stream().anyMatch(o -> o.findAll(NameExpr.class).stream().anyMatch(x -> x.getNameAsString().equals(n)));
            boolean reassigned = t.getTryBlock().findAll(AssignExpr.class).stream()
                    .filter(a -> a.getTarget() instanceof NameExpr x && x.getNameAsString().equals(n))
                    .anyMatch(a -> assign.map(s -> !same(s.getExpression(), a)).orElse(true));
            return named || reassigned;
        }

        /** close 가 던지는 체크 예외 — JDBC 는 SQLException, 스트림·파일·소켓·채널·POI 는 IOException, 나머지는 없음 */
        private static String closeException(String type) {
            if (JDBC.contains(type)) {
                return SQL_EXCEPTION;
            }
            boolean io = SUFFIXES.stream().anyMatch(type::endsWith) || IO_EXACT.contains(type) || type.endsWith(CHANNEL)
                    || type.startsWith(WORKBOOK) || type.endsWith(WORKBOOK);
            return io ? IO_EXCEPTION : UNKNOWN;
        }

        /** try 의 catch 나 둘러싼 메서드 throws 가 그 예외(또는 Exception·Throwable)를 받는다 */
        private static boolean covered(TryStmt t, String exception) {
            if (exception.isEmpty()) {
                return true;
            }
            for (CatchClause c : t.getCatchClauses()) {
                Type p = c.getParameter().getType();
                List<Type> types = p.isUnionType() ? new ArrayList<>(p.asUnionType().getElements()) : List.of(p);
                if (types.stream().map(ResourceAst::simpleType).anyMatch(s -> BROAD.contains(s) || s.equals(exception))) {
                    return true;
                }
            }
            Optional<Node> owner = enclosingBody(t);
            return owner.filter(o -> o instanceof CallableDeclaration).map(o -> ((CallableDeclaration<?>) o).getThrownExceptions())
                    .map(ts -> ts.stream().map(ReferenceType::asString).map(ResourceAst::lastName)
                            .anyMatch(s -> BROAD.contains(s) || s.equals(exception))).orElse(false);
        }
    }

    // ---------------------------------------------------------------- 도우미

    private static int index(BlockStmt blk, Statement s) {
        List<Statement> ss = blk.getStatements();
        for (int i = 0; i < ss.size(); i++) {
            if (same(ss.get(i), s)) {
                return i;
            }
        }
        return -1;
    }

    private static Expression strip(Expression e) {
        Expression x = e;
        while (x instanceof EnclosedExpr en) {
            x = en.getInner();
        }
        return x;
    }

    private static boolean same(Node a, Node b) {
        return a.getRange().equals(b.getRange());
    }

    /** 선언 글에서 {@code <…>}·{@code []} 를 떼고 마지막 {@code .} 뒤 */
    private static String simpleType(Type t) {
        return lastName(t.asString());
    }

    private static String lastName(String type) {
        String s = type;
        int lt = s.indexOf('<');
        if (lt >= 0) {
            s = s.substring(0, lt);
        }
        s = s.replace("[]", "").trim();
        return s.substring(s.lastIndexOf('.') + 1);
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(1);
    }
}
