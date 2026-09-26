package kr.ejg.toolbox.core.logical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.ejg.toolbox.core.dict.DictStore;

/**
 * 행안부 공통표준도메인 매칭 — 순수본 {@code DOMCLS}·{@code matchDomainWord}·{@code matchDomainSpec}·{@code typeCode}·{@code domainFmt}(3-6).
 * 개념어(분류명)는 논리명 끝이 분류명인 것 중 가장 긴 것, 규격은 타입 계열(V·C·N)·길이·소수점이 같은 첫 행.
 */
public final class DomainMatcher {

    /** 규칙 생성값 — 이름(V20·N13,2)·저장형식·표현형식 */
    public record Fmt(String name, String store, String disp) {
    }

    private final Map<String, List<DictStore.Domain>> byCls = new LinkedHashMap<>();
    private final List<String> keysLongFirst;

    public DomainMatcher(List<DictStore.Domain> domains) {
        for (DictStore.Domain d : domains) {
            byCls.computeIfAbsent(d.cls(), k -> new ArrayList<>()).add(d);
        }
        List<String> keys = new ArrayList<>(byCls.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed()); // 안정 정렬 — 같은 길이는 표 순서
        keysLongFirst = List.copyOf(keys);
    }

    /** 논리명 끝이 분류명인 것 중 가장 긴 것. 없으면 null */
    public String word(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (String k : keysLongFirst) {
            if (name.endsWith(k)) {
                return k;
            }
        }
        return null;
    }

    public List<DictStore.Domain> candidates(String cls) {
        return byCls.getOrDefault(cls, List.of());
    }

    /** 타입 계열·길이·소수점이 같은 첫 후보. 없으면 null */
    public static DictStore.Domain spec(List<DictStore.Domain> cands, String dtype, String len, String scale) {
        String code = typeCode(dtype);
        if (code.isEmpty()) {
            return null;
        }
        String l = intOrBlank(len);
        String s = intOrBlank(scale);
        for (DictStore.Domain d : cands) {
            if (typeCode(d.dataType()).equals(code) && intOrBlank(d.length()).equals(l) && intOrBlank(d.scale()).equals(s)) {
                return d;
            }
        }
        return null;
    }

    /** V(가변 문자)·C(고정 문자)·N(수) — 순서가 중요하다(VARCHAR 가 CHAR 를 품는다) */
    public static String typeCode(String dtype) {
        String t = dtype == null ? "" : dtype.toUpperCase(Locale.ROOT);
        if (t.contains("VARCHAR") || t.contains("CHARACTER VARYING")) {
            return "V";
        }
        if (t.contains("CHAR")) {
            return "C";
        }
        if (t.contains("NUMERIC") || t.contains("DECIMAL") || t.contains("NUMBER") || t.contains("FLOAT") || t.contains("DOUBLE")
                || t.contains("REAL") || t.contains("INT")) {
            return "N";
        }
        return "";
    }

    /** 규격 규칙 생성값 — V20 「20자리 이내 문자」, N13,2 「99999999999.99」. 만들 수 없으면 null */
    public static Fmt fmt(String dtype, String len, String scale) {
        String code = typeCode(dtype);
        Integer l = parseInt(len);
        Integer sRaw = parseInt(scale);
        int s = sRaw == null ? 0 : sRaw;
        if (code.equals("V") || code.equals("C")) {
            if (l == null || l <= 0) {
                return null;
            }
            String f = l + "자리 이내 문자";
            return new Fmt(code + l, f, f);
        }
        if (code.equals("N")) {
            if (l == null || l <= 0) {
                return null;
            }
            int intDigits = s > 0 ? l - s : l;
            if (intDigits <= 0) {
                return null;
            }
            String mask = "9".repeat(intDigits) + (s > 0 ? "." + "9".repeat(s) : "");
            return new Fmt("N" + l + (s > 0 ? "," + s : ""), mask, mask);
        }
        return null;
    }

    /** JS {@code parseInt(s, 10)} — 앞 공백 뒤 부호·숫자만, 없으면 null(NaN) */
    static Integer parseInt(String s) {
        if (s == null) {
            return null;
        }
        String t = kr.ejg.toolbox.core.text.Js.trim(s);
        int i = 0;
        boolean neg = false;
        if (i < t.length() && (t.charAt(i) == '+' || t.charAt(i) == '-')) {
            neg = t.charAt(i) == '-';
            i++;
        }
        int start = i;
        while (i < t.length() && Character.isDigit(t.charAt(i)) && t.charAt(i) < 128) {
            i++;
        }
        if (i == start) {
            return null;
        }
        long v = Long.parseLong(t.substring(start, i));
        return (int) (neg ? -v : v);
    }

    /** JS {@code String(parseInt(x,10)||'')} — NaN·0 이면 빈 문자열 */
    static String intOrBlank(String s) {
        Integer v = parseInt(s);
        return v == null || v == 0 ? "" : String.valueOf(v);
    }
}
