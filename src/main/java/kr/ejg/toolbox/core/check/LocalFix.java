package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 코드 정리 지역 변수(설계 60) — LocalsAst 의 지적 가운데 지워도 동작이 안 바뀌는 선언 줄만 지운다(지적 = 고침 대상, 판정을 다시 안 한다) */
final class LocalFix {
    private static final String TWO = "한 문장에 선언 둘";
    private static final String MULTI = "여러 줄 선언";
    private static final String SHARED = "한 줄에 다른 코드";
    private static final String IMPURE = "초기화식이 리터럴이 아니다 — 지우면 동작이 바뀔 수 있다";
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
            if (why.isEmpty()) {
                e.delete(no);
            } else {
                skips.add(new Cleanup.Skip(Cleanup.LOCAL, "줄 " + no + ": " + why));
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
        if (v.getInitializer().filter(x -> !LocalsAst.pure(x)).isPresent()) {
            return IMPURE;
        }
        Optional<Node> parent = st.getParentNode();
        if (parent.filter(p -> p instanceof BlockStmt b && b.getStatements().size() == ONE).isPresent()) {
            return ALONE;
        }
        return "";
    }
}
