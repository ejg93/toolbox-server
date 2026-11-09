package log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 설계 60 — 인스턴스 로거: 인스턴스 메서드는 바꿈 · static 메서드는 건너뜀 */
class Instance {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    void a() {
        System.out.println("a");
    }

    static void b() {
        System.out.println("b");
    }
}
