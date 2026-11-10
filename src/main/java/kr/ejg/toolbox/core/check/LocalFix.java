package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 코드 정리 지역 변수(설계 60·61) — LocalsAst 의 지적 가운데 지워도 동작이 안 바뀌는 선언 줄만 지운다. 초기화식이 호출 하나면 호출만 남긴다(지적 = 고침 대상, 판정을 다시 안 한다) */
final class LocalFix {
    private static final String TWO = "한 문장에 선언 둘";
    private static final String MULTI = "여러 줄 선언";
    private static final String SHARED = "한 줄에 다른 코드";
    private static final String IMPURE = "초기화식이 리터럴도 호출 하나도 아니다 — 지우면 동작이 바뀔 수 있다";
    private static final String TARGET_TYPED = "초기화식에 람다·메서드 참조 — 호출만 남기면 형식 추론이 달라질 수 있다";
    private static final String ALONE = "블록에 이 선언뿐 — 지우면 빈 블록";
    private static final String LINE_COMMENT = "//";
    private static final int ONE = 1;

    private LocalFix() {
    }

    /** text 는 \n 정규화 · cur 는 지금 지적 */
    static void apply(String text, List<Finding> cur, JavaSource parser, Cleanup.Edits e, List<Cleanup.Skip> skips) {
        String unsafe = Cleanup.javaUnsafe(text, cur);
        if (unsafe != null) {
            skips.add(new Cleanup.Skip(Cleanup.LOCAL, unsafe));
            return;
        }
        ParseResult<CompilationUnit> r = parser.parse(text);
        if (!JavaSource.ok(r)) {
            skips.add(new Cleanup.Skip(Cleanup.LOCAL, "구문을 못 읽음"));
            return;
        }
        Set<Integer> flagged = cur.stream().filter(f -> LocalsAst.UNUSED_LOCAL.equals(f.rule())).map(Finding::line).collect(Collectors.toSet());
        String[] lines = text.split("\n", -1);
        for (Taint.Hit h : LocalsAst.apply(r.getResult().orElseThrow(), Set.of(LocalsAst.UNUSED_LOCAL))) {
            VariableDeclarator v = (VariableDeclarator) h.node();
            ExpressionStmt st = (ExpressionStmt) v.getParentNode().flatMap(Node::getParentNode).orElseThrow();
            Range g = st.getRange().orElseThrow();
            int no = g.begin.line;
            if (!flagged.contains(v.getBegin().orElseThrow().line)) {
                continue; // 억제 표식으로 눌린 선언(지적 없음)
            }
            String why = reason(v, st, g, lines[no - 1]);
            if (!why.isEmpty()) {
                skips.add(new Cleanup.Skip(Cleanup.LOCAL, "줄 " + no + ": " + why));
            } else if (v.getInitializer().filter(MethodCallExpr.class::isInstance).isPresent()) {
                e.replace(no, callOnly(v.getInitializer().orElseThrow(), g, lines[no - 1]));
            } else {
                e.delete(no);
            }
        }
    }

    /** 지울 수 없는 사유 — 빈 글이면 지운다 */
    private static String reason(VariableDeclarator v, ExpressionStmt st, Range g, String line) {
        if (((VariableDeclarationExpr) st.getExpression()).getVariables().size() > ONE) {
            return TWO;
        }
        if (g.end.line != g.begin.line) {
            return MULTI;
        }
        String rest = line.substring(Math.min(line.length(), g.end.column)).strip();
        if (!line.substring(0, g.begin.column - 1).isBlank() || !rest.isEmpty() && !rest.startsWith(LINE_COMMENT)) {
            return SHARED;
        }
        Optional<Expression> init = v.getInitializer();
        if (init.filter(MethodCallExpr.class::isInstance).isPresent()) {
            return typed(init.orElseThrow()) ? TARGET_TYPED : ""; // 호출 문장이 남으니 「블록에 이 선언뿐」 은 안 본다
        }
        if (init.filter(x -> !LocalsAst.pure(x)).isPresent()) {
            return IMPURE;
        }
        Optional<Node> parent = st.getParentNode();
        if (parent.filter(p -> p instanceof BlockStmt b && b.getStatements().size() == ONE).isPresent()) {
            return ALONE;
        }
        return "";
    }

    /** 대입 대상 형식에 기대는 식 — 선언을 떼면 추론이 달라질 수 있다 */
    private static boolean typed(Expression init) {
        return init.findFirst(LambdaExpr.class).isPresent() || init.findFirst(MethodReferenceExpr.class).isPresent();
    }

    /** 선언 머리(형식 이름 =)를 떼고 호출과 그 뒤(; · 꼬리 주석)를 남긴다 — 들여쓰기는 그대로 */
    private static String callOnly(Expression init, Range g, String line) {
        Range i = init.getRange().orElseThrow();
        return line.substring(0, g.begin.column - 1) + line.substring(i.begin.column - 1, i.end.column) + line.substring(g.end.column - 1);
    }
}
