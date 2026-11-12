package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 설계 64 §3-7 — 코드 정리 단계 interrupt. 단일형 {@code catch (InterruptedException e)} 몸 첫 줄에 {@code Thread.currentThread().interrupt();} 를 끼운다.
 * 몸 여는 중괄호가 그 줄 끝일 때만 — 한 줄 몸·다른 Thread import 는 사유로 건너뜀. 중단 신호를 돌려주는 것이라 동작이 바뀐다(기본 끔).
 */
final class InterruptFix {

    static final String ONE_LINE = "catch 몸이 한 줄";
    static final String OTHER_THREAD = "Thread 이름이 다른 형식";
    private static final String CALL = "Thread.currentThread().interrupt();";

    private InterruptFix() {
    }

    /** text 는 \n 정규화 · cur 는 지금 지적 */
    static void apply(String text, List<Finding> cur, JavaSource parser, Edits e, List<Cleanup.Skip> skips) {
        String unsafe = Cleanup.javaUnsafe(text, cur);
        if (unsafe != null) {
            skips.add(new Cleanup.Skip(Cleanup.INTERRUPT, unsafe));
            return;
        }
        ParseResult<CompilationUnit> r = parser.parse(text);
        if (!JavaSource.ok(r)) {
            skips.add(new Cleanup.Skip(Cleanup.INTERRUPT, "구문을 못 읽음"));
            return;
        }
        CompilationUnit cu = r.getResult().orElseThrow();
        boolean otherThread = cu.getImports().stream().anyMatch(InterruptFix::otherThread);
        String[] lines = text.split("\n", -1);
        for (Taint.Hit h : JavaShape.apply(cu, Set.of(JavaShape.INTERRUPTED))) {
            Parameter p = (Parameter) h.node();
            BlockStmt body = p.getParentNode().filter(CatchClause.class::isInstance).map(c -> ((CatchClause) c).getBody()).orElse(null);
            if (body == null) {
                continue;
            }
            int brace = line(body.getBegin());
            String why = otherThread ? OTHER_THREAD : ONE_LINE;
            if (otherThread || !lines[brace - 1].substring(col(body.getBegin())).isBlank()) {
                skips.add(new Cleanup.Skip(Cleanup.INTERRUPT, "줄 " + line(p.getBegin()) + ": " + why));
                continue;
            }
            e.replace(brace, lines[brace - 1] + "\n" + indent(lines, body, brace) + CALL);
        }
    }

    /** java.lang.Thread 가 아닌 단일 Thread import */
    private static boolean otherThread(ImportDeclaration d) {
        return !d.isStatic() && !d.isAsterisk() && "Thread".equals(d.getName().getIdentifier()) && !"java.lang.Thread".equals(d.getNameAsString());
    }

    /** 몸의 첫 문장 줄 들여쓰기 — 몸이 비면 { 줄 들여쓰기 + 한 단위(탭이 있으면 탭, 아니면 네 칸) */
    private static String indent(String[] lines, BlockStmt body, int brace) {
        if (body.getStatements().isNonEmpty()) {
            return lead(lines[line(body.getStatement(0).getBegin()) - 1]);
        }
        String base = lead(lines[brace - 1]);
        return base + (base.indexOf('\t') >= 0 ? "\t" : "    ");
    }

    private static String lead(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
            i++;
        }
        return s.substring(0, i);
    }

    private static int line(Optional<Position> p) {
        return p.map(x -> x.line).orElse(1);
    }

    private static int col(Optional<Position> p) {
        return p.map(x -> x.column).orElse(1);
    }
}
