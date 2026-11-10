package kr.ejg.toolbox.core.check;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * a11y.thScope 고침(설계 62) — JSP·HTML 표의 {@code <th>} 에 scope. thead 안 th 와 첫 행만 th 인 행의 th 는 col, th 로 시작해 td 가 있는 행(상세 표)의 th 는 row.
 * 그 밖(th 만인 행이 첫 행이 아님 · td 뒤에 th · tr 없는 표)은 표째 건너뛴다. 중첩 표는 안쪽이 자기 표로 판정. 주석·script·스크립틀릿·EL 안은 안 본다(BlankRelRule.dead)
 */
final class ThScopeFix {
    static final String TH_SCOPE = "a11y.thScope";
    static final String COL = "col";
    static final String ROW = "row";
    static final String NOT_FIRST = "th 만 있는 행이 첫 행이 아니다 — 표 구조를 못 가름";
    static final String MID_ROW = "td 뒤에 th — 표 구조를 못 가름";
    static final String NO_ROW = "tr 없는 표";
    static final String OUTSIDE = "table 밖 th(조각)";
    private static final Pattern TOKEN = Pattern.compile("(?i)<(/?)(table|thead|tr|th|td)(?=[\\s/>])");
    private static final Pattern SCOPED = Pattern.compile("(?i)(?<![\\w:-])(?:scope|headers)\\s*=");
    private static final String TABLE = "table";
    private static final String THEAD = "thead";
    private static final String TR = "tr";
    private static final String TH = "th";
    private static final String LINE = "줄 ";

    private ThScopeFix() {
    }

    /** 끼울 자리 — at 은 {@code <th} 바로 뒤 글 자리 */
    record Th(int at, String scope) {
    }

    /** 결과 — 끼울 자리(글 차례) + 건너뜀 사유(「줄 N: 사유」) */
    record Scan(List<Th> ths, List<String> skips) {
    }

    private record Cell(int at, boolean th, boolean scoped) {
    }

    private static final class Row {
        final boolean inThead;
        final List<Cell> cells = new ArrayList<>();

        Row(boolean inThead) {
            this.inThead = inThead;
        }
    }

    private static final class Table {
        final int line;
        final List<Row> rows = new ArrayList<>();
        boolean inThead;
        boolean cellOutsideRow;

        Table(int line) {
            this.line = line;
        }
    }

    static Scan scan(String text, String lang) {
        String code = BlankRelRule.dead(text, lang);
        List<Table> tables = new ArrayList<>();
        Deque<Table> open = new ArrayDeque<>();
        Matcher m = TOKEN.matcher(code);
        int outside = -1; // table 밖 첫 th 의 줄(사유는 표 사유 뒤에 한 번)
        while (m.find()) {
            outside = onTag(code, m, open, tables, outside);
        }
        while (!open.isEmpty()) {
            tables.add(open.pop()); // 안 닫힌 표(JSP 조각)도 판정
        }
        List<Th> out = new ArrayList<>();
        List<String> skips = new ArrayList<>();
        for (Table t : tables) {
            classify(t, out, skips); // 표는 닫힌 차례(안쪽 표가 바깥보다 먼저)
        }
        if (outside > 0) {
            skips.add(LINE + outside + ": " + OUTSIDE);
        }
        out.sort(Comparator.comparingInt(Th::at));
        return new Scan(out, skips);
    }

    /** 태그 하나 — 표를 열고 닫고, 행·칸을 지금 표에 단다. table 밖 th 의 첫 줄을 돌려준다 */
    private static int onTag(String code, Matcher m, Deque<Table> open, List<Table> tables, int outside) {
        boolean close = !m.group(1).isEmpty();
        String tag = m.group(2).toLowerCase(Locale.ROOT);
        Table cur = open.peek();
        if (TABLE.equals(tag)) {
            if (!close) {
                open.push(new Table(lineOf(code, m.start())));
            } else if (cur != null) {
                tables.add(open.pop());
            }
        } else if (cur == null) {
            return TH.equals(tag) && !close && outside < 0 ? lineOf(code, m.start()) : outside;
        } else if (THEAD.equals(tag)) {
            cur.inThead = !close;
        } else if (TR.equals(tag)) {
            if (!close) {
                cur.rows.add(new Row(cur.inThead));
            }
        } else if (!close) { // th·td
            if (cur.rows.isEmpty()) {
                cur.cellOutsideRow = true;
            } else {
                cur.rows.get(cur.rows.size() - 1).cells.add(cell(code, m, TH.equals(tag)));
            }
        }
        return outside;
    }

    private static Cell cell(String code, Matcher m, boolean th) {
        String attrs = th ? BlankRelRule.attrs(code, m.end()) : null;
        return new Cell(m.end(), th, attrs != null && SCOPED.matcher(attrs).find());
    }

    /** 표 하나 — 판정 못 하는 행이 하나라도 있으면 표째 건너뛴다 */
    private static void classify(Table t, List<Th> out, List<String> skips) {
        if (t.cellOutsideRow) {
            skips.add(LINE + t.line + ": " + NO_ROW);
            return;
        }
        boolean theadTh = t.rows.stream().anyMatch(r -> r.inThead && r.cells.stream().anyMatch(Cell::th));
        boolean firstLeft = !theadTh; // thead 에 th 가 없고 아직 thead 밖 행을 못 만났다
        List<Th> mine = new ArrayList<>();
        for (Row r : t.rows) {
            boolean first = firstLeft && !r.inThead;
            firstLeft &= r.inThead;
            String scope = rowScope(r, first);
            if (scope == null) {
                skips.add(LINE + t.line + ": " + (hasTd(r) ? MID_ROW : NOT_FIRST));
                return;
            }
            r.cells.stream().filter(c -> c.th() && !c.scoped()).forEach(c -> mine.add(new Th(c.at(), scope)));
        }
        out.addAll(mine);
    }

    /** 행의 th 에 줄 scope — thead 안·첫 행 th 만 = col · th 로 시작하고 td 가 있음 = row · th 없음 = col(안 쓰임) · 못 가름 = null */
    private static String rowScope(Row r, boolean firstBodyRow) {
        boolean th = r.cells.stream().anyMatch(Cell::th);
        if (!th || r.inThead) {
            return COL;
        }
        if (!hasTd(r)) {
            return firstBodyRow ? COL : null;
        }
        return r.cells.get(0).th() ? ROW : null;
    }

    private static boolean hasTd(Row r) {
        return r.cells.stream().anyMatch(c -> !c.th());
    }

    private static int lineOf(String code, int at) {
        return 1 + (int) code.substring(0, at).chars().filter(c -> c == '\n').count();
    }
}
