package kr.ejg.toolbox.core.analyze;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.check.JavaSource;
import kr.ejg.toolbox.core.check.Source;

/**
 * JPA 색인(6-14, 설계 14 U-6 ①) — 엔티티 → 표, 저장소 인터페이스 → 엔티티, 저장소 메서드 → 표·CRUD. 심볼 해석 없이 이름으로 푼다.
 * <p>
 * 엔티티: {@code javax·jakarta.persistence} 의 {@code @Entity}. 표는 {@code @Table(name)}, 없으면 상속(기본 SINGLE_TABLE 이면 루트 엔티티 표,
 * {@code JOINED}·{@code TABLE_PER_CLASS} 면 자기 표) 다음 클래스 이름 SNAKE_UPPER + {@code unresolved(entityName)}. {@code @MappedSuperclass} 는 표 없음.
 * {@code @SecondaryTable}·{@code @JoinTable}·{@code @CollectionTable} 은 {@code extraTables}(목록만 — CRUD 에는 주 표만 싣는다).
 * 저장소: Spring Data 바탕 인터페이스를 (사슬로) 상속한 인터페이스 — 엔티티는 첫 타입 인자. {@code @NoRepositoryBean} 은 바탕이라 뺀다.
 * 메서드: Spring Data JPA {@code @Query}(네이티브는 {@link SqlTables}, JPQL 은 동사 + 엔티티 이름), 없으면 이름 규칙.
 * 같은 단순 이름이 여럿이면 참조하는 쪽의 import → 같은 패키지 → 같은 모듈(경로의 {@code src/main/java} 앞) 순으로 고른다
 */
public final class JpaIndex {

    /** name — JPQL 식별자({@code @Entity(name)}, 없으면 클래스 단순 이름). table — 대문자·스키마 뗌. guessed — 클래스 이름에서 지었다 */
    public record Entity(String fqcn, String name, String table, boolean guessed, List<String> extraTables, String file) {

        public Entity {
            extraTables = List.copyOf(extraTables);
        }
    }

    /** entity — 엔티티 FQCN(못 풀면 null). customs — Spring Data 가 아닌 상위 인터페이스 단순 이름(커스텀 저장소, 6-15) */
    public record Repo(String fqcn, String entity, List<String> customs, String file) {

        public Repo {
            customs = List.copyOf(customs);
        }

        public String simpleName() {
            return fqcn.substring(fqcn.lastIndexOf('.') + 1);
        }
    }

    /** 참조하는 쪽 — 같은 단순 이름을 가를 때 쓴다 */
    public record From(String pkg, Map<String, String> imports, String module) {

        public From {
            imports = Map.copyOf(imports);
        }

        public static From of(String pkg, Map<String, String> imports, String file) {
            return new From(pkg, imports, JpaIndex.module(file));
        }
    }

    /** Spring Data 저장소 바탕 — 이 이름을 상속하면 저장소다 */
    static final Set<String> BASES = Set.of("Repository", "CrudRepository", "ListCrudRepository", "PagingAndSortingRepository",
            "ListPagingAndSortingRepository", "JpaRepository", "JpaRepositoryImplementation", "RevisionRepository");
    private static final List<String> READ = List.of("find", "get", "read", "query", "search", "stream", "count", "exists");
    /** 표를 안 건드리는 저장소 메서드 — 미해결로도 안 센다 */
    private static final Set<String> QUIET = Set.of("flush", "equals", "hashCode", "toString", "getClass");
    private static final Set<String> PERSISTENCE = Set.of("javax.persistence", "jakarta.persistence");
    private static final String SPRING_JPA = "org.springframework.data.jpa.repository";
    private static final Pattern JPQL_TOKEN = Pattern.compile("[A-Za-z_$][\\w$.]*|[(),]");
    private static final Set<String> JPQL_STOP = Set.of("WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FETCH", "ON", "WITH", "GROUP",
            "ORDER", "HAVING", "SET", "AS", "UNION", "SELECT", "FROM", "AND", "OR");

    private static final class Cls {
        String name;
        String fqcn;
        String pkg;
        String file;
        Map<String, String> imports = new HashMap<>();
        Set<String> stars = new HashSet<>();
        ClassOrInterfaceDeclaration decl;

        From from() {
            return From.of(pkg, imports, file);
        }
    }

    private final Map<String, List<Cls>> byName = new HashMap<>();
    private final Map<String, Cls> types = new HashMap<>();
    private final Map<String, Entity> entities = new TreeMap<>();
    private final Map<String, List<Entity>> byEntityName = new HashMap<>();
    private final Map<String, Repo> repos = new TreeMap<>();
    private final Map<String, Unresolved> unresolved = new TreeMap<>();
    private final Map<String, Optional<Entity>> memo = new HashMap<>();

    private JpaIndex() {
    }

    public static JpaIndex empty() {
        return new JpaIndex();
    }

    public static JpaIndex scan(List<Source> java) {
        JpaIndex x = new JpaIndex();
        x.run(java);
        return x;
    }

    public List<Entity> entities() {
        return List.copyOf(entities.values());
    }

    public List<Repo> repositories() {
        return List.copyOf(repos.values());
    }

    /** 색인하며 못 푼 자리 — entityName(표 이름을 지었다)·ambiguous(저장소 엔티티가 여럿)·jpaType(저장소 엔티티를 모른다) */
    public List<Unresolved> unresolved() {
        return List.copyOf(unresolved.values());
    }

    public boolean isEmpty() {
        return entities.isEmpty() && repos.isEmpty();
    }

    public Optional<Repo> repo(String fqcn) {
        return Optional.ofNullable(repos.get(fqcn));
    }

    /** 단순 이름 → 저장소(참조하는 쪽으로 가른다). 여럿이 남으면 전부 */
    public List<Repo> repos(String simple, From from) {
        return pick(repos.values().stream().filter(r -> r.simpleName().equals(simple)).toList(), r -> types.get(r.fqcn()), simple, from);
    }

    /** 단순 이름 → 엔티티(Q클래스·EntityManager 인자 — 6-15·6-16). 여럿이 남으면 전부 */
    public List<Entity> entitiesNamed(String simple, From from) {
        return pick(entities.values().stream().filter(e -> e.fqcn().endsWith("." + simple) || e.fqcn().equals(simple)).toList(),
                e -> types.get(e.fqcn()), simple, from);
    }

    // ---------------------------------------------------------------- 색인

    private void run(List<Source> java) {
        JavaSource parser = new JavaSource();
        for (Source s : java) {
            ParseResult<CompilationUnit> r = parser.parse(s.text());
            if (!JavaSource.ok(r)) {
                continue; // 읽기 실패는 JavaGraph 가 parse 로 센다
            }
            CompilationUnit cu = r.getResult().get();
            String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            Map<String, String> imports = new HashMap<>();
            Set<String> stars = new HashSet<>();
            for (ImportDeclaration i : cu.getImports()) {
                if (i.isStatic()) {
                    continue;
                }
                if (i.isAsterisk()) {
                    stars.add(i.getNameAsString());
                } else {
                    imports.put(i.getName().getIdentifier(), i.getNameAsString());
                }
            }
            for (ClassOrInterfaceDeclaration t : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                Cls c = new Cls();
                c.decl = t;
                c.name = t.getNameAsString();
                c.pkg = pkg;
                c.fqcn = t.getFullyQualifiedName().orElse(pkg.isEmpty() ? c.name : pkg + "." + c.name);
                c.file = s.rel();
                c.imports = imports;
                c.stars = stars;
                byName.computeIfAbsent(c.name, k -> new ArrayList<>()).add(c);
                types.put(c.fqcn, c);
            }
        }
        List<Cls> all = new ArrayList<>(types.values());
        all.sort((a, b) -> a.fqcn.compareTo(b.fqcn));
        for (Cls c : all) {
            if (!c.decl.isInterface()) {
                entity(c).ifPresent(e -> {
                    entities.put(e.fqcn(), e);
                    byEntityName.computeIfAbsent(e.name(), k -> new ArrayList<>()).add(e);
                });
            }
        }
        for (Cls c : all) {
            if (c.decl.isInterface() && !c.decl.isAnnotationPresent("NoRepositoryBean")) {
                String arg = base(c, new HashSet<>());
                if (arg != null) {
                    repos.put(c.fqcn, repo(c, arg));
                }
            }
        }
    }

    private Optional<Entity> entity(Cls c) {
        Optional<Entity> m = memo.get(c.fqcn);
        if (m != null) {
            return m;
        }
        memo.put(c.fqcn, Optional.empty()); // 상속 고리 막기
        Optional<AnnotationExpr> ea = annotation(c, c.decl.getAnnotations(), "Entity", PERSISTENCE);
        if (ea.isEmpty()) {
            return Optional.empty();
        }
        List<String> nm = strings(ea.get(), "name");
        String name = nm.isEmpty() || nm.get(0).isBlank() ? c.name : nm.get(0);
        Set<String> extra = new LinkedHashSet<>();
        for (AnnotationExpr a : c.decl.findAll(AnnotationExpr.class)) {
            String id = a.getName().getIdentifier();
            if ((id.equals("SecondaryTable") || id.equals("JoinTable") || id.equals("CollectionTable")) && jpa(c, a, id, PERSISTENCE)) {
                strings(a, "name").stream().filter(x -> !x.isBlank()).map(JpaIndex::norm).forEach(extra::add);
            }
        }
        String table = null;
        boolean guessed = false;
        Optional<AnnotationExpr> ta = annotation(c, c.decl.getAnnotations(), "Table", PERSISTENCE);
        if (ta.isPresent()) {
            List<String> tn = strings(ta.get(), "name");
            if (!tn.isEmpty() && !tn.get(0).isBlank()) {
                table = norm(tn.get(0));
            }
        }
        if (table == null) {
            Cls parent = superEntity(c);
            if (parent != null && !ownTable(root(c))) {
                Entity pe = entity(parent).orElse(null);
                if (pe != null) {
                    table = pe.table();
                    guessed = pe.guessed();
                }
            }
        }
        if (table == null) {
            table = snake(c.name);
            guessed = true;
            note("entityName", c.file, line(c.decl), c.name);
        }
        Optional<Entity> e = Optional.of(new Entity(c.fqcn, name, table, guessed, new ArrayList<>(extra), c.file));
        memo.put(c.fqcn, e);
        return e;
    }

    /** 가장 가까운 엔티티 조상(MappedSuperclass·엔티티 아닌 클래스는 건너뛴다) */
    private Cls superEntity(Cls c) {
        Set<String> seen = new HashSet<>();
        Cls cur = c;
        while (seen.add(cur.fqcn)) {
            NodeList<ClassOrInterfaceType> ext = cur.decl.getExtendedTypes();
            if (ext.isEmpty()) {
                return null;
            }
            List<Cls> up = lookup(ext.get(0).getName().getIdentifier(), cur.from());
            if (up.size() != 1) {
                return null;
            }
            cur = up.get(0);
            if (annotation(cur, cur.decl.getAnnotations(), "Entity", PERSISTENCE).isPresent()) {
                return cur;
            }
        }
        return null;
    }

    private Cls root(Cls c) {
        Cls cur = c;
        for (Cls up = superEntity(cur); up != null; up = superEntity(cur)) {
            cur = up;
        }
        return cur;
    }

    /** 루트의 {@code @Inheritance(strategy = JOINED | TABLE_PER_CLASS)} — 자식이 자기 표를 갖는다 */
    private boolean ownTable(Cls root) {
        return annotation(root, root.decl.getAnnotations(), "Inheritance", PERSISTENCE).map(a -> {
            String v = a instanceof NormalAnnotationExpr na ? na.getPairs().stream().filter(p -> p.getNameAsString().equals("strategy"))
                    .map(p -> p.getValue().toString()).findFirst().orElse("") : a instanceof SingleMemberAnnotationExpr sm ? sm.getMemberValue().toString() : "";
            return v.endsWith("JOINED") || v.endsWith("TABLE_PER_CLASS");
        }).orElse(false);
    }

    /** 이 인터페이스가 저장소면 엔티티 타입 인자(단순 이름), 아니면 null. 바탕 사슬의 타입 변수는 자식의 인자로 바꾼다 */
    private String base(Cls c, Set<String> seen) {
        if (!seen.add(c.fqcn)) {
            return null;
        }
        for (ClassOrInterfaceType x : c.decl.getExtendedTypes()) {
            String n = x.getName().getIdentifier();
            List<Type> args = x.getTypeArguments().map(List::copyOf).orElse(List.of());
            if (BASES.contains(n)) {
                return args.isEmpty() ? "" : simple(args.get(0));
            }
            List<Cls> up = lookup(n, c.from()).stream().filter(u -> u.decl.isInterface()).toList();
            if (up.size() != 1) {
                continue;
            }
            String a = base(up.get(0), seen);
            if (a == null) {
                continue;
            }
            NodeList<TypeParameter> params = up.get(0).decl.getTypeParameters();
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).getNameAsString().equals(a)) {
                    return i < args.size() ? simple(args.get(i)) : "";
                }
            }
            return a;
        }
        return null;
    }

    private Repo repo(Cls c, String arg) {
        String entity = null;
        if (arg.isEmpty()) {
            note("jpaType", c.file, line(c.decl), c.name);
        } else {
            List<Entity> hit = entitiesNamed(arg, c.from());
            if (hit.size() == 1) {
                entity = hit.get(0).fqcn();
            } else {
                note(hit.isEmpty() ? "jpaType" : "ambiguous", c.file, line(c.decl), c.name + "<" + arg + ">");
            }
        }
        List<String> customs = new ArrayList<>();
        for (ClassOrInterfaceType x : c.decl.getExtendedTypes()) {
            String n = x.getName().getIdentifier();
            if (BASES.contains(n)) {
                continue;
            }
            List<Cls> up = lookup(n, c.from());
            if (up.size() == 1 && base(up.get(0), new HashSet<>()) == null) {
                customs.add(n);
            }
        }
        return new Repo(c.fqcn, entity, customs, c.file);
    }

    // ---------------------------------------------------------------- 메서드 → 표

    /**
     * 저장소 메서드 하나가 건드리는 표. 모르면 빈 목록 + {@code out} 에 미해결(jpaMethod·jpql·jpaType·ambiguous).
     * 선언에 Spring Data JPA {@code @Query} 가 있으면 그 글, 없으면 이름 규칙(find… R · save… C·U · delete…·remove… D)
     */
    public List<SqlTables.Ref> refs(String repoFqcn, String method, List<Unresolved> out) {
        Repo r = repos.get(repoFqcn);
        if (r == null) {
            return List.of();
        }
        Cls c = types.get(repoFqcn);
        Optional<MethodDeclaration> decl = c.decl.getMethodsByName(method).stream().findFirst();
        int line = decl.map(JpaIndex::line).orElse(line(c.decl));
        String where = c.name + "." + method;
        for (MethodDeclaration m : c.decl.getMethodsByName(method)) {
            Optional<AnnotationExpr> q = annotation(c, m.getAnnotations(), "Query", Set.of(SPRING_JPA));
            if (q.isPresent()) {
                String text = literal(value(q.get(), "value"), c.decl, 0);
                if (text == null) {
                    out.add(new Unresolved("jpql", c.file, line(m), where));
                    return List.of();
                }
                if (isTrue(value(q.get(), "nativeQuery"))) {
                    return SqlTables.extract(text, null).refs();
                }
                return jpql(text, c.from(), c.file, line(m), where, out);
            }
        }
        if (QUIET.contains(method)) {
            return List.of();
        }
        EnumSet<SqlTables.Crud> crud = crud(method);
        if (crud == null) {
            out.add(new Unresolved("jpaMethod", c.file, line, where));
            return List.of();
        }
        if (r.entity() == null) {
            out.add(new Unresolved("jpaType", c.file, line, where));
            return List.of();
        }
        return List.of(new SqlTables.Ref(entities.get(r.entity()).table(), crud));
    }

    /** 이름 규칙 — 모르면 null */
    static EnumSet<SqlTables.Crud> crud(String method) {
        for (String p : READ) {
            if (method.startsWith(p)) {
                return EnumSet.of(SqlTables.Crud.R);
            }
        }
        if (method.startsWith("save")) {
            return EnumSet.of(SqlTables.Crud.C, SqlTables.Crud.U);
        }
        if (method.startsWith("delete") || method.startsWith("remove")) {
            return EnumSet.of(SqlTables.Crud.D);
        }
        return null;
    }

    /**
     * JPQL → 표. 첫 낱말이 동사, FROM·JOIN·UPDATE·INTO 뒤의 식별자를 엔티티 이름으로 맞춘다. 첫 대상이 UPDATE·DELETE·INSERT 의 표, 나머지는 R.
     * {@code JOIN b.items i} 꼴(연관 경로)은 대상 엔티티를 몰라 건너뛴다. 못 맞춘 식별자는 {@code jpql}
     */
    public List<SqlTables.Ref> jpql(String text, From from, String file, int line, String where, List<Unresolved> out) {
        List<String> t = new ArrayList<>();
        Matcher m = JPQL_TOKEN.matcher(text.replaceAll("'(?:[^']|'')*'", "''"));
        while (m.find()) {
            t.add(m.group());
        }
        if (t.isEmpty()) {
            return List.of();
        }
        String verb = t.get(0).toUpperCase(Locale.ROOT);
        SqlTables.Crud first = switch (verb) {
            case "UPDATE" -> SqlTables.Crud.U;
            case "DELETE" -> SqlTables.Crud.D;
            case "INSERT" -> SqlTables.Crud.C;
            default -> SqlTables.Crud.R;
        };
        Map<String, EnumSet<SqlTables.Crud>> refs = new TreeMap<>();
        boolean firstTarget = true;
        for (int i = 0; i < t.size(); i++) {
            String u = t.get(i).toUpperCase(Locale.ROOT);
            int j;
            boolean bareDelete = u.equals("DELETE") && i == 0 && t.size() > 1 && !t.get(1).equalsIgnoreCase("FROM"); // Hibernate 의 DELETE X
            if (u.equals("FROM") || u.equals("INTO") || u.equals("UPDATE") && i == 0 || bareDelete) {
                j = i + 1;
            } else if (u.equals("JOIN")) {
                j = i + 1 < t.size() && t.get(i + 1).equalsIgnoreCase("FETCH") ? i + 2 : i + 1;
            } else {
                continue;
            }
            while (j < t.size()) {
                String id = t.get(j);
                if (id.equals("(") || JPQL_STOP.contains(id.toUpperCase(Locale.ROOT))) {
                    break;
                }
                String name = id.contains(".") ? id.substring(id.lastIndexOf('.') + 1) : id;
                boolean path = id.contains(".") && !Character.isUpperCase(name.charAt(0));
                if (!path && !name.isEmpty()) {
                    List<Entity> hit = byEntity(name, from);
                    if (hit.size() == 1) {
                        refs.computeIfAbsent(hit.get(0).table(), k -> EnumSet.noneOf(SqlTables.Crud.class))
                                .add(firstTarget ? first : SqlTables.Crud.R);
                    } else if (!id.contains(".") || hit.size() > 1) {
                        out.add(new Unresolved(hit.isEmpty() ? "jpql" : "ambiguous", file, line, where + ":" + name));
                    }
                    firstTarget = false;
                }
                j++;
                if (j < t.size() && t.get(j).equalsIgnoreCase("AS")) {
                    j++;
                }
                if (j < t.size() && !t.get(j).equals(",") && !t.get(j).equals("(") && !t.get(j).equals(")")
                        && !JPQL_STOP.contains(t.get(j).toUpperCase(Locale.ROOT))) {
                    j++; // 별칭
                }
                if (j < t.size() && t.get(j).equals(",") && !u.equals("JOIN")) {
                    j++; // FROM A a, B b
                    continue;
                }
                break;
            }
        }
        List<SqlTables.Ref> list = new ArrayList<>();
        refs.forEach((k, v) -> list.add(new SqlTables.Ref(k, v)));
        return list;
    }

    private List<Entity> byEntity(String name, From from) {
        return pick(byEntityName.getOrDefault(name, List.of()), e -> types.get(e.fqcn()), name, from);
    }

    // ---------------------------------------------------------------- 도움

    private List<Cls> lookup(String simple, From from) {
        return pick(byName.getOrDefault(simple, List.of()), c -> c, simple, from);
    }

    /** 같은 단순 이름 가르기 — import → 같은 패키지 → 같은 모듈. 못 가르면 전부 */
    private static <T> List<T> pick(List<T> cands, Function<T, Cls> type, String simple, From from) {
        if (cands.size() <= 1 || from == null) {
            return cands;
        }
        String imp = from.imports().get(simple);
        if (imp != null) {
            List<T> hit = cands.stream().filter(x -> type.apply(x).fqcn.equals(imp)).toList();
            if (!hit.isEmpty()) {
                return hit;
            }
        }
        List<T> pkg = cands.stream().filter(x -> type.apply(x).pkg.equals(from.pkg())).toList();
        if (!pkg.isEmpty()) {
            return pkg;
        }
        List<T> mod = cands.stream().filter(x -> module(type.apply(x).file).equals(from.module())).toList();
        return mod.isEmpty() ? cands : mod;
    }

    /** 경로의 {@code src/main/java/} 앞 — 다중 모듈 프로젝트의 모듈 폴더(없으면 빈 글) */
    static String module(String rel) {
        String r = rel == null ? "" : rel.replace('\\', '/');
        int i = r.indexOf("src/main/java/");
        return i < 0 ? "" : r.substring(0, i);
    }

    /** 주석 이름이 simple 이고 그 패키지 것인가 — FQCN 표기·명시 import·와일드카드 import 로 가른다 */
    private static boolean jpa(Cls c, AnnotationExpr a, String simple, Set<String> pkgs) {
        String full = a.getNameAsString();
        if (full.contains(".")) {
            return pkgs.stream().anyMatch(p -> full.equals(p + "." + simple));
        }
        if (!full.equals(simple)) {
            return false;
        }
        String imp = c.imports.get(simple);
        if (imp != null) {
            return pkgs.stream().anyMatch(p -> imp.equals(p + "." + simple));
        }
        return pkgs.stream().anyMatch(c.stars::contains);
    }

    private static Optional<AnnotationExpr> annotation(Cls c, NodeList<AnnotationExpr> list, String simple, Set<String> pkgs) {
        return list.stream().filter(a -> a.getName().getIdentifier().equals(simple) && jpa(c, a, simple, pkgs)).findFirst();
    }

    private static Expression value(AnnotationExpr a, String key) {
        if (a instanceof SingleMemberAnnotationExpr sm) {
            return key.equals("value") ? sm.getMemberValue() : null;
        }
        if (a instanceof NormalAnnotationExpr na) {
            return na.getPairs().stream().filter(p -> p.getNameAsString().equals(key)).map(p -> p.getValue()).findFirst().orElse(null);
        }
        return null;
    }

    /** 주석 값의 문자열들 — 배열이면 원소마다, 중첩 주석(@SecondaryTables 의 원소)은 그 name */
    private static List<String> strings(AnnotationExpr a, String key) {
        Expression e = value(a, key);
        List<String> out = new ArrayList<>();
        if (e instanceof ArrayInitializerExpr ai) {
            ai.getValues().forEach(x -> {
                String s = literal(x);
                if (s != null) {
                    out.add(s);
                }
            });
        } else {
            String s = literal(e);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** 문자열 리터럴·텍스트 블록·그 둘의 + 이음. 상수 이름 등은 null */
    static String literal(Expression e) {
        return literal(e, null, 0);
    }

    /** owner 가 있으면 이름(같은 타입의 상수 — 저장소 인터페이스의 JPQL 머리 등)도 그 초기값으로 푼다 */
    static String literal(Expression e, ClassOrInterfaceDeclaration owner, int depth) {
        if (depth > 8) { // 상수가 상수를 부르는 고리
            return null;
        }
        if (e instanceof com.github.javaparser.ast.expr.NameExpr n && owner != null) {
            return owner.getFieldByName(n.getNameAsString()).flatMap(f -> f.getVariable(0).getInitializer())
                    .map(init -> literal(init, owner, depth + 1)).orElse(null);
        }
        if (e instanceof StringLiteralExpr sl) {
            return sl.asString();
        }
        if (e instanceof TextBlockLiteralExpr tb) {
            return tb.asString();
        }
        if (e instanceof EnclosedExpr en) {
            return literal(en.getInner(), owner, depth);
        }
        if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            String l = literal(b.getLeft(), owner, depth);
            String r = literal(b.getRight(), owner, depth);
            return l == null || r == null ? null : l + r;
        }
        return null;
    }

    private static boolean isTrue(Expression e) {
        return e instanceof BooleanLiteralExpr b && b.getValue();
    }

    private static String simple(Type t) {
        return t instanceof ClassOrInterfaceType ct ? ct.getName().getIdentifier() : t.asString();
    }

    /** 표 이름 — 따옴표·백틱·대괄호를 떼고 스키마를 떼고 대문자 */
    static String norm(String s) {
        String x = s.replaceAll("[\"`\\[\\]]", "").trim();
        return x.substring(x.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
    }

    /** BbsMaster → BBS_MASTER, URLInfo → URL_INFO */
    static String snake(String s) {
        return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private void note(String kind, String file, int line, String detail) {
        unresolved.putIfAbsent(String.format("%s|%s|%08d|%s", kind, file, line, detail), new Unresolved(kind, file, line, detail));
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(1);
    }
}
