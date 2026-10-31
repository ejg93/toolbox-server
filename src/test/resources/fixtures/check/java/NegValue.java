package egov.neg;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/** 설계 49 — ValueAst 음성: null·원시형·Object 비교 · 문자열·정수·long 의 BigDecimal · yyyy·주 번호 짝·호출 인자가 아닌 글 */
public class NegValue {
    private static final String OK = "yyyyMMdd";

    public boolean refs(String a, String b, int x, int y, Integer i, Object o, Object p) {
        boolean r1 = a == null;
        boolean r2 = null != b;
        boolean r3 = x == y;
        boolean r4 = i == 1;
        boolean r5 = o == p;
        boolean r6 = a.equals(b);
        return r1 && r2 && r3 && r4 && r5 && r6;
    }

    public boolean prims(char c, double d) {
        return c == 'x' || d == 1.0;
    }

    public BigDecimal decimals(double d, long n, BigInteger big, Integer i) {
        BigDecimal s = new BigDecimal("0.1").add(BigDecimal.valueOf(d));
        s = s.add(new BigDecimal(10)).add(new BigDecimal(n));
        return s.add(new BigDecimal(big)).add(new BigDecimal(i));
    }

    public String formats() {
        Date now = new Date();
        String label = "YYYY-MM-DD";
        log("YYYY 형식으로");
        String a = new SimpleDateFormat("yyyy-MM-dd").format(now);
        String b = DateTimeFormatter.ofPattern("YYYY-'W'ww").toString() + new SimpleDateFormat("'P'yyyy'Y'MM'M'dd'D'").toPattern();
        return a + b + new SimpleDateFormat(OK).format(now) + label;
    }

    public boolean identity(String a, String b) {
        if (a == b) {
            return true;
        }
        return a == b || a.equalsIgnoreCase(b);
    }

    void log(String m) {
        System.err.println(m);
    }
}
