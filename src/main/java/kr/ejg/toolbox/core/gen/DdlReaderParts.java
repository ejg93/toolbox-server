package kr.ejg.toolbox.core.gen;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DdlReader 도우미 — DdlReader·DdlPull 이 메서드 상한(30)이라 옮겼다(1-106 · 1-109) */
final class DdlReaderParts {

    private static final Pattern DEFAULT_WORD = Pattern.compile("\\bDEFAULT\\s+", Pattern.CASE_INSENSITIVE);
    /** 자동증가 낱말(A-08 읽기 쪽) — 글자 안을 비운 대문자 사본에서 */
    private static final Pattern AUTO_WORD = Pattern.compile("\\b(?:AUTO_INCREMENT|AUTOINCREMENT|IDENTITY)\\b");
    private static final int MAX_DIGITS = 18;

    private DdlReaderParts() {
    }

    /** 크기 자릿수 — 열여덟 자리를 넘으면 Long.MAX_VALUE(A-22) */
    static long num(String digits) {
        String d = digits.startsWith("-") ? digits.substring(1) : digits;
        return d.length() > MAX_DIGITS ? Long.MAX_VALUE : Long.parseLong(digits);
    }

    /** 원문 첫 토큰이 따옴표·백틱·대괄호로 싸였으면 안 글자가 비지 않고 줄바꿈이 없을 때 이름(A-35 — 공백 든 이름), 아니면 NAME 꼴 */
    static boolean columnName(String[] head, String name) {
        if (head != null && !head[0].isEmpty() && "\"`[".indexOf(head[0].charAt(0)) >= 0) {
            return !name.isEmpty() && name.indexOf('\n') < 0;
        }
        return DdlReader.NAME.matcher(name).matches();
    }

    /** 자동증가 컬럼인가 — AUTO_INCREMENT·AUTOINCREMENT·IDENTITY 낱말, 또는 SERIAL 꼴 타입 */
    static boolean auto(String upperMasked, String type) {
        String t = type == null ? "" : type.toUpperCase(Locale.ROOT);
        return AUTO_WORD.matcher(upperMasked).find() || "SERIAL".equals(t) || "BIGSERIAL".equals(t) || "SMALLSERIAL".equals(t);
    }

    /**
     * DEFAULT 뒤 식 하나 — 글자·괄호 묶음(겹친 것까지)·함수 호출(이름 + 괄호)·N'…' 를 통째로. GENERATED … BY DEFAULT 의 DEFAULT 는 아니다(A-29).
     * masked 는 글자 안을 비운 같은 길이 사본, 값은 원문(raw)에서. 없으면 null
     */
    static String defaultExpr(String masked, String raw) {
        Matcher m = DEFAULT_WORD.matcher(masked);
        while (m.find()) {
            if (!"BY".equalsIgnoreCase(prevWord(masked, m.start())) && m.end() < masked.length()) {
                return raw.substring(m.end(), exprEnd(masked, m.end()));
            }
        }
        return null;
    }

    /** at 앞 낱말(공백 건너뜀) */
    private static String prevWord(String s, int at) {
        int e = at;
        while (e > 0 && Character.isWhitespace(s.charAt(e - 1))) {
            e--;
        }
        int b = e;
        while (b > 0 && Character.isLetter(s.charAt(b - 1))) {
            b--;
        }
        return s.substring(b, e);
    }

    /** 식 끝 — 글자는 닫는 따옴표까지, 괄호는 짝까지, 그 밖은 공백·쉼표 앞까지(바로 뒤 괄호·따옴표는 같이) */
    private static int exprEnd(String s, int from) {
        char c = s.charAt(from);
        if (c == '\'' || c == '(') {
            return group(s, from);
        }
        int k = from;
        while (k < s.length() && !Character.isWhitespace(s.charAt(k)) && ",('".indexOf(s.charAt(k)) < 0) {
            k++;
        }
        return k < s.length() && (s.charAt(k) == '(' || s.charAt(k) == '\'') ? group(s, k) : k;
    }

    /** at 의 따옴표·괄호 묶음 끝 다음 — 안 닫히면 글 끝 */
    private static int group(String s, int at) {
        int close = s.charAt(at) == '\'' ? closingQuote(s, at + 1) : DdlReader.matching(s, at);
        return close < 0 ? s.length() : close + 1;
    }

    /** from 부터 닫는 따옴표 자리 — '' 는 글자 안 따옴표. 없으면 -1 */
    private static int closingQuote(String s, int from) {
        int q = s.indexOf('\'', from);
        while (q >= 0 && q + 1 < s.length() && s.charAt(q + 1) == '\'') {
            q = s.indexOf('\'', q + 2);
        }
        return q;
    }

    /** 1-70 — 찾은 PK 이름(따옴표 벗김), 없으면 지금 이름 그대로 */
    static String pkNameOf(String found, String current) {
        return found == null ? current : DdlReader.unquote(found);
    }
}
