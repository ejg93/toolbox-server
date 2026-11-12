package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.UnionType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 설계 64 §3-7 — 코드 정리 단계 interrupt. {@code catch (InterruptedException e)} 몸 첫 줄에 {@code Thread.currentThread().interrupt();} 를 끼운다.
 * union catch 는 {@code if (e instanceof InterruptedException) { … }} 세 줄로(설계 65 5-94).
 * 몸 여는 중괄호 뒤가 공백·줄 주석일 때만 — 한 줄 몸·중괄호 뒤 다른 글·다른 Thread import·루프 안(몸에 break·return·throw 없음)은 사유로 건너뜀.
 * Allman 꼴은 catch 줄도 고친 줄로 올린다(설계 65 5-93a). 중단 신호를 돌려주는 것이라 동작이 바뀐다(기본 끔).
 */
final class InterruptFix {

    static final String ONE_LINE = "catch 몸이 한 줄";
    static final String OTHER_THREAD = "Thread 이름이 다른 형식";
    static final String AFTER_BRACE = "여는 중괄호 뒤에 다른 글";
    static final String IN_LOOP = "루프 안 catch — interrupt 를 끼우면 대기가 곧바로 다시 깨진다, 손으로";
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
        for (Taint.Hit h : JavaShape.apply(cu, Set.of(JavaShape.INTERRUPTED)).stream().filter(x -> JavaShape.flagged(cur, x)).toList()) {
            Parameter p = (Parameter) h.node();
            CatchClause k = p.getParentNode().filter(CatchClause.class::isInstance).map(CatchClause.class::cast).orElse(null);
            if (k == null) {
                continue;
            }
            String why = otherThread ? OTHER_THREAD : reason(lines, k);
            if (why != null) {
                skips.add(new Cleanup.Skip(Cleanup.INTERRUPT, "줄 " + line(p.getBegin()) + ": " + why));
                continue;
            }
            BlockStmt body = k.getBody();
            int brace = line(body.getBegin());
            String in = indent(lines, body, brace);
            String call = p.getType() instanceof UnionType ? union(in, p.getNameAsString()) : in + CALL;
            e.replace(brace, lines[brace - 1] + "\n" + call);
            int head = line(k.getBegin());
            if (head != brace) {
                e.replace(head, lines[head - 1]); // 5-93a ② Allman — catch 줄을 고친 줄로(java.emptyCatch 가 그 줄에서 사라진다)
            }
        }
    }

    /** 5-94 — union catch 는 InterruptedException 일 때만 되세운다(세 줄, 한 단위 = 들여쓰기에 탭이 있으면 탭) */
    private static String union(String in, String name) {
        String unit = in.indexOf('\t') >= 0 ? "\t" : "    ";
        return in + "if (" + name + " instanceof InterruptedException) {\n" + in + unit + CALL + "\n" + in + "}";
    }

    /** 고칠 수 있으면 null — ① 루프 안(탈출 없음) ③ { 뒤가 공백·줄 주석이 아니면 한 줄 몸·다른 글 */
    private static String reason(String[] lines, CatchClause k) {
        BlockStmt body = k.getBody();
        if (inLoop(k) && !leaves(body)) {
            return IN_LOOP;
        }
        int brace = line(body.getBegin());
        String rest = lines[brace - 1].substring(col(body.getBegin()));
        if (rest.isBlank() || rest.strip().startsWith("//")) {
            return null;
        }
        return line(body.getEnd()) == brace ? ONE_LINE : AFTER_BRACE;
    }

    /** catch 에서 위로 메서드·람다·초기화 블록까지 가는 길에 for·foreach·while·do 가 있다 */
    private static boolean inLoop(Node k) {
        for (Node a = k.getParentNode().orElse(null); a != null && !(a instanceof BodyDeclaration) && !(a instanceof LambdaExpr); a = a.getParentNode().orElse(null)) {
            if (a instanceof Statement s && JavaShape.loopBody(s) != null) {
                return true;
            }
        }
        return false;
    }

    /** 몸에 루프를 벗어나는 문장(break·return·throw)이 있다 — 람다·안쪽 클래스 안 것은 안 센다 */
    private static boolean leaves(BlockStmt body) {
        return body.findAll(Statement.class).stream().filter(s -> s instanceof BreakStmt || s instanceof ReturnStmt || s instanceof ThrowStmt)
                .anyMatch(s -> s.findAncestor(LambdaExpr.class, a -> body.isAncestorOf(a)).isEmpty()
                        && s.findAncestor(BodyDeclaration.class, a -> body.isAncestorOf(a)).isEmpty());
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
