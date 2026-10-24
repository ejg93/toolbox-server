package egov.neg.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** 5-45 음성 — 단언·verify 류·check·람다 안 단언·throw·도우미 아님(시험 아님)·TestFactory */
class NegTestNoAssert {

    static void helper() {
        Integer.toString(1);
    }

    @Test
    void direct() {
        assertEquals(2, 1 + 1);
    }

    @Test
    void throwsExpected() {
        assertThrows(IllegalStateException.class, () -> {
            throw new IllegalStateException("x");
        });
    }

    @Test
    void archRule() {
        Rule rule = new Rule();
        rule.check(List.of());
    }

    @Test
    void insideLambda() {
        List.of(1).forEach(n -> assertEquals(1, n));
    }

    @Test
    void manualFail() {
        if (Integer.parseInt("1") != 1) {
            throw new AssertionError("1");
        }
    }

    @TestFactory
    List<DynamicTest> dynamic() {
        return List.of();
    }

    static final class Rule {
        void check(List<Object> classes) {
            classes.size();
        }
    }
}
