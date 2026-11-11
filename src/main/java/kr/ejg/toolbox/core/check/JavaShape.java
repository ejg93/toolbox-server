package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 설계 64 — 문장 꼴 다섯. JavaParser AST 만 본다(타입 해석 없음).
 * <ul>
 *   <li>{@code java.needBraces} — if·else·for·while·do 몸이 블록이 아니다(else if 는 사슬이라 아님)</li>
 *   <li>{@code java.emptyStatement} — 블록·case 안 빈 문장 {@code ;}(제어문 몸 {@code if (x);} 은 밖)</li>
 *   <li>{@code java.unneededBlock} — 블록 안 홀로 블록(초기화 블록·람다·case 블록·synchronized 는 부모가 달라 밖)</li>
 *   <li>{@code java.defaultLast} — switch 의 default 가 마지막 entry 가 아니다</li>
 *   <li>{@code java.interruptedIgnored} — 단일형 {@code catch (InterruptedException e)} 몸에 {@code interrupt()} 호출도 throw 도 없다
 *   (람다·안쪽 클래스·안쪽 catch 몸은 문맥 밖, union catch 는 규칙 밖)</li>
 * </ul>
 * path·barrier·catchBarrier·same·simpleType 은 JavaDefects 것을 복사했다(가시성을 열지 않는다 — 7-22 전례).
 */
final class JavaShape {

    static final String NEED_BRACES = "java.needBraces";
    static final String EMPTY_STATEMENT = "java.emptyStatement";
    static final String UNNEEDED_BLOCK = "java.unneededBlock";
    static final String DEFAULT_LAST = "java.defaultLast";
    static final String INTERRUPTED = "java.interruptedIgnored";
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
            if (t instanceof UnionType || !INTERRUPTED_EXCEPTION.equals(simpleType(t))) {
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

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(1);
    }
}
