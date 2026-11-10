package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * jsp.blankNoopener(설계 61) — JSP·HTML 의 target=_blank 인 {@code <a>} 에 rel 속성이 없으면 그 태그 첫 줄에 한 건.
 * 줄 단위 정규식(tsx.blankNoopener)이 못 보는 둘을 본다 — 여러 줄에 걸친 태그 · 속성 값 안의 JSP 태그({@code href="<c:url value='/x'/>"}).
 * 안 보는 것: 주석 · script 블록 · 스크립틀릿 안(문자열로 만드는 태그 — 고치면 그 문자열이 깨진다). 자바 규칙이라 순수본에는 없다
 */
public final class BlankRelRule implements Rule {
    private static final Pattern A_OPEN = Pattern.compile("(?i)<a(?=\\s)");
    private static final Pattern TARGET = Pattern.compile("(?i)(?<![\\w:-])target\\s*=\\s*");
    private static final Pattern REL = Pattern.compile("(?i)(?<![\\w:-])rel\\s*=");
    private static final String BLANK = "_blank";
    /** 따옴표 없는 값 뒤에 올 수 있는 글자 — 값이 _blank 로 끝났다 */
    private static final String BARE_END = " \t\r\n/>";
    private static final char DQ = '"';
    private static final char SQ = '\'';
    private static final char LT = '<';
    private static final char GT = '>';
    private static final char NL = '\n';
    private static final char SPACE = ' ';
    private static final char NONE = 0;
    /** 태그 하나의 글자 상한 — 넘으면 못 닫은 것으로 본다 */
    private static final int LIMIT = 4000;

    private final Def ruleDef;

    public BlankRelRule(Def def) {
        this.ruleDef = def;
    }

    @Override
    public Def def() {
        return ruleDef;
    }

    /** 지적할 태그 — line 은 태그 첫 줄(1부터), at 은 target 속성 값이 끝난 글 자리(rel 을 끼울 곳), quote 는 값의 따옴표(없으면 0) */
    record Tag(int line, int at, char quote) {
    }

    @Override
    public List<Finding> apply(Source s) {
        String[] raw = s.text().split("\n", -1);
        List<Finding> out = new ArrayList<>();
        for (Tag t : tags(s.text(), s.lang())) {
            out.add(new Finding(s.rel(), t.line(), ruleDef.group(), ruleDef.id(), ruleDef.severity(), Finding.excerpt(raw[t.line() - 1])));
        }
        return out;
    }

    /** rel 없는 target=_blank {@code <a>} 들(글 차례) — MarkupFix 도 이것으로 자리를 찾는다 */
    static List<Tag> tags(String text, String lang) {
        String code = dead(text, lang);
        List<Tag> out = new ArrayList<>();
        Matcher m = A_OPEN.matcher(code);
        while (m.find()) {
            String attrs = attrs(code, m.end());
            Matcher t = attrs == null ? null : TARGET.matcher(attrs);
            if (t == null || REL.matcher(attrs).find() || !t.find()) {
                continue;
            }
            Tag tag = blankTag(code, m.start(), m.end() + t.end());
            if (tag != null) {
                out.add(tag);
            }
        }
        return out;
    }

    /** valueAt(= target 값 첫 글자)의 값이 _blank 이면 그 태그 — 따옴표 값은 같은 따옴표로 닫혀야, 맨 값은 공백·/·> 로 끝나야 */
    private static Tag blankTag(String code, int tagStart, int valueAt) {
        char q = code.charAt(valueAt);
        int open = q == DQ || q == SQ ? 1 : 0;
        int after = valueAt + open + BLANK.length();
        if (after >= code.length() || !code.regionMatches(true, valueAt + open, BLANK, 0, BLANK.length())) {
            return null;
        }
        boolean closed = open == 1 ? code.charAt(after) == q : BARE_END.indexOf(code.charAt(after)) >= 0;
        return closed ? new Tag(lineOf(code, tagStart), after + open, open == 1 ? q : NONE) : null;
    }

    /** 주석·script 블록·스크립틀릿·EL 을 공백으로(줄바꿈은 남긴다, 길이 불변) — 그 안의 {@code <a} 는 문자열이고, 값 안의 > 와 따옴표는 태그 훑기를 흩뜨린다 */
    static String dead(String text, String lang) {
        String code = RegexRule.stripComments(text, lang);
        StringBuilder sb = new StringBuilder(code.length() == text.length() ? code : text);
        String low = sb.toString().toLowerCase(Locale.ROOT);
        String find = low.length() == sb.length() ? low : sb.toString(); // 소문자로 길이가 바뀌는 글자가 있으면 원문으로
        spaceOut(sb, find, "<script", "</script");
        spaceOut(sb, find, "<%", "%>");
        spaceOut(sb, find, "${", "}");
        return sb.toString();
    }

    private static void spaceOut(StringBuilder sb, String find, String open, String close) {
        int i = find.indexOf(open);
        while (i >= 0) {
            int close0 = find.indexOf(close, i + open.length());
            int end = close0 < 0 ? find.length() : close0 + close.length();
            for (int k = i; k < end; k++) {
                if (sb.charAt(k) != NL) {
                    sb.setCharAt(k, SPACE);
                }
            }
            i = find.indexOf(open, end);
        }
    }

    /**
     * from(= {@code <a} 바로 뒤)부터 태그 끝까지 — 따옴표 값 안을 공백으로 바꾼 속성 글(끝의 > 포함, 길이 = 훑은 글자 수). 못 닫으면 null.
     * 끝은 따옴표 밖·중첩 태그 밖의 >. 값 안에 같은 따옴표의 JSP 태그가 겹쳐도({@code href="<c:out value="x"/>"}) 따옴표가 짝수라 끝이 맞는다
     */
    private static String attrs(String code, int from) {
        StringBuilder sb = new StringBuilder();
        char quote = NONE;
        int depth = 0;
        for (int i = from; i < code.length() && i - from < LIMIT; i++) {
            char ch = code.charAt(i);
            boolean in = quote != NONE;
            if (in) {
                quote = ch == quote ? NONE : quote;
            } else if (ch == DQ || ch == SQ) {
                quote = ch;
            } else if (ch == LT) {
                depth++;
            } else if (ch == GT) {
                if (depth == 0) {
                    return sb.append(ch).toString();
                }
                depth--;
            }
            sb.append(in && quote != NONE && ch != NL ? SPACE : ch);
        }
        return null;
    }

    private static int lineOf(String code, int at) {
        return 1 + (int) code.substring(0, at).chars().filter(c -> c == NL).count();
    }
}
