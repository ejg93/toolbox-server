package egov.neg.order;

/** 5-32b — 선언 순서 음성 가장자리: 애너테이션 멤버는 메서드가 아니다 · 인터페이스 상수 뒤 메서드 · record 는 static 뒤 compact 생성자 · enum 생성자 뒤 메서드 · 중첩 타입 어디든 */
@interface Ann {
    int x() default 1;
    String K = "k";
}

interface Api {
    String X = "x";
    int Y = 1;
    void m();
    default void n() {
    }
}

record Pair(int a, int b) {
    static final int K = 1;
    public Pair {
    }
    int sum() {
        return a + b;
    }
}

enum Color {
    RED, BLUE;
    private static final int K = 1;
    private final int v = 2;
    Color() {
    }
    int v() {
        return v + K;
    }
}

class Outer {
    static class First {
    }
    static final int A = 1;
    private int b;
    Outer() {
    }
    class Inner {
        private int c;
        int c() {
            return c;
        }
    }
    void m() {
    }
}
