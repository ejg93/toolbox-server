package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 설계 60 — java.unusedLocal 판정. 지적 노드를 이름으로 단언한다 */
class LocalsAstTest {

    static final Path DIR = Path.of("src/test/resources/fixtures/check/java");
    static final Set<String> ON = Set.of(LocalsAst.UNUSED_LOCAL);

    static List<String> names(String code) {
        CompilationUnit cu = new JavaSource().parse(code).getResult().orElseThrow();
        return LocalsAst.apply(cu, ON).stream().map(h -> ((VariableDeclarator) h.node()).getNameAsString()).toList();
    }

    static List<String> file(String name) throws Exception {
        return names(Files.readString(DIR.resolve(name), StandardCharsets.UTF_8));
    }

    @Test
    void positiveFixture() throws Exception {
        assertEquals(List.of("count", "label", "n", "b", "z"), file("PosLocals.java"));
    }

    @Test
    void negativeFixture() throws Exception {
        assertEquals(List.of(), file("NegLocals.java"));
    }

    /** 옛 꼴 case 묶음 — 앞 case 에서 선언한 이름은 뒤 case 에서 보인다 */
    @Test
    void switchScopeIsWholeSwitch() {
        assertEquals(List.of(), names("class A { int f(int k) { int r = 0; switch (k) { case 1: int z = 3; break; case 2: z = 5; r = z; break; default: break; } return r; } }"));
    }

    /** 규칙이 꺼져 있으면 0 */
    @Test
    void offIsEmpty() {
        CompilationUnit cu = new JavaSource().parse("class A { void f() { int x = 0; } }").getResult().orElseThrow();
        assertEquals(List.of(), LocalsAst.apply(cu, Set.of()));
    }

    /** 지워도 되는 초기화식 — 리터럴·null·this.x 와 + - *·괄호·삼항·단항. 다른 이름을 읽거나 부작용·예외가 있을 수 있으면 아니다 */
    @Test
    void pureTable() {
        for (String e : List.of("0", "\"x\"", "null", "this.f", "-1", "1 + 2", "(1)", "true ? 1 : 2", "!true", "~1", "'c'")) {
            assertTrue(LocalsAst.pure(StaticJavaParser.parseExpression(e)), e);
        }
        for (String e : List.of("a", "a + 1", "f()", "new A()", "xs[0]", "1 / 2", "1 % 2", "(T) o", "o.f", "x++", "x = 1")) {
            assertFalse(LocalsAst.pure(StaticJavaParser.parseExpression(e)), e);
        }
    }
}
