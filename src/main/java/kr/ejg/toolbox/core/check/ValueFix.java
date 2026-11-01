package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 코드 정리 ③ 값·비교(설계 50) — {@link ValueAst} 의 지적 노드만 한 줄 안에서 바꾼다(지적 = 고침, 판정을 다시 하지 않는다).
 * 문자열 {@code ==} → equals · {@code YYYY} → {@code yyyy} · 리터럴 앞세우기 · {@code new BigDecimal(double)} → {@code BigDecimal.valueOf}.
 * 못 바꾸는 지적은 건너뜀 사유 한 줄(코드 본문 없음)
 */
final class ValueFix {

    private static final String OBJECTS_IMPORT = "java.util.Objects";
    private static final String OBJECTS = "Objects";
    private static final String MULTI_LINE = "여러 줄 식";
    private static final int ONE = 1;

    /** 한 줄 안 바꾸기 — 열은 0 기준 [from, to) */
    private record Change(int from, int to, String text) {
    }

    private ValueFix() {
    }

    /** text 는 \n 정규화 · cur 는 지금 지적 · targets 는 고를 값 규칙 */
    static void apply(String text, List<Finding> cur, Set<String> targets, JavaSource parser, Cleanup.Edits e,
            List<Cleanup.Skip> skips) {
        String unsafe = Cleanup.javaUnsafe(text, cur);
        if (unsafe != null) {
            skips.add(new Cleanup.Skip(Cleanup.VALUE, unsafe));
            return;
        }
        ParseResult<CompilationUnit> r = parser.parse(text);
        if (!JavaSource.ok(r)) {
            skips.add(new Cleanup.Skip(Cleanup.VALUE, "구문을 못 읽음"));
            return;
        }
        CompilationUnit cu = r.getResult().orElseThrow();
        String[] lines = text.split("\n", -1);
        Map<Integer, List<Change>> byLine = new TreeMap<>();
        for (Taint.Hit h : ValueAst.apply(cu, targets)) {
            Range g = h.node().getRange().orElseThrow();
            int no = g.begin.line;
            if (g.end.line != no) {
                skip(skips, no, MULTI_LINE);
                continue;
            }
            rewrite(h, cu, lines[no - 1], no, skips).ifPresent(t -> byLine.computeIfAbsent(no, k -> new ArrayList<>())
                    .add(new Change(g.begin.column - 1, g.end.column, t)));
        }
        byLine.forEach((no, cs) -> e.replace(no, merge(lines[no - 1], no, cs, skips)));
    }

    /** 바깥 바꾸기만 — 안에 든 것은 「겹친 식」. 열 내림차순으로 끼운다 */
    private static String merge(String line, int no, List<Change> cs, List<Cleanup.Skip> skips) {
        cs.sort(Comparator.comparingInt(Change::from).thenComparing(Comparator.comparingInt(Change::to).reversed()));
        List<Change> kept = new ArrayList<>();
        for (Change c : cs) {
            if (kept.stream().anyMatch(k -> c.from() < k.to() && k.from() <= c.from())) {
                skip(skips, no, "겹친 식");
            } else {
                kept.add(c);
            }
        }
        StringBuilder sb = new StringBuilder(line);
        for (int i = kept.size() - 1; i >= 0; i--) {
            Change c = kept.get(i);
            sb.replace(c.from(), c.to(), c.text());
        }
        return sb.toString();
    }

    private static Optional<String> rewrite(Taint.Hit h, CompilationUnit cu, String line, int no, List<Cleanup.Skip> skips) {
        Node n = h.node();
        switch (h.rule()) {
            case ValueAst.REF_EQUALS:
                return refEquals((BinaryExpr) n, cu, line, no, skips);
            case ValueAst.WEEK_YEAR:
                return Optional.of(weekYear(slice(line, n)));
            case ValueAst.BIG_DECIMAL:
                return bigDecimal((ObjectCreationExpr) n, line, no, skips);
            case ValueAst.EQUALS_FIRST:
                return Optional.of(literalFirst((MethodCallExpr) n, line));
            default:
                return Optional.empty();
        }
    }

    /** 리터럴 한쪽 → {@code "L".equals(X)} · 이름 둘 → {@code Objects.equals(a, b)}(import 가 있을 때만) */
    private static Optional<String> refEquals(BinaryExpr b, CompilationUnit cu, String line, int no, List<Cleanup.Skip> skips) {
        Expression l = strip(b.getLeft());
        Expression r = strip(b.getRight());
        String not = b.getOperator() == BinaryExpr.Operator.NOT_EQUALS ? "!" : "";
        if (l.isStringLiteralExpr() && r.isStringLiteralExpr()) {
            skip(skips, no, "양쪽이 리터럴");
            return Optional.empty();
        }
        if (l.isStringLiteralExpr() || r.isStringLiteralExpr()) {
            Expression lit = l.isStringLiteralExpr() ? l : r;
            Expression other = l.isStringLiteralExpr() ? r : l;
            return Optional.of(not + slice(line, lit) + ".equals(" + slice(line, other) + ")");
        }
        if (!hasObjectsImport(cu)) {
            skip(skips, no, "이름끼리 비교 — java.util.Objects import 필요");
            return Optional.empty();
        }
        return Optional.of(not + "Objects.equals(" + slice(line, l) + ", " + slice(line, r) + ")");
    }

    /** 작은따옴표 밖 Y 만 y 로 — 따옴표 안은 형식 문자가 아니라 글이다 */
    private static String weekYear(String literal) {
        StringBuilder sb = new StringBuilder(literal.length());
        boolean quoted = false;
        for (char c : literal.toCharArray()) {
            quoted ^= c == '\'';
            sb.append(!quoted && c == 'Y' ? 'y' : c);
        }
        return sb.toString();
    }

    /** {@code S.equals("L")} → {@code "L".equals(S)} — scope 괄호는 벗긴다 */
    private static String literalFirst(MethodCallExpr m, String line) {
        Expression scope = strip(m.getScope().orElseThrow());
        return slice(line, strip(m.getArgument(0))) + "." + m.getNameAsString() + "(" + slice(line, scope) + ")";
    }

    /** {@code new BigDecimal(A)} → {@code BigDecimal.valueOf(A)} — 인자 둘(MathContext)·float 는 건너뜀 */
    private static Optional<String> bigDecimal(ObjectCreationExpr o, String line, int no, List<Cleanup.Skip> skips) {
        if (o.getArguments().size() > ONE) {
            skip(skips, no, "MathContext 인자");
            return Optional.empty();
        }
        if (ValueAst.isFloat(o)) {
            skip(skips, no, "float 는 valueOf 가 double 로 넓힌다");
            return Optional.empty();
        }
        Range t = o.getType().getRange().orElseThrow();
        Range all = o.getRange().orElseThrow();
        String rest = line.substring(t.end.column, all.end.column).stripLeading();
        return Optional.of(slice(line, o.getType()) + ".valueOf" + rest);
    }

    /** {@code import java.util.Objects;} 가 있고 다른 {@code *.Objects} 단일 import 가 없다 */
    private static boolean hasObjectsImport(CompilationUnit cu) {
        boolean util = false;
        for (ImportDeclaration i : cu.getImports()) {
            if (i.isStatic() || i.isAsterisk() || !OBJECTS.equals(i.getName().getIdentifier())) {
                continue;
            }
            if (!OBJECTS_IMPORT.equals(i.getNameAsString())) {
                return false;
            }
            util = true;
        }
        return util;
    }

    /** 한 줄 안 노드의 원문 글 */
    private static String slice(String line, Node n) {
        Range g = n.getRange().orElseThrow();
        return line.substring(g.begin.column - 1, g.end.column);
    }

    private static Expression strip(Expression e) {
        Expression x = e;
        while (x instanceof EnclosedExpr en) {
            x = en.getInner();
        }
        return x;
    }

    private static void skip(List<Cleanup.Skip> skips, int no, String why) {
        skips.add(new Cleanup.Skip(Cleanup.VALUE, "줄 " + no + ": " + why));
    }
}
