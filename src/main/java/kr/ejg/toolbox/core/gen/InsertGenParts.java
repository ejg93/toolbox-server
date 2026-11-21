package kr.ejg.toolbox.core.gen;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/** InsertGen 도우미 — InsertGen 이 메서드 상한(30)이라 옮겼다(4-38) */
final class InsertGenParts {

    private InsertGenParts() {
    }

    /** 행 수를 1~MAX_ROWS 로 — 바꿨으면 경고(A-25) */
    static int rowCount(int asked, List<String> warns) {
        int rows = Math.max(1, Math.min(InsertGen.MAX_ROWS, asked));
        if (rows != asked) {
            warns.add("행 수 " + asked + " → " + rows + "(1~" + InsertGen.MAX_ROWS + " 만)");
        }
        return rows;
    }

    /** upsert 인데 그 표의 PK 컬럼이 CSV 에 없으면 거절(A-34 — 첫 컬럼을 키로 한 MERGE 는 다른 행을 고친다). PK 가 아예 없는 표는 지금처럼 */
    static void requireKeys(Map<String, InsertGen.Col> byName, List<InsertGen.Col> head, InsertGen.Options o) {
        if (!o.upsert()) {
            return;
        }
        List<String> missing = byName.values().stream().filter(c -> c.pk() && !head.contains(c)).map(c -> c.name().toUpperCase(Locale.ROOT)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("upsert 는 CSV 에 PK 컬럼(" + String.join(", ", missing) + ")이 있어야 한다");
        }
    }

    /** 날짜 값의 흠 — 모양이 틀리면 지금 글, 모양은 맞는데 없는 날짜·시각이면 새 글(A-27). 괜찮으면 null */
    static String dateProblem(String v) {
        if (!InsertGen.dateShape(v)) {
            return "날짜 모양(YYYY-MM-DD[ HH:MI[:SS]])이 아니다";
        }
        try {
            LocalDate.parse(v.substring(0, 10));
            if (v.length() > 10) {
                LocalTime.parse(v.substring(11));
            }
            return null;
        } catch (DateTimeException e) {
            return "없는 날짜·시각이다";
        }
    }

    /** CHECK (col IN (…)) 마다 컬럼(대문자) → 값 리터럴들 — 목록 끝은 글자 안 괄호를 건너뛴다(A-32) */
    static void collect(String text, Map<String, List<String>> out) {
        Matcher m = InsertGen.CHECK_IN.matcher(text);
        while (m.find()) {
            int open = m.end() - 1;
            int close = DdlReader.matching(text, open);
            if (close > open) {
                out.putIfAbsent(m.group(1).toUpperCase(Locale.ROOT), InsertGen.splitVals(text.substring(open + 1, close)));
            }
        }
    }
}
