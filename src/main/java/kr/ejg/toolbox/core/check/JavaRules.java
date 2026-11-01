package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * Java 구조 묶음 ㄴ(5-2). 파일 하나를 JavaParser 로 한 번 읽어 켜진 규칙만 돈다. 실행(검사 한 번)마다 새로 만든다 —
 * {@code java.dupMapping} 이 파일 사이 상태(URL → 자리)를 들고 {@link #finish()} 에서 한 번 낸다.
 * 못 읽은 파일은 {@code java.parseError}(info) 한 건이고 나머지 규칙은 건너뛴다. Java 17 로 못 읽으면 Java 8 로 한 번 더(`_` 식별자 등).
 */
public final class JavaRules {

    public static final String PARSE_ERROR = "java.parseError";
    public static final String ORDER = "java.declarationOrder";
    /** ㅊ 크기·복잡도(5-35, 설계 32) — 기준은 규칙 params.max, 측정값은 발췌에 */
    static final String COMPLEXITY = "metric.complexity";
    static final String METHOD_LENGTH = "metric.methodLength";
    static final String NESTING = "metric.nesting";
    static final String PARAMS = "metric.params";
    static final String CLASS_METHODS = "metric.classMethods";
    static final String CLASS_LENGTH = "metric.classLength";
    /** 단언 없는 시험(5-45, 설계 37) */
    static final String TEST_NO_ASSERT = "java.testNoAssert";

    /** 프로필 naming 이 비었을 때 — eGov 표준(PLAN 6장) */
    static final Profile.Naming DEFAULT_NAMING = new Profile.Naming("^[A-Z]\\w*Controller$", "^[A-Z]\\w*Service$",
            "^[A-Z]\\w*ServiceImpl$", "^[A-Z]\\w*(DAO|Mapper)$", "egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl", null);

    private static final Set<String> CONTROLLER = Set.of("Controller", "RestController");
    private static final Set<String> LOG_METHODS = Set.of("error", "warn", "info", "debug", "trace", "fatal");
    private static final Pattern LOGGER = Pattern.compile("(?i)^(log|logger|.*log|.*logger)$");
    /** 시험 메서드 표시 — 단순 이름(5-45). TestFactory 는 동적 시험을 돌려줄 뿐이라 뺀다 */
    private static final Set<String> TEST_ANNOTATIONS = Set.of("Test", "ParameterizedTest", "RepeatedTest");
    /** 단언으로 치는 호출 이름 — JUnit·AssertJ·Mockito·ArchUnit(check)·도우미 관례 */
    // 마무리 리뷰 — Spring MockMvc 의 andExpect·andExpectAll 처럼 and 로 잇는 단언도
    private static final Pattern ASSERTION_CALL = Pattern.compile("^(?:assert|verify|expect|check|fail|should|and(?:Assert|Verify|Expect|Check|Should))");
    /** 선언 순서 단계(0-61e Checkstyle DeclarationOrder 와 같은 판정, 설계 29) */
    private static final int STATIC = 0;
    private static final int INSTANCE = 1;
    private static final int CTOR = 2;
    private static final int METHOD = 3;

    private final Map<String, Rule.Def> on = new LinkedHashMap<>();
    private final Pattern controller;
    private final Pattern service;
    private final Pattern serviceImpl;
    private final Pattern dao;
    private final String baseSimple;
    private final JavaSource parser = new JavaSource();
    /** HTTP 방식 + 경로 → 자리들 */
    private final Map<String, List<Finding>> mappings = new LinkedHashMap<>();
    /** 오염 추적·상수 전파(설계 42) + 메서드 사이 요약(설계 43) — 해당 규칙이 하나도 안 켜졌으면 null */
    private final TaintLink link;

    JavaRules(List<Rule.Def> defs, Profile.Naming naming) {
        this(defs, naming, new PropertiesIndex());
    }

    /** props — 검사 폴더의 *.properties 값(설계 44 §3-6). {@link RuleSet.Run} 이 같은 색인을 채운다 */
    JavaRules(List<Rule.Def> defs, Profile.Naming naming, PropertiesIndex props) {
        defs.forEach(d -> on.put(d.id(), d));
        Profile.Naming n = naming == null ? DEFAULT_NAMING : naming;
        controller = Pattern.compile(or(n.controller(), DEFAULT_NAMING.controller()));
        service = Pattern.compile(or(n.service(), DEFAULT_NAMING.service()));
        serviceImpl = Pattern.compile(or(n.serviceImpl(), DEFAULT_NAMING.serviceImpl()));
        dao = Pattern.compile(or(n.dao(), DEFAULT_NAMING.dao()));
        String base = or(n.serviceImplBase(), DEFAULT_NAMING.serviceImplBase());
        baseSimple = base.substring(base.lastIndexOf('.') + 1);
        link = Taint.wants(on.keySet()) ? new TaintLink(new Taint(on), on, props) : null;
    }

    private static String or(String v, String dflt) {
        return v == null || v.isBlank() ? dflt : v;
    }

    boolean any() {
        return !on.isEmpty();
    }

    List<Finding> apply(Source s) {
        List<Finding> out = new ArrayList<>();
        String[] raw = s.text().split("\n", -1);
        ParseResult<CompilationUnit> r = parser.parse(s.text());
        if (!JavaSource.ok(r)) {
            if (on.containsKey(PARSE_ERROR)) {
                int line = r.getProblems().stream().findFirst().flatMap(p -> p.getLocation())
                        .flatMap(l -> l.getBegin().getRange()).map(x -> x.begin.line).orElse(1);
                String msg = r.getProblems().isEmpty() ? "" : r.getProblems().get(0).getMessage().lines().findFirst().orElse("");
                out.add(finding(PARSE_ERROR, s, line, Finding.excerpt(msg)));
            }
            hybridFallback(s, out);
            return out;
        }
        CompilationUnit cu = r.getResult().get();
        Ctx c = new Ctx(s, raw, out);
        imports(cu, c);
        taint(cu, c);
        for (ClassOrInterfaceDeclaration t : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            type(t, c);
        }
        if (on.containsKey("java.emptyCatch") || on.containsKey("java.swallow")) {
            for (CatchClause cc : cu.findAll(CatchClause.class)) {
                catchClause(cc, c);
            }
        }
        if (on.containsKey(ORDER)) {
            boolean mods = !Boolean.TRUE.equals(on.get(ORDER).params().get("ignoreModifiers"));
            for (TypeDeclaration<?> t : cu.findAll(TypeDeclaration.class)) {
                order(t.getMembers(), mods, c);
            }
            for (EnumConstantDeclaration e : cu.findAll(EnumConstantDeclaration.class)) {
                order(e.getClassBody(), mods, c);
            }
            for (ObjectCreationExpr o : cu.findAll(ObjectCreationExpr.class)) {
                o.getAnonymousClassBody().ifPresent(b -> order(b, mods, c));
            }
        }
        if (on.containsKey(COMPLEXITY) || on.containsKey(METHOD_LENGTH) || on.containsKey(NESTING) || on.containsKey(PARAMS)) {
            for (CallableDeclaration<?> m : cu.findAll(CallableDeclaration.class)) {
                method(m, c);
            }
        }
        if (on.containsKey(TEST_NO_ASSERT)) {
            for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
                testNoAssert(m, c);
            }
        }
        out.sort(java.util.Comparator.comparingInt(Finding::line).thenComparing(Finding::rule));
        return out;
    }

    /**
     * 변경분 검사(5-6b)에서 안 바뀐 Java — 결과는 안 내고 파일 사이 상태(dupMapping 의 매핑 자리 · 설계 43 오염 요약)만 모은다.
     * 둘 다 필요 없으면 파싱하지 않는다. 못 읽는 파일은 조용히 넘긴다(그 파일의 parseError 는 그 파일이 바뀔 때 낸다)
     */
    void observe(Source s) {
        boolean dup = on.containsKey("java.dupMapping") && s.text().contains("Mapping");
        if (!dup && link == null) {
            return;
        }
        ParseResult<CompilationUnit> r = parser.parse(s.text());
        if (!JavaSource.ok(r)) {
            return;
        }
        if (link != null) {
            link.summarize(r.getResult().get(), s, false);
        }
        if (dup) {
            Ctx c = new Ctx(s, s.text().split("\n", -1), new ArrayList<>());
            for (ClassOrInterfaceDeclaration t : r.getResult().get().findAll(ClassOrInterfaceDeclaration.class)) {
                if (!t.isInterface() && annotated(t, CONTROLLER)) {
                    mappings(t, c);
                }
            }
        }
    }

    /** 파일 사이 규칙 — 같은 HTTP 방식·경로가 둘 이상이면 자리마다 한 건 */
    List<Finding> finish() {
        List<Finding> out = new ArrayList<>();
        for (List<Finding> l : mappings.values()) {
            if (l.size() > 1) {
                out.addAll(l);
            }
        }
        mappings.clear();
        if (link != null) {
            out.addAll(link.finish());
        }
        return out;
    }

    private record Ctx(Source s, String[] raw, List<Finding> out) {
    }

    /**
     * 오염 추적·상수 전파(설계 42) — 켜진 규칙이 없으면 안 돈다. 메서드 사이 요약도 여기서 모은다(설계 43).
     * + 파서·TLS 설정 결함(설계 48, {@link SecurityAst})
     * + Java 결함 열하나(설계 49, {@link JavaDefects}·{@link ValueAst}·{@link TxAst})
     * + 자원 해제 둘(설계 50, {@link ResourceAst})
     */
    private void taint(CompilationUnit cu, Ctx c) {
        if (link != null) {
            for (Taint.Hit h : link.summarize(cu, c.s, true)) {
                add(c, h.rule(), h.node());
            }
        }
        for (Taint.Hit h : SecurityAst.apply(cu, on.keySet())) {
            add(c, h.rule(), h.node());
        }
        for (Taint.Hit h : JavaDefects.apply(cu, on.keySet())) {
            add(c, h.rule(), h.node());
        }
        for (Taint.Hit h : ValueAst.apply(cu, on.keySet())) {
            add(c, h.rule(), h.node());
        }
        for (Taint.Hit h : TxAst.apply(cu, on.keySet())) {
            add(c, h.rule(), h.node());
        }
        for (Taint.Hit h : ResourceAst.apply(cu, on.keySet())) {
            add(c, h.rule(), h.node());
        }
    }

    /** 못 읽은 파일 — 하이브리드 규칙은 정규식으로 되돌린다(RegexRule 이 .java 를 건너뛰니 지적이 사라지지 않게) */
    private void hybridFallback(Source s, List<Finding> out) {
        for (Rule.Def d : on.values()) {
            if (RuleSet.hybrid(d)) {
                out.addAll(new RegexRule(d).applyText(s));
            }
        }
    }

    /**
     * 5-45 — 시험 메서드(@Test·@ParameterizedTest·@RepeatedTest) 몸에 단언이 없다: 이름이 assert·verify·expect·check·fail·should 로
     * 시작하는 호출도, {@code params.helpers} 에 적은 도우미 호출도, throw 문도 없으면. 람다 안 호출도 센다
     */
    private void testNoAssert(MethodDeclaration m, Ctx c) {
        if (m.getBody().isEmpty() || m.getAnnotations().stream().noneMatch(a -> TEST_ANNOTATIONS.contains(a.getName().getIdentifier()))) {
            return;
        }
        BlockStmt body = m.getBody().get();
        Object h = on.get(TEST_NO_ASSERT).params().get("helpers");
        Set<String> helpers = new java.util.HashSet<>();
        if (h instanceof List<?> l) {
            l.forEach(x -> helpers.add(String.valueOf(x)));
        }
        boolean asserts = !body.findAll(ThrowStmt.class).isEmpty() || body.findAll(MethodCallExpr.class).stream()
                .map(mc -> mc.getNameAsString()).anyMatch(n -> ASSERTION_CALL.matcher(n).find() || helpers.contains(n));
        if (!asserts) {
            int line = m.getName().getBegin().map(p -> p.line).orElse(line(m));
            c.out.add(finding(TEST_NO_ASSERT, c.s, line, Finding.excerpt(m.getNameAsString() + "()")));
        }
    }

    private Finding finding(String rule, Source s, int line, String excerpt) {
        Rule.Def d = on.get(rule);
        return new Finding(s.rel(), line, d.group(), rule, d.severity(), excerpt);
    }

    private void add(Ctx c, String rule, Node n) {
        if (on.containsKey(rule)) {
            int line = line(n);
            c.out.add(finding(rule, c.s, line, Finding.excerpt(line - 1 < c.raw.length ? c.raw[line - 1] : "")));
        }
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(1);
    }

    // ---------------------------------------------------------------- import

    private void imports(CompilationUnit cu, Ctx c) {
        if (!on.containsKey("java.unusedImport") && !on.containsKey("java.starImport")) {
            return;
        }
        Set<String> used = null;
        for (ImportDeclaration im : cu.getImports()) {
            if (im.isAsterisk()) {
                add(c, "java.starImport", im);
                continue;
            }
            if (!on.containsKey("java.unusedImport")) { // 정적 import 도 본다(5-48) — 마지막 이름이 호출·참조·주석 단어로 안 나오면
                continue;
            }
            if (used == null) {
                used = usedNames(cu);
            }
            if (!used.contains(im.getName().getIdentifier())) {
                add(c, "java.unusedImport", im);
            }
        }
    }

    /** import 밖에서 쓰인 이름 + 주석(javadoc {@code @link}·{@code @see}) 안 단어 */
    private static Set<String> usedNames(CompilationUnit cu) {
        Set<String> used = new HashSet<>();
        for (SimpleName n : cu.findAll(SimpleName.class)) {
            used.add(n.getIdentifier());
        }
        for (Name n : cu.findAll(Name.class)) {
            if (n.findAncestor(ImportDeclaration.class).isEmpty()) {
                used.add(n.getIdentifier());
            }
        }
        for (Comment cm : cu.getAllComments()) {
            for (String w : cm.getContent().split("[^\\w$]+")) {
                used.add(w);
            }
        }
        return used;
    }

    // ---------------------------------------------------------------- 클래스

    private void type(ClassOrInterfaceDeclaration t, Ctx c) {
        if (t.isInterface()) {
            return;
        }
        classMetrics(t, c);
        String name = t.getNameAsString();
        boolean isController = annotated(t, CONTROLLER);
        Optional<AnnotationExpr> svc = t.getAnnotationByName("Service");
        boolean isRepo = t.getAnnotationByName("Repository").isPresent();
        boolean implName = serviceImpl.matcher(name).matches();
        if (isController && !controller.matcher(name).matches()
                || svc.isPresent() && !serviceImpl.matcher(name).matches() && !service.matcher(name).matches()
                || isRepo && !dao.matcher(name).matches()) {
            add(c, "java.naming", t.getName());
        }
        Set<String> impl = new HashSet<>();
        t.getImplementedTypes().forEach(x -> impl.add(x.getNameAsString()));
        if (implName && name.endsWith("Impl") && !impl.contains(name.substring(0, name.length() - 4))) {
            add(c, "java.serviceImplPair", t.getName());
        }
        if (svc.isPresent()) {
            String v = value(svc.get());
            // 첫 글자 소문자(Spring 기본) 또는 이름 그대로(eGov 관례 @Service("EgovCmmUseService") — V-11 실측)
            if (v != null && !v.isEmpty() && !v.equals(lowerFirst(name)) && !v.equals(name)
                    && impl.stream().noneMatch(i -> v.equals(lowerFirst(i)) || v.equals(i))) {
                add(c, "java.serviceName", svc.get());
            }
        }
        if (implName && t.getExtendedTypes().stream().noneMatch(x -> x.getNameAsString().equals(baseSimple))) {
            add(c, "java.egovBase", t.getName());
        }
        if (isController) {
            for (FieldDeclaration f : t.getFields()) {
                if (f.getElementType() instanceof ClassOrInterfaceType ft && dao.matcher(ft.getNameAsString()).matches()) {
                    add(c, "java.controllerDao", f);
                }
            }
            if (on.containsKey("java.dupMapping")) {
                mappings(t, c);
            }
        }
    }

    private static boolean annotated(ClassOrInterfaceDeclaration t, Set<String> names) {
        return t.getAnnotations().stream().anyMatch(a -> names.contains(a.getName().getIdentifier()));
    }

    static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** {@code @X("v")}·{@code @X(value = "v")} 의 첫 문자열. 없으면 null */
    private static String value(AnnotationExpr a) {
        List<String> v = SpringMappings.values(a, "value");
        return v.isEmpty() ? null : v.get(0);
    }

    /** 클래스 머리 경로 × 메서드 경로 + params. 방식은 GetMapping 류 이름, RequestMapping 은 method= 값(없으면 ANY) */
    private void mappings(ClassOrInterfaceDeclaration t, Ctx c) {
        for (MethodDeclaration m : t.getMethods()) {
            for (SpringMappings.Mapping mp : SpringMappings.of(t, m)) {
                mappings.computeIfAbsent(mp.key(), k -> new ArrayList<>()).add(finding("java.dupMapping", c.s, mp.line(),
                        Finding.excerpt(mp.key())));
            }
        }
    }

    // ---------------------------------------------------------------- 선언 순서

    /**
     * 선언 순서(5-32) — static 변수 → 인스턴스 변수 → 생성자 → 메서드, 같은 종류 안 public → protected → package → private.
     * 중첩 타입·초기화 블록·애너테이션 멤버는 자리를 안 바꾼다. 자리가 틀린 필드는 접근자 지적을 따로 안 낸다(한 필드에 하나).
     * 옮기면 전방 참조 오류가 되는 접근자 역순 필드는 지적하지 않는다(5-33, {@link #readsJumped}).
     * {@code mods} 가 false(params.ignoreModifiers) 면 접근자 순서를 안 보고, 생성자·메서드 전의 「인스턴스 뒤 static」 도 허용한다
     */
    private void order(NodeList<BodyDeclaration<?>> members, boolean mods, Ctx c) {
        for (BodyDeclaration<?> m : orderViolations(members, mods)) {
            add(c, ORDER, m);
        }
    }

    /** 선언 순서 위반 멤버(설계 29 §3 상태기계) — {@link #order} 와 코드 정리 OrderFix(5-34b)가 같이 쓴다 */
    static List<BodyDeclaration<?>> orderViolations(NodeList<BodyDeclaration<?>> members, boolean mods) {
        List<BodyDeclaration<?>> out = new ArrayList<>();
        List<FieldDeclaration> before = new ArrayList<>();
        Set<FieldDeclaration> pinned = Collections.newSetFromMap(new IdentityHashMap<>()); // Node.equals 는 구조 비교
        int stage = STATIC;
        int access = 0;
        for (BodyDeclaration<?> m : members) {
            if (m instanceof FieldDeclaration f) {
                before.add(f);
                boolean valid = true;
                if (f.isStatic()) {
                    if (stage > STATIC && (mods || stage > INSTANCE)) {
                        out.add(f);
                        valid = false;
                    }
                } else if (stage > INSTANCE) {
                    out.add(f);
                    valid = false;
                } else if (stage == STATIC) {
                    stage = INSTANCE;
                    access = 0;
                }
                int r = rank(f.getAccessSpecifier());
                if (access > r) {
                    if (valid && mods) {
                        if (readsJumped(f, before, pinned, r)) {
                            pinned.add(f);
                        } else {
                            out.add(f);
                        }
                    }
                } else {
                    access = r;
                }
            } else if (m instanceof ConstructorDeclaration || m instanceof CompactConstructorDeclaration) {
                if (stage > CTOR) {
                    out.add(m);
                } else {
                    stage = CTOR;
                }
            } else if (m instanceof MethodDeclaration) {
                stage = METHOD;
            }
        }
        return out;
    }

    /**
     * 5-33 — 접근자 역순 필드 f 를 제자리로 옮기면 앞질러야 할 필드(앞에 있고 static 여부가 같고 접근자 순위가 뒤)를 f 의 초기화식이
     * 단순 이름으로 읽으면 javac 의 illegal forward reference(JLS 8.3.3)가 되어 옮길 수 없다 — 지적하지 않는다.
     * 실측(JDK 17): 단순 이름·{@code P.length()} 의 P·람다 몸·복합 대입은 오류, {@code A.X}·{@code this.x}·익명·지역 클래스 몸·
     * {@code x = …} 의 왼쪽·람다 인자나 람다 안 지역 변수가 가린 이름은 오류 아님.
     * 이렇게 면제돼 제자리에 묶인 필드(pinned)도 f 의 목표 자리(앞질러야 할 첫 필드) 뒤에 있으면 같이 앞질러야 할 필드로 친다 —
     * egov {@code EgovProperties.GLOBALS_PROPERTIES_FILE} 이 묶인 {@code RELATIVE_PATH_PREFIX} 를 읽는 꼴
     */
    private static boolean readsJumped(FieldDeclaration f, List<FieldDeclaration> before, Set<FieldDeclaration> pinned, int r) {
        Set<String> jumped = new HashSet<>();
        boolean past = false;
        for (FieldDeclaration g : before) {
            if (g == f || g.isStatic() != f.isStatic()) {
                continue;
            }
            boolean later = rank(g.getAccessSpecifier()) > r;
            past |= later;
            if (later || past && pinned.contains(g)) {
                g.getVariables().forEach(v -> jumped.add(v.getNameAsString()));
            }
        }
        if (jumped.isEmpty()) {
            return false;
        }
        for (VariableDeclarator v : f.getVariables()) {
            if (v.getInitializer().isPresent()) {
                for (NameExpr n : v.getInitializer().get().findAll(NameExpr.class)) {
                    if (jumped.contains(n.getNameAsString()) && forwardUse(n, f)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** n 이 f 초기화식 안에서 그 필드를 읽는 자리인가 — 대입 왼쪽·익명·지역 클래스 몸·람다가 가린 이름이면 아니다 */
    private static boolean forwardUse(NameExpr n, FieldDeclaration f) {
        if (n.getParentNode().orElse(null) instanceof AssignExpr a && a.getTarget() == n && a.getOperator() == AssignExpr.Operator.ASSIGN) {
            return false;
        }
        if (n.findAncestor(BodyDeclaration.class).orElse(null) != f) {
            return false;
        }
        String name = n.getNameAsString();
        for (Node p = n.getParentNode().orElse(null); p != null && p != f; p = p.getParentNode().orElse(null)) {
            if (p instanceof LambdaExpr l && (l.getParameters().stream().anyMatch(q -> q.getNameAsString().equals(name))
                    || l.getBody().findAll(VariableDeclarator.class).stream().anyMatch(d -> d.getNameAsString().equals(name)))) {
                return false;
            }
        }
        return true;
    }

    static int rank(AccessSpecifier a) {
        return switch (a) {
            case PUBLIC -> 0;
            case PROTECTED -> 1;
            case NONE -> 2;
            case PRIVATE -> 3;
        };
    }

    // ---------------------------------------------------------------- 크기·복잡도(5-35)

    /** 본문 있는 메서드·생성자 하나 — 규칙마다 최대 한 건. 지적 줄은 이름 줄(중첩은 처음 기준을 넘는 문장) */
    private void method(CallableDeclaration<?> m, Ctx c) {
        Optional<BlockStmt> body = m instanceof MethodDeclaration md ? md.getBody()
                : m instanceof ConstructorDeclaration cd ? Optional.of(cd.getBody()) : Optional.empty();
        if (body.isEmpty()) {
            return;
        }
        String who = m.getNameAsString() + "(" + m.getParameters().size() + ")";
        int at = line(m.getName());
        if (on.containsKey(COMPLEXITY)) {
            int v = complexity(body.get());
            int max = max(COMPLEXITY, 15);
            if (v > max) {
                c.out.add(finding(COMPLEXITY, c.s, at, Finding.excerpt("복잡도 " + v + " > " + max + " · " + who)));
            }
        }
        if (on.containsKey(METHOD_LENGTH)) {
            int v = body.get().getEnd().map(p -> p.line).orElse(at) - line(m) + 1;
            int max = max(METHOD_LENGTH, 100);
            if (v > max) {
                c.out.add(finding(METHOD_LENGTH, c.s, at, Finding.excerpt("길이 " + v + " > " + max + " · " + who)));
            }
        }
        if (on.containsKey(NESTING)) {
            int max = max(NESTING, 4);
            Node[] over = new Node[1];
            int v = depth(body.get(), 0, max, over);
            if (v > max) {
                c.out.add(finding(NESTING, c.s, line(over[0]), Finding.excerpt("중첩 " + v + " > " + max + " · " + who)));
            }
        }
        if (on.containsKey(PARAMS)) {
            int v = m.getParameters().size();
            int max = max(PARAMS, 7);
            if (v > max) {
                c.out.add(finding(PARAMS, c.s, at, Finding.excerpt("인자 " + v + " > " + max + " · " + who)));
            }
        }
    }

    /** 클래스 메서드 수·길이(인터페이스 제외 — 부르는 type 이 거른다) */
    private void classMetrics(ClassOrInterfaceDeclaration t, Ctx c) {
        int at = line(t.getName());
        if (on.containsKey(CLASS_METHODS)) {
            int v = t.getMethods().size();
            int max = max(CLASS_METHODS, 30);
            if (v > max) {
                c.out.add(finding(CLASS_METHODS, c.s, at, Finding.excerpt("메서드 " + v + " > " + max + " · " + t.getNameAsString())));
            }
        }
        if (on.containsKey(CLASS_LENGTH)) {
            int v = t.getEnd().map(p -> p.line).orElse(at) - line(t) + 1;
            int max = max(CLASS_LENGTH, 1000);
            if (v > max) {
                c.out.add(finding(CLASS_LENGTH, c.s, at, Finding.excerpt("길이 " + v + " > " + max + " · " + t.getNameAsString())));
            }
        }
    }

    /** 기준 params.max — 수가 아니면 기본값 */
    private int max(String rule, int dflt) {
        Object v = on.get(rule).params().get("max");
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? dflt : Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    /** 순환 복잡도 — 1 + 분기(if·for·foreach·while·do·catch·?:·라벨 있는 case·&&·||). 람다·익명 클래스 몸도 그 메서드에 든다(설계 32 §3) */
    static int complexity(Node body) {
        int v = 1;
        for (Node n : body.findAll(Node.class)) {
            if (n instanceof IfStmt || n instanceof ForStmt || n instanceof ForEachStmt || n instanceof WhileStmt || n instanceof DoStmt
                    || n instanceof CatchClause || n instanceof ConditionalExpr
                    || n instanceof SwitchEntry se && !se.getLabels().isEmpty()
                    || n instanceof BinaryExpr be && (be.getOperator() == BinaryExpr.Operator.AND || be.getOperator() == BinaryExpr.Operator.OR)) {
                v++;
            }
        }
        return v;
    }

    /** 제어문 중첩 깊이 — else-if 는 같은 깊이. over[0] 은 깊이가 처음 limit 을 넘는 문장 */
    static int depth(Node n, int d, int limit, Node[] over) {
        int best = d;
        for (Node ch : n.getChildNodes()) {
            boolean elseIf = ch instanceof IfStmt && n instanceof IfStmt p && p.getElseStmt().orElse(null) == ch;
            boolean nests = ch instanceof IfStmt || ch instanceof ForStmt || ch instanceof ForEachStmt || ch instanceof WhileStmt
                    || ch instanceof DoStmt || ch instanceof SwitchStmt || ch instanceof TryStmt;
            int nd = nests && !elseIf ? d + 1 : d;
            if (nd > limit && over[0] == null) {
                over[0] = ch;
            }
            best = Math.max(best, depth(ch, nd, limit, over));
        }
        return best;
    }

    // ---------------------------------------------------------------- catch

    private void catchClause(CatchClause cc, Ctx c) {
        if (cc.getBody().getStatements().isEmpty()) {
            add(c, "java.emptyCatch", cc);
            return;
        }
        Optional<ClassOrInterfaceDeclaration> owner = cc.findAncestor(ClassOrInterfaceDeclaration.class);
        if (owner.isEmpty() || !serviceImpl.matcher(owner.get().getNameAsString()).matches()) {
            return;
        }
        boolean handled = !cc.getBody().findAll(ThrowStmt.class).isEmpty()
                || cc.getBody().findAll(MethodCallExpr.class).stream().anyMatch(JavaRules::handles);
        if (!handled) {
            add(c, "java.swallow", cc);
        }
    }

    /** 로거 호출(log.error 꼴) 또는 eGov processException */
    private static boolean handles(MethodCallExpr m) {
        String n = m.getNameAsString();
        if (n.equals("processException") || n.equals("leaveaTrace")) {
            return true;
        }
        return LOG_METHODS.contains(n) && m.getScope().map(s -> LOGGER.matcher(s.toString().replaceAll(".*\\.", "")).matches())
                .orElse(false);
    }
}
