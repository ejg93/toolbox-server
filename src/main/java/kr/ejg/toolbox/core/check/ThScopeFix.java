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
 * 그 밖(th 만인 행이 첫 행이 아님 · td 뒤에 th · td 가 있는 행에 th 가 이웃 · tr 없는 표)은 표째 건너뛴다. 중첩 표는 안쪽이 자기 표로 판정.
 * 주석·script·스크립틀릿·EL 안은 안 본다(BlankRelRule.dead) · 속성 값 안 글도 안 본다(quietAttrs)
 */
final class ThScopeFix {
    static final String TH_SCOPE = "a11y.thScope";
    static final String COL = "col";
    static final String ROW = "row";
    static final String NOT_FIRST = "th 만 있는 행이 첫 행이 아니다 — 표 구조를 못 가름";
    static final String MID_ROW = "td 뒤에 th — 표 구조를 못 가름";
    static final String NO_ROW = "tr 없는 표";
    static final String OUTSIDE = "table 밖 th(조각)";
    /** 토큰 — <th 경계는 rules.yaml a11y.thScope 의 when 과 같은 판정(줄 끝 포함). CheckRulesTest 가 둘을 같은 표본에 댄다 */
    static final Pattern TOKEN = Pattern.compile("(?i)<(/?)(table|thead|tr|th|td)(?=[\\s/>]|$)");
    static final String TH_PAIR = "td 가 있는 행에 th 가 이웃 — 표 구조를 못 가름";
    private static final Pattern SCOPED = Pattern.compile("(?i)(?<![\\w:-])(?:scope|headers)\\s*=");
    private static final String TABLE = "table";
    private static final String THEAD = "thead";
    private static final String TR = "tr";
    private static final String TH = "th";
    private static final String LINE = "줄 ";
    private static final char DQ = '"';
    private static final char SQ = '\'';
    private static final char LT = '<';
    private static final char GT = '>';
    private static final char SLASH = '/';
    private static final char NL = '\n';
    private static final char SPACE = ' ';
    private static final char NONE = 0;

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
        String code = quietAttrs(BlankRelRule.dead(text, lang));
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
            String why = problem(r, first);
            if (why != null) {
                skips.add(LINE + t.line + ": " + why);
                return;
            }
            String scope = rowScope(r);
            r.cells.stream().filter(c -> c.th() && !c.scoped()).forEach(c -> mine.add(new Th(c.at(), scope)));
        }
        out.addAll(mine);
    }

    /** 행을 못 가르는 사유, 가르면 null — th 만인 행이 첫 행이 아님 · 첫 칸이 td · td 가 있는데 th 가 이웃(5-81) */
    private static String problem(Row r, boolean firstBodyRow) {
        if (r.inThead || !hasTh(r)) {
            return null;
        }
        if (!hasTd(r)) {
            return firstBodyRow ? null : NOT_FIRST;
        }
        if (!r.cells.get(0).th()) {
            return MID_ROW;
        }
        return adjacentTh(r) ? TH_PAIR : null;
    }

    /** 사유가 없는 행의 scope — td 가 있는 몸 행은 row, 그 밖(thead·th 만인 첫 행)은 col */
    private static String rowScope(Row r) {
        return !r.inThead && hasTd(r) ? ROW : COL;
    }

    private static boolean hasTh(Row r) {
        return r.cells.stream().anyMatch(Cell::th);
    }

    /** 연속한 두 칸이 모두 th */
    private static boolean adjacentTh(Row r) {
        for (int i = 1; i < r.cells.size(); i++) {
            if (r.cells.get(i - 1).th() && r.cells.get(i).th()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTd(Row r) {
        return r.cells.stream().anyMatch(c -> !c.th());
    }

    /** 5-81 — 태그 안 따옴표 값(onclick="…<table>…" · value='<th>')을 공백으로. 길이·줄바꿈 보존, 따옴표 글자는 남긴다(attrs 가 짝을 센다). 안 닫힌 따옴표는 끝까지 */
    static String quietAttrs(String code) {
        StringBuilder sb = new StringBuilder(code);
        char quote = NONE;
        boolean inTag = false;
        for (int i = 0; i < sb.length(); i++) {
            char ch = sb.charAt(i);
            if (quote != NONE) {
                if (ch == quote) {
                    quote = NONE;
                } else if (ch != NL) {
                    sb.setCharAt(i, SPACE);
                }
            } else if (inTag) {
                if (ch == DQ || ch == SQ) {
                    quote = ch;
                } else if (ch == GT) {
                    inTag = false;
                }
            } else if (ch == LT && i + 1 < sb.length() && (Character.isLetter(sb.charAt(i + 1)) || sb.charAt(i + 1) == SLASH)) {
                inTag = true;
            }
        }
        return sb.toString();
    }

    private static int lineOf(String code, int at) {
        return 1 + (int) code.substring(0, at).chars().filter(c -> c == '\n').count();
    }
}
