package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.HashSet;
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

    /** 프로필 naming 이 비었을 때 — eGov 표준(PLAN 6장) */
    static final Profile.Naming DEFAULT_NAMING = new Profile.Naming("^[A-Z]\\w*Controller$", "^[A-Z]\\w*Service$",
            "^[A-Z]\\w*ServiceImpl$", "^[A-Z]\\w*(DAO|Mapper)$", "egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl", null);

    private static final Set<String> CONTROLLER = Set.of("Controller", "RestController");
    private static final Set<String> LOG_METHODS = Set.of("error", "warn", "info", "debug", "trace", "fatal");
    private static final Pattern LOGGER = Pattern.compile("(?i)^(log|logger|.*log|.*logger)$");
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

    JavaRules(List<Rule.Def> defs, Profile.Naming naming) {
        defs.forEach(d -> on.put(d.id(), d));
        Profile.Naming n = naming == null ? DEFAULT_NAMING : naming;
        controller = Pattern.compile(or(n.controller(), DEFAULT_NAMING.controller()));
        service = Pattern.compile(or(n.service(), DEFAULT_NAMING.service()));
        serviceImpl = Pattern.compile(or(n.serviceImpl(), DEFAULT_NAMING.serviceImpl()));
        dao = Pattern.compile(or(n.dao(), DEFAULT_NAMING.dao()));
        String base = or(n.serviceImplBase(), DEFAULT_NAMING.serviceImplBase());
        baseSimple = base.substring(base.lastIndexOf('.') + 1);
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
            return out;
        }
        CompilationUnit cu = r.getResult().get();
        Ctx c = new Ctx(s, raw, out);
        imports(cu, c);
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
        out.sort(java.util.Comparator.comparingInt(Finding::line).thenComparing(Finding::rule));
        return out;
    }

    /**
     * 변경분 검사(5-6b)에서 안 바뀐 Java — 결과는 안 내고 파일 사이 상태(dupMapping 의 매핑 자리)만 모은다.
     * 매핑 애너테이션이 글에 없으면 파싱하지 않는다. 못 읽는 파일은 조용히 넘긴다(그 파일의 parseError 는 그 파일이 바뀔 때 낸다)
     */
    void observe(Source s) {
        if (!on.containsKey("java.dupMapping") || !s.text().contains("Mapping")) {
            return;
        }
        ParseResult<CompilationUnit> r = parser.parse(s.text());
        if (!JavaSource.ok(r)) {
            return;
        }
        Ctx c = new Ctx(s, s.text().split("\n", -1), new ArrayList<>());
        for (ClassOrInterfaceDeclaration t : r.getResult().get().findAll(ClassOrInterfaceDeclaration.class)) {
            if (!t.isInterface() && annotated(t, CONTROLLER)) {
                mappings(t, c);
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
        return out;
    }

    private record Ctx(Source s, String[] raw, List<Finding> out) {
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
            if (im.isStatic() || !on.containsKey("java.unusedImport")) {
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
        int stage = STATIC;
        int access = 0;
        for (BodyDeclaration<?> m : members) {
            if (m instanceof FieldDeclaration f) {
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
                        out.add(f);
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

    static int rank(AccessSpecifier a) {
        return switch (a) {
            case PUBLIC -> 0;
            case PROTECTED -> 1;
            case NONE -> 2;
            case PRIVATE -> 3;
        };
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
