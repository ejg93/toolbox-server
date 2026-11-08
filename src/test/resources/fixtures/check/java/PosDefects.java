package egov.pos;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/** 설계 49 — JavaDefects 양성: finally 탈출·throw · 원 예외 잃음 · ThreadLocal remove 없음 · static DateFormat */
public class PosDefects {
    static DateFormat DF = DateFormat.getDateInstance();
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    public void finallyReturn() {
        try {
            work();
        } finally {
            return;
        }
    }

    public void finallyBreak(int n) {
        for (int i = 0; i < n; i++) {
            try {
                work();
            } finally {
                break;
            }
        }
    }

    public void finallyContinue(int n) {
        int i = 0;
        while (i < n) {
            i++;
            try {
                work();
            } finally {
                continue;
            }
        }
    }

    public void finallyThrow() throws IOException {
        try {
            work();
        } finally {
            throw new IllegalStateException("x");
        }
    }

    public void finallyThrowInCatch() throws IOException {
        try {
            work();
        } finally {
            try {
                work();
            } catch (IOException e) {
                log(e);
                throw new IllegalStateException("y");
            }
        }
    }

    public void lostCause() {
        try {
            work();
        } catch (Exception e) {
            throw new IllegalStateException("fail");
        }
    }

    public void lostCauseMulti(String s) {
        try {
            Integer.parseInt(s);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new RuntimeException();
        }
    }

    public void threadLocal() {
        USER.set("u");
    }

    void work() throws IOException {
        DF.format(SDF.get2DigitYearStart());
    }

    void log(Exception e) {
        System.err.println(e.getMessage());
    }
}

/** 설계 58 — 새 규칙 넷 양성 + staticDateFormat 형식(DecimalFormat·MessageFormat) + 중첩 catch 안쪽 lostCause */
class PosDefects58 {
    static java.text.DecimalFormat NF = new java.text.DecimalFormat("#,##0");
    private static final java.text.MessageFormat MF = new java.text.MessageFormat("{0}");
    private static final org.slf4j.Logger LOGGER = null;

    void broad(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            log(t);
        }
        try {
            r.run();
        } catch (Error | RuntimeException e) {
            log(e);
        }
        try {
            r.run();
        } catch (Throwable t) {
            log(t);
            throw new IllegalStateException("m");
        }
    }

    void logged() throws IOException {
        try {
            work();
        } catch (IOException e) {
            LOGGER.error("x", e);
            throw new IllegalStateException(e);
        }
        try {
            work();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @org.springframework.web.bind.annotation.RequestMapping("/a")
    private String a() {
        return "a";
    }

    @GetMapping("/b")
    static String b() {
        return "b";
    }

    boolean floats(double d, Double x, Double y, int i, double j) {
        boolean p = d == 0.0;
        boolean q = x != y;
        boolean s = (double) i == j;
        return p && q && s;
    }

    void nested() {
        try {
            work();
        } catch (Exception e) {
            try {
                work();
            } catch (IOException io) {
                throw new IllegalStateException("m");
            }
        }
    }

    void work() throws IOException {
        NF.format(MF.hashCode());
    }

    void log(Throwable t) {
        System.err.println(t.getMessage());
    }
}

/** 5-64 ② — 래퍼 대 기본형은 floatEquals 그대로(래퍼끼리는 refEquals 몫) */
class PosDefects64 {
    boolean wrapperVsPrimitive(Double x, double j) {
        return x == j;
    }
}
