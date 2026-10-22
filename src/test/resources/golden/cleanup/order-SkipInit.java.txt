package cleanup.order;

/** 5-34b — 초기화 블록이 있는 몸체는 안 옮긴다 */
public class SkipInit {
    static final java.util.List<String> L = new java.util.ArrayList<>();
    static {
        L.add("a");
    }
    private int a;
    public int b;
}
