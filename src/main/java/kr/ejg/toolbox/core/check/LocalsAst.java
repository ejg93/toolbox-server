package kr.ejg.toolbox.core.check;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** 설계 60 — 지역 변수: 선언만 하고 이름이 다시 안 나오는 것. JavaRules.taint 가 부른다 */
final class LocalsAst {
    static final String UNUSED_LOCAL = "java.unusedLocal";
    /** 일부러 안 쓰는 이름 */
    private static final Pattern IGNORED = Pattern.compile("(?i)^(_+|ignored?|unused|dummy)\\d*$");
    private static final Set<String> SUPPRESS = Set.of("\"unused\"", "\"all\"");
    private static final String SUPPRESS_WARNINGS = "SuppressWarnings";
    private static final Set<BinaryExpr.Operator> BINARY = Set.of(BinaryExpr.Operator.PLUS, BinaryExpr.Operator.MINUS, BinaryExpr.Operator.MULTIPLY);
    private static final Set<UnaryExpr.Operator> UNARY = Set.of(UnaryExpr.Operator.PLUS, UnaryExpr.Operator.MINUS,
            UnaryExpr.Operator.LOGICAL_COMPLEMENT, UnaryExpr.Operator.BITWISE_COMPLEMENT);

    private LocalsAst() {
    }

    static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on) {
        List<Taint.Hit> out = new ArrayList<>();
        if (!on.contains(UNUSED_LOCAL)) {
            return out;
        }
        for (ExpressionStmt es : cu.findAll(ExpressionStmt.class)) {
            Node parent = es.getParentNode().orElse(null);
            if (!(es.getExpression() instanceof VariableDeclarationExpr d) || !(parent instanceof BlockStmt || parent instanceof SwitchEntry)) {
                continue;
            }
            // 옛 꼴 case 묶음에서 선언은 뒤 case 에도 보인다 — 범위는 switch 전체
            Node scope = parent instanceof SwitchEntry ? parent.getParentNode().orElse(parent) : parent;
            for (VariableDeclarator v : d.getVariables()) {
                if (!IGNORED.matcher(v.getNameAsString()).matches() && !suppressed(v) && unused(v, scope)) {
                    out.add(new Taint.Hit(UNUSED_LOCAL, v));
                }
            }
        }
        out.sort(Comparator.comparingInt((Taint.Hit h) -> h.node().getBegin().orElseThrow().line));
        return out;
    }

    /** 선언을 가진 블록(중첩 블록·람다·익명 클래스 포함) 안에서 그 이름의 SimpleName 이 선언자 자신뿐이다 — 대입·증감·메서드 참조도 「나옴」 */
    private static boolean unused(VariableDeclarator v, Node scope) {
        Optional<Range> own = v.getName().getRange();
        return scope.findAll(SimpleName.class).stream()
                .noneMatch(n -> n.getIdentifier().equals(v.getNameAsString()) && !n.getRange().equals(own));
    }

    /** 선언·둘러싼 메서드·클래스의 @SuppressWarnings("unused"|"all") */
    private static boolean suppressed(Node n) {
        for (Node at = n; at != null; at = at.getParentNode().orElse(null)) {
            if (at instanceof NodeWithAnnotations<?> a) {
                for (AnnotationExpr x : a.getAnnotations()) {
                    String text = x.toString();
                    if (SUPPRESS_WARNINGS.equals(x.getName().getIdentifier()) && SUPPRESS.stream().anyMatch(text::contains)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 지워도 동작도 다른 지적도 안 바뀌는 초기화식 — 리터럴·null·this.x 와 그것들의 + - * · 괄호 · 삼항 · 단항(+ - ! ~). 단순 이름은 아니다(지우면 그 이름이 새로 안 쓰는 변수가 된다) */
    static boolean pure(Expression e) {
        if (e instanceof LiteralExpr) {
            return true;
        }
        if (e instanceof EnclosedExpr x) {
            return pure(x.getInner());
        }
        if (e instanceof UnaryExpr u) {
            return UNARY.contains(u.getOperator()) && pure(u.getExpression());
        }
        if (e instanceof BinaryExpr b) {
            return BINARY.contains(b.getOperator()) && pure(b.getLeft()) && pure(b.getRight());
        }
        if (e instanceof ConditionalExpr c) {
            return pure(c.getCondition()) && pure(c.getThenExpr()) && pure(c.getElseExpr());
        }
        return e instanceof FieldAccessExpr f && f.getScope() instanceof ThisExpr;
    }
}
