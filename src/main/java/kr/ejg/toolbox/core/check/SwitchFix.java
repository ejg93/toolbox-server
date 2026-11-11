package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.nodeTypes.SwitchNode;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.ThrowStmt;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 설계 64 §3-6 — 코드 정리 단계 switch. 가운데 default 의 줄을 마지막 case 뒤로 옮긴다.
 * default·그 앞 case·마지막 case 가 전부 탈출(break·return·throw·continue, 또는 {@code ->})로 끝나고 entry 마다 줄을 혼자 쓸 때만 — 아니면 사유로 건너뜀.
 */
final class SwitchFix {

    static final String SPLIT = "case 가 줄을 나눠 쓴다";
    static final String GROUPED = "default 가 다음 case 와 묶임";
    static final String FALLS = "default 가 다음 case 로 떨어진다";
    static final String PREV_FALLS = "앞 case 가 default 로 떨어진다";
    static final String LAST_FALLS = "마지막 case 가 떨어진다";

    private SwitchFix() {
    }

    /** text 는 \n 정규화 · cur 는 지금 지적 */
    static void apply(String text, List<Finding> cur, JavaSource parser, Edits e, List<Cleanup.Skip> skips) {
        String unsafe = Cleanup.javaUnsafe(text, cur);
        if (unsafe != null) {
            skips.add(new Cleanup.Skip(Cleanup.SWITCH, unsafe));
            return;
        }
        ParseResult<CompilationUnit> r = parser.parse(text);
        if (!JavaSource.ok(r)) {
            skips.add(new Cleanup.Skip(Cleanup.SWITCH, "구문을 못 읽음"));
            return;
        }
        String[] lines = text.split("\n", -1);
        for (Taint.Hit h : JavaShape.apply(r.getResult().orElseThrow(), Set.of(JavaShape.DEFAULT_LAST))) {
            SwitchEntry d = (SwitchEntry) h.node();
            List<SwitchEntry> all = d.getParentNode().filter(SwitchNode.class::isInstance).map(p -> List.copyOf(((SwitchNode) p).getEntries()))
                    .orElse(List.of(d));
            String why = refuse(lines, d, all);
            if (why != null) {
                skips.add(new Cleanup.Skip(Cleanup.SWITCH, "줄 " + line(d.getBegin()) + ": " + why));
                continue;
            }
            int from = d.getComment().map(c -> line(c.getBegin())).orElse(line(d.getBegin()));
            int to = line(end(d));
            int last = line(end(all.get(all.size() - 1)));
            List<String> moved = new ArrayList<>();
            for (int n = from; n <= to; n++) {
                moved.add(lines[n - 1]);
                e.delete(n);
            }
            e.replace(last, lines[last - 1] + "\n" + String.join("\n", moved));
        }
    }

    /** 옮기면 안 되는 사유, 옮겨도 되면 null */
    private static String refuse(String[] lines, SwitchEntry d, List<SwitchEntry> all) {
        for (int i = 0; i < all.size(); i++) {
            SwitchEntry x = all.get(i);
            boolean alone = lines[line(x.getBegin()) - 1].substring(0, col(x.getBegin()) - 1).isBlank()
                    && lines[line(end(x)) - 1].substring(col(end(x))).replaceFirst("^;", "").isBlank(); // -> 꼴 범위는 ; 앞에서 끝난다
            if (!alone || i + 1 < all.size() && line(all.get(i + 1).getBegin()) <= line(end(x))) {
                return SPLIT;
            }
        }
        if (d.getStatements().isEmpty()) {
            return GROUPED;
        }
        if (!escapes(d)) {
            return FALLS;
        }
        int at = all.indexOf(d);
        if (at > 0 && !escapes(all.get(at - 1))) {
            return PREV_FALLS;
        }
        return escapes(all.get(all.size() - 1)) ? null : LAST_FALLS;
    }

    /** entry 끝 */
    private static Optional<Position> end(SwitchEntry x) {
        return x.getEnd();
    }

    private static boolean escapes(SwitchEntry x) {
        if (x.getType() != SwitchEntry.Type.STATEMENT_GROUP) {
            return true;
        }
        if (x.getStatements().isEmpty()) {
            return false;
        }
        Statement s = x.getStatements().getLast().orElseThrow();
        return s instanceof BreakStmt || s instanceof ReturnStmt || s instanceof ThrowStmt || s instanceof ContinueStmt;
    }

    private static int line(Optional<Position> p) {
        return p.map(x -> x.line).orElse(1);
    }

    private static int col(Optional<Position> p) {
        return p.map(x -> x.column).orElse(1);
    }
}
