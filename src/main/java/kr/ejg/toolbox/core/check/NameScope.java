package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 5-159b(리뷰 M9) — 값 고침·판정이 묻는 「이 이름이 여기서 무엇을 가리키나」 — 지역 가림과 보이는 필드. ValueAst 가 메서드 상한에 가까워 따로 둔다 */
final class NameScope {

    /** 같은 파일 조상을 따라 오르는 깊이 상한 */
    private static final int MAX_UP = 8;

    private NameScope() {
    }

    /** from 을 감싼 callable(없으면 초기화 블록, 없으면 필드 선언)에 같은 이름 매개변수(메서드·생성자·람다·catch)·지역 변수·패턴 변수가 있나 */
    static boolean local(Node from, String x) {
        Node m = scope(from);
        if (m == null) {
            return false;
        }
        return m.findAll(Parameter.class).stream().anyMatch(p -> p.getNameAsString().equals(x))
                || m.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals(x)
                        && v.getParentNode().filter(p -> p instanceof FieldDeclaration).isEmpty())
                || m.findAll(TypePatternExpr.class).stream().anyMatch(p -> p.getNameAsString().equals(x));
    }

    /**
     * from 에서 보이는 필드 x 의 선언 — 바깥으로 나가며 층마다: record 구성요소면 가림(빈 값) · 그 형식(익명 클래스 몸 포함)의 필드 ·
     * 같은 파일에 선언된 조상(extends·implements)의 필드. 어디에도 없으면 빈 값(파일 밖 조상의 필드는 못 본다)
     */
    static Optional<FieldDeclaration> field(Node from, String x) {
        Node p = from.getParentNode().orElse(null);
        while (p != null) {
            if (p instanceof RecordDeclaration r && r.getParameters().stream().anyMatch(c -> c.getNameAsString().equals(x))) {
                return Optional.empty();
            }
            Optional<FieldDeclaration> hit = inType(p, x, MAX_UP);
            if (hit.isPresent()) {
                return hit;
            }
            p = p.getParentNode().orElse(null);
        }
        return Optional.empty();
    }

    /** callable 조상, 없으면 초기화 블록, 없으면 필드 선언 */
    private static Node scope(Node from) {
        Optional<Node> c = from.findAncestor(CallableDeclaration.class).map(n -> (Node) n);
        if (c.isPresent()) {
            return c.get();
        }
        return from.findAncestor(InitializerDeclaration.class).map(n -> (Node) n)
                .or(() -> from.findAncestor(FieldDeclaration.class).map(n -> (Node) n)).orElse(null);
    }

    /** 형식 하나의 필드 x — 없으면 같은 파일에 선언된 조상에서(left 단까지) */
    private static Optional<FieldDeclaration> inType(Node type, String x, int left) {
        for (BodyDeclaration<?> b : members(type)) {
            if (b instanceof FieldDeclaration f && f.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(x))) {
                return Optional.of(f);
            }
        }
        if (left <= 0) {
            return Optional.empty();
        }
        List<String> ups = supers(type);
        Optional<CompilationUnit> cu = ups.isEmpty() ? Optional.empty() : type.findCompilationUnit();
        for (String s : ups) {
            Optional<FieldDeclaration> hit = cu.stream().flatMap(u -> u.findAll(ClassOrInterfaceDeclaration.class).stream())
                    .filter(d -> !d.equals(type) && d.getNameAsString().equals(s))
                    .map(d -> inType(d, x, left - 1)).flatMap(Optional::stream).findFirst();
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    /** 형식의 멤버 — 형식 선언이면 그 멤버, 익명 몸이 있는 new 면 그 몸, 아니면 빈 목록 */
    private static List<BodyDeclaration<?>> members(Node n) {
        if (n instanceof TypeDeclaration<?> t) {
            return new ArrayList<>(t.getMembers());
        }
        if (n instanceof ObjectCreationExpr o) {
            return o.getAnonymousClassBody().<List<BodyDeclaration<?>>>map(ArrayList::new).orElse(List.of());
        }
        return List.of();
    }

    /** 조상 이름(단순) — 클래스·인터페이스의 extends·implements, 익명 new 의 형식 */
    private static List<String> supers(Node n) {
        List<String> out = new ArrayList<>();
        if (n instanceof ClassOrInterfaceDeclaration d) {
            names(d.getExtendedTypes(), out);
            names(d.getImplementedTypes(), out);
        } else if (n instanceof ObjectCreationExpr o && o.getAnonymousClassBody().isPresent()) {
            out.add(o.getType().getNameAsString());
        }
        return out;
    }

    private static void names(List<ClassOrInterfaceType> types, List<String> out) {
        types.forEach(t -> out.add(t.getNameAsString()));
    }
}
