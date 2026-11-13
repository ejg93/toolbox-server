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
    static final String DECL = "default 안 지역 선언 — 범위가 바뀐다";

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
        String[] bare = RegexRule.stripComments(text, "java").split("\n", -1); // 주석을 공백으로 — 줄 수가 같다
        for (Taint.Hit h : JavaShape.apply(r.getResult().orElseThrow(), Set.of(JavaShape.DEFAULT_LAST)).stream().filter(x -> JavaShape.flagged(cur, x)).toList()) {
            SwitchEntry d = (SwitchEntry) h.node();
            List<SwitchEntry> all = d.getParentNode().filter(SwitchNode.class::isInstance).map(p -> List.copyOf(((SwitchNode) p).getEntries()))
                    .orElse(List.of(d));
            String why = refuse(lines, d, all);
            if (why != null) {
                skips.add(new Cleanup.Skip(Cleanup.SWITCH, "줄 " + line(d.getBegin()) + ": " + why));
                continue;
            }
            int from = from(lines, bare, d);
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

    /**
     * 5-93c ⑥ — 옮길 첫 줄: default 위로 주석·빈 줄을 올라가고(앞 entry 의 끝 줄·switch 머리 줄은 코드가 있어 멈춘다) 앞쪽 빈 줄은 뺀다.
     * 텍스트 블록이 있어 줄 수가 다르면 옛 길(바로 위 주석 하나).
     * 5-99c — 소유는 들여쓰기로 가른다. default 줄보다 깊은 {@code //} 줄은 앞 case 것이라 그 줄과 그 위는 남긴다(블록 주석은 자르지 않는다)
     */
    private static int from(String[] lines, String[] bare, SwitchEntry d) {
        int at = line(d.getBegin());
        if (bare.length != lines.length) {
            return d.getComment().map(c -> line(c.getBegin())).orElse(at);
        }
        int from = at;
        while (from > 1 && bare[from - 2].isBlank()) {
            from--;
        }
        int own = indent(lines[at - 1]);
        for (int n = at - 1; n >= from; n--) { // 5-99c — default 보다 깊이 들여쓴 줄 주석은 앞 case 것. 그 줄과 그 위는 안 옮긴다
            if (lines[n - 1].stripLeading().startsWith("//") && indent(lines[n - 1]) > own) {
                from = n + 1;
                break;
            }
        }
        while (from < at && lines[from - 1].isBlank()) {
            from++;
        }
        return from;
    }

    private static int indent(String line) {
        return line.length() - line.stripLeading().length();
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
        if (declares(d)) {
            return DECL; // 마무리 리뷰 I2 — 옛 꼴 switch 는 블록 하나: 뒤 case 가 그 이름을 쓸 수 있다
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

    private static boolean declares(SwitchEntry x) {
        return x.getStatements().stream().anyMatch(s -> s.isExpressionStmt() && s.asExpressionStmt().getExpression().isVariableDeclarationExpr()
                || s.isLocalClassDeclarationStmt() || s.isLocalRecordDeclarationStmt());
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
