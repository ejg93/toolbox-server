package log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 설계 64 — printStackTrace 를 로거 error 로: catch 매개변수 둘(e·ex) · 건너뜀(catch 밖 · 람다 안 · main) */
class Stack {
    private static final Logger logger = LoggerFactory.getLogger(Stack.class);

    void a() {
        try {
            run();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    void b() {
        try {
            run();
        } catch (IllegalStateException ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    void c(Throwable t) {
        t.printStackTrace();
    }

    void d() {
        try {
            run();
        } catch (RuntimeException e) {
            Runnable r = () -> e.printStackTrace();
            r.run();
        }
    }

    public static void main(String[] args) {
        try {
            new Stack().run();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    void run() {
    }

    void e() {
        try {
            run();
        } catch (RuntimeException e) {
            e.printStackTrace(System.out);
        }
    }

    void f(java.io.PrintWriter pw) {
        try {
            run();
        } catch (RuntimeException e) {
            e.printStackTrace(pw);
        }
    }
}
