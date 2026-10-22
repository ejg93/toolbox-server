package egov.pos.order;

/** 5-32 — 선언 순서 양성: 접근자 역순·인스턴스 뒤 static·익명 클래스 몸체·애너테이션 줄·메서드 뒤 생성자·인스턴스·enum 상수 몸체 */
public class PosOrder {
    private static final int A = 1;
    static final int B = 2;
    private int c;
    public int d;
    static int e;

    public PosOrder() {
    }

    void run() {
        Runnable r = new Runnable() {
            public void run() {
            }
            private int z;
        };
    }

    @Deprecated
    private static final int F = 3;

    PosOrder(int x) {
    }

    int g;

    enum Kind {
        X {
            void m() {
            }
            int y;
        };
        abstract void m();
    }
}
