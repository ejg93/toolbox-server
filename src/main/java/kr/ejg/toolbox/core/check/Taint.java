package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 오염 추적(설계 42 §3-2~5) — 메서드 안, 흐름 무관, 고정점. 요청 값(출처)이 지역 변수를 거쳐 싱크 인자에 닿으면 지적.
 * 분기·순서를 안 본다 — 한 가지에서라도 오염되면 오염. 람다 몸은 안 걷는다. {@link JavaRules} 가 파일마다 한 번 부른다
 */
final class Taint {

    record Hit(String rule, Node node) {
    }

    /** 싱크 — 호출·생성자 이름, 보는 인자(-1 = 어느 것이든), 스코프 이름 조건(null = 없음) */
    record Sink(Set<String> names, int arg, Pattern scope) {
    }

    static final Set<String> SOURCES = Set.of("getParameter", "getParameterValues", "getParameterMap", "getHeader", "getHeaders",
            "getHeaderNames", "getCookies", "getQueryString", "getRequestURI", "getRequestURL", "getPathInfo", "getServletPath",
            "getInputStream", "getReader", "getPart", "getParts", "getOriginalFilename", "getRemoteUser", "getRequestedSessionId");
    static final Set<String> BINDING = Set.of("RequestParam", "PathVariable", "RequestHeader", "CookieValue", "RequestBody",
            "ModelAttribute", "RequestPart", "MatrixVariable");
    /** 컨트롤러 처리 메서드 매개변수 중 요청 값이 아닌 타입(단순 이름) */
    static final Set<String> NOT_INPUT = Set.of("HttpServletRequest", "HttpServletResponse", "HttpSession", "Model", "ModelMap",
            "ModelAndView", "BindingResult", "Errors", "Principal", "Authentication", "Locale", "RedirectAttributes", "SessionStatus",
            "WebRequest", "NativeWebRequest");
    static final Set<String> SCOPE_CLEAN = Set.of("equals", "equalsIgnoreCase", "length", "size", "isEmpty", "isBlank", "contains",
            "containsKey", "startsWith", "endsWith", "indexOf", "lastIndexOf", "matches", "hashCode", "compareTo", "compareToIgnoreCase",
            "getClass");
    /** 오염된 키로 깨끗한 맵·목록에서 꺼낸 값은 깨끗(허용목록 꼴) */
    static final Set<String> ARG_CLEAN = Set.of("get", "getOrDefault", "containsKey", "contains", "indexOf", "equals", "matches",
            "startsWith", "endsWith");
    static final Set<String> SANITIZER = Set.of("parseInt", "parseLong", "parseDouble", "parseFloat", "parseBoolean", "parseShort",
            "parseByte", "encodeForHTML", "encodeForHTMLAttribute", "encodeForJavaScript", "encodeForSQL", "encodeForLDAP",
            "encodeForDN", "encodeForXPath", "encodeForXML", "encodeForURL", "encodeForOS", "htmlEscape", "htmlEscapeHex", "escapeHtml",
            "escapeHtml4", "escapeXml", "escapeXml10", "escapeXml11", "escapeJava", "escapeEcmaScript", "escapeSql", "cleanXSS",
            "forHtml", "forHtmlAttribute", "forJavaScript", "forUri", "forUriComponent", "getCanonicalPath", "normalize");
    /** 스코프 변수를 오염시키는 변경자 — sb.append(오염) 뒤 sb 는 오염 */
    static final Set<String> MUTATORS = Set.of("append", "insert", "add", "addAll", "put", "putAll", "push", "offer", "set");
    static final String WRITER = "writer";
    static final Map<String, List<Sink>> SINKS = new LinkedHashMap<>();

    static {
        SINKS.put("security.osCommand", List.of(sink(-1, null, "exec", "ProcessBuilder", "command")));
        SINKS.put("security.pathTraversal", List.of(sink(-1, null, "File", "FileInputStream", "FileOutputStream", "FileReader",
                "FileWriter", "RandomAccessFile", "resolve", "transferTo"),
                sink(-1, "^(java\\.nio\\.file\\.)?Paths?$", "get", "of")));
        SINKS.put("security.openRedirect", List.of(sink(-1, null, "sendRedirect", "RedirectView")));
        SINKS.put("security.responseSplitting", List.of(sink(-1, null, "setHeader", "addHeader", "Cookie")));
        SINKS.put("security.ssrf", List.of(sink(0, null, "URL", "URI", "create", "getForObject", "getForEntity", "postForObject",
                "postForEntity", "exchange", "HttpGet", "HttpPost", "HttpPut", "uri"),
                sink(0, "(?i)(rest|template|client)", "execute")));
        SINKS.put("security.sqlInjection", List.of(sink(0, null, "executeQuery", "executeUpdate", "executeLargeUpdate", "addBatch",
                "prepareStatement", "prepareCall", "createQuery", "createNativeQuery", "createSQLQuery", "createNamedQuery",
                "queryForList", "queryForObject", "queryForMap", "queryForRowSet", "queryForStream", "batchUpdate"),
                sink(0, "(?i)(jdbc|template|statement|stmt|conn|connection|session|em|entitymanager)", "execute", "update", "query")));
        SINKS.put("security.xssServlet", List.of(sink(-1, WRITER, "print", "println", "printf", "format", "write", "append"),
                sink(1, null, "sendError")));
        SINKS.put("security.ldapInjection", List.of(sink(-1, "(?i)(ctx|context|ldap|dir)", "search"),
                sink(-1, null, "LdapName", "InitialDirContext")));
        SINKS.put("security.xpathInjection", List.of(sink(0, "(?i)xpath", "compile", "evaluate"),
                sink(0, null, "selectNodes", "selectSingleNode", "selectObject")));
        SINKS.put("security.trustBoundary", List.of(sink(-1, "(?i)session", "setAttribute", "putValue")));
    }

    private static final Pattern WRITER_NAME = Pattern.compile("(?i)^(out|writer|pw|printWriter|os|outputStream)$");
    private static final int ROUNDS = 10;

    private final Set<String> on;

    Taint(Set<String> rules) {
        this.on = rules;
    }

    private static Sink sink(int arg, String scope, String... names) {
        return new Sink(Set.of(names), arg, scope == null ? null : Pattern.compile(WRITER.equals(scope) ? "^$" : scope));
    }

    /** 켜진 규칙 중 오염 싱크가 있는 것이 하나라도 있나 */
    static boolean wants(Set<String> rules) {
        return rules.stream().anyMatch(SINKS::containsKey) || rules.contains("security.cookieSecureFlag");
    }

    List<Hit> scan(CompilationUnit cu) {
        List<Hit> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CallableDeclaration<?> m : cu.findAll(CallableDeclaration.class)) {
            for (Hit h : method(m)) {
                int line = h.node().getBegin().map(p -> p.line).orElse(1);
                if (seen.add(h.rule() + "|" + line)) {
                    out.add(h);
                }
            }
        }
        return out;
    }

    private List<Hit> method(CallableDeclaration<?> m) {
        Set<String> t = new HashSet<>();
        Set<String> writers = new HashSet<>();
        boolean controller = controller(m);
        for (Parameter p : m.getParameters()) {
            if (p.getAnnotations().stream().anyMatch(a -> BINDING.contains(a.getName().getIdentifier()))
                    || controller && !NOT_INPUT.contains(simple(p.getType().asString()))) {
                t.add(p.getNameAsString());
            }
        }
        grow(m, t, writers);
        List<Hit> out = new ArrayList<>();
        for (MethodCallExpr c : outsideLambda(m, MethodCallExpr.class)) {
            call(c.getNameAsString(), c.getScope(), c.getArguments(), c, t, writers, out);
            cookieFlag(c, out);
        }
        for (ObjectCreationExpr o : outsideLambda(m, ObjectCreationExpr.class)) {
            call(o.getType().getNameAsString(), Optional.empty(), o.getArguments(), o, t, writers, out);
        }
        if (on.contains("security.openRedirect")) {
            for (ReturnStmt r : outsideLambda(m, ReturnStmt.class)) {
                r.getExpression().filter(e -> redirectConcat(e, t)).ifPresent(e -> out.add(new Hit("security.openRedirect", r)));
            }
        }
        return out;
    }

    /** 고정점 — 대입·선언·for-each·변경자 호출로 오염 집합과 writer 변수를 키운다 */
    private static void grow(CallableDeclaration<?> m, Set<String> t, Set<String> writers) {
        List<VariableDeclarator> decls = outsideLambda(m, VariableDeclarator.class);
        List<AssignExpr> assigns = outsideLambda(m, AssignExpr.class);
        List<ForEachStmt> loops = outsideLambda(m, ForEachStmt.class);
        List<MethodCallExpr> calls = outsideLambda(m, MethodCallExpr.class);
        for (int i = 0; i < ROUNDS; i++) {
            int before = t.size() + writers.size();
            for (VariableDeclarator d : decls) {
                d.getInitializer().ifPresent(e -> flow(d.getNameAsString(), e, t, writers));
            }
            for (AssignExpr a : assigns) {
                if (a.getTarget().isNameExpr()) {
                    flow(a.getTarget().asNameExpr().getNameAsString(), a.getValue(), t, writers);
                }
            }
            for (ForEachStmt f : loops) {
                if (tainted(f.getIterable(), t)) {
                    f.getVariable().getVariables().forEach(v -> t.add(v.getNameAsString()));
                }
            }
            for (MethodCallExpr c : calls) {
                if (MUTATORS.contains(c.getNameAsString()) && c.getScope().filter(Expression::isNameExpr).isPresent()
                        && c.getArguments().stream().anyMatch(a -> tainted(a, t))) {
                    t.add(c.getScope().get().asNameExpr().getNameAsString());
                }
            }
            if (t.size() + writers.size() == before) {
                return;
            }
        }
    }

    private static void flow(String name, Expression value, Set<String> t, Set<String> writers) {
        if (tainted(value, t)) {
            t.add(name);
        }
        if (writerCall(value)) {
            writers.add(name);
        }
    }

    private void call(String name, Optional<Expression> scope, List<Expression> args, Node at, Set<String> t, Set<String> writers,
            List<Hit> out) {
        for (Map.Entry<String, List<Sink>> e : SINKS.entrySet()) {
            if (!on.contains(e.getKey())) {
                continue;
            }
            for (Sink s : e.getValue()) {
                if (s.names().contains(name) && scopeOk(s, scope, writers) && argTainted(s.arg(), args, t)) {
                    out.add(new Hit(e.getKey(), at));
                }
            }
        }
    }

    private static boolean scopeOk(Sink s, Optional<Expression> scope, Set<String> writers) {
        if (s.scope() == null) {
            return true;
        }
        if ("^$".equals(s.scope().pattern())) {
            return scope.map(x -> writerCall(x) || x.isNameExpr() && (writers.contains(x.asNameExpr().getNameAsString())
                    || WRITER_NAME.matcher(x.asNameExpr().getNameAsString()).matches())).orElse(false);
        }
        return scope.map(x -> s.scope().matcher(x.toString()).find()).orElse(false);
    }

    private static boolean argTainted(int arg, List<Expression> args, Set<String> t) {
        if (arg < 0) {
            return args.stream().anyMatch(a -> tainted(a, t));
        }
        return arg < args.size() && tainted(args.get(arg), t);
    }

    /** setSecure(false)·setHttpOnly(false) — 오염과 무관 */
    private void cookieFlag(MethodCallExpr c, List<Hit> out) {
        if (on.contains("security.cookieSecureFlag") && ("setSecure".equals(c.getNameAsString()) || "setHttpOnly".equals(c.getNameAsString()))
                && c.getArguments().size() == 1 && c.getArgument(0).isBooleanLiteralExpr()
                && !c.getArgument(0).asBooleanLiteralExpr().getValue()) {
            out.add(new Hit("security.cookieSecureFlag", c));
        }
    }

    /** "redirect:" + 오염 — 왼쪽 끝이 redirect: 로 시작하는 글자 */
    static boolean redirectConcat(Expression e, Set<String> t) {
        if (!e.isBinaryExpr() || e.asBinaryExpr().getOperator() != BinaryExpr.Operator.PLUS) {
            return false;
        }
        Expression left = e;
        while (left.isBinaryExpr()) {
            left = left.asBinaryExpr().getLeft();
        }
        return left.isStringLiteralExpr() && left.asStringLiteralExpr().getValue().startsWith("redirect:") && tainted(e, t);
    }

    static boolean writerCall(Expression e) {
        return e.isMethodCallExpr() && ("getWriter".equals(e.asMethodCallExpr().getNameAsString())
                || "getOutputStream".equals(e.asMethodCallExpr().getNameAsString()));
    }

    /** §3-4 전파 — 노드 종류마다 나눈다 */
    static boolean tainted(Expression e, Set<String> t) {
        if (e.isNameExpr()) {
            return t.contains(e.asNameExpr().getNameAsString());
        }
        if (e.isMethodCallExpr()) {
            return taintedCall(e.asMethodCallExpr(), t);
        }
        if (e.isBinaryExpr()) {
            BinaryExpr b = e.asBinaryExpr();
            return b.getOperator() == BinaryExpr.Operator.PLUS && (tainted(b.getLeft(), t) || tainted(b.getRight(), t));
        }
        return taintedWrapped(e, t);
    }

    private static boolean taintedWrapped(Expression e, Set<String> t) {
        if (e instanceof ConditionalExpr c) {
            return tainted(c.getThenExpr(), t) || tainted(c.getElseExpr(), t);
        }
        if (e instanceof EnclosedExpr x) {
            return tainted(x.getInner(), t);
        }
        if (e instanceof CastExpr x) {
            return tainted(x.getExpression(), t);
        }
        if (e instanceof ObjectCreationExpr o) {
            return o.getArguments().stream().anyMatch(a -> tainted(a, t));
        }
        if (e instanceof ArrayCreationExpr a) {
            return a.getInitializer().map(i -> tainted(i, t)).orElse(false);
        }
        if (e instanceof ArrayInitializerExpr a) {
            return a.getValues().stream().anyMatch(v -> tainted(v, t));
        }
        if (e instanceof ArrayAccessExpr a) {
            return tainted(a.getName(), t);
        }
        if (e instanceof FieldAccessExpr f) {
            return tainted(f.getScope(), t);
        }
        return false;
    }

    private static boolean taintedCall(MethodCallExpr c, Set<String> t) {
        String n = c.getNameAsString();
        if (SOURCES.contains(n)) {
            return true;
        }
        if (SANITIZER.contains(n)) {
            return false;
        }
        boolean scope = c.getScope().map(s -> tainted(s, t)).orElse(false);
        if (scope && !SCOPE_CLEAN.contains(n)) {
            return true;
        }
        return !ARG_CLEAN.contains(n) && c.getArguments().stream().anyMatch(a -> tainted(a, t));
    }

    private static boolean controller(CallableDeclaration<?> m) {
        boolean mapping = m.getAnnotations().stream().anyMatch(a -> a.getName().getIdentifier().endsWith("Mapping"));
        boolean inController = m.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(c -> c.getAnnotations().stream().anyMatch(a -> Set.of("Controller", "RestController").contains(a.getName().getIdentifier())))
                .orElse(false);
        return mapping || inController && m.isMethodDeclaration() && m.isPublic();
    }

    private static String simple(String type) {
        String s = type.replaceAll("<.*>", "");
        return s.substring(s.lastIndexOf('.') + 1);
    }

    /** 람다 몸 밖 노드만 */
    private static <T extends Node> List<T> outsideLambda(CallableDeclaration<?> m, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (T n : m.findAll(type)) {
            if (n.findAncestor(LambdaExpr.class).filter(l -> l.isDescendantOf(m)).isEmpty()) {
                out.add(n);
            }
        }
        return out;
    }
}
