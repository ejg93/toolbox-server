package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
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
import com.github.javaparser.ast.expr.ObjectCreationExpr;
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
    /** 5-61a — DOM setExpandEntityReferences(false) · jdom setExpandEntities(false) */
    private static final Set<String> EXPANDS = Set.of("setExpandEntityReferences", "setExpandEntities");
    /** 5-61a — new 로 만드는 파서(dom4j SAXReader · jdom SAXBuilder) */
    private static final Set<String> PARSERS = Set.of("SAXReader", "SAXBuilder");
    private static final Set<String> TRANSFORMERS = Set.of("newTransformer", "newTransformerHandler");
    private static final Set<String> STYLESHEETS = Set.of("newTemplates", "newXMLFilter", "getAssociatedStylesheet");
    private static final String TRANSFORM = "transform";
    private static final String DOM_SOURCE = "DOMSource";
    private static final String TRANSFORMER_FACTORY = "TransformerFactory";
    private static final String BUILDER = "newDocumentBuilder";
    private static final String NEW_DOCUMENT = "newDocument";
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
    private static final String VERIFIER_TYPE = "HostnameVerifier";
    private static final String BOXED = "Boolean";
    private static final String BOXED_TRUE = "TRUE";
    private static final String BOXED_FALSE = "FALSE";
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
        out.sort(Comparator.comparingInt((Taint.Hit h) -> line(h.node())).thenComparing(Taint.Hit::rule));
        return out;
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(0);
    }

    // ---------------------------------------------------------------- XXE

    private static void xmlFactories(CompilationUnit cu, List<Taint.Hit> out) {
        for (MethodCallExpr c : cu.findAll(MethodCallExpr.class)) {
            if (anchor(c) && !emptyDocument(c) && !guarded(c, cu)) {
                out.add(new Taint.Hit(XXE, c));
            }
        }
        for (ObjectCreationExpr o : cu.findAll(ObjectCreationExpr.class)) { // 5-61a — dom4j SAXReader · jdom SAXBuilder
            if (PARSERS.contains(o.getType().getNameAsString()) && !guarded(o, cu)) {
                out.add(new Taint.Hit(XXE, o));
            }
        }
    }

    /** 팩토리 일곱의 생성 호출 — scope 의 마지막 이름(FQN 도) */
    private static boolean anchor(MethodCallExpr c) {
        return CREATORS.contains(c.getNameAsString()) && c.getScope().map(SecurityAst::lastName).filter(FACTORIES::contains).isPresent();
    }

    /** R-13 — {@code newInstance().newDocumentBuilder().newDocument()} 사슬은 빈 문서만 만든다(파싱 없음) */
    private static boolean emptyDocument(MethodCallExpr anchor) {
        return anchor.getParentNode().filter(p -> p instanceof MethodCallExpr b && BUILDER.equals(b.getNameAsString()))
                .flatMap(Node::getParentNode).filter(p -> p instanceof MethodCallExpr d && NEW_DOCUMENT.equals(d.getNameAsString()))
                .isPresent();
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

    /**
     * 5-61a — 변수가 있으면 그 변수의 막음(같은 몸 또는 한 홉)만, 필드(지역 선언 없음)면 클래스 전체에서(static 블록·메서드).
     * 변수 없는 사슬은 몸 안 아무 막음(설계 48 그대로). 메모리 DOM 만 쓰는 변환기는 막음으로 친다
     */
    private static boolean guarded(Expression anchor, CompilationUnit cu) {
        Node body = anchor.findAncestor(BodyDeclaration.class).map(Node.class::cast).orElse(null);
        if (body == null) {
            return true;
        }
        String v = varName(anchor);
        if (v == null) {
            return anyGuard(body);
        }
        boolean local = declaresLocal(body, v)
                || body instanceof CallableDeclaration<?> c && c.getParameterByName(v).isPresent(); // 5-64 ③ — 매개변수도 메서드 범위
        if (body instanceof FieldDeclaration || !local) {
            body = anchor.findAncestor(TypeDeclaration.class).map(Node.class::cast).orElse(body);
        }
        return guardsOn(body, v) || hopGuarded(body, v, cu) || domOnly(body, anchor);
    }

    /** 몸 안에서 scope 의 마지막 이름이 v 이거나, v 에서 이은 객체(선언 초기화식 사슬의 뿌리가 v)인 막음 호출 — 마무리 리뷰(OWASP getXMLReader 꼴) */
    private static boolean guardsOn(Node body, String v) {
        return body.findAll(MethodCallExpr.class).stream().filter(SecurityAst::guards)
                .map(m -> m.getScope().map(SecurityAst::lastName).orElse(""))
                .anyMatch(sc -> sc.equals(v) || derivedFrom(body, sc, v));
    }

    /** 변수 sc 의 선언 초기화식이 v 에서 시작하는 호출 사슬({@code v.newSAXParser().getXMLReader()}) */
    private static boolean derivedFrom(Node body, String sc, String v) {
        for (VariableDeclarator d : body.findAll(VariableDeclarator.class)) {
            Expression x = d.getNameAsString().equals(sc) ? d.getInitializer().orElse(null) : null;
            while (x instanceof MethodCallExpr m && m.getScope().isPresent()) {
                x = m.getScope().get();
            }
            if (x != null && !sc.isEmpty() && v.equals(lastName(x))) {
                return true;
            }
        }
        return false;
    }

    private static boolean declaresLocal(Node body, String v) {
        return body.findAll(VariableDeclarator.class).stream().anyMatch(d -> d.getNameAsString().equals(v));
    }

    /** 5-61a — 변환기가 메모리 DOM 만 쓴다: newTransformer()·newTransformerHandler() 무인자 · 스타일시트 호출 0 · transform 첫 인자 전부 DOMSource(new 또는 선언 형식) */
    private static boolean domOnly(Node body, Expression anchor) {
        if (!(anchor instanceof MethodCallExpr a) || !a.getScope().map(SecurityAst::lastName).filter(s -> s.endsWith(TRANSFORMER_FACTORY)).isPresent()) {
            return false;
        }
        List<MethodCallExpr> calls = body.findAll(MethodCallExpr.class);
        boolean noArgs = calls.stream().filter(m -> TRANSFORMERS.contains(m.getNameAsString())).allMatch(m -> m.getArguments().isEmpty());
        boolean noSheet = calls.stream().noneMatch(m -> STYLESHEETS.contains(m.getNameAsString()));
        List<MethodCallExpr> transforms = calls.stream().filter(m -> TRANSFORM.equals(m.getNameAsString()) && m.getArguments().isNonEmpty()).toList();
        return noArgs && noSheet && !transforms.isEmpty() && transforms.stream().allMatch(m -> domSource(m.getArgument(0)));
    }

    private static boolean domSource(Expression e) {
        return e instanceof ObjectCreationExpr o && DOM_SOURCE.equals(o.getType().getNameAsString()) || DOM_SOURCE.equals(DeclaredTypes.declaredType(e));
    }

    private static boolean anyGuard(Node body) {
        return body.findAll(MethodCallExpr.class).stream().anyMatch(SecurityAst::guards);
    }

    /** DTD·외부 개체를 막는 설정 호출인가 — 값이 변수면 모르니 막음으로 친다 */
    static boolean guards(MethodCallExpr c) {
        String n = c.getNameAsString();
        if (EXPANDS.contains(n)) {
            return c.getArguments().size() == ONE && literal(c.getArgument(0), false);
        }
        if (!SETTERS.contains(n) || c.getArguments().size() < PAIR) {
            return false;
        }
        String key = keyText(c.getArgument(0)).toUpperCase(Locale.ROOT);
        Expression value = c.getArgument(1);
        if (has(KEYS_ANY, key)) {
            return !(value instanceof StringLiteralExpr s && !s.getValue().isEmpty()); // 마무리 리뷰 — "all"·"file" 은 허용 목록
        }
        if (has(KEYS_ON, key)) {
            return !literal(value, false);
        }
        return has(KEYS_OFF, key) && !literal(value, true);
    }

    private static boolean has(List<String> keys, String key) {
        return keys.stream().anyMatch(key::contains);
    }

    /** true·false 리터럴과 Boolean.TRUE·Boolean.FALSE(마무리 리뷰) */
    private static boolean literal(Expression e, boolean value) {
        if (e instanceof BooleanLiteralExpr b) {
            return b.getValue() == value;
        }
        return e instanceof FieldAccessExpr f && BOXED.equals(f.getScope().toString())
                && (value ? BOXED_TRUE : BOXED_FALSE).equals(f.getNameAsString());
    }

    private static String keyText(Expression e) {
        if (e instanceof StringLiteralExpr s) {
            return s.getValue();
        }
        String n = lastName(e);
        return n.isEmpty() ? e.toString() : n;
    }

    /** 앵커를 받은 변수 이름(형 변환·괄호를 지나) — 선언 초기값이나 대입 값이 아니면 null */
    private static String varName(Expression anchor) {
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
        return c.getArguments().stream().anyMatch(a -> v.equals(lastName(a))); // this.f 도(마무리 리뷰)
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
            if (l.getParameters().size() == PAIR && lambdaTrue(l) && verifierSlot(l)) {
                out.add(new Taint.Hit(TRUST_ALL, l));
            }
        }
    }

    /** 호스트 검증 자리 — 이름에 HostnameVerifier 가 든 호출의 인자 · 그 타입으로 형 변환 · 그 타입 변수(마무리 리뷰) */
    private static boolean verifierSlot(LambdaExpr l) {
        Node p = l.getParentNode().orElse(null);
        if (p instanceof CastExpr c) {
            return c.getType().asString().endsWith(VERIFIER_TYPE);
        }
        if (p instanceof VariableDeclarator d) {
            return d.getType().asString().endsWith(VERIFIER_TYPE);
        }
        return p instanceof MethodCallExpr m && m.getNameAsString().toLowerCase(Locale.ROOT).contains(VERIFIER);
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
