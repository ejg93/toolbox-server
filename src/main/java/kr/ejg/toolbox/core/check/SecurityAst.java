package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 설계 48 — 파서·TLS 설정 결함. JavaParser AST 만 본다(타입 해석 없음 — 이름은 글자로).
 * <ul>
 *   <li>{@code security.xxe} — XML 팩토리를 만든 자리({@code newInstance} 류)의 메서드·초기화 블록에 DTD·외부 개체 막음이 없고,
 *   팩토리 변수를 넘긴 같은 파일 메서드(한 홉)에도 없다</li>
 *   <li>{@code security.trustAll} — 몸이 빈 {@code checkServerTrusted}·{@code checkClientTrusted} · {@code return true} 인
 *   {@code verify(…, SSLSession)} · {@code (h, s) -> true} 호스트 검증 람다 · 모두 허용 이름</li>
 * </ul>
 */
final class SecurityAst {

    static final String XXE = "security.xxe";
    static final String TRUST_ALL = "security.trustAll";
    private static final Set<String> CREATORS = Set.of("newInstance", "newFactory", "newDefaultInstance", "createXMLReader");
    private static final Set<String> FACTORIES = Set.of("DocumentBuilderFactory", "SAXParserFactory", "XMLInputFactory",
            "TransformerFactory", "SAXTransformerFactory", "SchemaFactory", "XMLReaderFactory");
    private static final Set<String> SETTERS = Set.of("setFeature", "setAttribute", "setProperty");
    private static final String EXPAND = "setExpandEntityReferences";
    /** 참으로 켜야 막는 키(값이 false 리터럴이면 아님) */
    private static final List<String> KEYS_ON = List.of("DISALLOW-DOCTYPE-DECL", "SECURE_PROCESSING", "SECURE-PROCESSING");
    /** 거짓으로 꺼야 막는 키(값이 true 리터럴이면 아님) — 상수 이름과 글자 키 둘 다 */
    private static final List<String> KEYS_OFF = List.of("EXTERNAL-GENERAL-ENTITIES", "EXTERNAL-PARAMETER-ENTITIES", "LOAD-EXTERNAL-DTD",
            "EXTERNAL_GENERAL_ENTITIES", "EXTERNAL_PARAMETER_ENTITIES", "LOAD_EXTERNAL_DTD", "IS_SUPPORTING_EXTERNAL_ENTITIES",
            "ISSUPPORTINGEXTERNALENTITIES", "SUPPORT_DTD", "SUPPORTDTD");
    /** 값을 안 가리는 키 — ACCESS_EXTERNAL_DTD·_SCHEMA·_STYLESHEET(상수)와 accessExternal…(글자) */
    private static final List<String> KEYS_ANY = List.of("ACCESS_EXTERNAL_", "ACCESSEXTERNAL");
    private static final Set<String> CHECKS = Set.of("checkServerTrusted", "checkClientTrusted");
    private static final Set<String> TRUST_NAMES = Set.of("ALLOW_ALL_HOSTNAME_VERIFIER", "NoopHostnameVerifier", "TrustAllStrategy");
    private static final String VERIFY = "verify";
    private static final String SESSION = "SSLSession";
    private static final String VERIFIER = "hostnameverifier";
    private static final int PAIR = 2;
    private static final int ONE = 1;

    private SecurityAst() {
    }

    /** 켜진 규칙(on)의 판정만 — 줄 순서 */
    static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on) {
        List<Taint.Hit> out = new ArrayList<>();
        if (on.contains(XXE)) {
            xmlFactories(cu, out);
        }
        if (on.contains(TRUST_ALL)) {
            trustMethods(cu, out);
            trustLambdas(cu, out);
            trustNames(cu, out);
        }
        out.sort(Comparator.comparingInt(h -> line(h.node())));
        return out;
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(0);
    }

    // ---------------------------------------------------------------- XXE

    private static void xmlFactories(CompilationUnit cu, List<Taint.Hit> out) {
        for (MethodCallExpr c : cu.findAll(MethodCallExpr.class)) {
            if (anchor(c) && !guarded(c, cu)) {
                out.add(new Taint.Hit(XXE, c));
            }
        }
    }

    /** 팩토리 일곱의 생성 호출 — scope 의 마지막 이름(FQN 도) */
    private static boolean anchor(MethodCallExpr c) {
        return CREATORS.contains(c.getNameAsString()) && c.getScope().map(SecurityAst::lastName).filter(FACTORIES::contains).isPresent();
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

    /** 앵커를 둔 메서드·생성자·초기화 블록·필드 안에 막음이 있거나, 팩토리 변수를 넘긴 같은 파일 메서드에 막음이 있나 */
    private static boolean guarded(MethodCallExpr anchor, CompilationUnit cu) {
        Node body = anchor.findAncestor(BodyDeclaration.class).map(Node.class::cast).orElse(null);
        if (body == null || anyGuard(body)) {
            return true;
        }
        String v = varName(anchor);
        return v != null && hopGuarded(body, v, cu);
    }

    private static boolean anyGuard(Node body) {
        return body.findAll(MethodCallExpr.class).stream().anyMatch(SecurityAst::guards);
    }

    /** DTD·외부 개체를 막는 설정 호출인가 — 값이 변수면 모르니 막음으로 친다 */
    static boolean guards(MethodCallExpr c) {
        String n = c.getNameAsString();
        if (EXPAND.equals(n)) {
            return c.getArguments().size() == ONE && literal(c.getArgument(0), false);
        }
        if (!SETTERS.contains(n) || c.getArguments().size() < PAIR) {
            return false;
        }
        String key = keyText(c.getArgument(0)).toUpperCase(Locale.ROOT);
        Expression value = c.getArgument(1);
        if (has(KEYS_ANY, key)) {
            return true;
        }
        if (has(KEYS_ON, key)) {
            return !literal(value, false);
        }
        return has(KEYS_OFF, key) && !literal(value, true);
    }

    private static boolean has(List<String> keys, String key) {
        return keys.stream().anyMatch(key::contains);
    }

    private static boolean literal(Expression e, boolean value) {
        return e instanceof BooleanLiteralExpr b && b.getValue() == value;
    }

    private static String keyText(Expression e) {
        if (e instanceof StringLiteralExpr s) {
            return s.getValue();
        }
        String n = lastName(e);
        return n.isEmpty() ? e.toString() : n;
    }

    /** 앵커를 받은 변수 이름(형 변환·괄호를 지나) — 선언 초기값이나 대입 값이 아니면 null */
    private static String varName(MethodCallExpr anchor) {
        Node n = anchor;
        Node p = n.getParentNode().orElse(null);
        while (p instanceof CastExpr || p instanceof EnclosedExpr) {
            n = p;
            p = p.getParentNode().orElse(null);
        }
        if (p instanceof VariableDeclarator d) {
            return d.getNameAsString();
        }
        if (p instanceof AssignExpr a && a.getValue().equals(n)) {
            String t = lastName(a.getTarget());
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    /** 한 홉(사용자 2026-11-01) — 범위 안에서 변수 v 를 인자로 받는 호출의 같은 파일 메서드(이름·인자 수) 몸에 막음 */
    private static boolean hopGuarded(Node body, String v, CompilationUnit cu) {
        for (MethodCallExpr c : body.findAll(MethodCallExpr.class)) {
            if (passes(c, v) && helperGuards(cu, c)) {
                return true;
            }
        }
        return false;
    }

    private static boolean passes(MethodCallExpr c, String v) {
        return c.getArguments().stream().anyMatch(a -> a instanceof NameExpr n && v.equals(n.getNameAsString()));
    }

    private static boolean helperGuards(CompilationUnit cu, MethodCallExpr c) {
        return cu.findAll(MethodDeclaration.class).stream()
                .anyMatch(m -> m.getNameAsString().equals(c.getNameAsString()) && m.getParameters().size() == c.getArguments().size()
                        && m.getBody().map(SecurityAst::anyGuard).orElse(false));
    }

    // ---------------------------------------------------------------- 인증서·호스트 검증 끔

    private static void trustMethods(CompilationUnit cu, List<Taint.Hit> out) {
        for (MethodDeclaration d : cu.findAll(MethodDeclaration.class)) {
            BlockStmt b = d.getBody().orElse(null);
            if (b != null && (CHECKS.contains(d.getNameAsString()) && trivial(b) || sessionVerify(d) && returnsTrue(b))) {
                out.add(new Taint.Hit(TRUST_ALL, d.getName()));
            }
        }
    }

    /** 문장이 없거나 빈 문장·값 없는 return 뿐 */
    private static boolean trivial(BlockStmt b) {
        return b.getStatements().stream().allMatch(s -> s.isEmptyStmt() || s instanceof ReturnStmt r && r.getExpression().isEmpty());
    }

    private static boolean sessionVerify(MethodDeclaration d) {
        return VERIFY.equals(d.getNameAsString()) && d.getParameters().size() == PAIR
                && d.getParameter(1).getType().asString().endsWith(SESSION);
    }

    private static boolean returnsTrue(BlockStmt b) {
        return b.getStatements().size() == ONE && b.getStatement(0) instanceof ReturnStmt r
                && r.getExpression().map(e -> literal(e, true)).orElse(false);
    }

    private static void trustLambdas(CompilationUnit cu, List<Taint.Hit> out) {
        for (LambdaExpr l : cu.findAll(LambdaExpr.class)) {
            if (l.getParameters().size() == PAIR && lambdaTrue(l) && l.getParentNode()
                    .filter(p -> p instanceof MethodCallExpr m && m.getNameAsString().toLowerCase(Locale.ROOT).contains(VERIFIER)).isPresent()) {
                out.add(new Taint.Hit(TRUST_ALL, l));
            }
        }
    }

    private static boolean lambdaTrue(LambdaExpr l) {
        Statement body = l.getBody();
        if (body instanceof ExpressionStmt es) {
            return literal(es.getExpression(), true);
        }
        return body instanceof BlockStmt b && returnsTrue(b);
    }

    private static void trustNames(CompilationUnit cu, List<Taint.Hit> out) {
        for (SimpleName n : cu.findAll(SimpleName.class)) {
            if (TRUST_NAMES.contains(n.getIdentifier()) && n.findAncestor(ImportDeclaration.class).isEmpty()) {
                out.add(new Taint.Hit(TRUST_ALL, n));
            }
        }
    }
}
