package kr.ejg.toolbox.core.check;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /** 프로필 naming 이 비었을 때 — eGov 표준(PLAN 6장) */
    static final Profile.Naming DEFAULT_NAMING = new Profile.Naming("^[A-Z]\\w*Controller$", "^[A-Z]\\w*Service$",
            "^[A-Z]\\w*ServiceImpl$", "^[A-Z]\\w*(DAO|Mapper)$", "egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl", null);

    private static final Set<String> CONTROLLER = Set.of("Controller", "RestController");
    private static final Set<String> MAPPINGS = Set.of("RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping",
            "PatchMapping");
    private static final Set<String> LOG_METHODS = Set.of("error", "warn", "info", "debug", "trace", "fatal");
    private static final Pattern LOGGER = Pattern.compile("(?i)^(log|logger|.*log|.*logger)$");

    private final Map<String, Rule.Def> on = new LinkedHashMap<>();
    private final Pattern controller;
    private final Pattern service;
    private final Pattern serviceImpl;
    private final Pattern dao;
    private final String baseSimple;
    private final JavaParser java17;
    private final JavaParser java8;
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
        java17 = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        java8 = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8));
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
        ParseResult<CompilationUnit> r = java17.parse(s.text());
        if (!r.isSuccessful() || r.getResult().isEmpty()) {
            ParseResult<CompilationUnit> r8 = java8.parse(s.text());
            if (!r8.isSuccessful() || r8.getResult().isEmpty()) {
                if (on.containsKey(PARSE_ERROR)) {
                    int line = r.getProblems().stream().findFirst().flatMap(p -> p.getLocation())
                            .flatMap(l -> l.getBegin().getRange()).map(x -> x.begin.line).orElse(1);
                    String msg = r.getProblems().isEmpty() ? "" : r.getProblems().get(0).getMessage().lines().findFirst().orElse("");
                    out.add(finding(PARSE_ERROR, s, line, Finding.excerpt(msg)));
                }
                return out;
            }
            r = r8;
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
        ParseResult<CompilationUnit> r = java17.parse(s.text());
        if (!r.isSuccessful() || r.getResult().isEmpty()) {
            r = java8.parse(s.text());
        }
        if (!r.isSuccessful() || r.getResult().isEmpty()) {
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
        List<String> v = values(a, "value");
        return v.isEmpty() ? null : v.get(0);
    }

    private static List<String> values(AnnotationExpr a, String key) {
        Expression e = null;
        if (a instanceof SingleMemberAnnotationExpr sm && key.equals("value")) {
            e = sm.getMemberValue();
        } else if (a instanceof NormalAnnotationExpr na) {
            for (var p : na.getPairs()) {
                if (p.getNameAsString().equals(key) || key.equals("value") && p.getNameAsString().equals("path")) {
                    e = p.getValue();
                }
            }
        }
        List<String> out = new ArrayList<>();
        if (e instanceof StringLiteralExpr sl) {
            out.add(sl.getValue());
        } else if (e instanceof ArrayInitializerExpr ai) {
            ai.getValues().forEach(x -> {
                if (x instanceof StringLiteralExpr sl2) {
                    out.add(sl2.getValue());
                }
            });
        }
        return out;
    }

    /** 클래스 머리 경로 × 메서드 경로 + params. 방식은 GetMapping 류 이름, RequestMapping 은 method= 값(없으면 ANY) */
    private void mappings(ClassOrInterfaceDeclaration t, Ctx c) {
        List<String> prefixes = t.getAnnotationByName("RequestMapping").map(a -> values(a, "value")).orElse(List.of());
        if (prefixes.isEmpty()) {
            prefixes = List.of("");
        }
        for (MethodDeclaration m : t.getMethods()) {
            for (AnnotationExpr a : m.getAnnotations()) {
                String an = a.getName().getIdentifier();
                if (!MAPPINGS.contains(an)) {
                    continue;
                }
                String verb = an.equals("RequestMapping") ? verb(a) : an.replace("Mapping", "").toUpperCase(Locale.ROOT);
                List<String> paths = values(a, "value");
                // params 가 다르면 다른 매핑(eGov `params = "!cmd"` · `"cmd=Regist"` — V-11 실측)
                List<String> ps = values(a, "params");
                String params = ps.isEmpty() ? "" : " " + new java.util.TreeSet<>(ps);
                for (String pre : prefixes) {
                    for (String p : paths.isEmpty() ? List.of("") : paths) {
                        String key = verb + " " + join(pre, p) + params;
                        int line = line(a);
                        mappings.computeIfAbsent(key, k -> new ArrayList<>()).add(finding("java.dupMapping", c.s, line,
                                Finding.excerpt(key)));
                    }
                }
            }
        }
    }

    private static String verb(AnnotationExpr a) {
        if (a instanceof NormalAnnotationExpr na) {
            for (var p : na.getPairs()) {
                if (p.getNameAsString().equals("method")) {
                    Expression v = p.getValue();
                    if (v instanceof FieldAccessExpr fa) {
                        return fa.getNameAsString();
                    }
                    if (v instanceof NameExpr ne) {
                        return ne.getNameAsString();
                    }
                    return v.toString();
                }
            }
        }
        return "ANY";
    }

    private static String join(String a, String b) {
        String x = a.endsWith("/") ? a.substring(0, a.length() - 1) : a;
        if (b.isEmpty()) {
            return x.isEmpty() ? "/" : x;
        }
        return x + (b.startsWith("/") ? b : "/" + b);
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
