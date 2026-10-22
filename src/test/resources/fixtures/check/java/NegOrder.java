package egov.neg.order;

/** 5-32 — 선언 순서 음성: 규약 순서 그대로. 중첩 타입·static 블록은 자리 무관, 생성자·메서드는 접근자 순서 안 봄 */
public class NegOrder {
    public static final int A = 1;
    protected static final int B = 2;
    static final int C = 3;
    private static final int D = 4;
    private static final java.util.Map<String, Integer> E = new java.util.HashMap<>();

    static {
        E.put("a", A);
    }

    public int f;
    protected int g;
    int h;
    private int i;

    record Row(int id) {
    }

    public NegOrder() {
    }

    private NegOrder(int x) {
    }

    public void run() {
        Object o = new Object() {
            private int z;

            public String toString() {
                return String.valueOf(z);
            }
        };
        o.toString();
    }

    private void helper() {
    }

    enum Kind {
        X, Y;

        private final int k = 1;

        int k() {
            return k;
        }
    }
}
