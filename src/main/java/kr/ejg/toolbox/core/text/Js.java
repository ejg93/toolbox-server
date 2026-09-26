package kr.ejg.toolbox.core.text;

/**
 * 순수본 JS 와 같은 결과를 내야 하는 문자열 연산(3-2). 자바 {@code strip()}·{@code \s} 는 JS 와 공백 범위가 다르다 —
 * JS 는 NBSP(U+00A0)·전각 공백(U+3000)·BOM(U+FEFF) 등을 공백으로 본다(ECMAScript WhiteSpace + LineTerminator).
 */
public final class Js {

    private Js() {
    }

    public static boolean isSpace(char c) {
        return c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r' || c == ' '
                || c == 0x00A0 || c == 0x1680 || (c >= 0x2000 && c <= 0x200A)
                || c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F || c == 0x3000 || c == 0xFEFF;
    }

    /** JS {@code String.prototype.trim} */
    public static String trim(String s) {
        if (s == null) {
            return "";
        }
        int a = 0;
        int b = s.length();
        while (a < b && isSpace(s.charAt(a))) {
            a++;
        }
        while (b > a && isSpace(s.charAt(b - 1))) {
            b--;
        }
        return s.substring(a, b);
    }

    /** JS {@code s.replace(/\s+/g, '')} */
    public static String removeSpaces(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (!isSpace(s.charAt(i))) {
                sb.append(s.charAt(i));
            }
        }
        return sb.toString();
    }

    /** JS 객체 키가 「배열 인덱스」라 삽입 순서보다 먼저, 숫자순으로 나열되는지(0, 1, … 2^32-2, 앞자리 0 없음) */
    public static boolean isArrayIndex(String key) {
        if (key.isEmpty() || key.length() > 10) {
            return false;
        }
        if (key.length() > 1 && key.charAt(0) == '0') {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) < '0' || key.charAt(i) > '9') {
                return false;
            }
        }
        return Long.parseLong(key) < 4_294_967_295L;
    }
}
