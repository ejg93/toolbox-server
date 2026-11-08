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

    public boolean prims(char c, long d) {
        return c == 'x' || d == 1L;
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

    public boolean arrays(String[] x, String[] y, Integer... v) {
        Integer[] w = v;
        return x == y && w == v;
    }

    void log(String m) {
        System.err.println(m);
    }
}

/** 설계 58 — 상수 반환 빠른 길 · 세 항 OR 빠른 길 · 표지 객체 · String.format 은 날짜 자리 아님 */
class NegValue58 {
    private static final Boolean SAME = Boolean.TRUE;
    private static final String MARK = new String("");

    Boolean constantReturn(Integer a, Integer b) {
        if (a == b) {
            return SAME;
        }
        return a.equals(b);
    }

    Boolean wrapperConstant(String s, String t) {
        if (s == t) {
            return Boolean.TRUE;
        }
        return s.equals(t);
    }

    boolean threeTerms(Integer a, Integer b, boolean x) {
        return a == b || x || a.equals(b);
    }

    boolean sentinel(String s) {
        return s == MARK || s != MARK;
    }

    String format(Object v) {
        return String.format("YYYY-MM", v);
    }
}
