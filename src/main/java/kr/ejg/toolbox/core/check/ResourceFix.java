package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 코드 정리 ④ 자원 해제(설계 50) — {@link ResourceAst} 의 {@code java.tryWithResources} 지적 try 를 try-with-resources 로 바꾼다.
 * 판정(옮겨도 되나)은 {@link ResourceAst#plan} 이 끝냈고 여기는 줄 꼴만 본다 — 선언·대입·finally 줄을 지우고 try 줄에 자원 머리를 쓴다.
 * 줄을 지우기만 하고 끼우지 않는다. 못 바꾸는 지적은 건너뜀 사유 한 줄(코드 본문 없음)
 */
final class ResourceFix {

    private static final Pattern TRY_OPEN = Pattern.compile("^\\s*try\\s*\\{\\s*$");
    private static final Pattern FINALLY_OPEN = Pattern.compile("^\\s*}\\s*finally\\s*\\{\\s*$");
    private static final Pattern CLOSE_BRACE = Pattern.compile("^\\s*}\\s*$");
    private static final Pattern LEADING = Pattern.compile("^\\s*");
    private static final String SHAPE = "줄 꼴이 달라 못 고침(한 줄에 다른 코드·여러 줄 식)";

    /** 한 try 의 고침 — 지울 줄 · 새 try 머리 · finally 자리 닫는 줄 */
    private record Edit(List<Integer> deletes, String head, int finallyLine, String tail) {
        List<Integer> lines() {
            List<Integer> all = new ArrayList<>(deletes);
            all.add(finallyLine);
            return all;
        }
    }

    private ResourceFix() {
    }

    /** text 는 \n 정규화 · cur 는 지금 지적 */
    static void apply(String text, List<Finding> cur, JavaSource parser, Cleanup.Edits e, List<Cleanup.Skip> skips) {
        String unsafe = Cleanup.javaUnsafe(text, cur);
        if (unsafe != null) {
            skips.add(new Cleanup.Skip(Cleanup.RESOURCE, unsafe));
            return;
        }
        ParseResult<CompilationUnit> r = parser.parse(text);
        if (!JavaSource.ok(r)) {
            skips.add(new Cleanup.Skip(Cleanup.RESOURCE, "구문을 못 읽음"));
            return;
        }
        CompilationUnit cu = r.getResult().orElseThrow();
        List<String> lines = List.of(text.split("\n", -1));
        List<TryStmt> done = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (Taint.Hit h : ResourceAst.apply(cu, Set.of(ResourceAst.TRY_WITH_RESOURCES))) {
            TryStmt t = (TryStmt) h.node();
            int no = line(t);
            if (done.stream().anyMatch(o -> o.isAncestorOf(t))) {
                skip(skips, no, "겹친 try");
                continue;
            }
            Optional<ResourceAst.Twr> plan = ResourceAst.plan(t, cu);
            Optional<Edit> edit = plan.flatMap(p -> edit(p, lines));
            if (edit.isEmpty() || edit.get().lines().stream().anyMatch(used::contains)) {
                skip(skips, no, edit.isEmpty() ? SHAPE : "겹친 try");
                continue;
            }
            done.add(t);
            used.addAll(edit.get().lines());
            edit.get().deletes().forEach(e::delete);
            e.replace(no, edit.get().head());
            e.replace(edit.get().finallyLine(), edit.get().tail());
        }
    }

    /** 줄 꼴 다섯 — try 줄 · 선언·대입 줄 · } finally { 줄 · 닫는 줄. 하나라도 틀리면 빈 값 */
    private static Optional<Edit> edit(ResourceAst.Twr p, List<String> lines) {
        TryStmt t = p.tryStmt();
        BlockStmt fin = t.getFinallyBlock().orElseThrow();
        int tryLine = line(t);
        int finOpen = line(fin);
        int finClose = fin.getEnd().map(x -> x.line).orElse(0);
        boolean shape = TRY_OPEN.matcher(lines.get(tryLine - 1)).matches() && FINALLY_OPEN.matcher(lines.get(finOpen - 1)).matches()
                && CLOSE_BRACE.matcher(lines.get(finClose - 1)).matches();
        List<Integer> deletes = new ArrayList<>();
        List<String> heads = new ArrayList<>();
        for (ResourceAst.Res res : p.resources()) {
            shape &= alone(res.decl(), lines) && res.assign().map(a -> alone(a, lines)).orElse(true) && oneLine(res.init());
            if (!shape) {
                return Optional.empty();
            }
            deletes.add(line(res.decl()));
            res.assign().ifPresent(a -> deletes.add(line(a)));
            heads.add(slice(lines, res.type()) + " " + res.name() + " = " + slice(lines, res.init()));
        }
        if (!shape || !finalOnly(p)) {
            return Optional.empty();
        }
        for (int k = finOpen + 1; k <= finClose; k++) {
            deletes.add(k);
        }
        String indent = indent(lines.get(tryLine - 1));
        return Optional.of(new Edit(deletes, indent + "try (" + String.join("; ", heads) + ") {", finOpen, indent(lines.get(finOpen - 1)) + "}"));
    }

    /** 선언에 애너테이션이 없다 — 머리로 옮길 때 수식어(final)는 뗀다 */
    private static boolean finalOnly(ResourceAst.Twr p) {
        return p.resources().stream().map(r -> r.decl().getExpression()).filter(VariableDeclarationExpr.class::isInstance)
                .map(VariableDeclarationExpr.class::cast).allMatch(d -> d.getAnnotations().isEmpty());
    }

    /** 문장이 한 줄이고 그 줄에 다른 글이 없다 */
    private static boolean alone(Node n, List<String> lines) {
        Range g = n.getRange().orElseThrow();
        if (g.begin.line != g.end.line) {
            return false;
        }
        String s = lines.get(g.begin.line - 1);
        return s.substring(0, g.begin.column - 1).isBlank() && s.substring(Math.min(s.length(), g.end.column)).isBlank();
    }

    private static boolean oneLine(Node n) {
        Range g = n.getRange().orElseThrow();
        return g.begin.line == g.end.line;
    }

    private static String slice(List<String> lines, Node n) {
        Range g = n.getRange().orElseThrow();
        return lines.get(g.begin.line - 1).substring(g.begin.column - 1, g.end.column);
    }

    private static String indent(String line) {
        java.util.regex.Matcher m = LEADING.matcher(line);
        return m.find() ? m.group() : "";
    }

    private static int line(Node n) {
        return n.getBegin().map(x -> x.line).orElse(1);
    }

    private static void skip(List<Cleanup.Skip> skips, int no, String why) {
        skips.add(new Cleanup.Skip(Cleanup.RESOURCE, "줄 " + no + ": " + why));
    }
}
