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
    /** KEY·INDEX 뒤 `(` 앞 낱말에서 뺄 수식어(1-124) */
    private static final java.util.Set<String> KEY_MODS = java.util.Set.of("UNIQUE", "CLUSTERED", "NONCLUSTERED", "HASH", "COLUMNSTORE");
    /** 이름 한 마디 — 대괄호·큰따옴표·백틱으로 싼 것(안 공백 포함) 또는 공백·괄호·점·쉼표·세미콜론 없는 덩어리(1-123) */
    private static final String PART = "(?:\\[[^\\]]++\\]|\"[^\"]++\"|`[^`]++`|[^\\s(.,;\"\\[`]++)";
    /**
     * 1-123(A1-07) — 마디 셋까지의 이름(db.스키마.표·스키마.표·표). 마디 사이는 점 하나·둘. 묶음 반복 없이 선택 묶음을 잇는다(SpotBugs REDOS)
     */
    static final String QNAME = PART + "(?:\\.{1,2}" + PART + ")?(?:\\.{1,2}" + PART + ")?";

    private DdlReaderParts() {
    }

    /** 1-124 — KEY·INDEX 머리: 이름(없으면 null) · 그 밖 남은 낱말 수 · UNIQUE · 여는 괄호 자리 */
    record KeyHead(String name, int extra, boolean unique, int open) {
    }

    /**
     * 1-124(A1-10·A1-22) — KEY·INDEX 낱말 끝(wordEnd) 뒤 {@code (} 앞 낱말을 손으로 나눠 IF NOT EXISTS · USING 방식 · UNIQUE·CLUSTERED·
     * NONCLUSTERED·HASH·COLUMNSTORE 를 뺀다(정규식 수량자 겹침 없이). {@code (} 가 없으면 null
     */
    static KeyHead keyHead(String item, int wordEnd) {
        int open = item.indexOf('(', wordEnd);
        if (open < 0) {
            return null;
        }
        String between = item.substring(wordEnd, open).strip();
        String[] w = between.isEmpty() ? new String[0] : between.split("\\s+");
        java.util.List<String> rest = new java.util.ArrayList<>();
        boolean unique = false;
        int i = 0;
        while (i < w.length) {
            String u = w[i].toUpperCase(Locale.ROOT);
            if ("IF".equals(u) && i + 2 < w.length && "NOT".equalsIgnoreCase(w[i + 1]) && "EXISTS".equalsIgnoreCase(w[i + 2])) {
                i += 3;
            } else if ("USING".equals(u)) {
                i += 2;
            } else {
                unique |= "UNIQUE".equals(u);
                if (!KEY_MODS.contains(u)) {
                    rest.add(w[i]);
                }
                i++;
            }
        }
        return new KeyHead(rest.isEmpty() ? null : rest.get(0), rest.size(), unique, open);
    }

    /** 1-124(A1-20) — 괄호 묶음 안(깊이 1 이상)을 같은 길이 빈칸으로 */
    static String outsideParens(String s) {
        StringBuilder b = new StringBuilder(s);
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            depth += c == '(' ? 1 : 0;
            if (depth > 0) {
                b.setCharAt(i, ' ');
            }
            depth -= c == ')' && depth > 0 ? 1 : 0;
        }
        return b.toString();
    }

    /** 1-123 — 목록 한 항목의 이름 토큰: 싼 이름은 통째(안 공백 포함), 아니면 첫 공백·괄호 앞까지(뒤 ASC·DESC·(n) 은 뗀다) */
    static String firstName(String part) {
        String p = part.strip();
        if (p.isEmpty()) {
            return p;
        }
        char c = p.charAt(0);
        char close = c == '"' ? '"' : c == '`' ? '`' : c == '[' ? ']' : 0;
        int e = close == 0 ? -1 : p.indexOf(close, 1);
        if (e > 0) {
            return p.substring(0, e + 1);
        }
        int k = 0;
        while (k < p.length() && !Character.isWhitespace(p.charAt(k)) && p.charAt(k) != '(') {
            k++;
        }
        return p.substring(0, k);
    }

    /** 1-123(A1-11) — 머리의 대괄호 형식({@code [int]}·{@code [nvarchar](40)}, 앞 {@code [스키마].} 도)을 대괄호 없는 글로 */
    static String bareType(String rest) {
        String r = rest.strip();
        if (!r.startsWith("[")) {
            return rest;
        }
        int e = r.indexOf(']');
        if (e < 0) {
            return rest;
        }
        if (e + 2 < r.length() && r.charAt(e + 1) == '.' && r.charAt(e + 2) == '[') {
            return bareType(r.substring(e + 2)); // [스키마].[형식] — 스키마는 뗀다
        }
        return r.substring(1, e) + r.substring(e + 1);
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
