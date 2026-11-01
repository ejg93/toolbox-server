package value;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/** 설계 50 — 값·비교 고침: 리터럴 == · != · 이름끼리(Objects import) · 양쪽 리터럴 · 여러 줄 · YYYY · BigDecimal */
public class Pos {
    private static final String FMT = "YYYY-MM-dd HH:mm";

    boolean literal(String x, String y) {
        return x == "a" || y != "b";
    }

    boolean names(Integer a, Integer b) {
        return a == b;
    }

    boolean both() {
        return "a" == "a";
    }

    boolean multi(String x) {
        return x
                == "c";
    }

    int hash(Object o) {
        return Objects.hashCode(o);
    }

    String week(Date d) {
        return new SimpleDateFormat("YYYY/MM/dd").format(d) + new SimpleDateFormat(FMT).format(d);
    }

    BigDecimal money(double d, MathContext mc) {
        BigDecimal a = new BigDecimal(0.1);
        BigDecimal b = new BigDecimal(d);
        BigDecimal c = new BigDecimal(0.1f);
        BigDecimal e = new BigDecimal(d, mc);
        return a.add(b).add(c).add(e);
    }
}
