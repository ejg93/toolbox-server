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

    /** YYYY-MM-DD 길이 */
    private static final int DATE_LEN = 10;

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
            LocalDate.parse(v.substring(0, DATE_LEN));
            if (v.length() > DATE_LEN) {
                LocalTime.parse(v.substring(DATE_LEN + 1));
            }
            return null;
        } catch (DateTimeException e) {
            return "없는 날짜·시각이다";
        }
    }

    /** CHECK (col IN (…)) 마다 컬럼(대문자) → 값 리터럴들 — 목록 끝은 글자 안 괄호를 건너뛴다(A-32) */
    /**
     * 4-47(A2-03) — 조건이 Validation 의 허용 값(InSet — 겹괄호·OR 등식·PG ANY(ARRAY))이면 컬럼(대문자) → INSERT 리터럴(quoted 면 '…', 안 ' 는
     * 겹친다)을 넣고 참. 아니면 거짓(지금 꼴로 물러선다)
     */
    static boolean inSet(String condition, Map<String, List<String>> out) {
        if (!(Validation.parse(condition) instanceof Validation.InSet s)) {
            return false;
        }
        out.putIfAbsent(s.column().toUpperCase(Locale.ROOT), s.values().stream().map(v -> s.quoted() ? "'" + v.replace("'", "''") + "'" : v).toList());
        return true;
    }

    /** 4-48 A2-09 — Oracle INTERVAL 앞자리 기본 정밀도는 2: 자릿수가 넘으면 (자릿수) */
    static String leading(int n) {
        int digits = String.valueOf(n).length();
        return digits > 2 ? "(" + digits + ")" : "";
    }

    /** 4-48 A2-10 — BIT 값: PG 는 B'1'·B'0', 그 밖은 1·0 */
    static String bit(int idx, String db) {
        String v = idx % 2 != 0 ? "0" : "1";
        return "pg".equals(db) ? "B'" + v + "'" : v;
    }

    /** 4-48 A2-11 — 수 형식: NUMISH 이고 INTERVAL 로 시작하지 않고 POINT 를 품지 않는다(.*INT.* 가 POINT 에 걸린다) */
    static boolean numish(String t) {
        return InsertGen.NUMISH.matcher(t).matches() && !t.startsWith("INTERVAL") && !t.contains("POINT");
    }

    /** 4-48 A2-07 — 소수 없는 좁은 수(정밀도 1~9)의 값수 10^p − 1, 그 밖 0 */
    static int narrowNumber(InsertGen.Col c) {
        return numish(c.type()) && c.scale() <= 0 && c.prec() >= 1 && c.prec() <= 9 ? (int) Math.pow(10, c.prec()) - 1 : 0;
    }

    /** 4-48 A2-04 — CSV 값 행의 칸 수가 머리와 다르면 경고(값은 안 싣는다 — 칸이 밀리거나 사라진다) */
    static void columnCount(int cells, int head, int line, List<String> warns) {
        if (cells != head) {
            warns.add(line + "행: 칸 수 " + cells + " — 머리 " + head);
        }
    }

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

    /** 4-52 — 스냅샷 원본 방언이 있으면 그것으로 정확 이름을 잰다 · 없으면 {@link InsertGen#ident(String, String, boolean)} */
    static String ident(String name, String db, InsertGen.Options o) {
        if (o.source() == null) {
            return InsertGen.ident(name, db, o.exactNames());
        }
        return o.exactNames() && DdlGen.exactIn(DdlGen.lower(o.source()), name) ? "\"" + name.replace("\"", "\"\"") + "\"" : InsertGen.ident(name, db);
    }
}
