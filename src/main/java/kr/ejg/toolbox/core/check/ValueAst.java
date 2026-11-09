package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Pattern;

/**
 * 설계 49 — 값·비교 결함 셋. JavaParser AST 만 본다(타입 해석 없음 — 형식은 같은 파일의 선언 글로).
 * <ul>
 *   <li>{@code java.refEquals} — {@code ==}·{@code !=} 한쪽이 문자열 리터럴이거나, 양쪽 선언 형식이 둘 다 String·래퍼</li>
 *   <li>{@code java.bigDecimalDouble} — {@code new BigDecimal(…)} 첫 인자가 double·float 리터럴·변수·형 변환·변환 호출</li>
 *   <li>{@code java.weekYear} — 날짜 형식 자리(형식·메서드·scope 이름에 format·pattern·parse·date) 인자 글에 {@code YY} 가 있고
 *   {@code w} 가 없다. 같은 파일 static final 상수 한 홉은 필드 줄</li>
 *   <li>{@code java.equalsLiteralFirst}(설계 50) — scope 가 있는 {@code equals}·{@code equalsIgnoreCase} 의 인자 하나가 문자열 리터럴
 *   (scope 가 리터럴·super·클래스 리터럴이면 아님)</li>
 * </ul>
 */
final class ValueAst {

    static final String REF_EQUALS = "java.refEquals";
    static final String BIG_DECIMAL = "java.bigDecimalDouble";
    static final String DEFAULT_CHARSET = "java.defaultCharset";
    static final String WEEK_YEAR = "java.weekYear";
    static final String EQUALS_FIRST = "java.equalsLiteralFirst";
    /** 5-61b — JavaDefects.floatEquals 도 쓴다 */
    static final Set<String> FLOATING = Set.of("double", "float", "Double", "Float");
    private static final Set<String> EQUALS_NAMES = Set.of("equals", "equalsIgnoreCase");
    private static final Set<String> BOXED = Set.of("String", "Integer", "Long", "Short", "Byte", "Character", "Boolean",
            "Double", "Float", "BigDecimal", "BigInteger");
    private static final Set<String> FLOATS = Set.of("float", "Float");
    private static final Set<String> TO_FLOAT = Set.of("floatValue", "parseFloat", "nextFloat");
    private static final Set<String> TO_DOUBLE = Set.of("doubleValue", "floatValue", "parseDouble", "parseFloat", "nextDouble",
            "nextFloat");
    private static final Pattern WEEK_YEAR_PATTERN = Pattern.compile("^[A-Za-z0-9 '.,:/_-]{1,40}$");
    private static final List<String> SLOT_WORDS = List.of("format", "pattern", "parse", "date");
    private static final String BIG_DECIMAL_TYPE = "BigDecimal";
    private static final String STRING_TYPE = "String";
    private static final String GET_BYTES = "getBytes";
    private static final String TO_BYTE_ARRAY = "toByteArray";
    private static final String TO_STRING = "toString";
    /** String 을 돌려주는 String 정적 메서드 */
    private static final Set<String> STRING_FACTORIES = Set.of("valueOf", "format", "join");
    private static final String UNKNOWN = "";
    /** R-21 — Boolean.TRUE·FALSE 는 리터럴과 같다(commons-lang BooleanUtils 빠른 길) */
    private static final String BOOLEAN = "Boolean";
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
        if (on.contains(EQUALS_FIRST)) {
            literalFirst(cu, out);
        }
        if (on.contains(DEFAULT_CHARSET)) {
            defaultCharsets(cu, out);
        }
        out.sort(Comparator.comparingInt((Taint.Hit h) -> line(h.node())).thenComparing(Taint.Hit::rule));
        return out;
    }

    /** 설계 60 — 수신자가 String 인 무인자 getBytes() · 인자가 byte[] 인 한 인자 new String(b) */
    private static void defaultCharsets(CompilationUnit cu, List<Taint.Hit> out) {
        for (MethodCallExpr m : cu.findAll(MethodCallExpr.class)) {
            if (GET_BYTES.equals(m.getNameAsString()) && m.getArguments().isEmpty() && m.getScope().filter(ValueAst::stringy).isPresent()) {
                add(out, DEFAULT_CHARSET, m);
            }
        }
        for (ObjectCreationExpr o : cu.findAll(ObjectCreationExpr.class)) {
            if (STRING_TYPE.equals(o.getType().getNameAsString()) && o.getArguments().size() == ONE && bytes(o.getArgument(0))) {
                add(out, DEFAULT_CHARSET, o);
            }
        }
    }

    /** String 으로 보이는 식 — 문자열 리터럴(텍스트 블록) · 선언 형식 String 인 이름·this.x · 무인자 toString() · String.valueOf/format/join(…) · 한쪽이 String 인 + */
    static boolean stringy(Expression e) {
        Expression x = strip(e);
        if (x.isStringLiteralExpr() || x.isTextBlockLiteralExpr()) {
            return true;
        }
        if (x instanceof NameExpr || x instanceof FieldAccessExpr) {
            return STRING_TYPE.equals(DeclaredTypes.declaredType(x));
        }
        if (x instanceof MethodCallExpr m) {
            return TO_STRING.equals(m.getNameAsString()) && m.getArguments().isEmpty()
                    || STRING_FACTORIES.contains(m.getNameAsString()) && m.getScope().map(ValueAst::lastName).filter(STRING_TYPE::equals).isPresent();
        }
        return x instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS && (stringy(b.getLeft()) || stringy(b.getRight()));
    }

    /** byte[] 로 보이는 식 — 선언 형식 byte[] 인 이름·this.x · getBytes(…)·toByteArray() 호출 */
    private static boolean bytes(Expression e) {
        Expression x = strip(e);
        if (x instanceof MethodCallExpr m) {
            return GET_BYTES.equals(m.getNameAsString()) || TO_BYTE_ARRAY.equals(m.getNameAsString()) && m.getArguments().isEmpty();
        }
        return DeclaredTypes.isByteArray(x);
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
        if (sentinel(b)) {
            return true;
        }
        if (b.getOperator() != BinaryExpr.Operator.EQUALS) {
            return false;
        }
        Node child = b;
        Node p = b.getParentNode().orElse(null);
        while (p instanceof EnclosedExpr) {
            child = p;
            p = p.getParentNode().orElse(null);
        }
        List<Expression> others = new ArrayList<>();
        while (p instanceof BinaryExpr o && o.getOperator() == BinaryExpr.Operator.OR) { // 5-61c — 세 항 OR 도 각 반대쪽
            others.add(same(o.getLeft(), child) ? o.getRight() : o.getLeft());
            child = o;
            p = o.getParentNode().orElse(null);
            while (p instanceof EnclosedExpr) {
                child = p;
                p = p.getParentNode().orElse(null);
            }
        }
        if (!others.isEmpty()) {
            return others.stream().anyMatch(other -> other.findAll(MethodCallExpr.class).stream()
                    .anyMatch(m -> m.getNameAsString().startsWith(EQUALS) && namesBoth(m, b)));
        }
        return p instanceof IfStmt i && same(i.getCondition(), child) && returnsLiteral(i.getThenStmt());
    }

    /** 5-61c — 표지 객체: 한쪽이 같은 파일 static final 필드이고 초기값이 new(동일성 비교가 맞다 — ==·!= 둘 다) */
    private static boolean sentinel(BinaryExpr b) {
        return Stream.of(strip(b.getLeft()), strip(b.getRight()))
                .map(e -> e instanceof NameExpr || e instanceof FieldAccessExpr f && f.getScope() instanceof ThisExpr ? lastName(e) : UNKNOWN)
                .filter(x -> !x.isEmpty())
                .anyMatch(x -> staticFinal(b, x).flatMap(VariableDeclarator::getInitializer).map(ValueAst::strip)
                        .filter(Expression::isObjectCreationExpr).isPresent());
    }

    /** 5-61c — 같은 파일 어느 클래스든 static final 필드 x 의 선언자 */
    private static Optional<VariableDeclarator> staticFinal(Node from, String x) {
        return from.findCompilationUnit().stream().flatMap(cu -> cu.findAll(FieldDeclaration.class).stream())
                .filter(f -> f.isStatic() && f.isFinal())
                .flatMap(f -> f.getVariables().stream()).filter(v -> v.getNameAsString().equals(x)).findFirst();
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
        return s instanceof ReturnStmt r && r.getExpression().map(ValueAst::strip) // 5-61c — 상수 반환(return CONST;)도
                .filter(e -> e.isLiteralExpr() || e instanceof NameExpr n && staticFinal(r, n.getNameAsString()).isPresent()
                        || e instanceof FieldAccessExpr f && BOOLEAN.equals(f.getScope().toString())).isPresent();
    }

    private static Expression strip(Expression e) {
        Expression x = e;
        while (x instanceof EnclosedExpr en) {
            x = en.getInner();
        }
        return x;
    }

    /** 선언 형식이 래퍼 — 5-64 ② 로 JavaDefects.floatEquals 도 쓴다 */
    static boolean isBoxed(Expression e) {
        return BOXED.contains(DeclaredTypes.declaredType(e));
    }

    // ---------------------------------------------------------------- equalsLiteralFirst

    private static void literalFirst(CompilationUnit cu, List<Taint.Hit> out) {
        for (MethodCallExpr m : cu.findAll(MethodCallExpr.class)) {
            if (!EQUALS_NAMES.contains(m.getNameAsString()) || m.getArguments().size() != ONE) {
                continue;
            }
            Expression arg = strip(m.getArgument(0));
            boolean constant = arg instanceof NameExpr n && staticFinal(m, n.getNameAsString()) // 5-61c — 같은 파일 static final String 리터럴 상수
                    .filter(v -> STRING_TYPE.equals(DeclaredTypes.simpleType(v.getType())))
                    .flatMap(VariableDeclarator::getInitializer).map(ValueAst::strip).filter(Expression::isStringLiteralExpr).isPresent();
            if (!arg.isStringLiteralExpr() && !constant) {
                continue;
            }
            Optional<Expression> scope = m.getScope().map(ValueAst::strip);
            if (scope.filter(s -> !s.isStringLiteralExpr() && !s.isSuperExpr() && !s.isClassExpr()).filter(s -> stringLike(m, s)).isPresent()) {
                add(out, EQUALS_FIRST, m);
            }
        }
    }

    /** equalsIgnoreCase 는 String 만 받는다 — 선언 형식이 String 이거나 모를 때만(뒤집어도 컴파일되게, 마무리 리뷰) */
    private static boolean stringLike(MethodCallExpr m, Expression scope) {
        String t = DeclaredTypes.declaredType(scope);
        return EQUALS.equals(m.getNameAsString()) || t.isEmpty() || STRING_TYPE.equals(t);
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
            return FLOATING.contains(DeclaredTypes.simpleType(c.getType()));
        }
        if (a instanceof MethodCallExpr m) {
            return TO_DOUBLE.contains(m.getNameAsString());
        }
        return FLOATING.contains(DeclaredTypes.declaredType(a));
    }

    /** 설계 50 — 첫 인자가 float(f 리터럴·형 변환·선언·변환 호출). 코드 정리가 valueOf 를 건너뛴다(double 로 넓혀 값이 바뀐다) */
    static boolean isFloat(ObjectCreationExpr o) {
        if (o.getArguments().isEmpty()) {
            return false;
        }
        Expression a = strip(o.getArgument(0));
        while (a instanceof UnaryExpr u) {
            a = strip(u.getExpression());
        }
        if (a instanceof DoubleLiteralExpr d) {
            return d.getValue().endsWith("f") || d.getValue().endsWith("F");
        }
        if (a instanceof CastExpr c) {
            return FLOATS.contains(DeclaredTypes.simpleType(c.getType()));
        }
        if (a instanceof MethodCallExpr m) {
            return TO_FLOAT.contains(m.getNameAsString());
        }
        return FLOATS.contains(DeclaredTypes.declaredType(a));
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
        if (c instanceof MethodCallExpr m && m.getScope().map(ValueAst::lastName).filter(STRING_TYPE::equals).isPresent()) {
            return false; // 5-61c — String.format·String.valueOf 는 날짜 자리가 아니다
        }
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
        return staticFinal(cu, x).filter(v -> STRING_TYPE.equals(DeclaredTypes.simpleType(v.getType())))
                .flatMap(VariableDeclarator::getInitializer).map(ValueAst::strip)
                .filter(e -> e instanceof StringLiteralExpr s && patternLike(s.getValue())).map(e -> (StringLiteralExpr) e);
    }

    // ---------------------------------------------------------------- 도우미

    private static boolean same(Node a, Node b) {
        return a.getRange().equals(b.getRange());
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
