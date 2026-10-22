package cleanup.order;

/** 5-34b — 억제 표식으로 자리를 고정한 몸체는 다른 위반이 있어도 안 옮긴다 */
public class SkipSuppressed {
    private int a;
    public int b; // tb-ignore java.declarationOrder 직렬화 순서
    void m() {
    }
    static int c;
}
