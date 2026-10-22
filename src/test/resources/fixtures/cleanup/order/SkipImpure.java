package cleanup.order;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 5-34b — 초기화 부수 효과: 한정 없는 호출 · 이 파일 밖 호출 둘 · 인터페이스 필드(암묵 static) */
public class SkipImpure {
    private static int a = compute();
    public static int b = 5;

    static int compute() {
        return b * 2;
    }
}

class SkipImpure2 {
    private static final List<String> L = Collections.emptyList();
    public static final Map<String, String> M = new HashMap<>();
}

interface SkipImpure3 {
    int X = Util.next();
    public int Y = Util.next();
}
