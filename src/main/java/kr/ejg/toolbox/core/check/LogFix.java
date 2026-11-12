package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * 코드 정리 로거(설계 60) — {@code System.out/err.print·println(x)} 를 그 클래스의 로거 {@code debug·error} 로 바꾼다.
 * 로거가 없으면 같은 소스 루트에서 베낀 static 로거 선언({@link LoggerShape})을 최상위 클래스에 끼운다. 베낄 꼴이 없으면 건너뛴다.
 * main 안·catch 안·초기화 블록 안은 안 바꾼다. 건너뜀 사유는 한 줄(코드 본문 없음)
 * 5-86(설계 64) — catch 매개변수의 {@code e.printStackTrace();} 문장도 {@code 로거.error(e.getMessage(), e)} 로(같은 로거 길)
 */
final class LogFix {
    static final String SYSOUT = "common.sysout";
    static final String PRINT_STACK = "common.printStackTrace";
    private static final String PRINT_STACK_NAME = "printStackTrace";
    private static final Set<String> PRINTS = Set.of("print", "println");
    private static final Set<String> LOGGER_TYPES = Set.of("Logger", "Log");
    /** debug·error 가 있는 Lombok 로거 — @Log(java.util.logging)·@Flogger 는 없다 */
    private static final Set<String> LOMBOK = Set.of("Slf4j", "Log4j", "Log4j2", "CommonsLog", "XSlf4j", "JBossLog");
    private static final String LOMBOK_NAME = "log";
    private static final String JUL = "java.util.logging";
    /** JDK 9+ System.Logger — debug·error 가 없다(마무리 리뷰) */
    private static final List<String> SYSTEM_LOGGER = List.of("System.Logger", "java.lang.System.Logger");
    private static final String SLOT_SHAPE = "로거를 끼울 자리의 줄 꼴이 다르다";
    private static final String MAIN = "main";
    private static final String SYSTEM = "System";
    private static final String ERR = "err";
    private static final String NOT_CLASS = "인터페이스·enum·record — 로거 필드를 안 넣는다";
    private static final String NO_LOGGER = "로거가 없고 같은 소스 루트에 베낄 로거 꼴도 없다";

    /** 쓸 로거 — owner 는 선언한 형식(끼울 것이면 최상위 클래스), insert 면 아직 없다 */
    private record Logger(String name, boolean isStatic, Node owner, boolean insert) {
    }

    /** 최상위 클래스에 끼울 자리 — after 면 그 줄 뒤에, 아니면 그 줄 앞에. reason 이 있으면 못 끼운다 */
    private record Slot(int line, boolean after, String indent, String reason) {
    }

    /** 한 줄 안 바꾸기 — 열은 0 기준 [from, to) */
    private record Change(int from, int to, String text) {
    }

    private LogFix() {
    }

    /** @param targets 이 단계에서 고칠 규칙 — sysout·printStackTrace 가운데 고른 것만 */
    static void apply(Source s, List<Finding> cur, Set<String> targets, JavaSource parser, Function<String, Optional<LoggerShape>> loggers, Edits e,
            List<Cleanup.Skip> skips) {
        if (!"java".equals(s.lang())) {
            return; // jsp 는 지적만
        }
        String unsafe = Cleanup.javaUnsafe(s.text(), cur);
        if (unsafe != null) {
            skips.add(new Cleanup.Skip(Cleanup.LOG, unsafe));
            return;
        }
        ParseResult<CompilationUnit> r = parser.parse(s.text());
        if (!JavaSource.ok(r)) {
            skips.add(new Cleanup.Skip(Cleanup.LOG, "구문을 못 읽음"));
            return;
        }
        CompilationUnit cu = r.getResult().orElseThrow();
        String[] lines = s.text().split("\n", -1);
        Set<Integer> flagged = new TreeSet<>();
        cur.stream().filter(f -> targets.contains(f.rule())).forEach(f -> flagged.add(f.line()));
        Map<Integer, List<MethodCallExpr>> calls = new HashMap<>();
        cu.findAll(MethodCallExpr.class).stream().filter(c -> wanted(c, targets))
                .forEach(c -> calls.computeIfAbsent(c.getBegin().orElseThrow().line, k -> new ArrayList<>()).add(c));
        Map<Integer, List<Change>> byLine = new TreeMap<>();
        Map<Node, Slot> slots = new IdentityHashMap<>();
        Map<Node, Slot> chosen = new IdentityHashMap<>();
        Set<String> imports = new LinkedHashSet<>();
        Optional<LoggerShape> shape = Optional.empty();
        for (int no : flagged) {
            List<MethodCallExpr> cs = calls.getOrDefault(no, List.of());
            if (cs.isEmpty()) {
                skip(skips, no, "print·println 호출이 아니다");
            }
            for (MethodCallExpr c : cs) {
                String why = place(c, cu, stackCall(c));
                Optional<Logger> found = why.isEmpty() ? find(c) : Optional.empty();
                if (why.isEmpty() && found.isEmpty()) {
                    shape = shape.or(() -> loggers.apply(s.rel()));
                    why = shape.map(sh -> insertable(c, cu, sh, lines, slots)).orElseGet(() -> noShape(c));
                    found = why.isEmpty() ? Optional.of(new Logger(shape.get().name(), true, top(c), true)) : Optional.empty();
                }
                if (why.isEmpty()) {
                    why = usable(c, cu, found.orElseThrow());
                }
                if (!why.isEmpty()) {
                    skip(skips, no, why);
                    continue;
                }
                Logger lg = found.orElseThrow();
                if (lg.insert()) { // 그 클래스에서 실제로 바꾸는 호출이 있을 때만 끼운다
                    imports.addAll(shape.get().imports());
                    chosen.put(lg.owner(), slots.get(lg.owner()));
                }
                Range g = c.getRange().orElseThrow();
                byLine.computeIfAbsent(no, k -> new ArrayList<>()).add(new Change(g.begin.column - 1, g.end.column, call(c, lg, lines[no - 1])));
            }
        }
        write(cu, lines, byLine, chosen, shape, imports, e);
    }

    /** System.out/err.print·println 호출 */
    private static boolean printCall(MethodCallExpr m) {
        return PRINTS.contains(m.getNameAsString()) && m.getScope().filter(FieldAccessExpr.class::isInstance).map(FieldAccessExpr.class::cast)
                .filter(f -> f.getScope() instanceof NameExpr n && SYSTEM.equals(n.getNameAsString()))
                .filter(f -> "out".equals(f.getNameAsString()) || ERR.equals(f.getNameAsString())).isPresent();
    }

    /** 고른 규칙의 호출 — sysout 은 print·println, printStackTrace 는 stackCall */
    private static boolean wanted(MethodCallExpr c, Set<String> targets) {
        return targets.contains(SYSOUT) && printCall(c) || targets.contains(PRINT_STACK) && stackCall(c);
    }

    /** 5-86 — e.printStackTrace() — 인자 없음, 수신자는 이름 하나 */
    private static boolean stackCall(MethodCallExpr m) {
        return PRINT_STACK_NAME.equals(m.getNameAsString()) && m.getArguments().isEmpty() && m.getScope().filter(NameExpr.class::isInstance).isPresent();
    }

    /** 자리 때문에 못 바꾸는 사유 — 여러 줄 · 인자 없음 · main · catch(printStackTrace 는 catch 매개변수의 문장만) · 초기화 · java.util.logging */
    private static String place(MethodCallExpr c, CompilationUnit cu, boolean stack) {
        Range g = c.getRange().orElseThrow();
        if (g.begin.line != g.end.line) {
            return "여러 줄 식";
        }
        if (!stack && c.getArguments().isEmpty()) {
            return "인자 없는 출력";
        }
        Optional<MethodDeclaration> m = c.findAncestor(MethodDeclaration.class);
        if (m.filter(d -> MAIN.equals(d.getNameAsString()) && d.isStatic() && d.getParameters().size() == 1).isPresent()) {
            return "main 의 콘솔 출력";
        }
        String inCatch = stack ? stackPlace(c) : c.findAncestor(CatchClause.class).map(k -> "catch 안 출력 — 로그·던지기 판정이 바뀌어 손으로").orElse("");
        if (!inCatch.isEmpty()) {
            return inCatch;
        }
        if (c.findAncestor(InitializerDeclaration.class).isPresent() || c.findAncestor(FieldDeclaration.class).isPresent()) {
            return "초기화 블록·필드 초기화식 안";
        }
        if (cu.getImports().stream().anyMatch(i -> i.getNameAsString().startsWith(JUL))) {
            return "java.util.logging 로거 — debug·error 가 없다";
        }
        return "";
    }

    /** 5-86 — printStackTrace 는 가장 가까운 catch 의 매개변수를 받는 문장일 때만(람다·안쪽 클래스·안쪽 catch 를 넘지 않는다) */
    private static String stackPlace(MethodCallExpr c) {
        if (!ownCatch(c)) {
            return "catch 매개변수의 printStackTrace 가 아니다 — 손으로";
        }
        return c.getParentNode().orElse(null) instanceof ExpressionStmt ? "" : "식의 일부";
    }

    private static boolean ownCatch(MethodCallExpr c) {
        String name = ((NameExpr) c.getScope().orElseThrow()).getNameAsString();
        for (Node a = c.getParentNode().orElse(null); a != null; a = a.getParentNode().orElse(null)) {
            if (a instanceof CatchClause k) {
                return name.equals(k.getParameter().getNameAsString());
            }
            if (a instanceof LambdaExpr || a instanceof BodyDeclaration) {
                return false;
            }
        }
        return false;
    }

    /** 바깥으로 둘러싼 형식마다 Logger·Log 형식 필드 → Lombok 로거 애너테이션 */
    private static Optional<Logger> find(MethodCallExpr c) {
        for (Node a = c.getParentNode().orElse(null); a != null; a = a.getParentNode().orElse(null)) {
            if (!(a instanceof TypeDeclaration<?> t)) {
                continue;
            }
            for (FieldDeclaration f : t.getFields()) {
                VariableDeclarator v = f.getVariable(0);
                if (LOGGER_TYPES.contains(DeclaredTypes.simpleType(v.getType()))) {
                    return Optional.of(new Logger(v.getNameAsString(), f.isStatic(), t, false));
                }
            }
            if (t.getAnnotations().stream().anyMatch(x -> LOMBOK.contains(x.getName().getIdentifier()))) {
                return Optional.of(new Logger(LOMBOK_NAME, true, t, false));
            }
        }
        return Optional.empty();
    }

    /** 찾은·끼울 로거를 그 자리에서 쓸 수 있나 — JUL 필드 · 같은 이름 지역 변수·매개변수 · static 문맥의 인스턴스 로거 */
    private static String usable(MethodCallExpr c, CompilationUnit cu, Logger lg) {
        if (!lg.insert() && lg.owner() instanceof TypeDeclaration<?> t && t.getFieldByName(lg.name())
                .filter(f -> f.getVariable(0).getType().asString().startsWith(JUL)).isPresent()) {
            return "java.util.logging 로거 — debug·error 가 없다";
        }
        if (!lg.insert() && lg.owner() instanceof TypeDeclaration<?> t && t.getFieldByName(lg.name())
                .filter(f -> SYSTEM_LOGGER.contains(f.getVariable(0).getType().asString())).isPresent()
                || cu.getImports().stream().anyMatch(i -> SYSTEM_LOGGER.get(1).equals(i.getNameAsString()))) {
            return "System.Logger — debug·error 가 없다";
        }
        boolean shadow = cu.findAll(Parameter.class).stream().anyMatch(p -> p.getNameAsString().equals(lg.name()))
                || cu.findAll(VariableDeclarator.class).stream()
                        .anyMatch(v -> v.getNameAsString().equals(lg.name()) && !(v.getParentNode().orElse(null) instanceof FieldDeclaration));
        if (shadow) {
            return "로거 이름과 같은 지역 변수·매개변수가 있다";
        }
        if (!lg.isStatic() && staticContext(c, lg.owner())) {
            return "static 문맥 — 인스턴스 로거를 못 쓴다";
        }
        return "";
    }

    /** 호출에서 owner 까지 올라가는 길에 static 메서드·static 중첩 형식이 있다 */
    private static boolean staticContext(Node c, Node owner) {
        for (Node a = c.getParentNode().orElse(null); a != null && !same(a, owner); a = a.getParentNode().orElse(null)) {
            boolean staticType = a instanceof TypeDeclaration<?> t && !(t instanceof ClassOrInterfaceDeclaration k && !k.isStatic() && !k.isInterface());
            if (a instanceof MethodDeclaration m && m.isStatic() || staticType) {
                return true;
            }
        }
        return false;
    }

    /** 5-73 ⑤ — 베낄 꼴이 없을 때도 설계 60 §3-9 표 차례: 클래스가 아니면 그 사유가 먼저 */
    private static String noShape(MethodCallExpr c) {
        return top(c) instanceof ClassOrInterfaceDeclaration k && !k.isInterface() ? NO_LOGGER : NOT_CLASS;
    }

    /** 로거가 없을 때 — 최상위 클래스·베낄 꼴·이름·import·끼울 자리를 본다. 빈 글이면 끼울 수 있다(자리는 slots 에 기억) */
    private static String insertable(MethodCallExpr c, CompilationUnit cu, LoggerShape sh, String[] lines, Map<Node, Slot> slots) {
        Node top = top(c);
        if (!(top instanceof ClassOrInterfaceDeclaration k) || k.isInterface()) {
            return NOT_CLASS;
        }
        if (cu.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals(sh.name()))
                || cu.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals(sh.name()))) {
            return "물려받은 로거를 쓰는 것으로 보인다 — 손으로";
        }
        if (sh.imports().stream().anyMatch(f -> Imports.clash(cu, f) || !Imports.has(cu, f) && Imports.used(cu, Imports.simple(f)))) {
            return "로거 형식이 이 파일의 다른 것과 겹친다";
        }
        Slot slot = slots.computeIfAbsent(top, t -> slot(lines, k));
        return slot.reason();
    }

    /** 첫 인스턴스 필드·생성자·메서드 M 앞의 마지막 static 필드 뒤, 없으면 M 앞 */
    private static Slot slot(String[] lines, ClassOrInterfaceDeclaration k) {
        FieldDeclaration lastStatic = null;
        BodyDeclaration<?> first = null;
        for (BodyDeclaration<?> b : k.getMembers()) {
            if (b instanceof FieldDeclaration f && f.isStatic()) {
                lastStatic = f;
            } else if (b instanceof FieldDeclaration || b instanceof ConstructorDeclaration || b instanceof MethodDeclaration) {
                first = b;
                break;
            }
        }
        if (lastStatic != null) {
            Range g = lastStatic.getRange().orElseThrow();
            String line = lines[g.end.line - 1];
            boolean clean = line.substring(Math.min(line.length(), g.end.column)).isBlank();
            return new Slot(g.end.line, true, indent(lines[g.begin.line - 1]), clean ? "" : SLOT_SHAPE);
        }
        if (first == null) {
            return new Slot(0, false, "", "빈 클래스 몸");
        }
        com.github.javaparser.Position at = first.getComment().flatMap(Node::getBegin).orElse(first.getBegin().orElseThrow());
        String head = lines[at.line - 1].substring(0, Math.min(lines[at.line - 1].length(), at.column - 1));
        return new Slot(at.line, false, indent(lines[at.line - 1]), head.isBlank() ? "" : SLOT_SHAPE); // 마무리 리뷰 — 멤버가 class 줄에 있으면 앞에 못 끼운다
    }

    /** {@code logger.debug(x)} — 인자가 String 이 아니면 String.valueOf 로 감싼다 · printStackTrace 는 {@code logger.error(e.getMessage(), e)} */
    private static String call(MethodCallExpr c, Logger lg, String line) {
        if (stackCall(c)) {
            String p = ((NameExpr) c.getScope().orElseThrow()).getNameAsString();
            return lg.name() + ".error(" + p + ".getMessage(), " + p + ")";
        }
        Expression arg = c.getArgument(0);
        Range g = arg.getRange().orElseThrow();
        String text = line.substring(g.begin.column - 1, g.end.column);
        String level = ERR.equals(((FieldAccessExpr) c.getScope().orElseThrow()).getNameAsString()) ? "error" : "debug";
        return lg.name() + "." + level + "(" + (ValueAst.stringy(arg) ? text : "String.valueOf(" + text + ")") + ")";
    }

    /** 줄마다 한 번 — 호출 바꿈(열 내림차순) 뒤 필드·import 끼움을 덧붙인다 */
    private static void write(CompilationUnit cu, String[] lines, Map<Integer, List<Change>> byLine, Map<Node, Slot> slots,
            Optional<LoggerShape> shape, Set<String> imports, Edits e) {
        Map<Integer, String> out = new TreeMap<>();
        byLine.forEach((no, cs) -> {
            StringBuilder sb = new StringBuilder(lines[no - 1]);
            cs.stream().sorted(Comparator.comparingInt(Change::from).reversed()).forEach(c -> sb.replace(c.from(), c.to(), c.text()));
            out.put(no, sb.toString());
        });
        slots.forEach((top, sl) -> {
            String decl = sl.indent() + shape.orElseThrow().decl(((TypeDeclaration<?>) top).getNameAsString());
            String old = out.getOrDefault(sl.line(), lines[sl.line() - 1]);
            out.put(sl.line(), sl.after() ? old + "\n" + decl : decl + "\n\n" + old);
        });
        String imp = slots.isEmpty() ? "" : Imports.text(cu, imports);
        if (!imp.isEmpty()) {
            int at = Imports.line(cu);
            int no = Math.max(at, 1);
            String old = out.getOrDefault(no, lines[no - 1]);
            out.put(no, at == 0 ? imp + "\n" + old : old + "\n" + imp);
        }
        out.forEach(e::replace);
    }

    /** 최상위 형식 */
    private static Node top(Node n) {
        Node t = n;
        for (Node a = n; a != null; a = a.getParentNode().orElse(null)) {
            if (a instanceof TypeDeclaration<?>) {
                t = a;
            }
        }
        return t;
    }

    private static boolean same(Node a, Node b) {
        return a.getRange().equals(b.getRange());
    }

    private static String indent(String line) {
        return line.substring(0, line.length() - line.stripLeading().length());
    }

    private static void skip(List<Cleanup.Skip> skips, int no, String why) {
        skips.add(new Cleanup.Skip(Cleanup.LOG, "줄 " + no + ": " + why));
    }
}
