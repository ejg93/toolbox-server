package kr.ejg.toolbox.core.report;

import org.apache.poi.ss.usermodel.Sheet;

/**
 * xlsx 열 너비(1-33) — 열마다 머리·값 중 가장 긴 글 폭 + 2, 6~60 칸. 60 을 넘는 열은 값 셀을 줄바꿈한다.
 * 글 폭은 한글 등 넓은 글자 2·그 밖 1, 여러 줄이면 가장 긴 줄(TableXlsx 에서 옮김). 엑셀 글꼴 폭과는 근사다.
 */
public final class ColumnWidths {

    public static final int MIN = 6;
    public static final int MAX = 60;
    public static final int PAD = 2;

    private final int[] raw;

    public ColumnWidths(int cols) {
        raw = new int[cols];
    }

    /** 이 열의 글 하나를 본다. null·빈 글은 무시 */
    public void see(int col, String text) {
        if (text == null || text.isEmpty() || col < 0 || col >= raw.length) {
            return;
        }
        raw[col] = Math.max(raw[col], width(text));
    }

    /** 열 너비(칸) */
    public int chars(int col) {
        return Math.min(MAX, Math.max(MIN, raw[col] + PAD));
    }

    /** 가장 긴 글이 상한을 넘어 줄바꿈이 필요한가 */
    public boolean wraps(int col) {
        return raw[col] + PAD > MAX;
    }

    /** 계산한 너비로 */
    public void apply(Sheet s, int col) {
        s.setColumnWidth(col, chars(col) * 256);
    }

    /** 넓히기만 — 양식이 정한 너비보다 좁히지 않는다(발주처 양식 인쇄 배치) */
    public void applyFrom(Sheet s, int col) {
        s.setColumnWidth(col, Math.max(s.getColumnWidth(col), chars(col) * 256));
    }

    /** 가장 긴 줄의 칸 수 — 한글 등 넓은 글자는 2 */
    static int width(String text) {
        int best = 0;
        for (String line : text.split("\n", -1)) {
            int w = 0;
            for (int i = 0; i < line.length(); i++) {
                w += line.charAt(i) > 0x2E80 ? 2 : 1;
            }
            best = Math.max(best, w);
        }
        return best;
    }
}
