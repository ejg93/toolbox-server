package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 설계 64 — 문장 꼴 다섯 + 설계 65. JavaParser AST 만 본다(타입 해석 없음).
 * <ul>
 *   <li>{@code java.needBraces} — if·else·for·while·do 몸이 블록이 아니다(else if 는 사슬이라 아님)</li>
 *   <li>{@code java.emptyStatement} — 블록·case 안 빈 문장 {@code ;}(제어문 몸 {@code if (x);} 은 밖)</li>
 *   <li>{@code java.unneededBlock} — 블록 안 홀로 블록(초기화 블록·람다·case 블록·synchronized 는 부모가 달라 밖)</li>
 *   <li>{@code java.defaultLast} — switch 의 default 가 마지막 entry 가 아니다</li>
 *   <li>{@code java.interruptedIgnored} — {@code catch (InterruptedException e)} 몸에 {@code interrupt()} 호출도 throw 도 없다
 *   (람다·안쪽 클래스·안쪽 catch 몸은 문맥 밖, union 도 본다(설계 65))</li>
 *   <li>{@code java.fallThrough} — 옛 꼴 case 가 탈출 없이 다음 case 로 떨어진다(fall through 주석은 밖, 설계 65)</li>
 *   <li>{@code java.printStackTraceStream} — {@code printStackTrace(System.out·err)}(설계 65)</li>
 * </ul>
 * path·barrier·catchBarrier·same·simpleType 은 JavaDefects 것을 복사했다(가시성을 열지 않는다 — 7-22 전례).
 */
final class JavaShape {

    static final String NEED_BRACES = "java.needBraces";
    static final String EMPTY_STATEMENT = "java.emptyStatement";
    static final String UNNEEDED_BLOCK = "java.unneededBlock";
    static final String DEFAULT_LAST = "java.defaultLast";
    static final String INTERRUPTED = "java.interruptedIgnored";
    static final String STACK_STREAM = "java.printStackTraceStream";
    static final String FALL_THROUGH = "java.fallThrough";
    /** fall through·falls through·fallthrough·fall-thru·no break 주석 */
    private static final Pattern FALL_NOTE = Pattern.compile("(?i)falls?[ -]?thr(?:u|ough)|no break");
    private static final String INTERRUPTED_EXCEPTION = "InterruptedException";
    private static final String INTERRUPT = "interrupt";

    private JavaShape() {
    }

    /** 켜진 규칙(on)의 판정만 — 줄·규칙 순서, 같은 노드·같은 규칙은 한 건 */
    static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on) {
        List<Taint.Hit> out = new ArrayList<>();
        if (on.contains(NEED_BRACES)) {
            needBraces(cu, out);
        }
        if (on.contains(EMPTY_STATEMENT)) {
            for (EmptyStmt s : cu.findAll(EmptyStmt.class)) {
                if (s.getParentNode().filter(p -> p instanceof BlockStmt || p instanceof SwitchEntry).isPresent()) {
                    add(out, EMPTY_STATEMENT, s);
                }
            }
        }
        if (on.contains(UNNEEDED_BLOCK)) {
            for (BlockStmt b : cu.findAll(BlockStmt.class)) {
                if (b.getParentNode().filter(BlockStmt.class::isInstance).isPresent()) {
                    add(out, UNNEEDED_BLOCK, b);
                }
            }
        }
        if (on.contains(DEFAULT_LAST)) {
            cu.findAll(SwitchStmt.class).forEach(s -> defaultLast(s.getEntries(), out));
            cu.findAll(SwitchExpr.class).forEach(s -> defaultLast(s.getEntries(), out));
        }
        if (on.contains(INTERRUPTED)) {
            interruptCatches(cu, out);
        }
        if (on.contains(FALL_THROUGH)) {
            List<Comment> comments = cu.getAllComments();
            cu.findAll(SwitchStmt.class).forEach(s -> fallThrough(s.getEntries(), comments, out));
            cu.findAll(SwitchExpr.class).forEach(s -> fallThrough(s.getEntries(), comments, out));
        }
        if (on.contains(STACK_STREAM)) {
            cu.findAll(MethodCallExpr.class).stream().filter(JavaShape::stackStream).forEach(m -> add(out, STACK_STREAM, m));
        }
        out.sort(Comparator.comparingInt((Taint.Hit h) -> line(h.node())).thenComparing(Taint.Hit::rule));
        return out;
    }

    private static void needBraces(CompilationUnit cu, List<Taint.Hit> out) {
        for (IfStmt s : cu.findAll(IfStmt.class)) {
            boolean elseBare = s.getElseStmt().filter(e -> !(e instanceof BlockStmt) && !(e instanceof IfStmt)).isPresent();
            if (!(s.getThenStmt() instanceof BlockStmt) || elseBare) {
                add(out, NEED_BRACES, s);
            }
        }
        for (Statement s : cu.findAll(Statement.class)) {
            Statement body = loopBody(s);
            if (body != null && !(body instanceof BlockStmt)) {
                add(out, NEED_BRACES, s);
            }
        }
    }

    /** for·foreach·while·do 의 몸, 루프가 아니면 null */
    static Statement loopBody(Statement s) {
        if (s instanceof ForStmt f) {
            return f.getBody();
        }
        if (s instanceof ForEachStmt f) {
            return f.getBody();
        }
        if (s instanceof WhileStmt w) {
            return w.getBody();
        }
        return s instanceof DoStmt d ? d.getBody() : null;
    }

    private static void defaultLast(List<SwitchEntry> entries, List<Taint.Hit> out) {
        for (int i = 0; i < entries.size() - 1; i++) {
            if (entries.get(i).getLabels().isEmpty()) {
                add(out, DEFAULT_LAST, entries.get(i));
            }
        }
    }

    private static void interruptCatches(CompilationUnit cu, List<Taint.Hit> out) {
        for (CatchClause c : cu.findAll(CatchClause.class)) {
            Type t = c.getParameter().getType();
            boolean interrupted = t instanceof UnionType u ? u.getElements().stream().anyMatch(x -> INTERRUPTED_EXCEPTION.equals(simpleType(x)))
                    : INTERRUPTED_EXCEPTION.equals(simpleType(t));
            if (!interrupted) {
                continue;
            }
            BlockStmt body = c.getBody();
            boolean restores = body.findAll(MethodCallExpr.class).stream()
                    .anyMatch(m -> INTERRUPT.equals(m.getNameAsString()) && !catchBarrier(path(m, body)))
                    || body.findAll(ThrowStmt.class).stream().anyMatch(x -> !catchBarrier(path(x, body)));
            if (!restores) {
                add(out, INTERRUPTED, c.getParameter());
            }
        }
    }

    /** 설계 65 — x.printStackTrace(System.out) · (System.err). 수신자는 아무 식 */
    static boolean stackStream(MethodCallExpr m) {
        return "printStackTrace".equals(m.getNameAsString()) && m.getArguments().size() == 1
                && m.getArgument(0) instanceof FieldAccessExpr f && f.getScope() instanceof NameExpr n && "System".equals(n.getNameAsString())
                && ("out".equals(f.getNameAsString()) || "err".equals(f.getNameAsString()));
    }

    /** 마무리 리뷰 I1 — 고침은 지금 지적(cur)이 있는 노드만(tb-ignore 로 가린 줄은 지적이 없다). 지적 줄 = 노드 시작 줄(JavaRules.add) */
    static boolean flagged(List<Finding> cur, Taint.Hit h) {
        int at = h.node().getBegin().map(p -> p.line).orElse(1);
        return cur.stream().anyMatch(f -> f.line() == at && f.rule().equals(h.rule()));
    }

    private static void add(List<Taint.Hit> out, String rule, Node n) {
        if (out.stream().noneMatch(h -> h.rule().equals(rule) && same(h.node(), n))) {
            out.add(new Taint.Hit(rule, n));
        }
    }

    /** s 에서 위로 f 직전까지의 조상 — f 는 빼고, 가까운 것부터(JavaDefects 복사) */
    private static List<Node> path(Node s, Node f) {
        List<Node> out = new ArrayList<>();
        for (Node cur = s.getParentNode().orElse(null); cur != null && !same(cur, f); cur = cur.getParentNode().orElse(null)) {
            out.add(cur);
        }
        return out;
    }

    /** 람다·안쪽 클래스 몸·안쪽 catch 몸 — 그 자리의 문맥 밖(JavaDefects 복사) */
    private static boolean catchBarrier(List<Node> path) {
        return path.stream().anyMatch(n -> n instanceof LambdaExpr || n instanceof BodyDeclaration || n instanceof CatchClause);
    }

    private static boolean same(Node a, Node b) {
        return a.getRange().equals(b.getRange());
    }

    /** 선언 글에서 {@code <…>}·{@code []} 를 떼고 마지막 {@code .} 뒤(JavaDefects 복사) */
    private static String simpleType(Type t) {
        String s = t.asString();
        int lt = s.indexOf('<');
        if (lt >= 0) {
            s = s.substring(0, lt);
        }
        s = s.replace("[]", "").trim();
        return s.substring(s.lastIndexOf('.') + 1);
    }

    /** 설계 65 — 옛 꼴(case X:) entry 가 문장이 있는데 탈출로 안 끝나고, 다음 case 사이에 fall through 주석도 없다. 마지막 entry·빈 entry(묶인 label)는 밖 */
    private static void fallThrough(List<SwitchEntry> entries, List<Comment> comments, List<Taint.Hit> out) {
        for (int i = 0; i < entries.size() - 1; i++) {
            SwitchEntry x = entries.get(i);
            if (x.getType() != SwitchEntry.Type.STATEMENT_GROUP || x.getStatements().isEmpty()) {
                continue;
            }
            Statement last = x.getStatements().getLast().orElseThrow();
            int from = last.getEnd().map(p -> p.line).orElse(1);
            int to = line(entries.get(i + 1));
            boolean noted = comments.stream().anyMatch(c -> line(c) >= from && line(c) <= to && FALL_NOTE.matcher(c.getContent()).find());
            if (!exits(last) && !noted) {
                add(out, FALL_THROUGH, x);
            }
        }
    }

    /** 끝이 탈출 — break·return·throw·continue·yield, 블록이면 그 마지막 문장, if-else 면 두 갈래 다 */
    private static boolean exits(Statement s) {
        if (s instanceof BlockStmt b) {
            return b.getStatements().getLast().filter(JavaShape::exits).isPresent();
        }
        if (s instanceof IfStmt i) {
            return i.getElseStmt().filter(JavaShape::exits).isPresent() && exits(i.getThenStmt());
        }
        return s instanceof BreakStmt || s instanceof ReturnStmt || s instanceof ThrowStmt || s instanceof ContinueStmt || s instanceof YieldStmt;
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(1);
    }
}
