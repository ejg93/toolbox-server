package kr.ejg.toolbox.core.check;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * D-taintScope — 한 메서드 안 같은 이름 지역 변수를 선언마다 갈라 준다. 오염 집합이 이름 글자 열쇠라 형제 블록·익명 클래스 몸의 같은 이름이
 * 서로 섞인다 — 판정 로직은 그대로 두고 이름을 잠깐 바꾼 뒤 restore 로 되돌린다(AST 는 같은 스레드의 다른 규칙이 다시 쓴다)
 */
final class LocalScopes {

    private LocalScopes() {
    }

    /** 바꾼 이름들 — restore() 가 원래 글자로 */
    static final class Renamed {
        private final List<SimpleName> names = new ArrayList<>();
        private final List<String> was = new ArrayList<>();

        private void rename(SimpleName n, String to) {
            names.add(n);
            was.add(n.getIdentifier());
            n.setIdentifier(to);
        }

        /** 바꾼 반대 차례로 처음 글자를 되돌린다 */
        void restore() {
            for (int i = names.size() - 1; i >= 0; i--) {
                names.get(i).setIdentifier(was.get(i));
            }
            names.clear();
            was.clear();
        }
    }

    /**
     * m 안에서(람다 몸 밖 — Taint.outsideLambda 와 같은 범위) 같은 이름의 선언을 갈라 「이름$tb<n>」 으로. 선언은 셋 — 지역 변수 · 안쪽 callable
     * (익명·지역 클래스의 메서드·생성자)의 매개변수 · 패턴 변수(5-131 D-03). m 자신의 매개변수에 그 이름이 있으면 그것이 첫 선언이라 목록의
     * 선언을 전부 바꾼다(하나여도). 없으면 둘 넘을 때 첫 선언(가장 바깥·가장 앞)만 그대로. 안쪽(깊은) 선언부터 — 그 선언의 범위
     * (지역·패턴 변수는 가장 가까운 블록·for·try·catch·switch, 매개변수는 그 callable) 안에서 선언 뒤에 오는 같은 이름 NameExpr 와 선언 이름을 바꾼다
     */
    static Renamed split(CallableDeclaration<?> m) {
        Renamed r = new Renamed();
        Map<String, List<Node>> byName = new LinkedHashMap<>();
        for (VariableDeclarator v : Taint.outsideLambda(m, VariableDeclarator.class)) {
            if (v.getParentNode().filter(p -> p instanceof com.github.javaparser.ast.body.FieldDeclaration).isPresent()) {
                continue; // 익명·지역 클래스의 필드는 지역 변수가 아니다 — 범위가 바깥 블록으로 새면 바깥 참조를 가져간다(마무리 리뷰 I1)
            }
            byName.computeIfAbsent(v.getNameAsString(), k -> new ArrayList<>()).add(v);
        }
        for (Parameter p : Taint.outsideLambda(m, Parameter.class)) {
            if (p.getParentNode().filter(x -> x instanceof CallableDeclaration<?> && !x.equals(m)).isPresent()) {
                byName.computeIfAbsent(p.getNameAsString(), k -> new ArrayList<>()).add(p);
            }
        }
        for (TypePatternExpr t : Taint.outsideLambda(m, TypePatternExpr.class)) {
            byName.computeIfAbsent(t.getNameAsString(), k -> new ArrayList<>()).add(t);
        }
        Set<String> params = m.getParameters().stream().map(Parameter::getNameAsString).collect(Collectors.toSet());
        byName.forEach((name, decls) -> {
            if (params.contains(name) || decls.size() > 1) {
                splitName(name, decls, params.contains(name), r);
            }
        });
        return r;
    }

    private static void splitName(String name, List<Node> decls, boolean allOf, Renamed r) {
        Comparator<Node> byDepth = Comparator.comparingInt(LocalScopes::depth);
        Comparator<Node> byBegin = Comparator.comparing(LocalScopes::begin);
        List<Node> rest = new ArrayList<>(decls);
        if (!allOf) {
            rest.remove(decls.stream().min(byDepth.thenComparing(byBegin)).orElseThrow());
        }
        rest.sort(byDepth.reversed().thenComparing(byBegin));
        int n = 2;
        for (Node d : rest) {
            Optional<Node> scope = d instanceof Parameter ? d.getParentNode() : scope(d);
            if (scope.isEmpty()) {
                continue;
            }
            String to = name + "$tb" + n;
            n++;
            Position at = begin(d);
            for (NameExpr ref : scope.get().findAll(NameExpr.class)) {
                if (ref.getNameAsString().equals(name) && begin(ref).compareTo(at) > 0) {
                    r.rename(ref.getName(), to);
                }
            }
            r.rename(((NodeWithSimpleName<?>) d).getName(), to);
        }
    }

    /** 선언의 범위 노드 — 가장 가까운 BlockStmt·ForStmt·ForEachStmt·TryStmt·CatchClause·switch(옛 꼴 갈래는 switch 전체 — 5-131 D-04) */
    static Optional<Node> scope(Node d) {
        Optional<Node> p = d.getParentNode();
        while (p.isPresent()) {
            Node x = p.get();
            if (x instanceof SwitchEntry) {
                return x.getParentNode();
            }
            if (x instanceof BlockStmt || x instanceof ForStmt || x instanceof ForEachStmt || x instanceof TryStmt || x instanceof CatchClause
                    || x instanceof SwitchStmt || x instanceof SwitchExpr) {
                return p;
            }
            p = x.getParentNode();
        }
        return Optional.empty();
    }

    private static int depth(Node n) {
        int k = 0;
        for (Optional<Node> p = n.getParentNode(); p.isPresent(); p = p.get().getParentNode()) {
            k++;
        }
        return k;
    }

    private static Position begin(Node n) {
        return n.getBegin().orElse(Position.HOME);
    }
}
