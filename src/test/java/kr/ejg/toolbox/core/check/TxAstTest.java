package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 설계 49 — 트랜잭션 셋. 픽스처 줄을 직접 단언한다 */
class TxAstTest {

    static final Path DIR = Path.of("src/test/resources/fixtures/check/java");
    static final Set<String> ON = Set.of(TxAst.TX_ROLLBACK, TxAst.TX_SELF, TxAst.TX_PRIVATE);
    static final String ROLL = " txRollbackFor";
    static final String SELF = " txSelfCall";
    static final String PRIV = " txPrivate";

    static List<String> hits(String code, Set<String> on) {
        CompilationUnit cu = new JavaSource().parse(code).getResult().orElseThrow();
        return TxAst.apply(cu, on).stream()
                .map(h -> h.node().getBegin().map(p -> p.line).orElse(0) + " " + h.rule().replace("java.", "")).toList();
    }

    static List<String> file(String name) throws Exception {
        return hits(Files.readString(DIR.resolve(name), StandardCharsets.UTF_8), ON);
    }

    @Test
    void positiveFixture() throws Exception {
        assertEquals(List.of("8" + ROLL, "13" + ROLL, "18" + ROLL, "24" + SELF, "25" + SELF, "30" + SELF,
                "33" + PRIV, "42" + PRIV, "48" + ROLL), file("PosTx.java"));
    }

    @Test
    void negativeFixture() throws Exception {
        assertEquals(List.of(), file("NegTx.java"));
    }

    @Test
    void classHeadTxSilencesSelfCall() {
        assertEquals(List.of(), hits("@Transactional(rollbackFor = Exception.class) class A {\n"
                + " void f() { g(); }\n"
                + " @Transactional(rollbackFor = Exception.class, timeout = 3) void g() { } }", ON));
    }

    @Test
    void callerWithOtherAttributesIsReported() {
        assertEquals(List.of("2" + SELF), hits("class A { @Transactional(rollbackFor = Exception.class)\n"
                + " void f() { g(); }\n"
                + " @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW) void g() { } }", ON));
    }

    @Test
    void privateAndMissingRollbackShareLine() {
        assertEquals(List.of("1" + PRIV, "1" + ROLL), hits("class A { @Transactional private void f() { } }", ON));
    }

    @Test
    void offRulesAreSilent() {
        assertEquals(List.of(), hits("class A { @Transactional private void f() { } }", Set.of(TxAst.TX_SELF)));
    }
}
