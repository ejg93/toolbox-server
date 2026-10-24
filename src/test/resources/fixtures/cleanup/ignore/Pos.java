package cleanup.ignore;

/** 5-34a — 쓸모없는 표식 */
public class Pos {
    // tb-ignore common.sysout 지난번 println 자리
    int x = 1; // tb-ignore common.todo
    String s = "http://a"; // tb-ignore common.todo
    /* tb-ignore common.todo */ int y;
    // tb-ignore java.dupMapping
    // tb-ignore * 전부
    // tb-ignore java.emptyCatch
    /**
     * 설명 tb-ignore common.sysout
     * tb-ignore common.sysout 이유 — 5-44b: javadoc 연속 줄은 * 바로 뒤일 때만 표식
     */
    int z;
}
