package kr.ejg.toolbox.core.text;

import java.util.ArrayList;
import java.util.List;

/**
 * 줄 비교(4-2) — LCS. 같은 머리·꼬리를 먼저 떼고 가운데만 표로 잰다. 같은 길이면 지운 줄(-)을 넣은 줄(+)보다 앞에 둔다.
 * CRLF 와 LF 는 같은 줄로 본다. 한쪽이 {@link #MAX_LINES} 를 넘거나 가운데 표가 {@link #MAX_CELLS} 를 넘으면 {@code tooBig} 만 준다.
 */
public final class LineDiff {

    public static final int MAX_LINES = 20_000;
    /** 가운데 LCS 표 칸 수 상한 — int 4M 칸 = 16MB */
    public static final long MAX_CELLS = 4_000_000L;

    public record Op(String op, String line) {
    }

    public record Result(List<Op> ops, boolean tooBig) {
        public Result {
            ops = List.copyOf(ops);
        }
    }

    private LineDiff() {
    }

    static List<String> lines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String t = text.replace("\r\n", "\n");
        if (t.endsWith("\n")) {
            t = t.substring(0, t.length() - 1);
        }
        return List.of(t.split("\n", -1));
    }

    public static Result diff(String aText, String bText) {
        List<String> a = lines(aText);
        List<String> b = lines(bText);
        if (a.size() > MAX_LINES || b.size() > MAX_LINES) {
            return new Result(List.of(), true);
        }
        int head = 0;
        while (head < a.size() && head < b.size() && a.get(head).equals(b.get(head))) {
            head++;
        }
        int tail = 0;
        while (tail < a.size() - head && tail < b.size() - head
                && a.get(a.size() - 1 - tail).equals(b.get(b.size() - 1 - tail))) {
            tail++;
        }
        int n = a.size() - head - tail;
        int m = b.size() - head - tail;
        if ((long) (n + 1) * (m + 1) > MAX_CELLS) {
            return new Result(List.of(), true);
        }
        List<Op> ops = new ArrayList<>();
        for (int i = 0; i < head; i++) {
            ops.add(new Op("=", a.get(i)));
        }
        // dp[i][j] = a[head+i..], b[head+j..] 가운데 부분의 LCS 길이
        int w = m + 1;
        int[] dp = new int[(n + 1) * w];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i * w + j] = a.get(head + i).equals(b.get(head + j))
                        ? dp[(i + 1) * w + j + 1] + 1
                        : Math.max(dp[(i + 1) * w + j], dp[i * w + j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a.get(head + i).equals(b.get(head + j))) {
                ops.add(new Op("=", a.get(head + i)));
                i++;
                j++;
            } else if (j >= m || (i < n && dp[(i + 1) * w + j] >= dp[i * w + j + 1])) {
                ops.add(new Op("-", a.get(head + i)));
                i++;
            } else {
                ops.add(new Op("+", b.get(head + j)));
                j++;
            }
        }
        for (int k = a.size() - tail; k < a.size(); k++) {
            ops.add(new Op("=", a.get(k)));
        }
        return new Result(ops, false);
    }
}
