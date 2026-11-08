package egov.pos;

/** 설계 50 — equalsLiteralFirst 양성: 리터럴 뒤 equals · equalsIgnoreCase · 괄호 scope */
public class PosLiteral {
    boolean a(String x) {
        return x.equals("a");
    }

    boolean b(String x) {
        return x.equalsIgnoreCase("A");
    }

    boolean c(String x) {
        return (x).equals("a");
    }
}

/** 설계 58 — 같은 파일 static final String 리터럴 상수를 뒤에 둔 equals */
class PosLiteral58 {
    private static final String CONST = "a";

    boolean a(String x) {
        return x.equals(CONST);
    }
}
