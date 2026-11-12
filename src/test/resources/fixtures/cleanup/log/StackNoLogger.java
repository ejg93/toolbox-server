package log;

/** 설계 64 — 로거 없음: 베낄 꼴이 없으면 건너뜀, 있으면 끼우고 printStackTrace 를 error 로 */
class StackNoLogger {
    private int c;

    void f() {
        try {
            c++;
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }
}
