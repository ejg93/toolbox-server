package kr.ejg.toolbox.core.deliverable;

import kr.ejg.toolbox.core.logical.LogicalRun;

/**
 * 2-22 — 산출물 05·07 이 얼마나 찼나. 05 는 사전에 있는 단어만 담는다 — 사전에 없는 조각(미등록 약어) 수와 출현 수가 채울 일거리다.
 * 07 은 특이사항에 「부분매칭」·「미매칭」 이 적힌 행 수. 표준 사전 · 논리명 화면에서 약어를 채우고 다시 만들면 준다.
 *
 * @param words              05 행 수(이번 변환에 쓰인 사전 약어)
 * @param missingTokens      사전에 없는 조각 종류 수(사용자 사전에 이미 넣은 것 — 출현 0 — 은 뺀다)
 * @param missingOccurrences 그 조각이 나온 횟수 합
 */
public record Coverage(int words, int missingTokens, int missingOccurrences, int terms, int termsPartial, int termsUnmatched) {

    public static Coverage of(LogicalRun.Result r, Doc d07) {
        int tokens = 0;
        int occurrences = 0;
        for (LogicalRun.Rank k : r.rank()) {
            if (k.count() > 0) {
                tokens++;
                occurrences += k.count();
            }
        }
        int partial = 0;
        int unmatched = 0;
        int terms = 0;
        if (d07 != null) {
            terms = d07.rows().size();
            int note = d07.columns().indexOf("특이사항");
            for (java.util.List<Object> row : d07.rows()) {
                String v = note < 0 ? "" : String.valueOf(row.get(note));
                if (v.contains("부분매칭")) {
                    partial++;
                } else if (v.contains("미매칭")) {
                    unmatched++;
                }
            }
        }
        return new Coverage(r.usedTokens().size(), tokens, occurrences, terms, partial, unmatched);
    }

    /** 00_작성안내 「요약」 05 행 */
    public String wordsLine() {
        return "사전 단어 " + words + " · 사전에 없는 조각 " + missingTokens + "(출현 " + missingOccurrences + ")";
    }

    /** 00_작성안내 「요약」 07 행 */
    public String termsLine() {
        return "용어 " + terms + " · 부분매칭 " + termsPartial + " · 미매칭 " + termsUnmatched;
    }
}
