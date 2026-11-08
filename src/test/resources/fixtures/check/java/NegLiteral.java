package egov.neg;

import java.util.Objects;

/** 설계 50 — equalsLiteralFirst 음성: 리터럴 앞 · 상수 인자 · scope 없음 · super · Objects.equals */
public class NegLiteral {
    private static final String CONST = "a";

    boolean a(String x) {
        return "a".equals(x);
    }

    boolean b(String x) {
        return CONST.equals(x);
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

/** 설계 58 — 문자열 상수가 아닌 static final · 지역 변수 인자 */
class NegLiteral58 {
    private static final Object NOT_STRING = new Object();

    boolean a(String x) {
        String local = "a";
        return x.equals(NOT_STRING) || x.equals(local);
    }
}
