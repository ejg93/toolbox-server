package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 설계 64 §3-5 — 코드 정리 단계 syntax. 지적 셋을 줄 단위로 고친다(확실한 꼴만, 나머지는 사유로 건너뜀).
 * <ul>
 *   <li>{@code java.needBraces} — 같은 줄 몸은 {@code { … }} 로 감싼다(같은 줄에 중괄호 없는 문장이 겹치면 건너뜀) · 여러 줄 몸은 머리 줄 끝
 *   {@code )}·{@code else}·{@code do} 뒤에 {@code {}, 몸 끝 다음 줄에 {@code }} — 다음 줄이 같은 if 의 else·같은 do 의 while 이면 그 줄 앞에 {@code } }</li>
 *   <li>{@code java.emptyStatement} — 홀로 {@code ;} 줄은 지우고, 아니면 그 {@code ;} 한 글자</li>
 *   <li>{@code java.unneededBlock} — 선언이 없는 블록의 {@code {}·{@code }} 줄을 지우고 안 줄을 한 단위 내어쓴다</li>
 * </ul>
 * 줄마다 열 고침·접두·접미를 모아 한 번 바꾼다. 다른 지적의 고침과 같은 자리(접두·접미·겹친 열·지운 줄)에 닿으면 앞 것이 이긴다.
 */
final class SyntaxFix {

    static final String NESTED = "중첩된 중괄호 없는 문장";
    static final String HEAD = "머리 줄 끝에 다른 글";
    static final String BODY = "몸 줄에 다른 글";
    static final String DECL = "블록 안 선언 — 범위가 바뀐다";
    static final String BLOCK_LINE = "블록 줄에 다른 글";
    static final String SAME_LINE = "같은 줄에 다른 고침";
    private static final Pattern BRACELESS = Pattern.compile("(?:\\)|\\belse|\\bdo)\\s*$");
    private static final Pattern ELSE = Pattern.compile("^\\s*else\\b");
    private static final Pattern WHILE = Pattern.compile("^\\s*while\\b");
    private static final Pattern EMPTY_BLOCK = Pattern.compile("^\\s*\\{\\s*}\\s*$");
    private static final String SEMI = ";";
    private static final String OPEN = "{";
    private static final String CLOSE = "}";

    /** 한 줄의 고침 — 원문 열 [시작, 끝) 바꾸기(내림차순 적용) · 들여쓰기 뒤 접두 · 끝 접미 · 줄 삭제 */
    private static final class Line {
        private final List<int[]> spans = new ArrayList<>();
        private final List<String> texts = new ArrayList<>();
        private String prefix;
        private String suffix;
        private boolean delete;

        boolean clashes(Line o) {
            if (delete || o.delete || prefix != null && o.prefix != null || suffix != null && o.suffix != null) {
                return true;
            }
            for (int[] a : spans) {
                for (int[] b : o.spans) {
                    if (a[0] <= b[1] && b[0] <= a[1]) {
                        return true;
                    }
                }
            }
            return false;
        }

        void merge(Line o) {
            spans.addAll(o.spans);
            texts.addAll(o.texts);
            prefix = prefix == null ? o.prefix : prefix;
            suffix = suffix == null ? o.suffix : suffix;
        }

        String render(String orig) {
            String s = orig;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < spans.size(); i++) {
                order.add(i);
            }
            order.sort((x, y) -> Integer.compare(spans.get(y)[0], spans.get(x)[0]));
            for (int i : order) {
                s = s.substring(0, spans.get(i)[0]) + texts.get(i) + s.substring(spans.get(i)[1]);
            }
            if (prefix != null) {
                int ind = indent(s).length();
                s = s.substring(0, ind) + prefix + s.substring(ind);
            }
            return suffix == null ? s : s.stripTrailing() + suffix;
        }
    }

    /** 한 지적의 고침 — 줄 번호 → 고침, 못 고치면 사유 */
    private static final class Fix {
        private final Map<Integer, Line> lines = new TreeMap<>();
        private String why;
        private int whyLine;

        Line at(int n) {
            return lines.computeIfAbsent(n, k -> new Line());
        }

        void span(int n, int start, int end, String text) {
            at(n).spans.add(new int[] {start, end});
            at(n).texts.add(text);
        }

        void prefix(int n, String p) {
            if (at(n).prefix != null) {
                fail(n, SAME_LINE);
            }
            at(n).prefix = p;
        }

        void suffix(int n, String p) {
            if (at(n).suffix != null) {
                fail(n, SAME_LINE);
            }
            at(n).suffix = p;
        }

        void fail(int n, String reason) {
            if (why == null) {
                why = reason;
                whyLine = n;
            }
        }
    }

    private SyntaxFix() {
    }

    /** text 는 \n 정규화 · cur 는 지금 지적 · targets 는 이 단계가 고칠 규칙 */
    static void apply(String text, List<Finding> cur, Set<String> targets, JavaSource parser, Edits e, List<Cleanup.Skip> skips) {
        String unsafe = Cleanup.javaUnsafe(text, cur);
        if (unsafe != null) {
            skips.add(new Cleanup.Skip(Cleanup.SYNTAX, unsafe));
            return;
        }
        ParseResult<CompilationUnit> r = parser.parse(text);
        if (!JavaSource.ok(r)) {
            skips.add(new Cleanup.Skip(Cleanup.SYNTAX, "구문을 못 읽음"));
            return;
        }
        String[] lines = text.split("\n", -1);
        int[] starts = Cleanup.lineStarts(text);
        Map<Integer, Line> all = new TreeMap<>();
        for (Taint.Hit h : JavaShape.apply(r.getResult().orElseThrow(), targets)) {
            Fix f = new Fix();
            build(h, lines, starts, f);
            if (f.why == null && f.lines.entrySet().stream().anyMatch(x -> all.containsKey(x.getKey()) && all.get(x.getKey()).clashes(x.getValue()))) {
                f.fail(line(h.node().getBegin()), SAME_LINE);
            }
            if (f.why != null) {
                skips.add(new Cleanup.Skip(Cleanup.SYNTAX, "줄 " + f.whyLine + ": " + f.why));
                continue;
            }
            f.lines.forEach((n, l) -> all.merge(n, l, (a, b) -> {
                a.merge(b);
                return a;
            }));
        }
        all.forEach((n, l) -> {
            if (l.delete) {
                e.delete(n);
            } else {
                e.replace(n, l.render(lines[n - 1]));
            }
        });
    }

    private static void build(Taint.Hit h, String[] lines, int[] starts, Fix f) {
        if (h.node() instanceof EmptyStmt s) {
            empty(lines, s, f);
        } else if (h.node() instanceof BlockStmt b) {
            block(lines, b, f);
        } else if (h.node() instanceof IfStmt s) {
            if (!(s.getThenStmt() instanceof BlockStmt)) {
                brace(s, s.getThenStmt(), true, lines, starts, f);
            }
            s.getElseStmt().filter(x -> !(x instanceof BlockStmt) && !(x instanceof IfStmt)).ifPresent(x -> brace(s, x, false, lines, starts, f));
        } else if (h.node() instanceof Statement s && JavaShape.loopBody(s) != null) {
            brace(s, JavaShape.loopBody(s), false, lines, starts, f);
        }
    }

    /** 중괄호 없는 몸 b(owner 의 then·else·루프 몸)를 감싼다 */
    private static void brace(Statement owner, Statement b, boolean then, String[] lines, int[] starts, Fix f) {
        int bl = line(b.getBegin());
        int el = line(b.getEnd());
        int h = headLine(lines, starts, b);
        if (h == bl) {
            if (el != bl) {
                f.fail(bl, BODY);
            } else if (nestedBody(owner, b)) {
                f.fail(bl, NESTED);
            } else {
                int c0 = col(b.getBegin()) - 1;
                int c1 = col(b.getEnd());
                f.span(bl, c0, c0, "{ ");
                f.span(bl, c1, c1, " }");
            }
            return;
        }
        if (!BRACELESS.matcher(lines[h - 1]).find()) {
            f.fail(h, HEAD);
            return;
        }
        if (!lines[bl - 1].substring(0, col(b.getBegin()) - 1).isBlank() || !lines[el - 1].substring(col(b.getEnd())).isBlank()) {
            f.fail(bl, BODY);
            return;
        }
        f.suffix(h, " {");
        int next = el;
        while (next < lines.length && lines[next].isBlank()) {
            next++;
        }
        if (next < lines.length && joins(owner, then, lines[next])) {
            f.prefix(next + 1, "} ");
        } else {
            f.suffix(el, "\n" + indent(lines[h - 1]) + "}");
        }
    }

    /** 몸 끝 다음 줄이 같은 if 의 else·같은 do 의 while 이면 닫는 } 를 그 줄 앞에 */
    private static boolean joins(Statement owner, boolean then, String next) {
        if (owner instanceof IfStmt i && then && i.getElseStmt().isPresent()) {
            return ELSE.matcher(next).find();
        }
        return owner instanceof DoStmt && WHILE.matcher(next).find();
    }

    /** 같은 줄 몸 안에 제어문이 있거나, owner 자신이 바깥 제어문의 같은 줄 몸(else if 사슬은 아님) */
    private static boolean nestedBody(Statement owner, Statement b) {
        if (b instanceof IfStmt || JavaShape.loopBody(b) != null) {
            return true;
        }
        Node p = owner.getParentNode().orElse(null);
        boolean chain = p instanceof IfStmt pi && pi.getElseStmt().filter(x -> x.getRange().equals(owner.getRange())).isPresent();
        boolean bare = p instanceof IfStmt || p instanceof Statement ps && JavaShape.loopBody(ps) != null;
        return bare && !chain && line(p.getBegin()) == line(owner.getBegin());
    }

    /** 몸 b 앞 마지막 공백 아닌 글자의 줄 */
    private static int headLine(String[] lines, int[] starts, Statement b) {
        int bl = line(b.getBegin());
        int off = starts[bl - 1] + col(b.getBegin()) - 2;
        int n = bl;
        while (off >= 0) {
            while (n > 1 && off < starts[n - 1]) {
                n--;
            }
            char c = lines[n - 1].isEmpty() || off - starts[n - 1] >= lines[n - 1].length() ? ' ' : lines[n - 1].charAt(off - starts[n - 1]);
            if (!Character.isWhitespace(c)) {
                return n;
            }
            off--;
        }
        return bl;
    }

    private static void empty(String[] lines, EmptyStmt s, Fix f) {
        int n = line(s.getBegin());
        int c = col(s.getBegin()) - 1;
        if (SEMI.equals(lines[n - 1].trim())) {
            f.at(n).delete = true;
        } else if (c < lines[n - 1].length() && lines[n - 1].charAt(c) == ';') {
            f.span(n, c, c + 1, "");
        } else {
            f.fail(n, BODY);
        }
    }

    private static void block(String[] lines, BlockStmt b, Fix f) {
        int bl = line(b.getBegin());
        int el = line(b.getEnd());
        boolean decl = b.getStatements().stream().anyMatch(s -> s instanceof LocalClassDeclarationStmt || s instanceof LocalRecordDeclarationStmt
                || s instanceof ExpressionStmt x && x.getExpression() instanceof VariableDeclarationExpr);
        if (decl) {
            f.fail(bl, DECL);
        } else if (bl == el) {
            if (EMPTY_BLOCK.matcher(lines[bl - 1]).find()) {
                f.at(bl).delete = true;
            } else {
                f.fail(bl, BLOCK_LINE);
            }
        } else if (!OPEN.equals(lines[bl - 1].trim()) || !CLOSE.equals(lines[el - 1].trim())) {
            f.fail(bl, BLOCK_LINE);
        } else {
            f.at(bl).delete = true;
            f.at(el).delete = true;
            dedent(lines, bl, el, f);
        }
    }

    /** 안 줄 전부가 { 줄 들여쓰기 + 한 단위로 시작하면 그 단위만큼 내어쓰기 */
    private static void dedent(String[] lines, int bl, int el, Fix f) {
        String base = indent(lines[bl - 1]);
        int first = bl + 1;
        while (first < el && lines[first - 1].isBlank()) {
            first++;
        }
        String inner = first < el ? indent(lines[first - 1]) : base;
        if (inner.length() <= base.length() || !inner.startsWith(base)) {
            return;
        }
        for (int n = bl + 1; n < el; n++) {
            if (!lines[n - 1].isBlank() && !lines[n - 1].startsWith(inner)) {
                return;
            }
        }
        for (int n = bl + 1; n < el; n++) {
            if (!lines[n - 1].isBlank()) {
                f.span(n, base.length(), inner.length(), "");
            }
        }
    }

    private static String indent(String s) {
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
