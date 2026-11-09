package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.ArrayList;
import java.util.Comparator;
import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 코드 정리 ③ 값·비교(설계 50) — {@link ValueAst} 의 지적 노드만 한 줄 안에서 바꾼다(지적 = 고침, 판정을 다시 하지 않는다).
 * 문자열 {@code ==} → equals · {@code YYYY} → {@code yyyy} · 리터럴 앞세우기 · {@code new BigDecimal(double)} → {@code BigDecimal.valueOf}.
 * 못 바꾸는 지적은 건너뜀 사유 한 줄(코드 본문 없음)
 */
final class ValueFix {

    private static final String OBJECTS_IMPORT = "java.util.Objects";
    private static final String OBJECTS = "Objects";
    private static final String MULTI_LINE = "여러 줄 식";
    private static final String STANDARD_IMPORT = "java.nio.charset.StandardCharsets";
    private static final String CHARSET_IMPORT = "java.nio.charset.Charset";
    private static final String UTF_8 = "UTF-8";
    /** 프로필 인코딩(대문자) → StandardCharsets 상수 */
    private static final Map<String, String> STANDARD = Map.of("UTF-8", "UTF_8", "UTF8", "UTF_8", "UTF-8-BOM", "UTF_8",
            "US-ASCII", "US_ASCII", "ASCII", "US_ASCII", "ISO-8859-1", "ISO_8859_1", "LATIN1", "ISO_8859_1", "UTF-16", "UTF_16");
    private static final Pattern CHARSET_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:+-]*");
    private static final int ONE = 1;

    /** 한 줄 안 바꾸기 — 열은 0 기준 [from, to) */
    private record Change(int from, int to, String text) {
    }

    private ValueFix() {
    }

    /** text 는 \n 정규화 · cur 는 지금 지적 · targets 는 고를 값 규칙 */
    static void apply(String text, List<Finding> cur, Set<String> targets, JavaSource parser, String encoding, Cleanup.Edits e,
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
        Set<String> need = new LinkedHashSet<>();
        for (Taint.Hit h : ValueAst.apply(cu, targets)) {
            Range g = h.node().getRange().orElseThrow();
            int no = g.begin.line;
            if (g.end.line != no) {
                skip(skips, no, MULTI_LINE);
                continue;
            }
            if (ValueAst.DEFAULT_CHARSET.equals(h.rule())) {
                charset(h.node(), lines[no - 1], no, encoding, cu, need, skips).ifPresent(t -> byLine.computeIfAbsent(no, k -> new ArrayList<>())
                        .add(new Change(g.begin.column - 1, g.end.column, t)));
                continue;
            }
            rewrite(h, cu, lines, no, need, skips).ifPresent(t -> byLine.computeIfAbsent(no, k -> new ArrayList<>())
                    .add(new Change(g.begin.column - 1, g.end.column, t)));
        }
        String imp = Imports.text(cu, need);
        if (!imp.isEmpty()) { // 파일에 한 번 — 줄 바꾸기보다 먼저 써야 첫 쓰기가 이긴다(5-62c 차례 그대로)
            int at = Imports.line(cu);
            if (at == 0) {
                e.replace(1, imp + "\n" + lines[0]);
            } else {
                e.replace(at, lines[at - 1] + "\n" + imp);
            }
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

    private static Optional<String> rewrite(Taint.Hit h, CompilationUnit cu, String[] lines, int no, Set<String> need,
            List<Cleanup.Skip> skips) {
        Node n = h.node();
        String line = lines[no - 1];
        switch (h.rule()) {
            case ValueAst.REF_EQUALS:
                return refEquals((BinaryExpr) n, cu, lines, no, need, skips);
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

    /** 리터럴 한쪽 → {@code "L".equals(X)} · 이름 둘 → {@code Objects.equals(a, b)}(import 가 없으면 끼운다 — 다른 Objects import 면 건너뜀, 5-62c) */
    private static Optional<String> refEquals(BinaryExpr b, CompilationUnit cu, String[] lines, int no, Set<String> need,
            List<Cleanup.Skip> skips) {
        String line = lines[no - 1];
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
        if (!Imports.has(cu, OBJECTS_IMPORT)) {
            if (Imports.clash(cu, OBJECTS_IMPORT)) {
                skip(skips, no, "이름끼리 비교 — 다른 Objects import 와 충돌");
                return Optional.empty();
            }
            if (Imports.used(cu, OBJECTS)) { // 마무리 리뷰 — import 없이 쓰는 같은 패키지 Objects 를 끼운 import 가 가린다
                skip(skips, no, "이름끼리 비교 — 다른 Objects 사용과 충돌");
                return Optional.empty();
            }
            need.add(OBJECTS_IMPORT); // 끼우기는 apply 가 파일에 한 번
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

    /** 설계 60 — x.getBytes() → x.getBytes(CS) · new String(b) → new String(b, CS). CS 는 프로필 인코딩(없으면 UTF-8) */
    private static Optional<String> charset(Node n, String line, int no, String encoding, CompilationUnit cu, Set<String> need,
            List<Cleanup.Skip> skips) {
        String[] cs = charsetExpr(encoding);
        if (cs.length == 0) {
            skip(skips, no, "프로필 project.encoding 꼴을 못 읽음");
            return Optional.empty();
        }
        if (Imports.clash(cu, cs[1]) || !Imports.has(cu, cs[1]) && Imports.used(cu, Imports.simple(cs[1]))) {
            skip(skips, no, "다른 StandardCharsets·Charset 과 이름 충돌");
            return Optional.empty();
        }
        need.add(cs[1]);
        String t = slice(line, n);
        String head = t.substring(0, t.length() - 1).stripTrailing();
        return Optional.of(head + (n instanceof ObjectCreationExpr ? ", " : "") + cs[0] + ")");
    }

    /**
     * 대문자로 맞대 UTF-8·UTF8·UTF-8-BOM → StandardCharsets.UTF_8 · US-ASCII·ASCII → US_ASCII · ISO-8859-1·LATIN1 → ISO_8859_1 · UTF-16 → UTF_16.
     * 그 밖은 이름 꼴이고 Charset.isSupported 일 때만 Charset.forName("프로필에 적힌 글 그대로") — 아니면 길이 0. null·빈 글은 UTF-8.
     * 돌려주는 것은 {식, import}
     */
    private static String[] charsetExpr(String encoding) {
        String raw = encoding == null || encoding.isBlank() ? UTF_8 : encoding.strip();
        String std = STANDARD.get(raw.toUpperCase(Locale.ROOT));
        if (std != null) {
            return new String[] {"StandardCharsets." + std, STANDARD_IMPORT};
        }
        if (CHARSET_NAME.matcher(raw).matches() && Charset.isSupported(raw)) {
            return new String[] {"Charset.forName(\"" + raw + "\")", CHARSET_IMPORT};
        }
        return new String[0];
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
