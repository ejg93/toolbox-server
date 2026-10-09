package kr.ejg.toolbox.core.analyze;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.check.JavaSource;
import kr.ejg.toolbox.core.check.Source;
import kr.ejg.toolbox.core.check.SpringMappings;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * Java 호출 그래프(6-3) — 컨트롤러 매핑 메서드마다 서비스·DAO 를 따라가 MyBatis 문장 참조(ns.id)를 모은다. 심볼 해석 없이 이름으로 푼다.
 * <p>
 * 필드 타입 단순 이름 → 색인의 클래스(인터페이스면 구현 — 여럿이면 {@code @Resource(name)}·{@code @Qualifier} = 빈 이름으로 가름).
 * 색인에 없는 타입은 프레임워크 — 조용히 건너뛴다. DAO({@code @Repository}·프로필 {@code naming.dao}·조상 {@code *AbstractDAO}·{@code *AbstractMapper})
 * 안 {@code selectList("ns.id", …)} 류의 첫 인자가 문장 참조 — 리터럴·리터럴 접두(+식)·리터럴을 받은 변수까지 푼다.
 * 구현 없는 {@code @Mapper} 인터페이스는 「FQCN.메서드」 가 문장이다. 뷰는 컨트롤러 메서드의 리턴·{@code ModelAndView}·{@code setViewName}.
 * <p>
 * JPA(6-15) — {@link JpaIndex} 가 비어 있지 않을 때만. 필드 타입이 저장소면 커스텀 구현({@code <Custom>Impl}·{@code <저장소>Impl})의 본문 먼저,
 * 없으면 「저장소FQCN.메서드」 문장(resolution {@code jpa} — 표는 {@code AnalyzeRunner} 가 색인에서). {@code EntityManager} 호출은 표를 색인 곁 등록부에
 * 「클래스FQCN.메서드#em」 으로 적는다. JPA 표지(EntityManager·JPAQueryFactory 필드, QuerydslRepositorySupport 상속)가 있는 DAO 는 MyBatis 싱크로 안 본다.
 * QueryDSL(6-16) — JPAQueryFactory 등에서 시작한 사슬의 selectFrom·from·join 류 R · update U · delete D · insert C, Q 식(QX.x·QX 변수·
 * new QX(…)·static import) → 엔티티 X 의 표. 「클래스FQCN.메서드#qdsl」(resolution {@code qdsl}), 엔티티로 안 풀리는 Q 는 {@code querydsl}.
 */
public final class JavaGraph {

    /** kind — view·forward·redirect·class. forward·redirect 는 접두와 {@code ?…} 를 뗀 URL */
    public record View(String kind, String name) {
    }

    /** resolution — literal·prefix(접두로 시작하는 문장 전부)·var(변수에 넣은 리터럴)·mapper(Mapper 인터페이스 메서드) */
    public record Stmt(String id, String resolution) {
    }

    /** 프로그램 = 컨트롤러 메서드의 매핑 하나. 열쇠 (클래스, 메서드, verb, url, params) — URL 로 합치지 않는다. kind — view·json */
    public record Program(String className, String method, String file, int line, String verb, String url, String params, String kind,
            List<View> views, String description, List<Stmt> statements) {

        public Program {
            views = List.copyOf(views);
            statements = List.copyOf(statements);
        }
    }

    /** daoStatements — 프로그램과 무관하게 DAO 전체가 가리키는 문장 참조(정렬·중복 없음, 6-11 안 불리는 문장 판정) */
    public record Graph(List<Program> programs, List<Unresolved> unresolved, List<Stmt> daoStatements) {

        public Graph {
            programs = List.copyOf(programs);
            unresolved = List.copyOf(unresolved);
            daoStatements = List.copyOf(daoStatements);
        }
    }

    static final Set<String> SINKS = Set.of("selectList", "selectOne", "selectByPk", "selectListWithPaging", "list", "insert", "update",
            "delete");
    /** QueryDSL 사슬의 뿌리 타입 */
    static final Set<String> QDSL_ROOTS = Set.of("JPAQueryFactory", "JPQLQueryFactory", "JPAQuery", "JPQLQuery", "JPAUpdateClause",
            "JPADeleteClause", "JPAInsertClause");
    static final int MAX_DEPTH = 12;
    static final int MAX_FRAMES = 500;
    private static final Pattern ABSTRACT_DAO = Pattern.compile("^\\w*Abstract(DAO|Dao|Mapper)$|^SqlSessionDaoSupport$");
    private static final String DEFAULT_DAO = "^[A-Z]\\w*(DAO|Mapper)$";
    private static final Pattern TAG = Pattern.compile("<[^>]{0,200}>");
    private static final int DESCR_MAX = 100;

    private record Field(String type, String bean, int line) {
    }

    private static final class Cls {
        String name;
        String fqcn;
        String pkg;
        String file;
        boolean iface;
        boolean controller;
        boolean repository;
        boolean mapperAnno;
        String bean;
        String superName;
        List<String> interfaces = new ArrayList<>();
        Map<String, Field> fields = new LinkedHashMap<>();
        Map<String, List<MethodDeclaration>> methods = new LinkedHashMap<>();
        Map<String, String> imports = new HashMap<>();
        /** static import 멤버 → 그 클래스 FQCN(QueryDSL {@code import static …QBbs.bbs}, 6-16) */
        Map<String, String> statics = new HashMap<>();
        ClassOrInterfaceDeclaration decl;
    }

    /** 한 컨트롤러 메서드를 따라가며 모은 것 */
    private static final class Acc {
        final Set<Stmt> stmts = new LinkedHashSet<>();
        final Set<String> visited = new HashSet<>();
        int frames;
    }

    private final Map<String, List<Cls>> byName = new HashMap<>();
    private final Map<String, List<Cls>> implementors = new HashMap<>();
    private final Map<String, Unresolved> unresolved = new TreeMap<>();
    private final Pattern dao;
    private final JpaIndex jpa;
    /** DAO 전체 훑기 동안은 미해결을 안 적는다 — 프로그램 추적의 미해결 목록이 그대로여야 한다(6-11) */
    private boolean quiet;

    private JavaGraph(Profile.Naming naming, JpaIndex jpa) {
        String d = naming == null || naming.dao() == null || naming.dao().isBlank() ? DEFAULT_DAO : naming.dao();
        dao = Pattern.compile(d);
        this.jpa = jpa == null ? JpaIndex.empty() : jpa;
    }

    public static Graph scan(List<Source> java, Profile.Naming naming) {
        return scan(java, naming, JpaIndex.empty());
    }

    /** @param jpa JPA 색인(6-15) — EntityManager·QueryDSL 문장의 표를 이 색인 곁 등록부에 적는다 */
    public static Graph scan(List<Source> java, Profile.Naming naming, JpaIndex jpa) {
        return scan(java, naming, jpa, null);
    }

    /** @param tick 파일 파싱마다·컨트롤러 클래스마다 부른다 — 취소 확인(6-25). 없으면 null */
    public static Graph scan(List<Source> java, Profile.Naming naming, JpaIndex jpa, Runnable tick) {
        return new JavaGraph(naming, jpa).run(java, tick);
    }

    private Graph run(List<Source> java, Runnable tick) {
        JavaSource parser = new JavaSource();
        List<Cls> all = new ArrayList<>();
        for (Source s : java) {
            if (tick != null) {
                tick.run();
            }
            ParseResult<CompilationUnit> r = parser.parse(s.text());
            if (!JavaSource.ok(r)) {
                int line = r.getProblems().stream().findFirst().flatMap(p -> p.getLocation()).flatMap(l -> l.getBegin().getRange())
                        .map(x -> x.begin.line).orElse(1);
                note("parse", s.rel(), line, "");
                continue;
            }
            CompilationUnit cu = r.getResult().get();
            String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            Map<String, String> imports = new HashMap<>();
            Map<String, String> statics = new HashMap<>();
            for (ImportDeclaration i : cu.getImports()) {
                if (!i.isStatic() && !i.isAsterisk()) {
                    imports.put(i.getName().getIdentifier(), i.getNameAsString());
                } else if (i.isStatic() && !i.isAsterisk() && i.getName().getQualifier().isPresent()) {
                    statics.put(i.getName().getIdentifier(), i.getName().getQualifier().get().asString());
                }
            }
            for (ClassOrInterfaceDeclaration t : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                Cls c = index(t, s.rel(), pkg, imports);
                c.statics = statics;
                all.add(c);
            }
        }
        all.forEach(c -> byName.computeIfAbsent(c.name, k -> new ArrayList<>()).add(c));
        for (Cls c : all) {
            if (!c.iface) {
                for (String i : interfacesOf(c, new HashSet<>())) {
                    implementors.computeIfAbsent(i, k -> new ArrayList<>()).add(c);
                }
            }
        }

        List<Program> programs = new ArrayList<>();
        for (Cls c : all) {
            if (!c.controller || c.iface) {
                continue;
            }
            if (tick != null) {
                tick.run();
            }
            for (MethodDeclaration m : c.decl.getMethods()) {
                List<SpringMappings.Mapping> maps = SpringMappings.of(c.decl, m);
                if (maps.isEmpty()) {
                    continue;
                }
                Acc acc = new Acc();
                walk(c, m, 0, acc);
                List<Stmt> stmts = new ArrayList<>(acc.stmts);
                stmts.sort(Comparator.comparing(Stmt::id).thenComparing(Stmt::resolution));
                List<View> views = views(c, m);
                String kind = kind(c, m, views);
                String descr = description(m);
                for (SpringMappings.Mapping mp : maps) {
                    programs.add(new Program(c.name, m.getNameAsString(), c.file, mp.line(), mp.verb(), mp.url(),
                            mp.params().isEmpty() ? "" : String.join(",", mp.params()), kind, views, descr, stmts));
                }
            }
        }
        programs.sort(Comparator.comparing(Program::file).thenComparingInt(Program::line).thenComparing(Program::url)
                .thenComparing(Program::verb));

        // 6-11 — DAO 구체 클래스의 본문 있는 메서드를 전부 훑어 문장 참조를 모은다(안 불리는 매퍼 문장 판정용)
        Set<Stmt> daoStmts = new TreeSet<>(Comparator.comparing(Stmt::id).thenComparing(Stmt::resolution));
        quiet = true;
        for (Cls c : all) {
            if (c.iface || !isDao(c)) {
                continue;
            }
            for (MethodDeclaration m : c.decl.getMethods()) {
                if (m.getBody().isPresent()) {
                    Acc acc = new Acc();
                    walk(c, m, 0, acc);
                    daoStmts.addAll(acc.stmts);
                }
            }
        }
        quiet = false;
        return new Graph(programs, new ArrayList<>(unresolved.values()), new ArrayList<>(daoStmts));
    }

    // ---------------------------------------------------------------- 색인

    private Cls index(ClassOrInterfaceDeclaration t, String file, String pkg, Map<String, String> imports) {
        Cls c = new Cls();
        c.decl = t;
        c.name = t.getNameAsString();
        c.pkg = pkg;
        c.fqcn = pkg.isEmpty() ? c.name : pkg + "." + c.name;
        c.file = file;
        c.iface = t.isInterface();
        c.imports = imports;
        for (AnnotationExpr a : t.getAnnotations()) {
            String n = a.getName().getIdentifier();
            switch (n) {
                case "Controller", "RestController" -> c.controller = true;
                case "Repository" -> c.repository = true;
                case "Mapper" -> c.mapperAnno = true;
                default -> {
                }
            }
            if (Set.of("Service", "Repository", "Component", "Controller", "RestController", "Mapper").contains(n)) {
                List<String> v = SpringMappings.values(a, "value");
                if (!v.isEmpty() && !v.get(0).isBlank()) {
                    c.bean = v.get(0);
                }
            }
        }
        if (c.bean == null) {
            c.bean = c.name.isEmpty() ? c.name : Character.toLowerCase(c.name.charAt(0)) + c.name.substring(1);
        }
        if (c.iface) {
            t.getExtendedTypes().forEach(x -> c.interfaces.add(x.getNameAsString()));
        } else {
            c.superName = t.getExtendedTypes().isEmpty() ? null : t.getExtendedTypes().get(0).getNameAsString();
            t.getImplementedTypes().forEach(x -> c.interfaces.add(x.getNameAsString()));
        }
        for (FieldDeclaration fd : t.getFields()) {
            if (!(fd.getElementType() instanceof ClassOrInterfaceType ct)) {
                continue;
            }
            String bean = fd.getAnnotationByName("Resource").map(a -> {
                List<String> v = SpringMappings.values(a, "name");
                return v.isEmpty() ? "" : v.get(0);
            }).orElse(null);
            String q = fd.getAnnotationByName("Qualifier").map(a -> SpringMappings.values(a, "value")).filter(v -> !v.isEmpty())
                    .map(v -> v.get(0)).orElse(null);
            for (VariableDeclarator v : fd.getVariables()) {
                String b = q != null ? q : bean == null ? null : bean.isEmpty() ? v.getNameAsString() : bean;
                c.fields.put(v.getNameAsString(), new Field(ct.getNameAsString(), b, line(fd)));
            }
        }
        for (MethodDeclaration m : t.getMethods()) {
            c.methods.computeIfAbsent(m.getNameAsString(), k -> new ArrayList<>()).add(m);
        }
        return c;
    }

    /** 이 클래스가 (조상 클래스·상위 인터페이스까지) 구현하는 인터페이스 단순 이름 */
    private Set<String> interfacesOf(Cls c, Set<String> seen) {
        Set<String> out = new LinkedHashSet<>();
        if (!seen.add(c.fqcn)) {
            return out;
        }
        for (String i : c.interfaces) {
            out.add(i);
            for (Cls ic : lookup(c, i)) {
                out.addAll(interfacesOf(ic, seen));
            }
        }
        if (c.superName != null) {
            for (Cls s : lookup(c, c.superName)) {
                out.addAll(interfacesOf(s, seen));
            }
        }
        return out;
    }

    /** 단순 이름 → 색인의 클래스. 같은 이름이 여럿이면 import·같은 패키지로 가르고, 못 가르면 전부 */
    private List<Cls> lookup(Cls from, String simple) {
        List<Cls> cands = byName.getOrDefault(simple, List.of());
        if (cands.size() <= 1) {
            return cands;
        }
        String want = from.imports.getOrDefault(simple, from.pkg.isEmpty() ? simple : from.pkg + "." + simple);
        List<Cls> hit = cands.stream().filter(x -> x.fqcn.equals(want)).toList();
        return hit.isEmpty() ? cands : hit;
    }

    private boolean isDao(Cls c) {
        if (c.repository || dao.matcher(c.name).matches()) {
            return true;
        }
        Set<String> seen = new HashSet<>();
        String s = c.superName;
        Cls cur = c;
        while (s != null && seen.add(s)) {
            if (ABSTRACT_DAO.matcher(s).matches()) {
                return true;
            }
            List<Cls> up = lookup(cur, s);
            if (up.isEmpty()) {
                return false;
            }
            cur = up.get(0);
            s = cur.superName;
        }
        return false;
    }

    // ---------------------------------------------------------------- 추적

    private void walk(Cls c, MethodDeclaration m, int depth, Acc acc) {
        if (!acc.visited.add(c.fqcn + "#" + m.getNameAsString() + "/" + m.getParameters().size())) {
            return;
        }
        if (depth > MAX_DEPTH || ++acc.frames > MAX_FRAMES) {
            note("depth", c.file, line(m), c.name + "." + m.getNameAsString());
            return;
        }
        if (!jpa.isEmpty()) {
            querydsl(c, m, acc);
        }
        for (MethodCallExpr mc : m.findAll(MethodCallExpr.class)) {
            String name = mc.getNameAsString();
            int argc = mc.getArguments().size();
            Optional<Expression> scope = mc.getScope();
            boolean self = scope.isEmpty() || scope.get().isThisExpr() || scope.get().isSuperExpr();
            if (SINKS.contains(name) && argc >= 1 && sink(c, scope, self, name, argc)) {
                statement(c, m, mc, acc);
                continue;
            }
            List<Cls> targets = new ArrayList<>();
            if (scope.isEmpty() || scope.get().isThisExpr()) {
                targets.add(c);
            } else if (scope.get().isSuperExpr()) {
                if (c.superName != null) {
                    targets.addAll(lookup(c, c.superName));
                }
            } else {
                if (!jpa.isEmpty() && scope.get() instanceof MethodCallExpr sm && sm.getNameAsString().equals("getEntityManager")
                        && entityManager(c, m, mc, acc)) {
                    continue;
                }
                String fieldName = null;
                if (scope.get() instanceof NameExpr n) {
                    fieldName = n.getNameAsString();
                } else if (scope.get() instanceof FieldAccessExpr fa && fa.getScope().isThisExpr()) {
                    fieldName = fa.getNameAsString();
                }
                if (fieldName == null) {
                    continue;
                }
                Field f = field(c, fieldName);
                if (!jpa.isEmpty()) {
                    String vt = f != null ? f.type() : scope.get() instanceof NameExpr ? localType(m, fieldName) : null;
                    if ("EntityManager".equals(vt) && entityManager(c, m, mc, acc)) {
                        continue;
                    }
                    if (f != null) {
                        List<JpaIndex.Repo> rs = jpa.repos(f.type(), from(c));
                        if (!rs.isEmpty()) {
                            repoCall(c, rs, fieldName, f, name, argc, depth, acc);
                            continue;
                        }
                    }
                }
                if (f != null) {
                    targets.addAll(resolve(c, fieldName, f));
                } else if (scope.get() instanceof NameExpr && byName.containsKey(fieldName)) {
                    targets.addAll(lookup(c, fieldName)); // static 호출
                }
            }
            for (Cls t : targets) {
                if (t.iface) {
                    if (t.mapperAnno || dao.matcher(t.name).matches()) {
                        acc.stmts.add(new Stmt(t.fqcn + "." + name, "mapper"));
                    }
                    continue;
                }
                for (Target x : methods(t, name, argc)) {
                    if (x.method().getBody().isPresent()) {
                        walk(x.owner(), x.method(), depth + 1, acc);
                    }
                }
            }
        }
    }

    /** 문장 실행 호출인가 — DAO 안 이름 없는 호출(같은 이름·인자 수의 선언 메서드가 없을 때), 또는 SqlSession 필드·getSqlSession() 위 */
    private boolean sink(Cls c, Optional<Expression> scope, boolean self, String name, int argc) {
        if (self) {
            return isDao(c) && !jpaDao(c) && methods(c, name, argc).stream().noneMatch(x -> x.method().getParameters().size() == argc);
        }
        Expression s = scope.get();
        if (s instanceof NameExpr n) {
            Field f = field(c, n.getNameAsString());
            return f != null && f.type().contains("SqlSession");
        }
        return s instanceof MethodCallExpr mc && (mc.getNameAsString().equals("getSqlSession") || mc.getNameAsString().equals("getSqlSessionTemplate"));
    }

    private void statement(Cls c, MethodDeclaration m, MethodCallExpr mc, Acc acc) {
        Expression a = mc.getArgument(0);
        int line = line(mc);
        if (a instanceof StringLiteralExpr sl) {
            acc.stmts.add(new Stmt(sl.getValue(), "literal"));
            return;
        }
        if (a instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            Expression left = b;
            while (left instanceof BinaryExpr lb && lb.getOperator() == BinaryExpr.Operator.PLUS) {
                left = lb.getLeft();
            }
            if (left instanceof StringLiteralExpr sl) {
                acc.stmts.add(new Stmt(sl.getValue(), "prefix"));
                note("prefix", c.file, line, sl.getValue());
                return;
            }
        }
        if (a instanceof NameExpr n) {
            List<String> lits = varLiterals(c, m, n.getNameAsString());
            if (!lits.isEmpty()) {
                lits.forEach(l -> acc.stmts.add(new Stmt(l, "var")));
                return;
            }
        }
        note("statement", c.file, line, c.name + "." + m.getNameAsString());
    }

    /** 메서드 안 선언·대입의 리터럴, 없으면 클래스 필드 초기값 */
    private static List<String> varLiterals(Cls c, MethodDeclaration m, String var) {
        List<String> out = new ArrayList<>();
        for (VariableDeclarator v : m.findAll(VariableDeclarator.class)) {
            if (v.getNameAsString().equals(var) && v.getInitializer().orElse(null) instanceof StringLiteralExpr sl) {
                out.add(sl.getValue());
            }
        }
        for (AssignExpr as : m.findAll(AssignExpr.class)) {
            if (as.getTarget() instanceof NameExpr t && t.getNameAsString().equals(var) && as.getValue() instanceof StringLiteralExpr sl) {
                out.add(sl.getValue());
            }
        }
        if (out.isEmpty()) {
            for (FieldDeclaration fd : c.decl.getFields()) {
                for (VariableDeclarator v : fd.getVariables()) {
                    if (v.getNameAsString().equals(var) && v.getInitializer().orElse(null) instanceof StringLiteralExpr sl) {
                        out.add(sl.getValue());
                    }
                }
            }
        }
        return out;
    }

    private Field field(Cls c, String name) {
        Set<String> seen = new HashSet<>();
        Cls cur = c;
        while (cur != null && seen.add(cur.fqcn)) {
            Field f = cur.fields.get(name);
            if (f != null) {
                return f;
            }
            List<Cls> up = cur.superName == null ? List.of() : lookup(cur, cur.superName);
            cur = up.isEmpty() ? null : up.get(0);
        }
        return null;
    }

    /** 필드 타입 → 대상 클래스. 인터페이스면 구현, 구현이 없으면 인터페이스 그대로(Mapper 인터페이스) */
    private List<Cls> resolve(Cls owner, String fieldName, Field f) {
        List<Cls> out = new ArrayList<>();
        for (Cls t : lookup(owner, f.type())) {
            if (!t.iface) {
                out.add(t);
                continue;
            }
            List<Cls> impl = implementors.getOrDefault(t.name, List.of());
            if (impl.isEmpty()) {
                out.add(t);
            } else {
                out.addAll(impl);
            }
        }
        if (out.size() > 1 && f.bean() != null) {
            List<Cls> named = out.stream().filter(x -> x.bean.equals(f.bean())).toList();
            if (!named.isEmpty()) {
                out = new ArrayList<>(named);
            }
        }
        if (out.size() > 1) {
            note("ambiguous", owner.file, f.line(), owner.name + "." + fieldName);
        }
        return out;
    }

    /** 메서드와 그것을 선언한 클래스 */
    private record Target(Cls owner, MethodDeclaration method) {
    }

    /** 이름이 같은 메서드 — 인자 수가 같은 것, 없으면 이름만 같은 것 전부(오버로드 합침). 이 클래스에 없으면 조상 클래스에서 */
    private List<Target> methods(Cls c, String name, int argc) {
        Set<String> seen = new HashSet<>();
        Cls cur = c;
        while (cur != null && seen.add(cur.fqcn)) {
            List<MethodDeclaration> ms = cur.methods.getOrDefault(name, List.of());
            if (!ms.isEmpty()) {
                List<MethodDeclaration> same = ms.stream().filter(x -> x.getParameters().size() == argc).toList();
                Cls owner = cur;
                return (same.isEmpty() ? ms : same).stream().map(md -> new Target(owner, md)).toList();
            }
            List<Cls> up = cur.superName == null ? List.of() : lookup(cur, cur.superName);
            cur = up.isEmpty() ? null : up.get(0);
        }
        return List.of();
    }

    // ---------------------------------------------------------------- JPA(6-15)

    private static JpaIndex.From from(Cls c) {
        return JpaIndex.From.of(c.pkg, c.imports, c.file);
    }

    /** JPA 표지가 있는 DAO — 이름 없는 insert·update·delete 를 MyBatis 싱크로 안 본다(QueryDSL 사슬의 update(q) 등) */
    private boolean jpaDao(Cls c) {
        if (jpa.isEmpty()) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (Cls cur = c; cur != null && seen.add(cur.fqcn);) {
            if ("QuerydslRepositorySupport".equals(cur.superName)
                    || cur.fields.values().stream().anyMatch(f -> f.type().equals("EntityManager") || f.type().equals("JPAQueryFactory"))) {
                return true;
            }
            List<Cls> up = cur.superName == null ? List.of() : lookup(cur, cur.superName);
            cur = up.isEmpty() ? null : up.get(0);
        }
        return false;
    }

    /** 저장소 필드 호출 — 커스텀 구현 본문이 있으면 그리로, 없으면 「저장소FQCN.메서드」 문장 */
    private void repoCall(Cls c, List<JpaIndex.Repo> rs, String fieldName, Field f, String name, int argc, int depth, Acc acc) {
        if (rs.size() > 1) {
            note("ambiguous", c.file, f.line(), c.name + "." + fieldName);
            return;
        }
        JpaIndex.Repo r = rs.get(0);
        List<String> impls = new ArrayList<>();
        r.customs().forEach(x -> impls.add(x + "Impl"));
        impls.add(r.simpleName() + "Impl");
        boolean walked = false;
        for (String in : impls) {
            for (Cls t : byName.getOrDefault(in, List.of())) {
                if (t.iface) {
                    continue;
                }
                for (Target x : methods(t, name, argc)) {
                    if (x.method().getBody().isPresent()) {
                        walk(x.owner(), x.method(), depth + 1, acc);
                        walked = true;
                    }
                }
            }
        }
        if (!walked) {
            acc.stmts.add(new Stmt(r.fqcn() + "." + name, "jpa"));
        }
    }

    /** EntityManager 호출 — 표는 색인 곁 등록부에. 표를 안 건드리는 호출(flush·clear …)은 false(그냥 지나간다) */
    private boolean entityManager(Cls c, MethodDeclaration m, MethodCallExpr mc, Acc acc) {
        String name = mc.getNameAsString();
        int line = line(mc);
        String where = c.name + "." + m.getNameAsString();
        List<SqlTables.Ref> refs = new ArrayList<>();
        List<Unresolved> out = new ArrayList<>();
        Expression a0 = mc.getArguments().isEmpty() ? null : mc.getArgument(0);
        switch (name) {
            case "persist", "merge", "remove", "find", "getReference" -> {
                String type = a0 instanceof ClassExpr ce ? simple(ce.getType())
                        : name.equals("find") || name.equals("getReference") ? null : valueType(c, m, a0);
                EnumSet<SqlTables.Crud> crud = switch (name) {
                    case "persist" -> EnumSet.of(SqlTables.Crud.C);
                    case "merge" -> EnumSet.of(SqlTables.Crud.C, SqlTables.Crud.U);
                    case "remove" -> EnumSet.of(SqlTables.Crud.D);
                    default -> EnumSet.of(SqlTables.Crud.R);
                };
                List<JpaIndex.Entity> hit = type == null ? List.of() : jpa.entitiesNamed(type, from(c));
                if (hit.size() == 1) {
                    refs.add(new SqlTables.Ref(hit.get(0).table(), crud));
                } else {
                    note(hit.isEmpty() ? "jpaType" : "ambiguous", c.file, line, where + ":" + (type == null ? "?" : type));
                }
            }
            case "createQuery", "createNativeQuery" -> {
                String q = JpaIndex.literal(a0);
                if (q == null) {
                    note("statement", c.file, line, where);
                } else if (name.equals("createNativeQuery")) {
                    refs.addAll(SqlTables.extract(q, null).refs());
                } else {
                    refs.addAll(jpa.jpql(q, from(c), c.file, line, where, out));
                }
            }
            case "createNamedQuery" -> note("namedQuery", c.file, line, where);
            case "getCriteriaBuilder" -> note("criteria", c.file, line, where);
            default -> {
                return false;
            }
        }
        out.forEach(u -> note(u.kind(), u.file(), u.line(), u.detail()));
        if (!refs.isEmpty()) {
            String id = c.fqcn + "." + m.getNameAsString() + "#em";
            jpa.record(id, refs);
            acc.stmts.add(new Stmt(id, "jpa"));
        }
        return true;
    }

    /** 식의 타입 단순 이름 — new X()·지역 변수·매개변수·필드 선언. 모르면 null */
    private String valueType(Cls c, MethodDeclaration m, Expression e) {
        if (e instanceof ObjectCreationExpr oc) {
            return oc.getType().getName().getIdentifier();
        }
        if (e instanceof NameExpr n) {
            String t = localType(m, n.getNameAsString());
            if (t != null) {
                return t;
            }
            Field f = field(c, n.getNameAsString());
            return f == null ? null : f.type();
        }
        return null;
    }

    /** 메서드 안 지역 변수·매개변수 선언의 타입 단순 이름 */
    private static String localType(MethodDeclaration m, String var) {
        for (Parameter p : m.getParameters()) {
            if (p.getNameAsString().equals(var)) {
                return simple(p.getType());
            }
        }
        for (VariableDeclarator v : m.findAll(VariableDeclarator.class)) {
            if (v.getNameAsString().equals(var)) {
                return simple(v.getType());
            }
        }
        return null;
    }

    private static String simple(Type t) {
        return t instanceof ClassOrInterfaceType ct ? ct.getName().getIdentifier() : t.asString();
    }

    // ---------------------------------------------------------------- QueryDSL(6-16)

    /** 이 메서드의 QueryDSL 사슬을 모아 「클래스FQCN.메서드#qdsl」 하나로 */
    private void querydsl(Cls c, MethodDeclaration m, Acc acc) {
        List<SqlTables.Ref> refs = new ArrayList<>();
        String where = c.name + "." + m.getNameAsString();
        for (MethodCallExpr mc : m.findAll(MethodCallExpr.class)) {
            SqlTables.Crud crud = switch (mc.getNameAsString()) {
                case "selectFrom", "from", "join", "leftJoin", "rightJoin", "innerJoin", "fullJoin" -> SqlTables.Crud.R;
                case "update" -> SqlTables.Crud.U;
                case "delete" -> SqlTables.Crud.D;
                case "insert" -> SqlTables.Crud.C;
                default -> null;
            };
            if (crud == null || mc.getArguments().isEmpty() || !qdslChain(c, m, mc)) {
                continue;
            }
            Expression q = mc.getName().getIdentifier().endsWith("oin") && mc.getArguments().size() >= 2 ? mc.getArgument(1) : mc.getArgument(0);
            String type = qType(c, m, q);
            if (type == null) {
                continue; // 연관 경로(QX.x.items) — 대상 엔티티를 모른다
            }
            List<JpaIndex.Entity> hit = type.length() > 1 ? jpa.entitiesNamed(type.substring(1), from(c)) : List.of();
            String qFqcn = c.imports.get(type);
            if (qFqcn == null && q instanceof NameExpr qn && c.statics.containsKey(qn.getNameAsString())) {
                qFqcn = c.statics.get(qn.getNameAsString());
            }
            if (hit.size() > 1 && qFqcn != null) {
                String want = qFqcn.substring(0, qFqcn.lastIndexOf('.') + 1) + type.substring(1); // Q 클래스는 엔티티와 같은 패키지에 난다
                List<JpaIndex.Entity> byQ = hit.stream().filter(e -> e.fqcn().equals(want)).toList();
                hit = byQ.isEmpty() ? hit : byQ;
            }
            if (hit.size() == 1) {
                refs.add(new SqlTables.Ref(hit.get(0).table(), EnumSet.of(crud)));
            } else {
                note(hit.isEmpty() ? "querydsl" : "ambiguous", c.file, line(mc), where + ":" + type);
            }
        }
        if (!refs.isEmpty()) {
            String id = c.fqcn + "." + m.getNameAsString() + "#qdsl";
            jpa.record(id, refs);
            acc.stmts.add(new Stmt(id, "qdsl"));
        }
    }

    /** 사슬의 뿌리가 QueryDSL 인가 — 뿌리 타입 필드·지역 변수·new, JPAExpressions, QuerydslRepositorySupport 안 이름 없는 호출 */
    private boolean qdslChain(Cls c, MethodDeclaration m, MethodCallExpr mc) {
        Expression cur = mc;
        while (cur instanceof MethodCallExpr x) {
            if (x.getScope().isEmpty()) {
                return support(c);
            }
            cur = x.getScope().get();
        }
        if (cur instanceof NameExpr n) {
            if (n.getNameAsString().equals("JPAExpressions")) {
                return true;
            }
            String t = localType(m, n.getNameAsString());
            if (t == null) {
                Field f = field(c, n.getNameAsString());
                t = f == null ? null : f.type();
            }
            return t != null && QDSL_ROOTS.contains(t);
        }
        if (cur instanceof FieldAccessExpr fa && fa.getScope().isThisExpr()) {
            Field f = field(c, fa.getNameAsString());
            return f != null && QDSL_ROOTS.contains(f.type());
        }
        return cur instanceof ObjectCreationExpr oc && QDSL_ROOTS.contains(oc.getType().getName().getIdentifier());
    }

    private boolean support(Cls c) {
        Set<String> seen = new HashSet<>();
        for (Cls cur = c; cur != null && seen.add(cur.fqcn);) {
            if ("QuerydslRepositorySupport".equals(cur.superName)) {
                return true;
            }
            List<Cls> up = cur.superName == null ? List.of() : lookup(cur, cur.superName);
            cur = up.isEmpty() ? null : up.get(0);
        }
        return false;
    }

    /** Q 식의 Q 타입 단순 이름 — QX.x · new QX(…) · QX 타입 변수·필드 · static import 이름. 연관 경로·모르는 식은 null */
    private String qType(Cls c, MethodDeclaration m, Expression e) {
        if (e instanceof FieldAccessExpr fa && fa.getScope() instanceof NameExpr sn && isQ(sn.getNameAsString())) {
            return sn.getNameAsString();
        }
        if (e instanceof ObjectCreationExpr oc && isQ(oc.getType().getName().getIdentifier())) {
            return oc.getType().getName().getIdentifier();
        }
        if (e instanceof NameExpr n) {
            String t = localType(m, n.getNameAsString());
            if (t == null) {
                Field f = field(c, n.getNameAsString());
                String st = c.statics.get(n.getNameAsString());
                t = f != null ? f.type() : st == null ? null : st.substring(st.lastIndexOf('.') + 1);
            }
            return t != null && isQ(t) ? t : null;
        }
        return null;
    }

    private static boolean isQ(String s) {
        return s.length() > 1 && s.charAt(0) == 'Q' && Character.isUpperCase(s.charAt(1));
    }

    // ---------------------------------------------------------------- 뷰·설명

    /**
     * json — {@code @RestController}·{@code @ResponseBody}·리턴이 String·ModelAndView 가 아님(VO·void·Map …)·뷰가 {@code jsonView} 뿐.
     * 그 밖은 view — 뷰 이름을 못 풀어도(helper 메서드 리턴 등) 화면 프로그램이다
     */
    private static String kind(Cls c, MethodDeclaration m, List<View> views) {
        boolean rest = c.decl.isAnnotationPresent("RestController") || m.isAnnotationPresent("ResponseBody");
        String rt = m.getType().asString();
        boolean page = rt.equals("String") || rt.equals("java.lang.String") || rt.equals("ModelAndView");
        if (rest || !page) {
            return "json";
        }
        boolean onlyJson = views.isEmpty() && m.findAll(StringLiteralExpr.class).stream().anyMatch(s -> s.getValue().equals("jsonView"));
        return onlyJson ? "json" : "view";
    }

    private List<View> views(Cls c, MethodDeclaration m) {
        Set<String> names = new LinkedHashSet<>();
        boolean[] dynamic = {false};
        for (ObjectCreationExpr oc : m.findAll(ObjectCreationExpr.class)) {
            if (oc.getType().getNameAsString().equals("ModelAndView") && !oc.getArguments().isEmpty()) {
                Expression a = oc.getArgument(0);
                if (a instanceof ObjectCreationExpr inner) {
                    names.add("class:" + inner.getType().getNameAsString());
                } else {
                    value(m, a, names, dynamic);
                }
            }
        }
        for (MethodCallExpr mc : m.findAll(MethodCallExpr.class)) {
            if (mc.getNameAsString().equals("setViewName") && mc.getArguments().size() == 1) {
                value(m, mc.getArgument(0), names, dynamic);
            }
        }
        String rt = m.getType().asString();
        if (rt.equals("String") || rt.equals("java.lang.String")) {
            for (ReturnStmt rs : m.findAll(ReturnStmt.class)) {
                rs.getExpression().ifPresent(e -> value(m, e, names, dynamic));
            }
        }
        if (dynamic[0]) {
            note("viewDynamic", c.file, line(m), c.name + "." + m.getNameAsString());
        }
        List<View> out = new ArrayList<>();
        for (String n : names) {
            if (n.startsWith("class:")) {
                out.add(new View("class", n.substring(6)));
            } else if (n.startsWith("redirect:")) {
                out.add(new View("redirect", url(n.substring(9))));
            } else if (n.startsWith("forward:")) {
                out.add(new View("forward", url(n.substring(8))));
            } else if (!n.equals("jsonView") && !n.isBlank()) {
                out.add(new View("view", n));
            }
        }
        return out.stream().distinct().toList();
    }

    private static String url(String s) {
        int q = s.indexOf('?');
        return (q < 0 ? s : s.substring(0, q)).trim();
    }

    /** 뷰 이름 식 — 리터럴, 리터럴을 받은 지역 변수(대입 전부), 이어붙임은 앞쪽 리터럴을 쓰고 나머지가 식이면 dynamic */
    private static void value(MethodDeclaration m, Expression e, Set<String> out, boolean[] dynamic) {
        if (e instanceof StringLiteralExpr sl) {
            out.add(sl.getValue());
        } else if (e instanceof NameExpr n) {
            boolean any = false;
            for (VariableDeclarator v : m.findAll(VariableDeclarator.class)) {
                if (v.getNameAsString().equals(n.getNameAsString()) && v.getInitializer().isPresent()) {
                    any |= assigned(m, n.getNameAsString(), v.getInitializer().get(), out, dynamic);
                }
            }
            for (AssignExpr as : m.findAll(AssignExpr.class)) {
                if (as.getTarget() instanceof NameExpr t && t.getNameAsString().equals(n.getNameAsString())) {
                    any |= assigned(m, n.getNameAsString(), as.getValue(), out, dynamic);
                }
            }
            if (!any) {
                dynamic[0] = true;
            }
        } else if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            List<Expression> parts = new ArrayList<>();
            flatten(b, parts);
            StringBuilder lead = new StringBuilder();
            int i = 0;
            while (i < parts.size() && parts.get(i) instanceof StringLiteralExpr sl) {
                lead.append(sl.getValue());
                i++;
            }
            if (i == 0) {
                value(m, parts.get(0), out, dynamic);
                i = 1;
            } else {
                out.add(lead.toString());
            }
            if (i < parts.size()) {
                dynamic[0] = true;
            }
        } else if (!e.isNullLiteralExpr() && !(e instanceof ObjectCreationExpr)) {
            dynamic[0] = true;
        }
    }

    /** 변수에 들어간 값 하나 — 자기 자신에 덧붙이기(x = x + "…")는 dynamic 만. 리터럴을 하나라도 얻었으면 true */
    private static boolean assigned(MethodDeclaration m, String var, Expression v, Set<String> out, boolean[] dynamic) {
        if (v.isNullLiteralExpr()) {
            return false;
        }
        if (v instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            List<Expression> parts = new ArrayList<>();
            flatten(b, parts);
            if (parts.get(0) instanceof NameExpr n && n.getNameAsString().equals(var)) {
                dynamic[0] = true;
                return false;
            }
        }
        if (v instanceof NameExpr n && n.getNameAsString().equals(var)) {
            return false;
        }
        int before = out.size();
        value(m, v, out, dynamic);
        return out.size() > before || v instanceof StringLiteralExpr;
    }

    private static void flatten(Expression e, List<Expression> out) {
        if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            flatten(b.getLeft(), out);
            flatten(b.getRight(), out);
        } else {
            out.add(e);
        }
    }

    /** javadoc 설명 첫 문장(마침표·「。」+공백, 또는 줄 끝까지) 100자. 없으면 블록 주석, 그것도 없으면 빈 칸 */
    static String description(MethodDeclaration m) {
        String d = m.getJavadoc().map(j -> j.getDescription().toText()).orElse(null);
        if (d == null) {
            d = m.getComment().filter(Comment::isBlockComment).map(Comment::getContent).map(x -> x.replaceAll("(?m)^\\s*\\*", ""))
                    .orElse("");
        }
        d = TAG.matcher(d).replaceAll(" ").strip();
        int nl = d.indexOf('\n');
        if (nl >= 0) {
            d = d.substring(0, nl).strip();
        }
        for (int i = 0; i < d.length(); i++) {
            char ch = d.charAt(i);
            if ((ch == '.' || ch == '。') && (i + 1 == d.length() || Character.isWhitespace(d.charAt(i + 1)))) {
                d = d.substring(0, i + 1);
                break;
            }
        }
        d = d.replaceAll("\\s+", " ").strip();
        return d.length() > DESCR_MAX ? d.substring(0, DESCR_MAX) : d;
    }

    // ---------------------------------------------------------------- 공통

    private void note(String kind, String file, int line, String detail) {
        if (quiet) {
            return;
        }
        unresolved.putIfAbsent(String.format("%s|%s|%08d|%s", kind, file, line, detail), new Unresolved(kind, file, line, detail));
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(1);
    }
}
