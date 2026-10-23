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

/** 5-33 — 앞질러야 할 필드를 단순 이름으로 읽는 접근자 역순 필드는 옮기면 illegal forward reference — 지적 안 함 */
class FwdRead {
    private static final String PREFIX = "Win";
    public static final boolean WIN = check(PREFIX);
    private static final String NAME = "n";
    public static final int LEN = NAME.length();
    private static final int ONE = 1;
    public static final java.util.function.IntSupplier SUP = () -> ONE;
    private static int count;
    public static final int NEXT = count += 1;
    private int base = 1;
    public int next = base + 1;
    static boolean check(String s) {
        return s.isEmpty();
    }
}

/** 5-33 — 면제돼 묶인 필드를 읽는 필드도 같이 묶인다(egov EgovProperties 꼴) */
class FwdChain {
    static final String SEP = "/";
    public static final String ROOT = "r" + SEP;
    public static final String FILE = ROOT + "f";
}
