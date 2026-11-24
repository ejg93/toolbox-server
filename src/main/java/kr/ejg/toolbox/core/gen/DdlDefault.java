package kr.ejg.toolbox.core.gen;

import java.util.Locale;
import java.util.Set;

/**
 * DEFAULT 뒤 식의 범위(1-130) — 항 하나, 그 뒤 이항 연산자(+ - * / % ||)가 이으면 다음 항까지. 글은 글자 안을 비운 같은 길이 사본(masked).
 * 컬럼 속성 낱말(NOT NULL·ON UPDATE·COMMENT …)은 연산자가 아니라 거기서 끝난다
 */
final class DdlDefault {
    /** 낱말 + 공백 + '…' 꼴의 형식 붙은 글자 */
    private static final Set<String> TYPED = Set.of("DATE", "TIME", "TIMESTAMP", "TIMESTAMPTZ", "INTERVAL");
    private static final Set<String> UNITS = Set.of("YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND", "TO");
    private static final String INTERVAL = "INTERVAL";
    private static final String STOPS = ",('";

    private DdlDefault() {
    }

    /** from = DEFAULT 낱말 뒤 첫 글자. {시작, 끝} — 시작은 ON NULL(Oracle 12c)을 건너뛴 자리. 식이 없으면 시작 == 끝 */
    static int[] span(String s, int from) {
        int start = afterOnNull(s, from);
        int end = term(s, start);
        int next = nextTerm(s, end);
        while (next >= 0) {
            int e = term(s, next);
            if (e == next) {
                break;
            }
            end = e;
            next = nextTerm(s, end);
        }
        return new int[] {start, end};
    }

    /** end 뒤(공백 건너)가 이항 연산자면 그 뒤 항의 시작, 아니면 -1. -- 와 블록 주석 머리는 연산자가 아니다 */
    private static int nextTerm(String s, int end) {
        int k = skip(s, end);
        if (k >= s.length() || s.startsWith("--", k) || s.startsWith("/*", k)) {
            return -1;
        }
        int after = s.startsWith("||", k) ? k + 2 : "+-*/%".indexOf(s.charAt(k)) >= 0 ? k + 1 : -1;
        int t = after < 0 ? s.length() : skip(s, after);
        return t < s.length() ? t : -1;
    }

    /** 항 하나의 끝 — 앞 부호를 건너뛴 맨 항. 항이 비면 from */
    private static int term(String s, int from) {
        int k = from < s.length() && "+-".indexOf(s.charAt(from)) >= 0 ? skip(s, from + 1) : from;
        int e = k < s.length() ? bare(s, k) : k;
        return e == k ? from : e;
    }

    /** 글자·괄호 묶음 · 낱말(바로 뒤 괄호·따옴표는 같이) · 형식 붙은 글자 · NEXT VALUE FOR 이름 */
    private static int bare(String s, int k) {
        if (s.charAt(k) == '\'' || s.charAt(k) == '(') {
            return DdlReaderParts.group(s, k);
        }
        int e = wordEnd(s, k);
        if (e > k && e < s.length() && (s.charAt(e) == '(' || s.charAt(e) == '\'')) {
            return DdlReaderParts.group(s, e);
        }
        String w = s.substring(k, e).toUpperCase(Locale.ROOT);
        if (TYPED.contains(w)) {
            return typed(s, e, INTERVAL.equals(w));
        }
        return "NEXT".equals(w) ? nextValue(s, e) : e;
    }

    /** 낱말 뒤(공백 건너)가 '…' 면 그 묶음까지, INTERVAL 이면 뒤 단위 낱말(DAY · DAY TO SECOND · DAY(2))도. 아니면 낱말 끝 */
    private static int typed(String s, int wordEnd, boolean interval) {
        int q = skip(s, wordEnd);
        if (q >= s.length() || s.charAt(q) != '\'') {
            return wordEnd;
        }
        int e = DdlReaderParts.group(s, q);
        int u = interval ? unitEnd(s, e) : -1;
        while (u >= 0) {
            e = u;
            u = unitEnd(s, e);
        }
        return e;
    }

    /** e 뒤(공백 건너)가 단위 낱말이면 그 끝(바로 뒤 (정밀도) 포함), 아니면 -1 */
    private static int unitEnd(String s, int e) {
        int u = skip(s, e);
        int ue = wordEnd(s, u);
        if (ue == u || !UNITS.contains(s.substring(u, ue).toUpperCase(Locale.ROOT))) {
            return -1;
        }
        return ue < s.length() && s.charAt(ue) == '(' ? DdlReaderParts.group(s, ue) : ue;
    }

    /** NEXT VALUE FOR 이름 — 아니면 NEXT 낱말 끝 */
    private static int nextValue(String s, int nextEnd) {
        int v = skip(s, nextEnd);
        int ve = wordEnd(s, v);
        int f = skip(s, ve);
        int fe = wordEnd(s, f);
        if (!"VALUE".equalsIgnoreCase(s.substring(v, ve)) || !"FOR".equalsIgnoreCase(s.substring(f, fe))) {
            return nextEnd;
        }
        int n = skip(s, fe);
        int ne = wordEnd(s, n);
        return ne == n ? nextEnd : ne;
    }

    /** ON NULL 뒤 자리 — 아니면 from */
    private static int afterOnNull(String s, int from) {
        int oe = wordEnd(s, from);
        int n = skip(s, oe);
        int ne = wordEnd(s, n);
        boolean onNull = "ON".equalsIgnoreCase(s.substring(from, oe)) && "NULL".equalsIgnoreCase(s.substring(n, ne));
        return onNull ? skip(s, ne) : from;
    }

    private static int skip(String s, int k) {
        int i = k;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /** 공백·쉼표·괄호·따옴표 앞까지 — 옛 exprEnd 의 낱말 꼴과 같다(SYSDATE+1 은 한 낱말) */
    private static int wordEnd(String s, int k) {
        int i = k;
        while (i < s.length() && !Character.isWhitespace(s.charAt(i)) && STOPS.indexOf(s.charAt(i)) < 0) {
            i++;
        }
        return i;
    }
}
