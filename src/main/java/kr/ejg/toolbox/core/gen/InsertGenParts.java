package kr.ejg.toolbox.core.gen;

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
