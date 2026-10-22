package cleanup.order;

/** 5-34b — 고쳐지는 몸체: 접근자 역순·메서드 뒤 상수(javadoc·애너테이션)·메서드 뒤 생성자·enum 상수 몸체·익명 몸체 */
public class Pos {
    // ---- 필드
    private int a;
    public int b; // 꼬리 주석은 제 선언과

    void run() {
        Runnable r = new Runnable() {
            public void run() {
            }
            private int z;
        };
        r.run();
    }

    /** 상수 설명 */
    @Deprecated
    public static final int C = 1;

    Pos() {
    }

    enum Kind {
        X {
            void m() {
            }
            int y;
        };
        abstract void m();
    }
}
