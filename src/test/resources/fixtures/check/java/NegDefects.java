package egov.neg;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

/** 설계 49 — JavaDefects 음성: finally 안 루프·람다·switch·잡히는 throw · 원 예외를 쓰는 catch · remove 있는 ThreadLocal · 캐시 */
public class NegDefects {
    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_DATE;
    private static final ThreadLocal<SimpleDateFormat> TL = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy"));
    private static final ThreadLocal<String> USER = new ThreadLocal<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    public void close(InputStream in) throws IOException {
        try {
            work();
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                log(e);
            }
        }
    }

    public int loops(int n) throws IOException {
        int sum = 0;
        try {
            work();
        } finally {
            for (int i = 0; i < n; i++) {
                if (i > 2) {
                    break;
                }
                sum += i;
            }
            int j = 0;
            while (j < n) {
                j++;
                if (j % 2 == 0) {
                    continue;
                }
                sum += j;
            }
        }
        return sum;
    }

    public Runnable lambda() throws IOException {
        Runnable r = null;
        try {
            work();
        } finally {
            r = () -> {
                return;
            };
        }
        return r;
    }

    public int choose(int k) throws IOException {
        int v = 0;
        try {
            work();
        } finally {
            switch (k) {
                case 1:
                    v = 1;
                    break;
                default:
                    v = 2;
                    break;
            }
        }
        return v;
    }

    public void caught() throws IOException {
        try {
            work();
        } finally {
            try {
                work();
                throw new IllegalStateException("z");
            } catch (IOException e) {
                log(e);
            }
        }
    }

    public void causes() {
        try {
            work();
        } catch (IOException e) {
            throw new IllegalStateException("m", e);
        }
        try {
            work();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        try {
            work();
        } catch (IOException e) {
            log(e);
            throw new IllegalStateException("m");
        }
    }

    public void rethrow() {
        try {
            work();
        } catch (RuntimeException e) {
            throw e;
        } catch (IOException e) {
            String s = e.getMessage();
            throw new IllegalStateException(s);
        }
    }

    public void enter() {
        USER.set("u");
    }

    public void leave() {
        USER.remove();
    }

    public String today() {
        return TL.get().format(new java.util.Date()) + sdf.format(new java.util.Date()) + DTF.toString() + USER.get();
    }

    void work() throws IOException {
        sdf.format(new java.util.Date());
    }

    void log(Exception e) {
        System.err.println(e.getMessage());
    }
}
