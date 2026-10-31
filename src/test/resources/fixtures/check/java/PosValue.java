package egov.pos;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import org.apache.commons.lang3.time.DateFormatUtils;

/** 설계 49 — ValueAst 양성: String·래퍼 == · new BigDecimal(double) · 날짜 형식 YYYY(상수 한 홉은 필드 줄) */
public class PosValue {
    private static final String FMT = "YYYYMMdd";

    public boolean refEquals(String a, String b, Integer i, Integer j, Long k, Long l) {
        boolean r1 = a == b;
        boolean r2 = i != j;
        boolean r3 = a == "x";
        boolean r4 = "y" != b;
        boolean r5 = k == l;
        Boolean f = Boolean.TRUE;
        Boolean g = Boolean.FALSE;
        boolean r6 = f == g;
        return r1 && r2 && r3 && r4 && r5 && r6;
    }

    public BigDecimal bigDecimal(double d, Double e, float f) {
        BigDecimal s = new BigDecimal(0.1);
        s = s.add(new BigDecimal(d));
        s = s.add(new BigDecimal(e));
        s = s.add(new BigDecimal(f));
        s = s.add(new BigDecimal((double) 3));
        s = s.add(new BigDecimal(Double.parseDouble("1.5")));
        return s.add(new BigDecimal(e.doubleValue()));
    }

    public String weekYear() {
        Date now = new Date();
        String a = new SimpleDateFormat("YYYY-MM-dd").format(now);
        String b = DateTimeFormatter.ofPattern("YYYYMMdd HH:mm").toString();
        String c = new SimpleDateFormat(FMT).format(now);
        String d = DateFormatUtils.format(now, "YYYY/MM/dd");
        return a + b + c + d;
    }
}
