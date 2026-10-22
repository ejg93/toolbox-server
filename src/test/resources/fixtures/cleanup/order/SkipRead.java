package cleanup.order;

/** 5-34b — 다른 필드를 읽는 초기화식: 옮긴 뒤 합법이 되는 쪽도 값이 바뀐다 */
public class SkipRead {
    private static final String B = Fwd.A + "x";
    static final String A = "a";
}

class SkipRead2 {
    private static final int Y = 1;
    public static final int X = Y + 1;
}

class Fwd {
    static final String A = "f";
}
