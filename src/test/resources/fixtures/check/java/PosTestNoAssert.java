package egov.pos.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 5-45 양성 — 돌기만 하고 단언이 없는 시험 둘 */
class PosTestNoAssert {

    @Test
    void runsOnly() {
        new StringBuilder("x").append(1).toString();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void loopsOnly(int n) {
        Runnable r = () -> Integer.toString(n);
        r.run();
    }
}
