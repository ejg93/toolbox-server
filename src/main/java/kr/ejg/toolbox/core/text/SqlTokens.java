package kr.ejg.toolbox.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SQL 토큰 주사(5-57a, 설계 47) — 프로그램 분석(SqlTables·SqlJoins)과 코드 검사(SqlLint)가 같이 쓴다. check 가 analyze 를 부르면
 * 패키지 순환이라 여기(text)에 둔다. 주석은 부르는 쪽이 먼저 지운다.
 * <p>
 * 토큰 — 단어(대문자, 따옴표·백틱·대괄호 식별자는 벗김)·{@code (}·{@code )}·{@code ,}·{@code .}·그 밖 한 글자. 문자열 리터럴은 {@code '} 하나로,
 * {@code #{…}}·{@code ${…}} 는 {@code ?} 로 줄인다. 위치는 토큰이 시작하는 글자 자리(리터럴·식별자는 여는 따옴표, 바인드는 {@code #}·{@code $}).
 */
public final class SqlTokens {

    /** {@code 이름(} 뒤 이 말이면 표 함수가 아니라 표 + 잠금 힌트({@code FROM t (NOLOCK)}) — analyze·check 공용(5-57d) */
    public static final Set<String> HINTS = Set.of("NOLOCK", "READUNCOMMITTED", "READPAST", "ROWLOCK", "UPDLOCK", "HOLDLOCK", "TABLOCK",
            "TABLOCKX", "XLOCK", "NOWAIT", "INDEX", "FORCESEEK");
    /** 표 자리에 오면 표가 아닌 말 — 실물 표본 A 가 표 이름 전체와 대조한다(6-8) */
    public static final Set<String> STOP = Set.of("SELECT", "DUAL", "ON", "WHERE", "SET", "VALUES", "AS", "LEFT", "RIGHT", "INNER", "OUTER",
            "FULL", "CROSS", "NATURAL", "JOIN", "GROUP", "ORDER", "HAVING", "UNION", "MINUS", "EXCEPT", "INTERSECT", "LIMIT", "OFFSET",
            "WITH", "START", "CONNECT", "USING", "WHEN", "THEN", "FETCH", "FOR", "RETURNING", "PARTITION", "WINDOW", "USE", "FORCE",
            "INTO", "FROM", "UPDATE", "DELETE", "INSERT", "MERGE", "AND", "OR", "NOT", "IN", "EXISTS", "IS", "NULL", "LATERAL",
            "ONLY", "TABLE");

    private static final char QUOTE = '\'';
    private static final char DQUOTE = '"';
    private static final char BACKTICK = '`';
    private static final char LBRACKET = '[';
    private static final char RBRACKET = ']';
    private static final char HASH = '#';
    private static final char DOLLAR = '$';
    private static final char LBRACE = '{';
    private static final char RBRACE = '}';
    private static final char UNDERSCORE = '_';
    private static final String LITERAL = "'";
    private static final String BIND_MARK = "?";

    /** 토큰 글자와 시작 위치 · quoted — 따옴표·백틱·대괄호 식별자였나(C-TBL-KEYWORD) */
    public record Tok(String s, int at, boolean quoted) {
        public Tok(String s, int at) {
            this(s, at, false);
        }
    }

    private SqlTokens() {
    }

    /** 주석을 지운 글 → 토큰과 위치 */
    public static List<Tok> scan(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            i = next(s, i, out);
        }
        return out;
    }

    /** 토큰 글자만 — 따옴표 친 식별자가 예약어(STOP)이고 앞이 {@code .} 이 아니면 {@code "ORDER"} 꼴로(표 이름 · C-TBL-KEYWORD) */
    public static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        for (Tok t : scan(s)) {
            boolean keyword = t.quoted() && STOP.contains(t.s()) && (out.isEmpty() || !".".equals(out.get(out.size() - 1)));
            out.add(keyword ? DQUOTE + t.s() + DQUOTE : t.s());
        }
        return out;
    }

    public static boolean word(String x) {
        return !x.isEmpty() && wordChar(x.charAt(0));
    }

    public static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c) || c == UNDERSCORE || c == DOLLAR || c == HASH;
    }

    /** i 의 토큰 하나를 out 에 더하고(공백이면 안 더한다) 다음 위치를 돌려준다 */
    private static int next(String s, int i, List<Tok> out) {
        char c = s.charAt(i);
        if (Character.isWhitespace(c)) {
            return i + 1;
        }
        if (c == QUOTE) {
            out.add(new Tok(LITERAL, i));
            return literalEnd(s, i);
        }
        if (bindAt(s, i)) {
            int e = s.indexOf(RBRACE, i + 2);
            out.add(new Tok(BIND_MARK, i));
            return e < 0 ? s.length() : e + 1;
        }
        int lit = quotedLiteral(s, i);
        if (lit > 0) {
            out.add(new Tok(LITERAL, i));
            return lit;
        }
        if (c == DQUOTE || c == BACKTICK || c == LBRACKET && s.indexOf(RBRACKET, i + 1) > 0) {
            return quoted(s, i, c == LBRACKET ? RBRACKET : c, out);
        }
        if (wordChar(c)) {
            return wordAt(s, i, out);
        }
        out.add(new Tok(String.valueOf(c), i));
        return i + 1;
    }

    /** 낱말 하나 — MySQL # 줄 주석이면 줄 끝까지 건너뛰고(C-TBL-HASH), 이름에 붙은 ${}(TB_LOG_${ym})는 이름째 바인드 하나(C-TBL-DOLLAR) */
    private static int wordAt(String s, int i, List<Tok> out) {
        if (s.charAt(i) == HASH && hashComment(s, i)) {
            int e = s.indexOf('\n', i);
            return e < 0 ? s.length() : e;
        }
        int k = wordEnd(s, i);
        if (k < s.length() && bindAt(s, k)) {
            int e = s.indexOf(RBRACE, k + 2);
            out.add(new Tok(BIND_MARK, i));
            return e < 0 ? s.length() : e + 1;
        }
        out.add(new Tok(s.substring(i, k).toUpperCase(Locale.ROOT), i));
        return k;
    }

    /** 낱말 끝 — 낱말 글자가 이어지되 바인드({@code ${}}·{@code #{}}) 자리에서 멈춘다 */
    private static int wordEnd(String s, int i) {
        int k = i;
        while (k < s.length() && wordChar(s.charAt(k)) && !(k > i && bindAt(s, k))) {
            k++;
        }
        return k;
    }

    /** MySQL {@code #} 줄 주석인가 — 뒤가 공백·탭·줄 끝·글 끝이고 앞이 글 머리이거나 낱말 글자가 아님({@code #{x}}·{@code #TEMP}·{@code COL#}·{@code A#B} 는 아니다) */
    private static boolean hashComment(String s, int i) {
        boolean after = i + 1 >= s.length() || s.charAt(i + 1) == ' ' || s.charAt(i + 1) == '\t' || s.charAt(i + 1) == '\n' || s.charAt(i + 1) == '\r';
        return after && (i == 0 || !wordChar(s.charAt(i - 1)));
    }

    /** {@code #{}·${}} 의 시작인가 */
    private static boolean bindAt(String s, int i) {
        char c = s.charAt(i);
        return (c == HASH || c == DOLLAR) && i + 1 < s.length() && s.charAt(i + 1) == LBRACE;
    }

    /** PG $$…$$·$꼬리표$…$꼬리표$ · Oracle q'[…]' 의 끝 다음 위치. 그 꼴이 아니거나 안 닫히면 -1(G2-K10) */
    private static int quotedLiteral(String s, int i) {
        char c = s.charAt(i);
        if (c == DOLLAR) {
            return dollarEnd(s, i);
        }
        boolean q = (c == 'q' || c == 'Q') && i + 2 < s.length() && s.charAt(i + 1) == QUOTE && (i == 0 || !wordChar(s.charAt(i - 1)));
        return q ? qEnd(s, i + 2) : -1;
    }

    private static int dollarEnd(String s, int i) {
        int k = i + 1;
        while (k < s.length() && (Character.isLetterOrDigit(s.charAt(k)) || s.charAt(k) == UNDERSCORE)) {
            k++;
        }
        if (k >= s.length() || s.charAt(k) != DOLLAR) {
            return -1;
        }
        String tag = s.substring(i, k + 1);
        int e = s.indexOf(tag, k + 1);
        return e < 0 ? -1 : e + tag.length();
    }

    private static int qEnd(String s, int open) {
        char d = s.charAt(open);
        char close = d == '[' ? ']' : d == '{' ? '}' : d == '(' ? ')' : d == '<' ? '>' : d;
        int e = s.indexOf(String.valueOf(close) + QUOTE, open + 1);
        return e < 0 ? -1 : e + 2;
    }

    /** 홑따옴표 글자의 끝 다음 위치 — {@code ''} 는 글자 안. {@code \'} 는 뒤가 낱말 글자일 때만 글자 안(6-47) */
    private static int literalEnd(String s, int i) {
        int n = s.length();
        int k = i + 1;
        while (k < n && !(s.charAt(k) == QUOTE && (k + 1 >= n || s.charAt(k + 1) != QUOTE))) {
            k += s.charAt(k) == QUOTE || escapedQuote(s, k) ? 2 : 1;
        }
        return k + 1;
    }

    /** \' 바로 뒤가 낱말 글자면 벗어난 따옴표('it\'s' — MySQL·PG E''). 뒤가 공백·괄호·끝이면 표준 리터럴의 끝('%\' ESCAPE '\') */
    private static boolean escapedQuote(String s, int k) {
        return s.charAt(k) == '\\' && k + 2 < s.length() && s.charAt(k + 1) == QUOTE && wordChar(s.charAt(k + 2));
    }

    /** 따옴표·백틱·대괄호 식별자 — 따옴표·백틱의 겹친 닫는 글자({@code ""})는 글자 하나(6-36a). 끝 다음 위치를 돌려준다 */
    private static int quoted(String s, int i, char close, List<Tok> out) {
        StringBuilder sb = new StringBuilder();
        int n = s.length();
        int k = i + 1;
        while (k < n && !(s.charAt(k) == close && (close == RBRACKET || k + 1 >= n || s.charAt(k + 1) != close))) {
            sb.append(s.charAt(k));
            k += s.charAt(k) == close ? 2 : 1;
        }
        String id = sb.toString().trim();
        out.add(new Tok(id.isEmpty() ? String.valueOf(s.charAt(i)) : id.toUpperCase(Locale.ROOT), i, !id.isEmpty()));
        return k + 1;
    }
}
