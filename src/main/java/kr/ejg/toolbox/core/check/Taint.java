package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * 오염 추적(설계 42 §3-2~5) — 메서드 안, 흐름 무관, 고정점. 요청 값(출처)이 지역 변수를 거쳐 싱크 인자에 닿으면 지적.
 * 분기·순서를 안 본다 — 한 가지에서라도 오염되면 오염. 람다 몸은 안 걷는다. {@link JavaRules} 가 파일마다 한 번 부른다.
 * 설계 43 — 같은 메서드를 씨앗 집합으로 다시 돌리는 {@link #run} 이 메서드 사이 요약({@link TaintLink})의 재료
 */
final class Taint {

    record Hit(String rule, Node node) {
    }

    /** 싱크 — 호출·생성자 이름, 보는 인자(-1 = 어느 것이든), 스코프 이름 조건(null = 없음) */
    record Sink(Set<String> names, int arg, Pattern scope, Set<String> types) {
    }

    /** 메서드 하나의 노드 목록(람다 밖) — 씨앗마다 다시 돌 때 한 번만 뽑는다(설계 43 §3-1). fields 는 둘러싼 타입의 필드 이름 */
    record Body(CallableDeclaration<?> m, List<VariableDeclarator> decls, List<AssignExpr> assigns, List<ForEachStmt> loops,
            List<MethodCallExpr> calls, List<ObjectCreationExpr> news, List<ReturnStmt> returns, String cls, Set<String> fields,
            Set<String> locals, Map<String, String> types, Set<String> fresh, boolean json) {
    }

    /** 한 번 도는 동안의 상태 — 몸 · 오염 집합 · 응답 writer 이름 */
    private record Run(Body b, Set<String> t, Set<String> writers) {
    }

    /** 한 번 돈 결과 — 싱크 지적과 끝난 오염 집합(요약이 호출 인자·필드 쓰기·반환을 이것으로 판정한다) */
    record Facts(List<Hit> sinks, Set<String> tainted) {
    }

    static final Set<String> SOURCES = Set.of("getParameter", "getParameterValues", "getParameterMap", "getHeader", "getHeaders",
            "getHeaderNames", "getParameterNames", "getCookies", "getQueryString", "getRequestURI", "getRequestURL", "getPathInfo", "getServletPath",
            "getInputStream", "getReader", "getPart", "getParts", "getOriginalFilename", "getRemoteUser", "getRequestedSessionId");
    static final Set<String> BINDING = Set.of("RequestParam", "PathVariable", "RequestHeader", "CookieValue", "RequestBody",
            "ModelAttribute", "RequestPart", "MatrixVariable");
    /** 컨트롤러 처리 메서드 매개변수 중 요청 값이 아닌 타입(단순 이름) */
    static final Set<String> NOT_INPUT = Set.of("HttpServletRequest", "HttpServletResponse", "HttpSession", "Model", "ModelMap",
            "ModelAndView", "BindingResult", "Errors", "Principal", "Authentication", "Locale", "RedirectAttributes", "SessionStatus",
            "WebRequest", "NativeWebRequest");
    static final Set<String> SCOPE_CLEAN = Set.of("equals", "equalsIgnoreCase", "length", "size", "isEmpty", "isBlank", "contains",
            "containsKey", "startsWith", "endsWith", "indexOf", "lastIndexOf", "matches", "hashCode", "compareTo", "compareToIgnoreCase",
            "getClass", "read", "available", "skip");
    /** 오염된 키로 깨끗한 맵·목록에서 꺼낸 값은 깨끗(허용목록 꼴) */
    static final Set<String> ARG_CLEAN = Set.of("get", "getOrDefault", "containsKey", "contains", "indexOf", "equals", "matches",
            "startsWith", "endsWith");
    /** 스코프 변수를 오염시키는 변경자 — sb.append(오염) 뒤 sb 는 오염 */
    static final Set<String> MUTATORS = Set.of("append", "insert", "add", "addAll", "put", "putAll", "push", "offer", "set");
    static final String WRITER = "writer";
    static final String COOKIE = "security.cookieSecureFlag";
    static final String REDIRECT = "security.openRedirect";
    /** 싱크 스코프 「응답 writer」 의 표시 */
    static final String ANY_WRITER = "^$";
    /** 오염 집합 안의 표식 — 있으면 요청 출처(SOURCES)가 오염을 낸다. 씨앗 실행(설계 43 §3-1)은 끈다 */
    static final String SRC_ON = "\u0000src";
    /** 고정점 상한 */
    static final int ROUNDS = 10;
    static final Map<String, List<Sink>> SINKS = new LinkedHashMap<>();

    static {
        // 스코프 조건은 스코프의 끝 단순 이름(변수·필드·호출 이름) 전체에 맞춘다 — 마무리 리뷰: 부분 일치가 itemService 를 em 으로 봤다
        SINKS.put("security.osCommand", List.of(sink(-1, null, "exec", "ProcessBuilder"),
                sink(-1, "(?i)^(pb|.*builder)$", "command")));
        SINKS.put("security.pathTraversal", List.of(sink(-1, null, "File", "FileInputStream", "FileOutputStream", "FileReader",
                "FileWriter", "RandomAccessFile"),
                sink(-1, "^Paths?$", "get", "of"),
                sink(-1, "(?i)^(.*path|.*dir|.*root|.*base|.*folder)$", "resolve"),
                sink(-1, "(?i)^(.*file|.*part|.*upload.*)$", "transferTo")));
        SINKS.put("security.openRedirect", List.of(sink(-1, null, "sendRedirect", "RedirectView")));
        SINKS.put("security.responseSplitting", List.of(sink(-1, null, "setHeader", "addHeader", "Cookie")));
        SINKS.put("security.ssrf", List.of(sink(0, null, "URL", "URI", "getForObject", "getForEntity", "postForObject",
                "postForEntity", "HttpGet", "HttpPost", "HttpPut"),
                sink(0, "^URI$", "create"),
                sink(0, "(?i)^(.*template|.*client)$", "execute", "exchange"),
                sink(0, "(?i)^(newBuilder|.*builder)$", "uri")));
        SINKS.put("security.sqlInjection", List.of(sink(0, null, "executeQuery", "executeUpdate", "executeLargeUpdate", "addBatch",
                "prepareStatement", "prepareCall", "createQuery", "createNativeQuery", "createSQLQuery", "createNamedQuery",
                "queryForList", "queryForObject", "queryForMap", "queryForRowSet", "queryForStream", "batchUpdate"),
                sinkT(0, "(?i)^(.*jdbc.*|.*template|stmt|pstmt|.*statement|conn|.*connection|.*session|em|.*entitymanager)$",
                        Set.of("Statement", "PreparedStatement", "CallableStatement", "Connection", "JdbcTemplate",
                                "NamedParameterJdbcTemplate", "SqlSession", "SqlSessionTemplate", "EntityManager", "Session", "Query",
                                "NativeQuery"),
                        "execute", "update", "query")));
        SINKS.put("security.xssServlet", List.of(sink(-1, WRITER, "print", "println", "printf", "format", "write", "append"),
                sink(1, null, "sendError")));
        SINKS.put("security.ldapInjection", List.of(sinkT(-1, "(?i)^(ctx|.*context|.*ldap.*)$",
                Set.of("DirContext", "InitialDirContext", "LdapContext", "InitialLdapContext", "LdapTemplate"), "search"),
                sink(-1, null, "LdapName", "InitialDirContext")));
        SINKS.put("security.xpathInjection", List.of(sinkT(0, "(?i)^(xp|.*xpath)$", Set.of("XPath"), "compile", "evaluate"),
                sink(0, null, "selectNodes", "selectSingleNode", "selectObject")));
        SINKS.put("security.trustBoundary", List.of(sinkT(-1, "(?i)^(getSession|.*session)$", Set.of("HttpSession"), "setAttribute",
                "putValue")));
        SINKS.put(Errors.RULE, Errors.SINKS); // 설계 45 §3-6 — 예외 출처 실행에서만
    }

    private static final String THIS = "this.";

    private final Map<String, Rule.Def> on;

    /** @param on 켜진 규칙 id → 정의. 상수 판정(secret·algorithm)은 그 규칙의 정규식을 재사용한다 */
    Taint(Map<String, Rule.Def> on) {
        this.on = on;
    }

    private static Sink sink(int arg, String scope, String... names) {
        return sinkT(arg, scope, null, names);
    }

    /** 스코프 조건 — 이름 정규식 또는 스코프 변수의 선언 타입(설계 44 §3-4) */
    private static Sink sinkT(int arg, String scope, Set<String> types, String... names) {
        return new Sink(Set.of(names), arg, scope == null ? null : Pattern.compile(WRITER.equals(scope) ? ANY_WRITER : scope), types);
    }

    /** 이 엔진이 판정하는 규칙 id — 하이브리드는 이것만(모르는 id 의 params.java 는 정규식 규칙으로 남는다) */
    static boolean knows(String id) {
        return SINKS.containsKey(id) || TaintConst.CONST_RULES.contains(id) || COOKIE.equals(id);
    }

    /** 켜진 규칙 중 오염 싱크·상수 판정이 있는 것이 하나라도 있나 */
    static boolean wants(Set<String> rules) {
        return rules.stream().anyMatch(r -> SINKS.containsKey(r) || TaintConst.CONST_RULES.contains(r)) || rules.contains(COOKIE);
    }

    List<Hit> scan(CompilationUnit cu) {
        return scan(cu, null, null, null);
    }

    /**
     * each 가 있으면 메서드마다 (몸, 출처 켠 실행 결과) 를 넘긴다 — TaintLink 의 요약 재료(설계 43 §3-1).
     * later 가 있으면 설정 값 알고리즘 판정을 거기 보류하고, 없으면 빈 색인(기본값)으로 바로 푼다(설계 44 §3-7).
     * needs 가 있으면 못 푼 비밀번호·키 인자를 거기 넘기고, 없으면 버린다(설계 45 §3-3)
     */
    List<Hit> scan(CompilationUnit cu, BiConsumer<Body, Facts> each, List<TaintConst.Pending> later, List<TaintConst.Need> needs) {
        List<Hit> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        TaintConst k = new TaintConst(on);
        Map<String, Set<String>> fields = TaintConst.fieldConstants(cu);
        List<Hit> all = new ArrayList<>(k.fieldSecrets(cu, fields));
        for (CallableDeclaration<?> m : cu.findAll(CallableDeclaration.class)) {
            Body b = body(m);
            Facts f = run(b, entry(b), true);
            all.addAll(f.sinks());
            if (each != null) {
                each.accept(b, f);
            }
            Set<String> ex = on.containsKey(Errors.RULE) ? Errors.seeds(b) : Set.of();
            if (!ex.isEmpty()) { // 예외 출처 — 요청 출처와 섞지 않게 따로 한 번(설계 45 §3-6)
                Set<String> seed = new HashSet<>(ex);
                seed.add(Errors.EXC_ON);
                all.addAll(run(b, seed, false).sinks());
            }
            all.addAll(k.constants(m, fields));
            for (TaintConst.Pending p : k.pending()) { // 메서드마다 — 지적 순서가 줄 순서로 남게
                if (later != null) {
                    later.add(p);
                } else if (k.weak(p, PropertiesIndex.EMPTY)) {
                    all.add(new Hit(p.rule(), p.at()));
                }
            }
            k.pending().clear();
            if (needs != null) {
                needs.addAll(k.needs());
            }
            k.needs().clear();
        }
        all.sort(java.util.Comparator.comparingInt(h -> h.node().getBegin().map(x -> x.line).orElse(1))); // 보류를 푼 지적도 줄 순서로
        for (Hit h : all) {
            int line = h.node().getBegin().map(p -> p.line).orElse(1);
            if (seen.add(h.rule() + "|" + line)) {
                out.add(h);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 오염 추적(§3-3~5)

    static Body body(CallableDeclaration<?> m) {
        Set<String> fields = new HashSet<>();
        String cls = m.findAncestor(TypeDeclaration.class).map(t -> {
            for (Object o : t.getFields()) {
                ((FieldDeclaration) o).getVariables().forEach(v -> fields.add(v.getNameAsString()));
            }
            return t.getNameAsString();
        }).orElse("");
        List<VariableDeclarator> decls = outsideLambda(m, VariableDeclarator.class);
        Set<String> locals = new HashSet<>();
        Map<String, String> types = new HashMap<>();
        Set<String> fresh = new HashSet<>();
        m.getParameters().forEach(p -> {
            locals.add(p.getNameAsString());
            types.put(p.getNameAsString(), type(p.getTypeAsString()));
        });
        for (VariableDeclarator d : decls) {
            locals.add(d.getNameAsString());
            Expression init = d.getInitializer().map(Taint::unwrap).orElse(null);
            boolean made = init != null && init.isObjectCreationExpr();
            String ty = made ? init.asObjectCreationExpr().getType().getNameAsString() : type(d.getTypeAsString());
            if (!"var".equals(ty)) {
                types.put(d.getNameAsString(), ty); // 초기값 new T(…) 면 그 T 가 선언 타입을 이긴다(Juliet 81 「Base b = new Bad()」)
            }
            if (made) {
                fresh.add(d.getNameAsString());
            }
        }
        List<AssignExpr> assigns = outsideLambda(m, AssignExpr.class);
        for (AssignExpr a : assigns) { // 「X out = null; out = new X(…)」 도 new 로 만든 지역(R-9 — jeecg HttpRequest·OfficeHtmlUtil)
            if (a.getTarget().isNameExpr() && unwrap(a.getValue()).isObjectCreationExpr()) {
                fresh.add(a.getTarget().asNameExpr().getNameAsString());
            }
        }
        List<MethodCallExpr> calls = outsideLambda(m, MethodCallExpr.class);
        return new Body(m, decls, assigns, outsideLambda(m, ForEachStmt.class), calls,
                outsideLambda(m, ObjectCreationExpr.class), outsideLambda(m, ReturnStmt.class), cls, fields, locals, types, fresh,
                calls.stream().anyMatch(Writers::jsonType));
    }

    /** 단순 타입 이름 — 패키지·제네릭·배열 표시를 뗀다 */
    static String type(String t) {
        return simple(t).replace("[]", "").replace("...", "");
    }

    static Expression unwrap(Expression e) {
        Expression x = e;
        while (x.isEnclosedExpr() || x.isCastExpr()) {
            x = x.isEnclosedExpr() ? x.asEnclosedExpr().getInner() : x.asCastExpr().getExpression();
        }
        return x;
    }

    /** 처음부터 오염인 매개변수 — 바인딩 애너테이션, 컨트롤러 처리 메서드의 요청 값 타입(§3-3) */
    private static Set<String> entry(Body b) {
        Set<String> t = new HashSet<>();
        boolean controller = controller(b.m());
        for (Parameter p : b.m().getParameters()) {
            if (p.getAnnotations().stream().anyMatch(a -> BINDING.contains(a.getName().getIdentifier()))
                    || controller && !NOT_INPUT.contains(simple(p.getType().asString()))) {
                t.add(p.getNameAsString());
            }
        }
        return t;
    }

    /** 씨앗에서 고정점을 키우고 싱크를 판정한다. sources=false 면 요청 출처를 안 켠다 — 씨앗이 닿는 곳만(설계 43 §3-1·4) */
    Facts run(Body b, Set<String> seed, boolean sources) {
        Set<String> t = new HashSet<>(seed);
        if (sources) {
            t.add(SRC_ON);
        }
        Set<String> writers = new HashSet<>();
        b.types().forEach((n, ty) -> {
            if (Writers.PARAMS.contains(ty)) {
                writers.add(n);
            }
        });
        grow(b, t, writers);
        Run st = new Run(b, t, writers);
        List<Hit> out = new ArrayList<>();
        boolean exc = t.contains(Errors.EXC_ON);
        for (MethodCallExpr c : b.calls()) {
            call(c.getNameAsString(), c.getScope(), c.getArguments(), c, st, out);
            if (sources) {
                cookieFlag(c, out);
            }
            if (exc && Errors.stackToWriter(c, t, writers, b)) {
                out.add(new Hit(Errors.RULE, c));
            }
        }
        for (ObjectCreationExpr o : b.news()) {
            call(o.getType().getNameAsString(), Optional.empty(), o.getArguments(), o, st, out);
            if (!exc) {
                redirectView(o, t, out);
            }
        }
        if (!exc && on.containsKey(REDIRECT)) {
            for (ReturnStmt r : b.returns()) {
                r.getExpression().filter(e -> redirectConcat(e, t)).ifPresent(e -> out.add(new Hit(REDIRECT, r)));
            }
        }
        return new Facts(out, t);
    }

    /** 고정점 — 대입·선언·for-each·변경자 호출로 오염 집합과 writer 변수를 키운다 */
    private static void grow(Body b, Set<String> t, Set<String> writers) {
        for (int i = 0; i < ROUNDS; i++) {
            Set<String> before = new HashSet<>(t); // 범주 표식은 줄 수도 있다(설계 44 §3-2) — 크기가 아니라 내용으로
            int w = writers.size();
            for (VariableDeclarator d : b.decls()) {
                d.getInitializer().ifPresent(e -> flow(d.getNameAsString(), e, t, writers));
            }
            for (AssignExpr a : b.assigns()) {
                String to = target(a.getTarget());
                if (to != null) {
                    flow(to, a.getValue(), t, writers);
                }
            }
            for (ForEachStmt f : b.loops()) {
                if (Sanitizers.reaches(f.getIterable(), t)) {
                    f.getVariable().getVariables().forEach(v -> t.add(v.getNameAsString()));
                }
            }
            for (MethodCallExpr c : b.calls()) {
                if (MUTATORS.contains(c.getNameAsString()) && c.getScope().filter(Expression::isNameExpr).isPresent()
                        && c.getArguments().stream().anyMatch(a -> Sanitizers.reaches(a, t))) {
                    t.add(c.getScope().get().asNameExpr().getNameAsString());
                }
            }
            Sanitizers.alias(b, t);
            if (t.equals(before) && writers.size() == w) {
                return;
            }
        }
    }

    /**
     * 대입 대상 → 오염 집합에 넣을 이름(설계 43 §3-6) — 이름 그대로 · 배열 요소는 배열 · {@code this.f} 는 「this.f」 ·
     * {@code X.f}(대문자 스코프) 는 「X.f」 · 지역 객체의 필드 {@code b.v} 는 그 객체 b. 그 밖은 null
     */
    static String target(Expression e) {
        if (e.isNameExpr()) {
            return e.asNameExpr().getNameAsString();
        }
        if (e.isArrayAccessExpr()) {
            return target(e.asArrayAccessExpr().getName());
        }
        if (!e.isFieldAccessExpr()) {
            return null;
        }
        Expression scope = e.asFieldAccessExpr().getScope();
        if (scope.isThisExpr()) {
            return THIS + e.asFieldAccessExpr().getNameAsString();
        }
        if (scope.isNameExpr()) {
            return typeName(scope) ? scope.asNameExpr().getNameAsString() + "." + e.asFieldAccessExpr().getNameAsString()
                    : scope.asNameExpr().getNameAsString();
        }
        return null;
    }

    private static void flow(String name, Expression value, Set<String> t, Set<String> writers) {
        if (tainted(value, t)) {
            Sanitizers.mark(name, value, t, t.add(name));
        }
        if (Writers.fromResponse(value, writers)) {
            writers.add(name); // new OutputStreamWriter(response.getOutputStream()) — 안쪽이 응답이면 래퍼도(설계 44 §3-3)
        }
    }

    private void call(String name, Optional<Expression> scope, List<Expression> args, Node at, Run r, List<Hit> out) {
        boolean exc = r.t().contains(Errors.EXC_ON); // 예외 출처 실행은 그 규칙 싱크만, 요청 출처 실행은 그 밖만(설계 45 §3-6)
        for (Map.Entry<String, List<Sink>> e : SINKS.entrySet()) {
            if (!on.containsKey(e.getKey()) || exc != Errors.RULE.equals(e.getKey())) {
                continue;
            }
            for (Sink s : e.getValue()) {
                boolean jsonWriter = !exc && r.b().json() && s.scope() != null && ANY_WRITER.equals(s.scope().pattern()); // JSON 응답(§3-3)
                if (!jsonWriter && s.names().contains(name) && scopeOk(s, scope, r) && argTainted(e.getKey(), s.arg(), args, r.t())) {
                    out.add(new Hit(e.getKey(), at));
                }
            }
        }
    }

    private static boolean scopeOk(Sink s, Optional<Expression> scope, Run r) {
        if (s.scope() == null) {
            return true;
        }
        if (ANY_WRITER.equals(s.scope().pattern())) {
            return scope.map(x -> Writers.writerScope(x, r.writers(), r.b())).orElse(false);
        }
        return scope.map(x -> s.scope().matcher(lastName(x)).matches()
                || s.types() != null && x.isNameExpr() && s.types().contains(r.b().types().getOrDefault(x.asNameExpr().getNameAsString(), "")))
                .orElse(false);
    }

    /** 스코프 식의 끝 이름 — a.b.c → c, getSession() → getSession, Paths → Paths */
    static String lastName(Expression x) {
        if (x.isNameExpr()) {
            return x.asNameExpr().getNameAsString();
        }
        if (x.isFieldAccessExpr()) {
            return x.asFieldAccessExpr().getNameAsString();
        }
        return x.isMethodCallExpr() ? x.asMethodCallExpr().getNameAsString() : x.toString();
    }

    /** 그 인자가 오염이고 이 규칙의 범주로 안 씻겼다(설계 44 §3-1) */
    private static boolean argTainted(String rule, int arg, List<Expression> args, Set<String> t) {
        if (arg < 0) {
            return args.stream().anyMatch(a -> dirty(rule, a, t));
        }
        return arg < args.size() && dirty(rule, args.get(arg), t);
    }

    private static boolean dirty(String rule, Expression e, Set<String> t) {
        return tainted(e, t) && !Sanitizers.cleans(Sanitizers.families(e, t), rule);
    }

    /** setSecure(false)·setHttpOnly(false) — 오염과 무관 */
    private void cookieFlag(MethodCallExpr c, List<Hit> out) {
        if (on.containsKey(COOKIE) && ("setSecure".equals(c.getNameAsString()) || "setHttpOnly".equals(c.getNameAsString()))
                && c.getArguments().size() == 1 && c.getArgument(0).isBooleanLiteralExpr()
                && !c.getArgument(0).asBooleanLiteralExpr().getValue()) {
            out.add(new Hit(COOKIE, c));
        }
    }

    /** new ModelAndView("redirect:" + 오염) — 설계 42 §3-5 */
    private void redirectView(ObjectCreationExpr o, Set<String> t, List<Hit> out) {
        if (on.containsKey(REDIRECT) && "ModelAndView".equals(o.getType().getNameAsString()) && !o.getArguments().isEmpty()
                && redirectConcat(o.getArgument(0), t)) {
            out.add(new Hit(REDIRECT, o));
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
        return left.isStringLiteralExpr() && left.asStringLiteralExpr().getValue().startsWith("redirect:") && dirty(REDIRECT, e, t);
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
            return o.getArguments().stream().anyMatch(a -> Sanitizers.reaches(a, t));
        }
        if (e instanceof ArrayCreationExpr a) {
            return a.getInitializer().map(i -> tainted(i, t)).orElse(false);
        }
        if (e instanceof ArrayInitializerExpr a) {
            return a.getValues().stream().anyMatch(v -> Sanitizers.reaches(v, t));
        }
        if (e instanceof ArrayAccessExpr a) {
            return tainted(a.getName(), t);
        }
        if (e instanceof FieldAccessExpr f) {
            return taintedField(f, t);
        }
        return false;
    }

    /** {@code this.f} 는 「this.f」, {@code X.f}(대문자 스코프)는 「X.f」 가 집합에 있으면 — 그 밖은 스코프가 오염이면(설계 43 §3-6) */
    private static boolean taintedField(FieldAccessExpr f, Set<String> t) {
        Expression scope = f.getScope();
        if (scope.isThisExpr()) {
            return t.contains(THIS + f.getNameAsString());
        }
        if (scope.isNameExpr() && typeName(scope) && t.contains(scope.asNameExpr().getNameAsString() + "." + f.getNameAsString())) {
            return true;
        }
        return tainted(scope, t);
    }

    private static boolean taintedCall(MethodCallExpr c, Set<String> t) {
        String n = c.getNameAsString();
        if (SOURCES.contains(n) && t.contains(SRC_ON)) {
            return true;
        }
        if (Sanitizers.family(c).isPresent()) { // 정화 값도 오염 — 범주는 Sanitizers.families 가 본다(설계 44 §3-2)
            return c.getScope().map(s -> tainted(s, t)).orElse(false) || c.getArguments().stream().anyMatch(a -> tainted(a, t));
        }
        boolean scope = c.getScope().map(s -> Sanitizers.reaches(s, t)).orElse(false);
        if (scope && !SCOPE_CLEAN.contains(n)) {
            return true;
        }
        // 오염된 키로 꺼낸 값은 깨끗 — 단 스코프가 타입 이름(Paths.get 등 정적 호출)이면 키가 아니라 값이다
        boolean lookup = ARG_CLEAN.contains(n) && c.getScope().map(x -> !typeName(x)).orElse(false);
        return !lookup && c.getArguments().stream().anyMatch(a -> Sanitizers.reaches(a, t));
    }

    private static boolean controller(CallableDeclaration<?> m) {
        boolean mapping = m.getAnnotations().stream().anyMatch(a -> a.getName().getIdentifier().endsWith("Mapping"));
        boolean inController = m.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(c -> c.getAnnotations().stream().anyMatch(a -> Set.of("Controller", "RestController").contains(a.getName().getIdentifier())))
                .orElse(false);
        return mapping || inController && m.isMethodDeclaration() && m.isPublic();
    }

    /** 대문자로 시작하는 단순 이름 — 클래스(정적 호출 스코프)로 본다 */
    private static boolean typeName(Expression x) {
        String n = lastName(x);
        return x.isNameExpr() || x.isFieldAccessExpr() ? !n.isEmpty() && Character.isUpperCase(n.charAt(0)) : false;
    }

    static String simple(String type) {
        String s = type.replaceAll("<.*>", "");
        return s.substring(s.lastIndexOf('.') + 1);
    }

    /** 람다 몸 밖 노드만 */
    static <T extends Node> List<T> outsideLambda(CallableDeclaration<?> m, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (T n : m.findAll(type)) {
            if (n.findAncestor(LambdaExpr.class).filter(l -> l.isDescendantOf(m)).isEmpty()) {
                out.add(n);
            }
        }
        return out;
    }
}
