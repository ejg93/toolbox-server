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

/** 설계 58 — 새 규칙 넷 음성: 다시 던지는 Throwable · 로그만 · 던지기만 · public 매핑 · 정수 == · NaN 검사 · null 비교 · 중첩 catch 는 안쪽이 판정 · ThreadLocal 을 넘겨 지움 · 메서드 참조로 쓴 예외 */
class NegDefects58 {
    private static final org.slf4j.Logger LOG = null;
    private static final ThreadLocal<String> CTX = new ThreadLocal<>();
    private final java.text.DecimalFormat nf = new java.text.DecimalFormat("0");

    void rethrow(Runnable r) throws Throwable {
        try {
            r.run();
        } catch (Throwable t) {
            throw t;
        }
    }

    void helper(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    static RuntimeException rethrow(Throwable t) {
        return new RuntimeException(t);
    }

    void logOnly() {
        try {
            work();
        } catch (IOException e) {
            LOG.error("x", e);
        }
    }

    void throwOnly() throws IOException {
        try {
            work();
        } catch (IOException e) {
            throw new IOException("m", e);
        }
    }

    @GetMapping("/c")
    public String c() {
        return "c";
    }

    boolean compares(int a, int b, double d, Double x) {
        return a == b || d != d || x == null;
    }

    void nested() {
        try {
            work();
        } catch (Exception e) {
            try {
                work();
            } catch (IOException io) {
                throw new IllegalStateException("m", io);
            }
        }
    }

    void enter() {
        CTX.set("c");
    }

    void leave() {
        clear(CTX);
    }

    static void clear(ThreadLocal<?> tl) {
        tl.remove();
    }

    void ref(java.util.List<Throwable> list) {
        try {
            work();
        } catch (IOException e) {
            list.forEach(e::addSuppressed);
            throw new IllegalStateException("m");
        }
    }

    void work() throws IOException {
        nf.format(1);
    }
}

/** 5-64 ① — 감싸서 던지기·형 변환해 던지기도 다시 던지기 */
class NegDefects64 {
    void wrap(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    void cast(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new RuntimeException(t);
        }
    }
}
