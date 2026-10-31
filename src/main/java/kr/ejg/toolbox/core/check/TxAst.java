package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 설계 49 — 트랜잭션 결함 셋. {@code @Transactional} 은 단순 이름·FQN 다 마지막 이름으로 맞댄다(해석 없음).
 * <ul>
 *   <li>{@code java.txRollbackFor} — 클래스·메서드의 {@code @Transactional} 에 rollbackFor·rollbackForClassName·rollbackOn 이 없다
 *   ({@code readOnly = true} 는 아님)</li>
 *   <li>{@code java.txSelfCall} — 클래스 머리 tx 가 없는 클래스에서 tx 메서드를 scope 없이·{@code this.} 로 부르고, 호출자의
 *   애너테이션 글이 피호출자와 다르다(재귀 제외)</li>
 *   <li>{@code java.txPrivate} — private·static 메서드의 {@code @Transactional}</li>
 * </ul>
 */
final class TxAst {

    static final String TX_ROLLBACK = "java.txRollbackFor";
    static final String TX_SELF = "java.txSelfCall";
    static final String TX_PRIVATE = "java.txPrivate";
    private static final String TRANSACTIONAL = "Transactional";
    private static final Set<String> ROLLBACK_KEYS = Set.of("rollbackFor", "rollbackForClassName", "rollbackOn");
    private static final String READ_ONLY = "readOnly";
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private TxAst() {
    }

    /** 켜진 규칙(on)의 판정만 — 줄·규칙 순서 */
    static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on) {
        List<Taint.Hit> out = new ArrayList<>();
        if (on.contains(TX_ROLLBACK)) {
            rollbacks(cu, out);
        }
        if (on.contains(TX_PRIVATE)) {
            privates(cu, out);
        }
        if (on.contains(TX_SELF)) {
            selfCalls(cu, out);
        }
        out.sort(Comparator.comparingInt((Taint.Hit h) -> line(h.node())).thenComparing(Taint.Hit::rule));
        return out;
    }

    private static Optional<AnnotationExpr> tx(NodeWithAnnotations<?> d) {
        return d.getAnnotations().stream().filter(a -> TRANSACTIONAL.equals(a.getName().getIdentifier())).findFirst();
    }

    /** 이름 → 값 식. 마커·한 값 애너테이션은 빈 Map */
    private static Map<String, Expression> pairs(AnnotationExpr a) {
        Map<String, Expression> m = new LinkedHashMap<>();
        if (a instanceof NormalAnnotationExpr n) {
            n.getPairs().forEach(p -> m.put(p.getNameAsString(), p.getValue()));
        }
        return m;
    }

    /** 애너테이션 글(공백 뺌) — 호출자·피호출자 속성 비교용 */
    private static String text(AnnotationExpr a) {
        return SPACE.matcher(a.toString()).replaceAll("");
    }

    private static void rollbacks(CompilationUnit cu, List<Taint.Hit> out) {
        for (BodyDeclaration<?> d : cu.findAll(BodyDeclaration.class)) {
            Optional<AnnotationExpr> a = tx(d);
            if (a.isEmpty()) {
                continue;
            }
            Map<String, Expression> p = pairs(a.get());
            boolean readOnly = p.get(READ_ONLY) instanceof BooleanLiteralExpr b && b.getValue();
            if (!readOnly && p.keySet().stream().noneMatch(ROLLBACK_KEYS::contains)) {
                out.add(new Taint.Hit(TX_ROLLBACK, a.get()));
            }
        }
    }

    private static void privates(CompilationUnit cu, List<Taint.Hit> out) {
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            if (m.isPrivate() || m.isStatic()) {
                tx(m).ifPresent(a -> out.add(new Taint.Hit(TX_PRIVATE, a)));
            }
        }
    }

    private static void selfCalls(CompilationUnit cu, List<Taint.Hit> out) {
        for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (c.isInterface() || tx(c).isPresent()) {
                continue; // 클래스 머리 tx — public 메서드 전부 같은 트랜잭션
            }
            Map<String, String> txText = txMethods(c);
            if (txText.isEmpty()) {
                continue;
            }
            for (MethodDeclaration caller : c.getMethods()) {
                Optional<String> callerText = tx(caller).map(TxAst::text);
                for (MethodCallExpr call : caller.findAll(MethodCallExpr.class)) {
                    if (selfCall(call, caller, txText) && callerText.filter(txText.get(call.getNameAsString())::equals).isEmpty()) {
                        out.add(new Taint.Hit(TX_SELF, call));
                    }
                }
            }
        }
    }

    /** 직접 메서드 중 tx 있는 것의 이름 → 애너테이션 글(이름이 겹치면 첫 것) */
    private static Map<String, String> txMethods(ClassOrInterfaceDeclaration c) {
        Map<String, String> m = new LinkedHashMap<>();
        for (MethodDeclaration d : c.getMethods()) {
            tx(d).ifPresent(a -> m.putIfAbsent(d.getNameAsString(), text(a)));
        }
        return m;
    }

    /** scope 없음·this — 같은 클래스의 tx 메서드, 재귀는 아님 */
    private static boolean selfCall(MethodCallExpr call, MethodDeclaration caller, Map<String, String> txText) {
        String name = call.getNameAsString();
        boolean self = call.getScope().map(s -> s instanceof ThisExpr).orElse(true);
        return self && txText.containsKey(name) && !name.equals(caller.getNameAsString());
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(1);
    }
}
