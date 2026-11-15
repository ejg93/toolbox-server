package cleanup.order;

/** 설계 70 §3-3 — 인스턴스 필드 차례는 그대로, static 상수·생성자만 움직인다 */
public class PosStatic {
    private int first;
    private int second;

    void m() {
    }

    /** 빈 줄 너머 javadoc 은 선언을 따라간다 */

    public static final int C = 1;

    PosStatic() {
    }
}
