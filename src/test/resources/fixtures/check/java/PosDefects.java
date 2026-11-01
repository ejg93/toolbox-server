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
