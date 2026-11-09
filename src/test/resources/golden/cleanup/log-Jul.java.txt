package log;

import java.util.logging.Logger;

/** 설계 60 — java.util.logging 로거는 debug·error 가 없다 — 건너뜀 */
class Jul {
    private static final Logger LOG = Logger.getLogger("j");

    void f() {
        System.out.println("x");
    }
}
