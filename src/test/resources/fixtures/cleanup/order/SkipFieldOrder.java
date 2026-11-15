package cleanup.order;

/** 설계 70 §3-3 — 인스턴스 필드끼리 차례가 바뀌어야 풀리는 몸체(리플렉션·직렬화 열 순서) */
class SkipFieldOrder {
    private int a;
    public int b;

    void m() {
    }

    SkipFieldOrder() {
    }
}
