package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
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
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 설계 49 — 값·비교 결함 셋. JavaParser AST 만 본다(타입 해석 없음 — 형식은 같은 파일의 선언 글로).
 * <ul>
 *   <li>{@code java.refEquals} — {@code ==}·{@code !=} 한쪽이 문자열 리터럴이거나, 양쪽 선언 형식이 둘 다 String·래퍼</li>
 *   <li>{@code java.bigDecimalDouble} — {@code new BigDecimal(…)} 첫 인자가 double·float 리터럴·변수·형 변환·변환 호출</li>
 *   <li>{@code java.weekYear} — 날짜 형식 자리(형식·메서드·scope 이름에 format·pattern·parse·date) 인자 글에 {@code YY} 가 있고
 *   {@code w} 가 없다. 같은 파일 static final 상수 한 홉은 필드 줄</li>
 * </ul>
 */
final class ValueAst {

    static final String REF_EQUALS = "java.refEquals";
    static final String BIG_DECIMAL = "java.bigDecimalDouble";
    static final String WEEK_YEAR = "java.weekYear";
    private static final Set<String> BOXED = Set.of("String", "Integer", "Long", "Short", "Byte", "Character", "Boolean",
            "Double", "Float", "BigDecimal", "BigInteger");
    private static final Set<String> FLOATING = Set.of("double", "float", "Double", "Float");
    private static final Set<String> TO_DOUBLE = Set.of("doubleValue", "floatValue", "parseDouble", "parseFloat", "nextDouble",
            "nextFloat");
    private static final Pattern WEEK_YEAR_PATTERN = Pattern.compile("^[A-Za-z0-9 '.,:/_-]{1,40}$");
    private static final List<String> SLOT_WORDS = List.of("format", "pattern", "parse", "date");
    private static final String BIG_DECIMAL_TYPE = "BigDecimal";
    private static final String STRING_TYPE = "String";
    private static final String UNKNOWN = "";
    private static final String EQUALS = "equals";
    private static final int ONE = 1;

    private ValueAst() {
    }

    /** 켜진 규칙(on)의 판정만 — 줄·규칙 순서, 같은 노드·같은 규칙은 한 건 */
    static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on) {
        List<Taint.Hit> out = new ArrayList<>();
        if (on.contains(REF_EQUALS)) {
            refEquals(cu, out);
        }
        if (on.contains(BIG_DECIMAL)) {
            bigDecimals(cu, out);
        }
        if (on.contains(WEEK_YEAR)) {
            weekYears(cu, out);
        }
        out.sort(Comparator.comparingInt((Taint.Hit h) -> line(h.node())).thenComparing(Taint.Hit::rule));
        return out;
    }

    private static void add(List<Taint.Hit> out, String rule, Node n) {
        if (out.stream().noneMatch(h -> h.rule().equals(rule) && same(h.node(), n))) {
            out.add(new Taint.Hit(rule, n));
        }
    }

    // ---------------------------------------------------------------- refEquals

    private static void refEquals(CompilationUnit cu, List<Taint.Hit> out) {
        for (BinaryExpr b : cu.findAll(BinaryExpr.class)) {
            if (b.getOperator() != BinaryExpr.Operator.EQUALS && b.getOperator() != BinaryExpr.Operator.NOT_EQUALS) {
                continue;
            }
            Expression l = strip(b.getLeft());
            Expression r = strip(b.getRight());
            if (l.isNullLiteralExpr() || r.isNullLiteralExpr()) {
                continue;
            }
            if (l.isStringLiteralExpr() || r.isStringLiteralExpr() || isBoxed(l) && isBoxed(r) && !identityShortcut(b)) {
                add(out, REF_EQUALS, b);
            }
        }
    }

    /**
     * 같은 객체 빠른 길(R-14) — {@code a == b || a.equals(b)} 꼴, 또는 {@code if (a == b)} 가 상수 하나를 돌려준다.
     * 양쪽이 이름일 때만 부른다(문자열 리터럴 비교는 이 꼴이어도 지적)
     */
    private static boolean identityShortcut(BinaryExpr b) {
        if (b.getOperator() != BinaryExpr.Operator.EQUALS) {
            return false;
        }
        Node child = b;
        Node p = b.getParentNode().orElse(null);
        while (p instanceof EnclosedExpr) {
            child = p;
            p = p.getParentNode().orElse(null);
        }
        if (p instanceof BinaryExpr o && o.getOperator() == BinaryExpr.Operator.OR) {
            Expression other = same(o.getLeft(), child) ? o.getRight() : o.getLeft();
            return other.findAll(MethodCallExpr.class).stream()
                    .anyMatch(m -> m.getNameAsString().startsWith(EQUALS) && namesBoth(m, b));
        }
        return p instanceof IfStmt i && same(i.getCondition(), child) && returnsLiteral(i.getThenStmt());
    }

    /** equals 호출의 scope·인자에 비교 양쪽 이름이 다 있다 — {@code a.equals(b)}·{@code Objects.equals(b, a)} */
    private static boolean namesBoth(MethodCallExpr m, BinaryExpr b) {
        Set<String> names = new HashSet<>();
        m.getScope().ifPresent(s -> names.add(strip(s).toString()));
        m.getArguments().forEach(a -> names.add(strip(a).toString()));
        return names.contains(strip(b.getLeft()).toString()) && names.contains(strip(b.getRight()).toString());
    }

    private static boolean returnsLiteral(Statement t) {
        Statement s = t instanceof BlockStmt blk && blk.getStatements().size() == ONE ? blk.getStatement(0) : t;
        return s instanceof ReturnStmt r && r.getExpression().filter(Expression::isLiteralExpr).isPresent();
    }

    private static Expression strip(Expression e) {
        Expression x = e;
        while (x instanceof EnclosedExpr en) {
            x = en.getInner();
        }
        return x;
    }

    private static boolean isBoxed(Expression e) {
        return BOXED.contains(declaredType(e));
    }

    // ---------------------------------------------------------------- 선언 형식

    /** 이름(또는 this.x)의 선언 형식 — 람다·메서드 매개변수 → 둘러싼 블록·for·catch·try 자원 → 가장 가까운 클래스 필드. 못 찾으면 빈 글 */
    private static String declaredType(Expression e) {
        boolean thisField = e instanceof FieldAccessExpr f && f.getScope() instanceof ThisExpr;
        if (!(e instanceof NameExpr) && !thisField) {
            return UNKNOWN;
        }
        String x = e instanceof NameExpr n ? n.getNameAsString() : ((FieldAccessExpr) e).getNameAsString();
        for (Node a = e.getParentNode().orElse(null); a != null; a = a.getParentNode().orElse(null)) {
            if (a instanceof ClassOrInterfaceDeclaration c) {
                return c.getFieldByName(x).flatMap(fd -> varType(fd.getVariables(), x)).map(ValueAst::scalarType).orElse(UNKNOWN);
            }
            Optional<Type> t = thisField ? Optional.empty() : declIn(a, x);
            if (t.isPresent()) {
                return scalarType(t.get());
            }
        }
        return UNKNOWN;
    }

    private static Optional<Type> declIn(Node a, String x) {
        if (a instanceof LambdaExpr l) {
            return param(l.getParameters(), x);
        }
        if (a instanceof CallableDeclaration<?> c) {
            return param(c.getParameters(), x);
        }
        if (a instanceof CatchClause cc) {
            return param(List.of(cc.getParameter()), x);
        }
        if (a instanceof NodeWithStatements<?> b) {
            return locals(b.getStatements(), x);
        }
        if (a instanceof ForStmt f) {
            return declared(f.getInitialization(), x);
        }
        if (a instanceof ForEachStmt fe) {
            return varType(fe.getVariable().getVariables(), x);
        }
        if (a instanceof TryStmt t) {
            return declared(t.getResources(), x);
        }
        return Optional.empty();
    }

    private static Optional<Type> param(List<Parameter> ps, String x) {
        return ps.stream().filter(p -> p.getNameAsString().equals(x))
                .map(p -> p.isVarArgs() ? new ArrayType(p.getType()) : p.getType()).findFirst();
    }

    private static Optional<Type> locals(List<Statement> ss, String x) {
        List<Expression> decls = new ArrayList<>();
        for (Statement s : ss) {
            if (s instanceof ExpressionStmt es) {
                decls.add(es.getExpression());
            }
        }
        return declared(decls, x);
    }

    private static Optional<Type> declared(List<Expression> es, String x) {
        for (Expression e : es) {
            if (e instanceof VariableDeclarationExpr v) {
                Optional<Type> t = varType(v.getVariables(), x);
                if (t.isPresent()) {
                    return t;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Type> varType(List<VariableDeclarator> vs, String x) {
        return vs.stream().filter(v -> v.getNameAsString().equals(x)).map(VariableDeclarator::getType).findFirst();
    }

    // ---------------------------------------------------------------- bigDecimalDouble

    private static void bigDecimals(CompilationUnit cu, List<Taint.Hit> out) {
        for (ObjectCreationExpr o : cu.findAll(ObjectCreationExpr.class)) {
            if (BIG_DECIMAL_TYPE.equals(o.getType().getNameAsString()) && o.getArguments().isNonEmpty()
                    && doubleArg(o.getArgument(0))) {
                add(out, BIG_DECIMAL, o);
            }
        }
    }

    private static boolean doubleArg(Expression arg) {
        Expression a = strip(arg);
        if (a instanceof UnaryExpr u) {
            return doubleArg(u.getExpression());
        }
        if (a instanceof DoubleLiteralExpr) {
            return true;
        }
        if (a instanceof CastExpr c) {
            return FLOATING.contains(simpleType(c.getType()));
        }
        if (a instanceof MethodCallExpr m) {
            return TO_DOUBLE.contains(m.getNameAsString());
        }
        return FLOATING.contains(declaredType(a));
    }

    // ---------------------------------------------------------------- weekYear

    private static void weekYears(CompilationUnit cu, List<Taint.Hit> out) {
        List<Expression> calls = new ArrayList<>(cu.findAll(MethodCallExpr.class));
        calls.addAll(cu.findAll(ObjectCreationExpr.class));
        for (Expression c : calls) {
            if (!formatSlot(c)) {
                continue;
            }
            for (Expression a : ((NodeWithArguments<?>) c).getArguments()) {
                Expression x = strip(a);
                if (x instanceof StringLiteralExpr s && patternLike(s.getValue())) {
                    add(out, WEEK_YEAR, s);
                } else if (x instanceof NameExpr n) {
                    constantLiteral(cu, n.getNameAsString()).ifPresent(s -> add(out, WEEK_YEAR, s));
                }
            }
        }
    }

    /** 형식 이름(new X)·메서드 이름 + scope 이름을 소문자로 이은 글에 format·pattern·parse·date 가 든다 */
    private static boolean formatSlot(Expression c) {
        String name = c instanceof ObjectCreationExpr o ? o.getType().getNameAsString()
                : ((MethodCallExpr) c).getNameAsString() + ((MethodCallExpr) c).getScope().map(ValueAst::lastName).orElse(UNKNOWN);
        String low = name.toLowerCase(Locale.ROOT);
        return SLOT_WORDS.stream().anyMatch(low::contains);
    }

    /** 날짜 형식 꼴 — 글자 집합 안 · YY 있음(Y 하나는 이름·따옴표 글자 — R-14) · 주 번호 w 없음 · M 또는 d 있음 */
    private static boolean patternLike(String v) {
        return WEEK_YEAR_PATTERN.matcher(v).matches() && v.contains("YY") && !v.contains("w") && (v.contains("M") || v.contains("d"));
    }

    /** 같은 파일 static final String 필드 x 의 초기값이 날짜 형식 꼴 리터럴이면 그 리터럴 */
    private static Optional<StringLiteralExpr> constantLiteral(CompilationUnit cu, String x) {
        for (FieldDeclaration f : cu.findAll(FieldDeclaration.class)) {
            if (!f.isStatic() || !f.isFinal() || !STRING_TYPE.equals(simpleType(f.getElementType()))) {
                continue;
            }
            Optional<StringLiteralExpr> lit = f.getVariables().stream().filter(v -> v.getNameAsString().equals(x))
                    .flatMap(v -> v.getInitializer().stream()).map(ValueAst::strip)
                    .filter(e -> e instanceof StringLiteralExpr s && patternLike(s.getValue()))
                    .map(e -> (StringLiteralExpr) e).findFirst();
            if (lit.isPresent()) {
                return lit;
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- 도우미

    private static boolean same(Node a, Node b) {
        return a.getRange().equals(b.getRange());
    }

    /** 선언 형식 이름 — 배열·varargs 는 빈 글(배열 {@code ==} 는 동일성 비교가 맞다 — 마무리 리뷰) */
    private static String scalarType(Type t) {
        return t.isArrayType() ? UNKNOWN : simpleType(t);
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
        return UNKNOWN;
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(1);
    }
}
