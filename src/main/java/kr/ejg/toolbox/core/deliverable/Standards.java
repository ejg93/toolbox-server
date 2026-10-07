package kr.ejg.toolbox.core.deliverable;

import java.util.ArrayList;
import java.util.List;
import kr.ejg.toolbox.core.dict.Dictionaries;
import kr.ejg.toolbox.core.logical.Candidates;
import kr.ejg.toolbox.core.logical.DomainMatcher;
import kr.ejg.toolbox.core.logical.LogicalRun;

/**
 * 산출물 05 표준단어·06 표준도메인·07 표준용어 값 표(2-2). 행은 3-6 후보 CSV 와 같은 코드({@link Candidates} 의 *Rows)에서
 * 오고 여기서는 양식 열로 재배열만 한다(2.2 B — 산출물과 변환기가 같은 코드). 규칙 R21~R26 은 PLAN 2-2 행.
 */
public final class Standards {

    /** R21 — 기관명·관리부서명은 옵션. dbName 이 비면 스키마들을 / 로(3-6 과 같다) */
    public record Options(String org, String dept, String dbName, boolean orgFirst) {
    }

    /** 05~07 — 별표1 항목 글자로 띄어쓰기만 맞춘다(괄호 주석은 뗀다, 2-11). 순번·기관명·DB명·관리부서명·특이사항은 확장 */
    static final List<String> COLS_05 = List.of("순번", "기관명", "DB명", "표준단어명", "단어 영문명", "단어 영문약어명", "단어 설명",
            "형식단어 여부", "도메인 분류명", "이음동의어 목록", "금칙어 목록", "관리부서명", "제정일자", "특이사항");
    static final List<String> COLS_06 = List.of("순번", "기관명", "DB명", "표준도메인 그룹명", "도메인분류명", "도메인명", "도메인 설명",
            "데이터타입", "데이터길이", "소수점 길이", "저장형식", "표현형식", "단위", "허용값", "관리부서명", "제정일자", "특이사항");
    static final List<String> COLS_07 = List.of("순번", "기관명", "DB명", "표준용어명", "영문명", "영문약어명", "용어설명", "표준도메인명", "허용값",
            "관리부서명", "표준코드명", "업무분야", "행정표준코드명", "제정일자", "특이사항");

    private Standards() {
    }

    public static List<Doc> build(LogicalRun.Result r, Dictionaries d, DomainMatcher dm, Options o) {
        String db = Candidates.dbName(o.dbName(), r);
        return List.of(d05(r, d, db, o), d06(r, dm, db, o), d07(r, d, db, o));
    }

    /** R22·R23 — 영문명·설명·이음동의어·금칙어·도메인분류는 공통표준단어일 때만, 형식단어여부는 3-6 규칙 */
    static Doc d05(LogicalRun.Result r, Dictionaries d, String db, Options o) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (List<String> w : Candidates.stdWordRows(r, d, db)) {
            String kor = w.get(1);
            String abbr = w.get(2);
            String src = w.get(4);
            Dictionaries.WordMeta m = src.equals("공통표준단어") ? d.wordMeta().get(abbr) : null;
            List<String> note = new ArrayList<>();
            if (!src.equals("공통표준단어")) {
                note.add(src);
            }
            if (!w.get(5).isEmpty()) {
                note.add("중복: " + w.get(5));
            }
            rows.add(List.of(++n, nz(o.org()), db, kor, m == null ? "" : m.wordEn(), abbr, m == null ? "" : m.description(), w.get(3),
                    m == null ? "" : m.domain(), m == null ? "" : m.synonyms(), m == null ? "" : m.forbidden(), nz(o.dept()), "",
                    String.join(" · ", note)));
        }
        return new Doc("05", "DB 표준단어", COLS_05, rows);
    }

    /** R24 — 규격 일치는 행안부 값 그대로, 나머지는 규칙 생성값 + 특이사항에 검토 문구 */
    static Doc d06(LogicalRun.Result r, DomainMatcher dm, String db, Options o) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (List<String> x : Candidates.domainRows(r, dm, db)) {
            rows.add(List.of(++n, nz(o.org()), db, x.get(1), x.get(2), x.get(3), x.get(4), x.get(5), x.get(6), x.get(7), x.get(8), x.get(9),
                    x.get(10), x.get(11), nz(o.dept()), "", x.get(13)));
        }
        return new Doc("06", "DB 표준도메인", COLS_06, rows);
    }

    /** R25·R26 — 조립 한글·물리명·도메인, 검토 필요(미매칭·부분매칭)와 테이블 여부는 특이사항 */
    static Doc d07(LogicalRun.Result r, Dictionaries d, String db, Options o) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (List<String> t : Candidates.termRows(r, d, o.orgFirst(), db, false)) {
            List<String> note = new ArrayList<>();
            if (t.get(0).equals("테이블")) {
                note.add("테이블");
            }
            if (!t.get(7).isEmpty()) {
                note.add(t.get(7));
            }
            rows.add(List.of(++n, nz(o.org()), db, t.get(2), "", t.get(3), "", t.get(5), "", nz(o.dept()), "", "", "", "",
                    String.join(" · ", note)));
        }
        return new Doc("07", "DB 표준용어", COLS_07, rows);
    }

    /** 미등록 약어 시트의 「채우는 곳」 — 모든 행 같은 글 */
    static final String FILL_HOW = "표준 사전 · 논리명 화면 → 미등록 약어 랭킹에 한글을 넣으면 사용자 사전에 저장 → 산출물 다시 만들기";

    /**
     * 2-23 — 사전에 없는 조각: [약어, 출현, 예시(표.컬럼 최대 3, 없으면 표), 채우는 곳]. 출현 내림차순 → 약어순.
     * 사용자 사전에 이미 넣은 약어(출현 0)는 뺀다. 00_작성안내 「미등록 약어」 시트 — 05 행으로는 안 싣는다(사용자 2026-10-07)
     */
    static List<List<Object>> missingAbbrs(LogicalRun.Result r) {
        java.util.Map<String, List<String>> examples = new java.util.HashMap<>();
        for (LogicalRun.Row row : r.rows()) {
            for (String t : row.missing()) {
                List<String> ex = examples.computeIfAbsent(t, k -> new java.util.ArrayList<>());
                String where = row.table() + "." + row.col();
                if (ex.size() < 3 && !ex.contains(where)) {
                    ex.add(where);
                }
            }
        }
        for (LogicalRun.TableRow row : r.tableRows()) {
            for (String t : row.missing()) {
                List<String> ex = examples.computeIfAbsent(t, k -> new java.util.ArrayList<>());
                if (ex.size() < 3 && !ex.contains(row.table())) {
                    ex.add(row.table());
                }
            }
        }
        List<LogicalRun.Rank> rank = new java.util.ArrayList<>(r.rank().stream().filter(k -> k.count() > 0).toList());
        rank.sort(java.util.Comparator.comparingInt(LogicalRun.Rank::count).reversed().thenComparing(LogicalRun.Rank::token));
        List<List<Object>> out = new java.util.ArrayList<>();
        for (LogicalRun.Rank k : rank) {
            out.add(List.of(k.token(), k.count(), String.join(" · ", examples.getOrDefault(k.token(), List.of())), FILL_HOW));
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
