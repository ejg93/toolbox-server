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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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

    /** 한 try 의 고침 — 지울 줄 · 새 try 머리 · finally 자리(꼴 A' 는 마지막 close 줄) 닫는 줄 · 꼴 A' 의 한 단 들여쓴 몸 줄(그 밖은 빈 맵) */
    private record Edit(List<Integer> deletes, String head, int finallyLine, String tail, Map<Integer, String> body) {
        List<Integer> lines() {
            List<Integer> all = new ArrayList<>(deletes);
            if (finallyLine > 0) {
                all.add(finallyLine);
            }
            all.addAll(body.keySet());
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
        List<Node> done = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (Taint.Hit h : ResourceAst.apply(cu, Set.of(ResourceAst.TRY_WITH_RESOURCES))) {
            Node n = h.node();
            int no = line(n);
            if (done.stream().anyMatch(o -> o.isAncestorOf(n))) {
                skip(skips, no, "겹친 try");
                continue;
            }
            Optional<ResourceAst.Twr> plan = ResourceAst.plan(n, cu);
            Optional<Edit> edit = plan.flatMap(p -> p.anchor() instanceof TryStmt ? edit(p, lines) : wrap(p, lines));
            if (edit.isEmpty() || edit.get().lines().stream().anyMatch(used::contains)) {
                skip(skips, no, edit.isEmpty() ? SHAPE : "겹친 try");
                continue;
            }
            done.add(n);
            used.addAll(edit.get().lines());
            edit.get().deletes().forEach(e::delete);
            e.replace(no, edit.get().head());
            if (edit.get().finallyLine() > 0) { // 5-62b — 꼴 C 는 finally 줄이 없다
                e.replace(edit.get().finallyLine(), edit.get().tail());
            }
            edit.get().body().forEach(e::replace);
        }
    }

    /** 줄 꼴 다섯 — try 줄 · 선언·대입 줄 · } finally { 줄 · 닫는 줄(꼴 C 는 finally 대신 혼자 있는 close 줄). 하나라도 틀리면 빈 값 */
    private static Optional<Edit> edit(ResourceAst.Twr p, List<String> lines) {
        TryStmt t = (TryStmt) p.anchor();
        Optional<BlockStmt> fin = t.getFinallyBlock();
        int tryLine = line(t);
        int finOpen = fin.map(ResourceFix::line).orElse(0);
        int finClose = fin.flatMap(f -> f.getEnd()).map(x -> x.line).orElse(0);
        boolean shape = TRY_OPEN.matcher(lines.get(tryLine - 1)).matches() && finallyShape(finOpen, finClose, lines)
                && p.closes().stream().allMatch(c -> alone(c, lines));
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
        p.closes().forEach(c -> deletes.add(line(c)));
        for (int k = finOpen + 1; k <= finClose; k++) { // 꼴 C 는 0 번 돈다
            deletes.add(k);
        }
        String indent = indent(lines.get(tryLine - 1));
        String tail = finOpen > 0 ? indent(lines.get(finOpen - 1)) + "}" : "";
        return Optional.of(new Edit(deletes, indent + "try (" + String.join("; ", heads) + ") {", finOpen, tail, Map.of()));
    }

    /** 5-63 — 꼴 A': 선언 줄 → try 머리 · 마지막 close 줄 → 닫는 줄 · 몸 한 단 들여쓰기. 줄 수 불변 */
    private static Optional<Edit> wrap(ResourceAst.Twr p, List<String> lines) {
        int first = line(p.anchor());
        List<Integer> deletes = new ArrayList<>();
        List<String> heads = new ArrayList<>();
        for (ResourceAst.Res res : p.resources()) {
            if (!alone(res.decl(), lines) || !oneLine(res.init())) {
                return Optional.empty();
            }
            if (line(res.decl()) != first) {
                deletes.add(line(res.decl()));
            }
            heads.add(slice(lines, res.type()) + " " + res.name() + " = " + slice(lines, res.init()));
        }
        if (!finalOnly(p) || !p.closes().stream().allMatch(c -> alone(c, lines))) {
            return Optional.empty();
        }
        int last = p.closes().stream().mapToInt(ResourceFix::line).max().orElseThrow();
        p.closes().stream().map(ResourceFix::line).filter(l -> l != last).forEach(deletes::add);
        String indent = indent(lines.get(first - 1));
        String step = step(lines, p.anchor(), indent);
        Map<Integer, String> body = new TreeMap<>();
        for (int k = first + 1; k < last; k++) {
            if (!deletes.contains(k) && !lines.get(k - 1).isBlank()) {
                body.put(k, step + lines.get(k - 1));
            }
        }
        return Optional.of(new Edit(deletes, indent + "try (" + String.join("; ", heads) + ") {", last, indent + "}", body));
    }

    /** 블록 한 단 — 선언 줄 들여쓰기에서 블록을 가진 문장 줄의 들여쓰기를 뗀 것. 못 재면 탭 또는 네 칸 */
    private static String step(List<String> lines, Node anchor, String indent) {
        String outer = anchor.getParentNode().flatMap(Node::getParentNode).map(o -> indent(lines.get(line(o) - 1))).orElse("");
        if (indent.startsWith(outer) && indent.length() > outer.length()) {
            return indent.substring(outer.length());
        }
        return indent.contains("\t") ? "\t" : "    ";
    }

    /** } finally { 줄과 닫는 줄 — finally 가 없으면(꼴 C) 참 */
    private static boolean finallyShape(int finOpen, int finClose, List<String> lines) {
        return finOpen == 0 || FINALLY_OPEN.matcher(lines.get(finOpen - 1)).matches() && CLOSE_BRACE.matcher(lines.get(finClose - 1)).matches();
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
