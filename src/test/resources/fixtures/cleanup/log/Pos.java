package log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 설계 60 — 로거 고침: 있는 static 로거로 · 건너뜀(println() · printf · 두 줄 · catch · static 초기화 · main) */
class Pos {
    private static final Logger logger = LoggerFactory.getLogger(Pos.class);

    static {
        System.out.println("init");
    }

    void a(int n) {
        System.out.println("a");
        System.err.println(n);
        System.out.print("x" + n);
        System.out.println();
        System.out.printf("%d%n", n);
    }

    void b(int n) {
        System.out.println(
            "two");
        try {
            a(n);
        } catch (RuntimeException e) {
            System.err.println("fail");
        }
    }

    public static void main(String[] args) {
        System.out.println("main");
    }
}
