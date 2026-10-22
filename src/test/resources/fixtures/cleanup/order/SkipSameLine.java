package cleanup.order;

/** 5-34b — 한 줄에 선언 둘 · enum 상수 뒤 같은 줄 선언 */
public class SkipSameLine {
    private int a; public int b;

    enum E {
        RED, GREEN; private int v;
        public int w;
    }
}
