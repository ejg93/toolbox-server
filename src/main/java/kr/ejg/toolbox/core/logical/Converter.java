package kr.ejg.toolbox.core.logical;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.dict.Dictionaries;

/**
 * 물리명 → 한글 논리명 조립. 순수본 논리명 변환기의 {@code lookup}·{@code convert} 를 그대로 옮겼다(2.2 B — 자바가 유일한 구현).
 * <ol>
 *   <li>물리명 대문자가 기관표준단어·사용자 사전에 통째로 있으면 그것</li>
 *   <li>없으면 {@code _} 로 나눠(무시 토큰은 테이블만) 토큰마다 찾는다 — 우선순위 기관 → 사용자 → 공통(기관 뒤로 미루는 설정이면 사용자 → 공통 → 기관)</li>
 *   <li>이어붙일 때 <b>연속으로 못 찾은 토큰 사이에만</b> {@code _} 를 남긴다</li>
 *   <li>src: 전부 찾으면 소스 하나(given·user·word) 또는 둘 이상 multi, 일부 못 찾으면 mix, 하나도 못 찾으면 none(이름은 원문)</li>
 * </ol>
 * 부수효과(순수본 USEDWORD·USEDTOK)는 이 객체가 모은다 — 한 번의 실행에 하나씩 만든다.
 */
public final class Converter {

    /** 조립 결과. missing 은 못 찾은 토큰(대문자) */
    public record Result(String name, String src, List<String> missing) {
        public Result {
            missing = List.copyOf(missing);
        }
    }

    /** 순수본 USEDTOK 한 칸 */
    public record Used(String kor, String src) {
    }

    private final Dictionaries dicts;
    private final boolean orgFirst;
    private final Set<String> usedWords = new LinkedHashSet<>();
    private final Map<String, Used> usedTokens = new LinkedHashMap<>();

    /** @param orgFirst 순수본 tokPri — 기본(기관표준단어 우선)이 true */
    public Converter(Dictionaries dicts, boolean orgFirst) {
        this.dicts = dicts;
        this.orgFirst = orgFirst;
    }

    /** 토큰 하나 — 없으면 null */
    Used lookup(String tok) {
        String t = tok.toUpperCase(Locale.ROOT);
        if (orgFirst && dicts.org().containsKey(t)) {
            return new Used(dicts.org().get(t), "given");
        }
        if (dicts.user().containsKey(t)) {
            return new Used(dicts.user().get(t), "user");
        }
        if (dicts.word().containsKey(t)) {
            return new Used(dicts.word().get(t), "word");
        }
        if (!orgFirst && dicts.org().containsKey(t)) {
            return new Used(dicts.org().get(t), "given");
        }
        return null;
    }

    /** @param skip 무시 토큰(대문자). 순수본은 테이블명에만 준다 */
    public Result convert(String phys, List<String> skip) {
        String p = phys.toUpperCase(Locale.ROOT);
        if (dicts.org().containsKey(p)) {
            return new Result(dicts.org().get(p), "given", List.of());
        }
        if (dicts.user().containsKey(p)) {
            return new Result(dicts.user().get(p), "user", List.of());
        }
        List<String> toks = new ArrayList<>();
        for (String t : p.split("_", -1)) {
            if (!t.isEmpty() && !skip.contains(t)) {
                toks.add(t);
            }
        }
        if (toks.isEmpty()) {
            return new Result(phys, "none", List.of());
        }
        List<String> parts = new ArrayList<>();
        List<Boolean> isMiss = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        int hit = 0;
        for (String t : toks) {
            Used r = lookup(t);
            if (r != null) {
                parts.add(r.kor());
                isMiss.add(false);
                used.add(r.src());
                hit++;
                if (r.src().equals("word")) {
                    usedWords.add(t);
                }
                usedTokens.putIfAbsent(t, r);
            } else {
                parts.add(t);
                isMiss.add(true);
                missing.add(t);
            }
        }
        if (hit == 0) {
            return new Result(phys, "none", missing);
        }
        StringBuilder name = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            if (isMiss.get(i - 1) && isMiss.get(i)) {
                name.append('_');
            }
            name.append(parts.get(i));
        }
        String src = !missing.isEmpty() ? "mix" : (used.size() > 1 ? "multi" : used.iterator().next());
        return new Result(name.toString(), src, missing);
    }

    /** word 소스로 쓰인 약어(순수본 USEDWORD) */
    public Set<String> usedWords() {
        return java.util.Collections.unmodifiableSet(usedWords);
    }

    /** 약어 → 첫 매칭(순수본 USEDTOK) */
    public Map<String, Used> usedTokens() {
        return java.util.Collections.unmodifiableMap(usedTokens);
    }
}
