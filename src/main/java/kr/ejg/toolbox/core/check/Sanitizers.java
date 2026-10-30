package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 정화 범주(설계 44 §3-1·2) — 정화 호출은 오염 값을 그 범주로 씻는다. 싱크는 자기 범주(또는 숫자 변환)로 씻긴 값만 건너뛴다.
 * 범주는 오염 집합 안의 표식 원소 「이름 + \u0001 + 범주」 로 나른다({@link Taint#SRC_ON} 과 같은 수법 — 정적 판정 시그니처를 안 바꾼다)
 */
final class Sanitizers {

    /** 정화 — scope 가 null 이면 어느 스코프든(스코프의 끝 단순 이름 전체 일치) */
    record San(String scope, String name, String family) {
    }

    /** 숫자 변환 — 모든 싱크를 씻는다 */
    static final String NUM = "*";
    static final String XSS = "xss";
    static final String URL = "url";
    static final String SQL = "sql";
    static final String LDAP = "ldap";
    static final String XPATH = "xpath";
    static final String OS = "os";
    static final String PATH = "path";
    static final String SEP = "\u0001";
    /** 제자리 정화(x = 정화(x)) 표식 — 그 이름의 범주는 다른 대입이 줄이지 않는다 */
    static final String PIN = "\u0002";

    /** 규칙 id → 씻는 범주(없으면 숫자 변환만) */
    static final Map<String, String> ACCEPTS = Map.of("security.pathTraversal", PATH, "security.osCommand", OS,
            "security.sqlInjection", SQL, "security.xssServlet", XSS, "security.ldapInjection", LDAP, "security.xpathInjection",
            XPATH, "security.responseSplitting", URL);
    private static final Map<String, List<San>> BY_NAME = new HashMap<>();
    /** 숫자·불리언·날짜 값을 돌려주는 호출 — 인자·스코프가 오염이어도 결과는 깨끗(설계 45 §3-7) */
    private static final Set<String> NUMBER_RETURNS = Set.of("size", "length", "count", "indexOf", "lastIndexOf", "hashCode", "ordinal",
            "intValue", "longValue", "doubleValue", "floatValue", "shortValue", "byteValue", "getTime", "getTimeInMillis", "isEmpty", "isBlank",
            "equals", "equalsIgnoreCase", "contains", "startsWith", "endsWith", "matches", "compareTo", "compareToIgnoreCase", "before",
            "after"); // getYear·getDate 등 Date 게터 이름은 VO 의 글자 게터와 겹쳐 뺐다(마무리 리뷰)
    /** cal.get(…) — 달력 필드는 정수 */
    private static final Pattern CALENDAR = Pattern.compile("(?i)^(cal|calendar|.*calendar|gcal|gc)$");
    /**
     * sdf.format(…) — 날짜·숫자 형식기. String.format·MessageFormat·java.util.Formatter(formatter)·pdf… 는 아니다
     * (인자 글이 그대로 든다, 마무리 리뷰)
     */
    private static final Pattern FORMAT = Pattern.compile("(?i)^(?!(message|string|pdf))(sdf|df|.*date.*|.*time.*|.*number.*|.*decimal.*|format(?!ter).*|.*format)$");

    static {
        add(null, NUM, "parseInt", "parseLong", "parseDouble", "parseFloat", "parseBoolean", "parseShort", "parseByte");
        add(null, XSS, "encodeForHTML", "encodeForHTMLAttribute", "encodeForJavaScript", "encodeForXML", "encodeForXMLAttribute",
                "htmlEscape", "htmlEscapeHex", "escapeHtml", "escapeHtml4", "escapeHTML", "escapeXml", "escapeXml10", "escapeXml11",
                "escapeJava", "escapeEcmaScript", "escapeJson", "cleanXSS", "forHtml", "forHtmlAttribute", "forHtmlContent",
                "forJavaScript", "forXml", "forXmlAttribute", "forXmlContent");
        add(null, SQL, "encodeForSQL", "escapeSql");
        add(null, LDAP, "encodeForLDAP", "encodeForDN");
        add(null, XPATH, "encodeForXPath");
        add(null, OS, "encodeForOS");
        add(null, URL, "encodeForURL", "forUri", "forUriComponent");
        add("URLEncoder", URL, "encode");
        add(null, PATH, "getCanonicalPath", "normalize");
    }

    private Sanitizers() {
    }

    private static void add(String scope, String family, String... names) {
        for (String n : names) {
            BY_NAME.computeIfAbsent(n, k -> new ArrayList<>()).add(new San(scope, n, family));
        }
    }

    /** 정화 이름이 하나 이상인 범주 */
    static Set<String> registered() {
        Set<String> out = new HashSet<>();
        BY_NAME.values().forEach(l -> l.forEach(s -> out.add(s.family())));
        return out;
    }

    /** 숫자·불리언·날짜 값을 돌려주는 호출(설계 45 §3-7) — 이름 · cal.get · 형식기.format */
    static boolean numeric(MethodCallExpr c) {
        String n = c.getNameAsString();
        if (NUMBER_RETURNS.contains(n)) {
            return true;
        }
        String scope = c.getScope().map(Taint::lastName).orElse("");
        return "get".equals(n) && CALENDAR.matcher(scope).matches() || "format".equals(n) && FORMAT.matcher(scope).matches();
    }

    /** 정화 호출이면 그 범주 */
    static Optional<String> family(MethodCallExpr c) {
        List<San> l = BY_NAME.get(c.getNameAsString());
        if (l == null) {
            return Optional.empty();
        }
        String scope = c.getScope().map(Taint::lastName).orElse(null);
        return l.stream().filter(s -> s.scope() == null || s.scope().equals(scope)).map(San::family).findFirst();
    }

    /** 씻긴 범주가 이 규칙의 싱크를 씻나 */
    static boolean cleans(Set<String> families, String rule) {
        String f = ACCEPTS.get(rule);
        return families.contains(NUM) || f != null && families.contains(f);
    }

    /**
     * 식이 씻긴 범주(§3-2) — 정화 호출 → 그 범주 · 이름 → 표식 · {@code +}·조건식 → 오염인 쪽들의 교집합 · 괄호·형변환 → 안.
     * 그 밖은 없음(정화가 안 된 값)
     */
    static Set<String> families(Expression e, Set<String> t) {
        if (e.isEnclosedExpr()) {
            return families(e.asEnclosedExpr().getInner(), t);
        }
        if (e.isCastExpr()) {
            return families(e.asCastExpr().getExpression(), t);
        }
        if (e.isNameExpr()) {
            return of(e.asNameExpr().getNameAsString(), t);
        }
        if (e.isMethodCallExpr()) {
            return family(e.asMethodCallExpr()).map(f -> (Set<String>) new HashSet<>(Set.of(f))).orElseGet(HashSet::new);
        }
        if (e.isBinaryExpr() && e.asBinaryExpr().getOperator() == BinaryExpr.Operator.PLUS) {
            return meet(t, e.asBinaryExpr().getLeft(), e.asBinaryExpr().getRight());
        }
        if (e instanceof ConditionalExpr c) {
            return meet(t, c.getThenExpr(), c.getElseExpr());
        }
        return new HashSet<>();
    }

    /** 오염인 쪽들의 범주 교집합 — 오염 아닌 쪽은 안 센다 */
    private static Set<String> meet(Set<String> t, Expression... parts) {
        Set<String> out = null;
        for (Expression p : parts) {
            if (Taint.tainted(p, t)) {
                Set<String> f = families(p, t);
                if (out == null) {
                    out = f;
                } else {
                    out.retainAll(f);
                }
            }
        }
        return out == null ? new HashSet<>() : out;
    }

    private static Set<String> of(String name, Set<String> t) {
        Set<String> out = new HashSet<>();
        String p = name + SEP;
        for (String x : t) {
            if (x.startsWith(p)) {
                out.add(x.substring(p.length()));
            }
        }
        return out;
    }

    /** 오염이고 정화가 하나도 안 된 값 — 호출 인자·변경자·메서드 사이 요약은 이것만 오염으로 친다(정화 값은 예전처럼 깨끗) */
    static boolean reaches(Expression e, Set<String> t) {
        return Taint.tainted(e, t) && families(e, t).isEmpty();
    }

    /**
     * 대입 name = value(오염) 의 범주 표식 — 처음이면 값의 범주, 있으면 교집합(흐름 무관 — 한 번이라도 안 씻긴 대입이면 없음).
     * 제자리 정화 {@code x = 정화(x)} 는 그 범주로 고정한다(Juliet B2G·{@code param = htmlEscape(param)} 꼴 — 순서를 못 보는 대신)
     */
    static void mark(String name, Expression value, Set<String> t, boolean fresh) {
        Set<String> f = families(value, t);
        if (inPlace(name, value)) {
            t.removeIf(x -> x.startsWith(name + SEP));
            f.forEach(x -> t.add(name + SEP + x));
            t.add(name + PIN);
            return;
        }
        if (t.contains(name + PIN)) {
            return;
        }
        if (fresh) {
            f.forEach(x -> t.add(name + SEP + x));
        } else {
            t.removeIf(x -> x.startsWith(name + SEP) && !f.contains(x.substring(name.length() + SEP.length())));
        }
    }

    private static boolean inPlace(String name, Expression value) {
        Expression v = value;
        while (v.isEnclosedExpr() || v.isCastExpr()) {
            v = v.isEnclosedExpr() ? v.asEnclosedExpr().getInner() : v.asCastExpr().getExpression();
        }
        return v.isMethodCallExpr() && family(v.asMethodCallExpr()).isPresent()
                && v.asMethodCallExpr().getArguments().stream().anyMatch(a -> a.isNameExpr() && a.asNameExpr().getNameAsString().equals(name));
    }

    /** 메서드 안 필드 별칭 — 지역에 가리지 않은 필드 f 와 this.f 를 같은 이름으로(범주 표식째, 설계 44 §3-5) */
    static void alias(Taint.Body b, Set<String> t) {
        Set<String> bases = new HashSet<>();
        for (String x : t) {
            int cut = x.indexOf(SEP) >= 0 ? x.indexOf(SEP) : x.indexOf(PIN);
            String base = cut < 0 ? x : x.substring(0, cut);
            String bare = base.startsWith("this.") ? base.substring(5) : base;
            if (b.fields().contains(bare) && !b.locals().contains(bare)) {
                bases.add(bare);
            }
        }
        for (String f : bases) {
            pair(f, "this." + f, t);
        }
    }

    /** 별칭 둘 — 한쪽만 오염이면 그대로 옮기고, 둘 다면 범주는 교집합(흐름 무관 규칙을 별칭 사이에도, 마무리 리뷰) */
    private static void pair(String a, String b, Set<String> t) {
        boolean ta = t.contains(a);
        boolean tb = t.contains(b);
        Set<String> fa = of(a, t);
        Set<String> fb = of(b, t);
        Set<String> keep = ta && tb ? new HashSet<>(fa) : ta ? fa : fb;
        if (ta && tb) {
            keep.retainAll(fb);
        }
        boolean pin = t.contains(a + PIN) || t.contains(b + PIN);
        for (String n : List.of(a, b)) {
            t.add(n);
            t.removeIf(x -> x.startsWith(n + SEP));
            keep.forEach(f -> t.add(n + SEP + f));
            if (pin) {
                t.add(n + PIN);
            }
        }
    }
}
