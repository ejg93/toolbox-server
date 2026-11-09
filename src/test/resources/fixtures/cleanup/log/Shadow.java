package log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 설계 60 — 로거 이름과 같은 매개변수가 있으면 건너뜀 */
class Shadow {
    private static final Logger log = LoggerFactory.getLogger(Shadow.class);

    void save(String log) {
        System.out.println(log);
    }
}
