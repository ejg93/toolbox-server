package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/** 코드 정리 단계 marker — 쓸모없는 tb-ignore 표식 떼기. 0-82 — Cleanup 안에서 밖으로(길이 상한). 몸 그대로 */
final class MarkerFix {

    /** 파일 하나 재검사로는 표식이 쓰이는지 모르는 규칙 — 짝 파일이 폴더에 있다 */
    // 0-64b — metric.duplicate(5-35b)가 빠져 정리가 쓸모 있는 중복 표식을 떼었다(파일 하나만 재검사하면 상대가 없다)
    static final Set<String> CROSS_FILE = Set.of("java.dupMapping", "mybatis.namespace", "metric.duplicate");
    private static final Set<String> C_LIKE = Set.of("java", "js", "jsx", "ts", "tsx", "mjs", "cjs", "css", "scss", "kt", "groovy");
    private static final Set<String> MARKUP = Set.of("xml", "html", "htm", "xhtml", "vue");
    private static final Set<String> JSP = Set.of("jsp", "jspf", "tag");
    private static final Set<String> HASH_LINE = Set.of("properties", "yml", "yaml", "sh");
    private static final Map<String, String> CLOSER = Map.of("/*", "*/", "<!--", "-->", "<%--", "--%>");
    /** 0-82 — 리터럴 대신 상수(PMD AvoidLiteralsInIfCondition — Cleanup 은 기준선 짝, 새 클래스는 아니다) */
    private static final int ONE_MARK = 1;
    private static final String SQL = "sql";
    private static final String PROPERTIES = "properties";

    private MarkerFix() {
    }

    /**
     * 쓸모없는 tb-ignore 표식을 뗀다 — 주석 모양은 {@code stripComments} 로 판정. 줄 전체가 표식 주석이면 줄째, 주석 안 글 뒤면 표식부터 주석 끝까지,
     * 코드 뒤 꼬리 주석이면 주석째. 파일 사이 규칙·전부 표식·꺼진 규칙·표식 둘·모르는 주석 문법은 건너뛴다
     */
    static void apply(Source s, List<Integer> lines, Set<String> enabledIds, Edits e, List<Cleanup.Skip> skips) {
        String lang = s.lang();
        boolean known = C_LIKE.contains(lang) || "sql".equals(lang) || JSP.contains(lang) || MARKUP.contains(lang) || HASH_LINE.contains(lang);
        boolean textBlocks = C_LIKE.contains(lang) && s.text().contains("\"\"\"");
        String[] tl = s.text().split("\n", -1);
        String[] sl = RegexRule.stripComments(s.text(), lang).split("\n", -1);
        for (int line : lines) {
            int i = line - 1;
            if (i < 0 || i >= tl.length) {
                continue;
            }
            String why = markerEdit(tl[i], i < sl.length ? sl[i] : tl[i], lang, known, textBlocks, enabledIds, line, e);
            if (why != null) {
                skips.add(new Cleanup.Skip(Cleanup.MARKER, "줄 " + line + ": " + why));
            }
        }
    }

    /** 한 줄 — 고쳤으면 null, 아니면 사유 */
    private static String markerEdit(String line, String sline, String lang, boolean known, boolean textBlocks, Set<String> enabledIds, int no,
            Edits e) {
        Matcher mm = Ignores.MARK.matcher(line);
        int hits = 0;
        int pm = -1;
        int pe = -1;
        while (mm.find()) {
            hits++;
            if (pm < 0) {
                pm = mm.start(1); // 5-44b — 매치는 주석 토큰부터, 표식 자리는 낱말부터
                pe = mm.end();
            }
        }
        if (hits != ONE_MARK) {
            return hits == 0 ? "표식을 못 찾음" : "표식 둘";
        }
        Set<String> ids = Ignores.ids(line, pe);
        if (ids.isEmpty() || ids.stream().anyMatch(CROSS_FILE::contains)) {
            return "파일 사이 규칙·전부 표식 — 폴더 검사에서만 판정 가능";
        }
        if (!enabledIds.containsAll(ids)) {
            return "꺼졌거나 없는 규칙의 표식";
        }
        if (!known) {
            return "주석 문법을 모르는 파일";
        }
        if (textBlocks) {
            return "텍스트 블록이 있는 파일 — 주석 판정을 못 믿는다"; // stripComments 는 """ 를 모른다
        }
        String shape = "표식이 든 주석 모양을 못 읽음";
        if (sline.length() != line.length() || sline.charAt(pm) != ' ') {
            return shape;
        }
        int q = 0;
        for (int k = pm - 1; k >= 0; k--) {
            if (!Character.isWhitespace(sline.charAt(k))) {
                q = k + 1;
                break;
            }
        }
        for (int k = q; k < sline.length(); k++) {
            if (!Character.isWhitespace(sline.charAt(k))) {
                return shape;
            }
        }
        boolean whole = line.substring(0, q).isBlank();
        String seg = line.substring(q);
        String st = seg.stripLeading();
        int segOff = q + seg.length() - st.length();
        String opener = opener(st, lang, whole);
        if (opener == null || HASH_LINE.contains(lang) && !whole) {
            return shape;
        }
        int afterOp = segOff + opener.length();
        if (afterOp > pm) {
            return shape;
        }
        String between = line.substring(afterOp, pm);
        if (between.contains("//") || between.contains("/*")) {
            return shape;
        }
        String closer = "*".equals(opener) ? "*/" : CLOSER.get(opener);
        int closeIdx = closer == null ? -1 : line.indexOf(closer, pe);
        if (closer != null && !"*".equals(opener) && closeIdx < 0) {
            return shape; // 블록 주석이 다음 줄로 이어진다
        }
        if (closeIdx >= 0 && !line.substring(closeIdx + closer.length()).isBlank()) {
            return shape;
        }
        boolean first = between.isBlank();
        if (!whole) {
            e.replace(no, line.substring(0, q).stripTrailing());
        } else if (first && !("*".equals(opener) && closeIdx >= 0)) {
            e.delete(no);
        } else {
            String prefix = line.substring(0, pm).stripTrailing();
            e.replace(no, closeIdx >= 0 ? prefix + " " + line.substring(closeIdx) : prefix);
        }
        return null;
    }

    /** 주석 여는 글 — 언어별. 줄 전체가 블록 주석 가운데 줄이면 「*」 */
    private static String opener(String st, String lang, boolean whole) {
        List<String> ops = new ArrayList<>();
        if (C_LIKE.contains(lang)) {
            ops.addAll(List.of("//", "/*"));
        } else if (SQL.equals(lang)) {
            ops.addAll(List.of("--", "/*"));
        } else if (JSP.contains(lang)) {
            ops.addAll(List.of("<%--", "<!--"));
        } else if (MARKUP.contains(lang)) {
            ops.add("<!--");
        } else if (PROPERTIES.equals(lang)) {
            ops.addAll(List.of("#", "!"));
        } else if (HASH_LINE.contains(lang)) {
            ops.add("#");
        }
        for (String op : ops) {
            if (st.startsWith(op)) {
                return op;
            }
        }
        if (whole && (C_LIKE.contains(lang) || "sql".equals(lang)) && st.startsWith("*") && !st.startsWith("*/")) {
            return "*";
        }
        return null;
    }
}
