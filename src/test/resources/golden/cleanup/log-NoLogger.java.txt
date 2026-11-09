package log;

/** 설계 60 — 로거 없음: 베낄 꼴이 없으면 건너뜀, 있으면 마지막 static 필드(B) 뒤에 끼운다 */
class NoLogger {
    public static final String A = "a";

    static {
        System.identityHashCode(A);
    }

    protected static int B = 1;
    private int c;

    void f() {
        System.out.println(A + c);
    }
}
