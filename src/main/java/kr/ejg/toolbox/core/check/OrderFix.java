package kr.ejg.toolbox.core.check;

import com.github.javaparser.JavaToken;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 선언 순서 수정기(5-34b, 설계 30 §3) — AST 로 다시 찍지 않고 <b>줄 슬롯 재조립</b>(주석·공백·들여쓰기 그대로).
 * 몸체(타입·enum 상수·익명 클래스)마다 정렬 대상(필드·생성자·메서드)을 통째 줄 조각으로 자르고, {@link JavaRules#orderViolations} 와 같은 술어로
 * 안정 정렬해 i번째 조각을 i번째 자리에 둔다. 조각 사이 글(빈 줄·구획 주석·중첩 타입)은 제자리.
 * 안전하지 않은 몸체(초기화 블록·한 줄에 선언 둘·경계를 걸친 주석·읽기 쌍 순서·초기화 부수 효과 순서·Lombok 생성자·억제 표식)는 사유를 남기고 건너뛴다.
 * 재조립 뒤 확인(파싱·줄 수·줄 다중집합·토큰 다중집합·주석 다중집합·몸체 위반 0·파일 위반 감소)이 하나라도 틀리면 처음 글을 돌려준다
 */
final class OrderFix {

    static final String SKIP_INIT = "초기화 블록";
    static final String SKIP_SAME_LINE = "한 줄에 선언 둘";
    static final String SKIP_STRADDLE = "경계를 걸친 주석";
    static final String SKIP_READ = "초기화 순서(다른 필드를 읽음)";
    static final String SKIP_IMPURE = "초기화 부수 효과 순서";
    static final String SKIP_LOMBOK = "Lombok 생성자 인자 순서";
    static final String SKIP_SUPPRESSED = "억제 표식이 있는 몸체";
    static final String RECHECK_FAILED = "재검 실패";

    private static final Pattern DIVIDER = Pattern.compile("^\\s*//\\s*[-=*#/]{4,}.*$");
    private static final Set<String> LOMBOK = Set.of("AllArgsConstructor", "RequiredArgsConstructor", "Value", "Data", "Builder", "SuperBuilder");
    private static final int INERT = 0;
    private static final int EXTERNAL = 1;
    private static final int OPAQUE = 2;

    private OrderFix() {
    }

    /** 몸체 하나 — 멤버 목록·주인(타입·enum 상수·익명 new)·주인 자리(재조립해도 안 움직인다) */
    record Body(NodeList<BodyDeclaration<?>> members, Node owner, String key, boolean interfaceLike) {
    }

    /** 통째 줄 조각 [l0, l1](1부터) — 노드와 정렬 키 */
    record Slice(int l0, int l1, BodyDeclaration<?> node, int bucket, int access) {
    }

    /**
     * 글 전체 — 고칠 수 있는 몸체를 하나씩(시작 순) 고친다. 건너뛴 몸체는 skips 에 「줄 n: 사유」. 확인이 틀리면 처음 글 + 「재검 실패」.
     *
     * @param order {@code java.declarationOrder} 정의(억제 판정용)
     */
    static String apply(String text, boolean mods, Rule.Def order, JavaSource parser, String rel, List<Cleanup.Skip> skips) {
        ParseResult<CompilationUnit> r0 = parser.parse(text);
        if (!JavaSource.ok(r0)) {
            return text;
        }
        int cap = bodies(r0.getResult().get()).size() + 1;
        int total = total(r0.getResult().get(), mods);
        String cur = text;
        Set<String> said = new LinkedHashSet<>();
        for (int round = 0; round < cap && total > 0; round++) {
            ParseResult<CompilationUnit> r = parser.parse(cur);
            if (!JavaSource.ok(r)) {
                return fail(text, skips);
            }
            CompilationUnit cu = r.getResult().get();
            Set<Integer> suppressed = suppressedLines(cur, rel, order);
            List<Comment> comments = comments(r, cu);
            String next = null;
            String nextKey = null;
            for (Body b : bodies(cu)) {
                List<BodyDeclaration<?>> v = JavaRules.orderViolations(b.members(), mods);
                if (v.isEmpty()) {
                    continue;
                }
                StringBuilder why = new StringBuilder();
                String out = reorder(cur, b, v, mods, cu, comments, suppressed, why);
                if (out == null) {
                    said.add("줄 " + line(b.owner()) + ": " + why);
                    continue;
                }
                next = out;
                nextKey = b.key();
                break;
            }
            if (next == null) {
                break;
            }
            int after = verify(cur, next, nextKey, mods, parser);
            if (after < 0 || after >= total) {
                return fail(text, skips);
            }
            cur = next;
            total = after;
        }
        for (String s : said) {
            skips.add(new Cleanup.Skip(Cleanup.ORDER, s));
        }
        return cur;
    }

    private static String fail(String text, List<Cleanup.Skip> skips) {
        skips.add(new Cleanup.Skip(Cleanup.ORDER, RECHECK_FAILED));
        return text;
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(1);
    }

    /** 파일 전체 위반 수(억제 무시) */
    static int total(CompilationUnit cu, boolean mods) {
        int n = 0;
        for (Body b : bodies(cu)) {
            n += JavaRules.orderViolations(b.members(), mods).size();
        }
        return n;
    }

    /** 몸체 전부 — 타입(중첩·지역 포함)·enum 상수 몸체·익명 몸체, 주인 시작 자리 순 */
    static List<Body> bodies(CompilationUnit cu) {
        List<Body> out = new ArrayList<>();
        for (TypeDeclaration<?> t : cu.findAll(TypeDeclaration.class)) {
            boolean ifc = t instanceof ClassOrInterfaceDeclaration ci && ci.isInterface() || t instanceof AnnotationDeclaration;
            out.add(new Body(t.getMembers(), t, key(t), ifc));
        }
        for (EnumConstantDeclaration e : cu.findAll(EnumConstantDeclaration.class)) {
            if (!e.getClassBody().isEmpty()) {
                out.add(new Body(e.getClassBody(), e, key(e), false));
            }
        }
        for (ObjectCreationExpr o : cu.findAll(ObjectCreationExpr.class)) {
            o.getAnonymousClassBody().ifPresent(b -> out.add(new Body(b, o, key(o), false)));
        }
        out.sort(Comparator.comparing((Body b) -> b.owner().getBegin().map(p -> p.line).orElse(0))
                .thenComparing(b -> b.owner().getBegin().map(p -> p.column).orElse(0)));
        return out;
    }

    private static String key(Node n) {
        return n.getClass().getSimpleName() + "@" + n.getBegin().map(p -> p.line + ":" + p.column).orElse("?");
    }

    private static List<Comment> comments(ParseResult<CompilationUnit> r, CompilationUnit cu) {
        List<Comment> out = new ArrayList<>();
        r.getCommentsCollection().ifPresentOrElse(c -> out.addAll(c.getComments()), () -> out.addAll(cu.getAllComments()));
        out.removeIf(c -> c.getRange().isEmpty());
        return out;
    }

    /** 억제된 선언 순서 지적의 줄 — 억제 전 지적 − 억제 뒤 지적 */
    private static Set<Integer> suppressedLines(String text, String rel, Rule.Def order) {
        Source s = new Source(rel == null ? "x.java" : rel, text, "UTF-8", "LF", "java");
        List<Finding> raw = new JavaRules(List.of(order), null).apply(s);
        Set<Integer> out = new HashSet<>();
        raw.forEach(f -> out.add(f.line()));
        Ignores ig = Ignores.of(s);
        if (ig.isEmpty()) {
            return Set.of();
        }
        ig.filter(raw).forEach(f -> out.remove(f.line()));
        return out;
    }

    // ---------------------------------------------------------------- 몸체 하나

    /** 고친 글, 건너뛰면 null(why 에 사유) */
    static String reorder(String text, Body b, List<BodyDeclaration<?>> violations, boolean mods, CompilationUnit cu, List<Comment> comments,
            Set<Integer> suppressed, StringBuilder why) {
        for (BodyDeclaration<?> v : violations) {
            if (suppressed.contains(line(v))) {
                why.append(SKIP_SUPPRESSED);
                return null;
            }
        }
        List<BodyDeclaration<?>> sortable = new ArrayList<>();
        List<Node> fixed = new ArrayList<>();
        for (BodyDeclaration<?> m : b.members()) {
            if (m instanceof InitializerDeclaration) {
                why.append(SKIP_INIT);
                return null;
            }
            if (m instanceof FieldDeclaration || m instanceof ConstructorDeclaration || m instanceof CompactConstructorDeclaration
                    || m instanceof MethodDeclaration) {
                sortable.add(m);
            } else {
                fixed.add(m);
            }
        }
        if (lombok(b.owner())) {
            why.append(SKIP_LOMBOK);
            return null;
        }
        String[] lines = text.split("\n", -1);
        boolean[][] covered = coverage(lines, comments);
        List<Slice> slices = new ArrayList<>();
        for (BodyDeclaration<?> m : sortable) {
            Optional<Range> rg = m.getRange();
            if (rg.isEmpty()) {
                why.append(SKIP_SAME_LINE);
                return null;
            }
            Range r = rg.get();
            int b0 = r.begin.line;
            int e0 = r.end.line;
            if (!columnsOk(lines, covered, comments, r)) {
                why.append(SKIP_SAME_LINE);
                return null;
            }
            int l0 = prefixStart(lines, covered, comments, b0);
            int[] k = key(m, mods);
            slices.add(new Slice(l0, e0, m, k[0], k[1]));
        }
        // 조각끼리·고정 멤버와 줄이 겹치면 못 자른다
        for (int i = 0; i < slices.size(); i++) {
            for (int j = i + 1; j < slices.size(); j++) {
                if (overlap(slices.get(i).l0(), slices.get(i).l1(), slices.get(j).l0(), slices.get(j).l1())) {
                    why.append(SKIP_SAME_LINE);
                    return null;
                }
            }
            for (Node f : fixed) {
                if (f.getRange().isPresent()
                        && overlap(slices.get(i).l0(), slices.get(i).l1(), f.getRange().get().begin.line, f.getRange().get().end.line)) {
                    why.append(SKIP_SAME_LINE);
                    return null;
                }
            }
        }
        // 경계를 걸친 주석 — 조각과 일부만 겹치면 옮길 때 뒤 코드를 삼킨다
        for (Comment c : comments) {
            int cb = c.getRange().get().begin.line;
            int ce = c.getRange().get().end.line;
            for (Slice s : slices) {
                if (overlap(cb, ce, s.l0(), s.l1()) && !(cb >= s.l0() && ce <= s.l1())) {
                    why.append(SKIP_STRADDLE);
                    return null;
                }
            }
        }
        List<Slice> sorted = new ArrayList<>(slices);
        sorted.sort(Comparator.comparingInt(Slice::bucket).thenComparingInt(Slice::access));
        boolean same = true;
        for (int i = 0; i < slices.size(); i++) {
            same &= sorted.get(i) == slices.get(i);
        }
        if (same) {
            why.append(SKIP_SAME_LINE); // 위반이 있는데 정렬해도 안 바뀜 — 판정과 키가 어긋난 것(없어야 한다)
            return null;
        }
        String r = readsAndPurity(slices, sorted, b, cu);
        if (r != null) {
            why.append(r);
            return null;
        }
        // 재조립 — 조각 사이 글은 제자리
        StringBuilder out = new StringBuilder();
        int first = slices.get(0).l0();
        int last = slices.get(slices.size() - 1).l1();
        appendLines(out, lines, 1, first - 1);
        for (int i = 0; i < slices.size(); i++) {
            Slice put = sorted.get(i);
            appendLines(out, lines, put.l0(), put.l1());
            if (i + 1 < slices.size()) {
                appendLines(out, lines, slices.get(i).l1() + 1, slices.get(i + 1).l0() - 1);
            }
        }
        appendLines(out, lines, last + 1, lines.length);
        return out.substring(0, out.length() - 1); // 끝에 붙인 \n 하나
    }

    private static void appendLines(StringBuilder out, String[] lines, int from, int to) {
        for (int i = from; i <= to; i++) {
            out.append(lines[i - 1]).append('\n');
        }
    }

    private static boolean overlap(int a0, int a1, int b0, int b1) {
        return a0 <= b1 && b0 <= a1;
    }

    /** 정렬 키 — {@link JavaRules#orderViolations} 와 같은 술어. ignoreModifiers 면 필드·생성자·메서드만 */
    static int[] key(BodyDeclaration<?> m, boolean mods) {
        if (m instanceof FieldDeclaration f) {
            return mods ? new int[] {f.isStatic() ? 0 : 1, JavaRules.rank(f.getAccessSpecifier())} : new int[] {0, 0};
        }
        if (m instanceof ConstructorDeclaration || m instanceof CompactConstructorDeclaration) {
            return mods ? new int[] {2, 0} : new int[] {1, 0};
        }
        return mods ? new int[] {3, 0} : new int[] {2, 0};
    }

    /** 주인·바깥 타입에 Lombok 생성자 애너테이션 */
    private static boolean lombok(Node owner) {
        Node n = owner;
        while (n != null) {
            if (n instanceof TypeDeclaration<?> t) {
                for (AnnotationExpr a : t.getAnnotations()) {
                    String name = a.getNameAsString();
                    if (LOMBOK.contains(name.substring(name.lastIndexOf('.') + 1))) {
                        return true;
                    }
                }
            }
            n = n.getParentNode().orElse(null);
        }
        return false;
    }

    // ---------------------------------------------------------------- 줄·열

    /** 줄마다 글자가 주석 안인가 */
    private static boolean[][] coverage(String[] lines, List<Comment> comments) {
        boolean[][] cov = new boolean[lines.length][];
        for (int i = 0; i < lines.length; i++) {
            cov[i] = new boolean[lines[i].length()];
        }
        for (Comment c : comments) {
            Range r = c.getRange().get();
            for (int ln = r.begin.line; ln <= r.end.line && ln <= lines.length; ln++) {
                int from = ln == r.begin.line ? r.begin.column - 1 : 0;
                int to = ln == r.end.line ? Math.min(r.end.column, lines[ln - 1].length()) : lines[ln - 1].length();
                for (int k = Math.max(0, from); k < to; k++) {
                    cov[ln - 1][k] = true;
                }
            }
        }
        return cov;
    }

    /** 그 글자를 덮는 주석 */
    private static Comment coverer(List<Comment> comments, int line, int col0) {
        for (Comment c : comments) {
            Range r = c.getRange().get();
            boolean after = line > r.begin.line || line == r.begin.line && col0 >= r.begin.column - 1;
            boolean before = line < r.end.line || line == r.end.line && col0 <= r.end.column - 1;
            if (after && before) {
                return c;
            }
        }
        return null;
    }

    /**
     * 열 규칙 — 시작 줄에서 노드 앞 글은 공백이거나 그 줄에서 끝나는 주석, 끝 줄에서 노드 뒤 글은 공백이거나 그 줄에서 열고 닫히는 주석.
     * 한 줄에 선언 둘·enum 상수 뒤 선언·{@code ;;}·여는 {@code {} 뒤 선언을 거른다
     */
    private static boolean columnsOk(String[] lines, boolean[][] cov, List<Comment> comments, Range r) {
        String b = lines[r.begin.line - 1];
        for (int k = 0; k < Math.min(r.begin.column - 1, b.length()); k++) {
            if (!Character.isWhitespace(b.charAt(k))) {
                Comment c = cov[r.begin.line - 1][k] ? coverer(comments, r.begin.line, k) : null;
                if (c == null || c.getRange().get().end.line != r.begin.line) {
                    return false;
                }
            }
        }
        String e = lines[r.end.line - 1];
        for (int k = Math.min(r.end.column, e.length()); k < e.length(); k++) {
            if (!Character.isWhitespace(e.charAt(k))) {
                Comment c = cov[r.end.line - 1][k] ? coverer(comments, r.end.line, k) : null;
                if (c == null || c.getRange().get().begin.line != r.end.line || c.getRange().get().end.line != r.end.line) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 줄 전체가 주석(공백 말고는 전부 주석 안) */
    private static boolean commentOnly(String line, boolean[] cov) {
        boolean any = false;
        for (int k = 0; k < line.length(); k++) {
            if (!Character.isWhitespace(line.charAt(k))) {
                if (!cov[k]) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    /**
     * 조각 시작 줄 — 노드 시작 줄에서 바로 위로 이어지는 주석뿐인 줄을 끌어안는다. 구획 줄({@code // ----})은 안 넣는다.
     * javadoc 이면 빈 줄 하나를 건너 끌어안는다(javadoc 도구가 다음 선언에 붙인다). 접두를 덮는 주석은 접두 안에서 시작해야
     */
    private static int prefixStart(String[] lines, boolean[][] cov, List<Comment> comments, int begin) {
        int l0 = begin;
        int k = begin - 1;
        boolean crossed = false;
        while (k >= 1) {
            String ln = lines[k - 1];
            if (ln.isBlank()) {
                // javadoc 과 선언 사이 빈 줄 하나 — 아직 아무것도 안 끌어안았을 때만
                if (l0 == begin && !crossed && k >= 2 && endsJavadoc(comments, k - 1)) {
                    crossed = true;
                    k--;
                    continue;
                }
                break;
            }
            if (DIVIDER.matcher(ln).matches() || !commentOnly(ln, cov[k - 1])) {
                break;
            }
            l0 = k;
            k--;
        }
        // 접두를 덮는 주석은 접두 안에서 시작해야 — 위에서 시작한 블록 주석의 꼬리면 접두 없이(걸친 주석 검사가 따로 본다)
        for (Comment c : comments) {
            Range r = c.getRange().get();
            if (r.begin.line < l0 && r.end.line >= l0 && r.end.line < begin) {
                return begin;
            }
        }
        return l0;
    }

    private static boolean endsJavadoc(List<Comment> comments, int line) {
        for (Comment c : comments) {
            if (c.isJavadocComment() && c.getRange().get().end.line == line) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 초기화 순서

    /** 읽기 쌍 보존·초기화 부수 효과 순서 — 어기면 사유, 아니면 null */
    private static String readsAndPurity(List<Slice> old, List<Slice> sorted, Body b, CompilationUnit cu) {
        // Node.equals 는 구조 비교 — 자리는 같은 객체로
        Map<BodyDeclaration<?>, Integer> oldIdx = new IdentityHashMap<>();
        Map<BodyDeclaration<?>, Integer> newIdx = new IdentityHashMap<>();
        for (int i = 0; i < old.size(); i++) {
            oldIdx.put(old.get(i).node(), i);
            newIdx.put(sorted.get(i).node(), i);
        }
        List<FieldDeclaration> fields = new ArrayList<>();
        Map<String, FieldDeclaration> byName = new HashMap<>();
        Map<FieldDeclaration, Integer> kind = new IdentityHashMap<>();
        for (Slice s : old) {
            if (s.node() instanceof FieldDeclaration f) {
                fields.add(f);
                for (VariableDeclarator v : f.getVariables()) {
                    byName.put(v.getNameAsString(), f);
                }
            }
        }
        for (FieldDeclaration f : fields) {
            for (VariableDeclarator v : f.getVariables()) {
                if (v.getInitializer().isEmpty()) {
                    continue;
                }
                for (SimpleName n : v.getInitializer().get().findAll(SimpleName.class)) {
                    FieldDeclaration g = byName.get(n.getIdentifier());
                    if (g != null && g != f && Integer.signum(oldIdx.get(f) - oldIdx.get(g)) != Integer.signum(newIdx.get(f) - newIdx.get(g))) {
                        return SKIP_READ;
                    }
                }
            }
        }
        Set<String> localTypes = new HashSet<>();
        cu.findAll(TypeDeclaration.class).forEach(t -> localTypes.add(t.getNameAsString()));
        Set<String> localFields = new HashSet<>();
        cu.findAll(VariableDeclarator.class).forEach(v -> {
            if (v.getParentNode().orElse(null) instanceof FieldDeclaration) {
                localFields.add(v.getNameAsString());
            }
        });
        for (FieldDeclaration f : fields) {
            int worst = INERT;
            for (VariableDeclarator v : f.getVariables()) {
                worst = Math.max(worst, v.getInitializer().map(e -> classify(e, localTypes, localFields)).orElse(INERT));
            }
            kind.put(f, worst);
        }
        for (int i = 0; i < fields.size(); i++) {
            for (int j = i + 1; j < fields.size(); j++) {
                FieldDeclaration a = fields.get(i);
                FieldDeclaration c = fields.get(j);
                if (effStatic(a, b) != effStatic(c, b)) {
                    continue;
                }
                int ka = kind.get(a);
                int kc = kind.get(c);
                boolean pinned = ka == OPAQUE || kc == OPAQUE || ka != INERT && kc != INERT;
                if (pinned && Integer.signum(oldIdx.get(a) - oldIdx.get(c)) != Integer.signum(newIdx.get(a) - newIdx.get(c))) {
                    return SKIP_IMPURE;
                }
            }
        }
        return null;
    }

    private static boolean effStatic(FieldDeclaration f, Body b) {
        return f.isStatic() || b.interfaceLike();
    }

    /** 초기화식 분류 — inert(값만)·external(이 파일 밖 정적 호출·new)·opaque(나머지) */
    static int classify(Expression init, Set<String> localTypes, Set<String> localFields) {
        int worst = INERT;
        for (Expression e : init.findAll(Expression.class)) {
            worst = Math.max(worst, kindOf(e, localTypes, localFields));
            if (worst == OPAQUE) {
                return OPAQUE;
            }
        }
        return worst;
    }

    private static int kindOf(Expression e, Set<String> localTypes, Set<String> localFields) {
        if (e instanceof LiteralExpr || e instanceof ClassExpr || e instanceof BinaryExpr || e instanceof ConditionalExpr || e instanceof CastExpr
                || e instanceof EnclosedExpr || e instanceof InstanceOfExpr || e instanceof ArrayAccessExpr || e instanceof ArrayInitializerExpr
                || e instanceof ArrayCreationExpr) {
            return INERT;
        }
        if (e instanceof UnaryExpr u) {
            return u.getOperator().isPrefix() && u.getOperator() != UnaryExpr.Operator.PREFIX_INCREMENT
                    && u.getOperator() != UnaryExpr.Operator.PREFIX_DECREMENT ? INERT : OPAQUE;
        }
        if (e instanceof ThisExpr) {
            Node p = e.getParentNode().orElse(null);
            return p instanceof MethodCallExpr || p instanceof ObjectCreationExpr ? OPAQUE : INERT;
        }
        if (e instanceof NameExpr || e instanceof FieldAccessExpr) {
            String root = root(e);
            return root != null && localTypes.contains(root) ? OPAQUE : INERT;
        }
        if (e instanceof MethodCallExpr m) {
            Optional<Expression> sc = m.getScope();
            if (sc.isEmpty() || !(sc.get() instanceof NameExpr || sc.get() instanceof FieldAccessExpr)) {
                return OPAQUE;
            }
            String root = root(sc.get());
            if (root == null || root.isEmpty() || !Character.isUpperCase(root.charAt(0)) || localTypes.contains(root) || localFields.contains(root)) {
                return OPAQUE;
            }
            for (Expression a : m.getArguments()) {
                if (a instanceof ThisExpr) {
                    return OPAQUE;
                }
            }
            return EXTERNAL;
        }
        if (e instanceof ObjectCreationExpr o) {
            if (o.getAnonymousClassBody().isPresent() || o.getScope().isPresent() || localTypes.contains(o.getType().getNameAsString())) {
                return OPAQUE;
            }
            return EXTERNAL;
        }
        return OPAQUE;
    }

    /** 이름 사슬의 맨 앞 — a.b.c → a. 사슬이 아니면 null */
    private static String root(Expression e) {
        Expression x = e;
        while (x instanceof FieldAccessExpr f) {
            x = f.getScope();
        }
        return x instanceof NameExpr n ? n.getNameAsString() : null;
    }

    // ---------------------------------------------------------------- 확인

    /**
     * 재조립 확인 — 파싱·줄 수·줄 다중집합·토큰 다중집합·주석 다중집합·그 몸체 위반 0. 통과하면 파일 전체 위반 수, 아니면 -1
     */
    static int verify(String before, String after, String bodyKey, boolean mods, JavaSource parser) {
        ParseResult<CompilationUnit> a = parser.parse(after);
        ParseResult<CompilationUnit> b = parser.parse(before);
        if (!JavaSource.ok(a) || !JavaSource.ok(b)) {
            return -1;
        }
        String[] lb = before.split("\n", -1);
        String[] la = after.split("\n", -1);
        if (lb.length != la.length || !multiset(List.of(lb)).equals(multiset(List.of(la)))) {
            return -1;
        }
        if (!multiset(tokens(b.getResult().get())).equals(multiset(tokens(a.getResult().get())))) {
            return -1;
        }
        if (!multiset(commentTexts(b, b.getResult().get())).equals(multiset(commentTexts(a, a.getResult().get())))) {
            return -1;
        }
        CompilationUnit cu = a.getResult().get();
        boolean found = false;
        for (Body body : bodies(cu)) {
            if (body.key().equals(bodyKey)) {
                found = true;
                if (!JavaRules.orderViolations(body.members(), mods).isEmpty()) {
                    return -1;
                }
            }
        }
        return found ? total(cu, mods) : -1; // 고친 몸체를 못 찾으면 확인 실패(주인 자리는 안 움직여야 한다)
    }

    private static List<String> tokens(CompilationUnit cu) {
        List<String> out = new ArrayList<>();
        cu.getTokenRange().ifPresent(tr -> {
            for (JavaToken t : tr) {
                if (!t.getCategory().isWhitespaceOrComment()) {
                    out.add(t.getKind() + ":" + t.getText());
                }
            }
        });
        return out;
    }

    private static List<String> commentTexts(ParseResult<CompilationUnit> r, CompilationUnit cu) {
        List<String> out = new ArrayList<>();
        for (Comment c : comments(r, cu)) {
            out.add(c.getContent());
        }
        return out;
    }

    private static Map<String, Integer> multiset(List<String> xs) {
        Map<String, Integer> m = new TreeMap<>();
        for (String x : xs) {
            m.merge(x, 1, Integer::sum);
        }
        return Collections.unmodifiableMap(m);
    }
}
