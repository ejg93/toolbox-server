package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 설계 60 — 단일 import 끼우기 공용(ValueFix·LogFix). 줄은 Edits 에 한 번만 쓴다 */
final class Imports {

    private Imports() {
    }

    /** 단일 import 로 fqn 이 있고 같은 단순 이름의 다른 단일 import 가 없다(java.util.* 는 같은 패키지 이름이 가린다 — claude[bot] ③) */
    static boolean has(CompilationUnit cu, String fqn) {
        return singles(cu, simple(fqn)).anyMatch(fqn::equals) && !clash(cu, fqn);
    }

    /** 5-62c — 같은 단순 이름의 다른 단일 import 가 있다 */
    static boolean clash(CompilationUnit cu, String fqn) {
        return singles(cu, simple(fqn)).anyMatch(n -> !fqn.equals(n));
    }

    /** 마무리 리뷰 — import 없이 그 단순 이름을 식의 이름(NameExpr)이나 형식으로 쓴다(같은 패키지·중첩 클래스) — 끼운 import 가 가린다 */
    static boolean used(CompilationUnit cu, String simple) {
        return cu.findAll(NameExpr.class).stream().anyMatch(n -> simple.equals(n.getNameAsString()))
                || cu.findAll(ClassOrInterfaceType.class).stream().anyMatch(t -> t.getScope().isEmpty() && simple.equals(t.getNameAsString()));
    }

    /** 5-62c — 끼울 줄: 마지막 import 의 끝 줄, 없으면 package 줄, 그것도 없으면 0(맨 앞) */
    static int line(CompilationUnit cu) {
        Optional<Node> last = cu.getImports().isEmpty() ? cu.getPackageDeclaration().map(Node.class::cast)
                : Optional.of(cu.getImports().get(cu.getImports().size() - 1));
        return last.flatMap(Node::getEnd).map(p -> p.line).orElse(0);
    }

    /** need 가운데 has 가 아닌 것을 "import a.B;" 줄들로(\n 이음, 주어진 차례). 없으면 빈 글 */
    static String text(CompilationUnit cu, Collection<String> need) {
        return need.stream().filter(f -> !has(cu, f)).map(f -> "import " + f + ";").collect(Collectors.joining("\n"));
    }

    /** fqn 의 마지막 이름 */
    static String simple(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    private static Stream<String> singles(CompilationUnit cu, String simple) {
        return cu.getImports().stream().filter(i -> !i.isStatic() && !i.isAsterisk() && simple.equals(i.getName().getIdentifier()))
                .map(ImportDeclaration::getNameAsString);
    }
}
