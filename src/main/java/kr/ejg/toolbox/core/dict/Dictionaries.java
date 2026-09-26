package kr.ejg.toolbox.core.dict;

import java.util.Map;

/**
 * 변환기(3-2)의 입력 — 순수본의 전역 사전 넷을 그대로 옮긴 모양.
 * <ul>
 *   <li>{@code word} — 공통표준단어 약어 → 한글(순수본 DICT). 같은 약어는 첫 줄</li>
 *   <li>{@code org} — 기관표준단어 물리명 통째 → 한글(OVER)</li>
 *   <li>{@code user} — 사용자 입력 약어 → 한글(USER)</li>
 *   <li>{@code wordMeta} — 약어 → 형식단어여부·도메인(WMETA)</li>
 *   <li>{@code domKor} — 한글 → 형식단어여부·도메인(DOMKOR — 같은 한글이면 첫 줄, 단 도메인이 비었고 뒤 줄에 있으면 뒤 줄)</li>
 * </ul>
 */
public record Dictionaries(Map<String, String> word, Map<String, String> org, Map<String, String> user,
        Map<String, WordMeta> wordMeta, Map<String, WordMeta> domKor) {

    /** 형식단어여부(Y/N, 없으면 빈 문자열)·도메인 분류(없으면 빈 문자열) */
    public record WordMeta(String formWord, String domain) {
    }

    public Dictionaries {
        word = Map.copyOf(word);
        org = Map.copyOf(org);
        user = Map.copyOf(user);
        wordMeta = Map.copyOf(wordMeta);
        domKor = Map.copyOf(domKor);
    }

    public static Dictionaries empty() {
        return new Dictionaries(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    /** 사용자 사전만 바꾼 사본 — 랭킹 화면에서 한글을 넣고 바로 다시 변환할 때 */
    public Dictionaries withUser(Map<String, String> value) {
        return new Dictionaries(word, org, value, wordMeta, domKor);
    }
}
