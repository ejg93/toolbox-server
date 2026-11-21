package kr.ejg.toolbox.core.check;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.TryStmt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * m 안에서 같은 이름으로 둘 넘게 선언된 지역 변수(람다 몸 밖 — Taint.outsideLambda 와 같은 범위)를 둘째 선언부터 「이름$tb<n>」 으로.
     * 안쪽(깊은) 선언부터 — 그 선언의 범위(가장 가까운 블록·for·try·switch 갈래·catch) 안에서 선언 뒤에 오는 같은 이름 NameExpr 와 선언 이름을 바꾼다.
     * 첫 선언(가장 바깥·가장 앞)과 매개변수는 그대로. 없으면 빈 Renamed
     */
    static Renamed split(CallableDeclaration<?> m) {
        Renamed r = new Renamed();
        Map<String, List<VariableDeclarator>> byName = new LinkedHashMap<>();
        for (VariableDeclarator v : Taint.outsideLambda(m, VariableDeclarator.class)) {
            byName.computeIfAbsent(v.getNameAsString(), k -> new ArrayList<>()).add(v);
        }
        for (Map.Entry<String, List<VariableDeclarator>> e : byName.entrySet()) {
            if (e.getValue().size() > 1) {
                splitName(e.getKey(), e.getValue(), r);
            }
        }
        return r;
    }

    private static void splitName(String name, List<VariableDeclarator> decls, Renamed r) {
        Comparator<VariableDeclarator> byDepth = Comparator.comparingInt(v -> depth(v));
        Comparator<VariableDeclarator> byBegin = Comparator.comparing(v -> begin(v));
        VariableDeclarator first = decls.stream().min(byDepth.thenComparing(byBegin)).orElseThrow();
        List<VariableDeclarator> rest = new ArrayList<>(decls);
        rest.remove(first);
        rest.sort(byDepth.reversed().thenComparing(byBegin));
        int n = 2;
        for (VariableDeclarator d : rest) {
            Optional<Node> scope = scope(d);
            if (scope.isEmpty()) {
                continue;
            }
            String to = name + "$tb" + n++;
            Position at = begin(d);
            for (NameExpr ref : scope.get().findAll(NameExpr.class)) {
                if (ref.getNameAsString().equals(name) && begin(ref).compareTo(at) > 0) {
                    r.rename(ref.getName(), to);
                }
            }
            r.rename(d.getName(), to);
        }
    }

    /** 선언의 범위 노드 — 가장 가까운 BlockStmt·ForStmt·ForEachStmt·TryStmt·SwitchEntry·CatchClause */
    private static Optional<Node> scope(VariableDeclarator d) {
        Optional<Node> p = d.getParentNode();
        while (p.isPresent()) {
            Node x = p.get();
            if (x instanceof BlockStmt || x instanceof ForStmt || x instanceof ForEachStmt || x instanceof TryStmt || x instanceof SwitchEntry
                    || x instanceof CatchClause) {
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
