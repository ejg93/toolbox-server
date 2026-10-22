package cleanup.order;

/** 5-34b — 중첩 타입 뒤에서 열린 블록 주석이 다음 선언 줄에서 닫힌다 */
public class SkipStraddle {
    class In {
    } /* 이 주석은
       다음 선언 앞에서 닫힌다 */ private int b;
    public int c;
}
