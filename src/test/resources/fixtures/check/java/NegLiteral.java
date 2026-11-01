package egov.neg;

import java.util.Objects;

/** 설계 50 — equalsLiteralFirst 음성: 리터럴 앞 · 상수 인자 · scope 없음 · super · Objects.equals */
public class NegLiteral {
    private static final String CONST = "a";

    boolean a(String x) {
        return "a".equals(x);
    }

    boolean b(String x) {
        return x.equals(CONST);
    }

    boolean c() {
        return equals("a");
    }

    boolean d() {
        return super.equals("a");
    }

    boolean e(String x) {
        return Objects.equals(x, "a");
    }
}
