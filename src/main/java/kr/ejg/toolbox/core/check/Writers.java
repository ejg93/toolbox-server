package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.Set;
import java.util.regex.Pattern;

/** 응답 writer 판정(설계 44 §3-3) — 응답의 getWriter()·getOutputStream(), 그것으로 만든 변수, new 로 만들지 않은 writer 이름·타입, JSON 응답 */
final class Writers {

    /** 처음부터 응답 writer 인 매개변수 타입 */
    static final Set<String> PARAMS = Set.of("JspWriter", "ServletOutputStream");
    /** 응답 writer 로 치는 선언 타입 — new 로 만든 지역은 빼고(파일·나가는 연결, 설계 44 §3-3) */
    private static final Set<String> TYPES = Set.of("PrintWriter", "JspWriter", "ServletOutputStream", "Writer");
    /** JSON 응답 표시 — 응답 형식 상수 이름 */
    private static final Set<String> JSON_NAMES = Set.of("APPLICATION_JSON", "APPLICATION_JSON_VALUE", "APPLICATION_JSON_UTF8_VALUE");
    private static final Set<String> CONTENT_TYPE = Set.of("setContentType", "setHeader", "addHeader");
    /** 응답 객체로 치는 이름 */
    private static final Pattern RESPONSE = Pattern.compile("(?i)^(res|rsp|resp|.*resp|response|.*response|getResponse)$");
    /** 응답 writer 로 치는 이름 — os·outputStream 은 파일 스트림과 갈리지 않아 뺐다(마무리 리뷰) */
    private static final Pattern NAME = Pattern.compile("(?i)^(out|writer|pw|printWriter)$");

    private Writers() {
    }

    /** 응답의 getWriter()·getOutputStream() — 스코프가 응답 이름일 때만(conn·socket·process 의 스트림은 아니다, 설계 44 §3-3) */
    static boolean writerCall(Expression e) {
        return e.isMethodCallExpr() && ("getWriter".equals(e.asMethodCallExpr().getNameAsString())
                || "getOutputStream".equals(e.asMethodCallExpr().getNameAsString()))
                && e.asMethodCallExpr().getScope().map(x -> RESPONSE.matcher(Taint.lastName(x)).matches()).orElse(true);
    }

    /** 응답 writer — getWriter()·getOutputStream() 호출, 그것으로 만든 변수, new 로 만들지 않은 writer 이름·타입(설계 44 §3-3) */
    static boolean writerScope(Expression x, Set<String> writers, Taint.Body b) {
        if (writerCall(x)) {
            return true;
        }
        if (!x.isNameExpr()) {
            return false;
        }
        String n = x.asNameExpr().getNameAsString();
        return writers.contains(n)
                || !b.fresh().contains(n) && (NAME.matcher(n).matches() || TYPES.contains(b.types().getOrDefault(n, "")));
    }

    /** 값이 응답 writer 에서 왔나 — 응답 호출 · writer 변수 · 그것을 (겹겹이) 감싼 new(마무리 리뷰: 중첩 래퍼) */
    static boolean fromResponse(Expression e, Set<String> writers) {
        Expression v = Taint.unwrap(e);
        if (writerCall(v) || v.isNameExpr() && writers.contains(v.asNameExpr().getNameAsString())) {
            return true;
        }
        return v.isObjectCreationExpr() && v.asObjectCreationExpr().getArguments().stream().anyMatch(a -> fromResponse(a, writers));
    }

    /** 응답 형식을 JSON 으로 — setContentType·setHeader·addHeader 의 글자 상수에 json, 또는 APPLICATION_JSON… 이름(설계 44 §3-3) */
    static boolean jsonType(MethodCallExpr c) {
        boolean response = c.getScope().map(x -> RESPONSE.matcher(Taint.lastName(x)).matches()).orElse(true); // 나가는 요청의 헤더는 아니다(마무리 리뷰)
        return response && CONTENT_TYPE.contains(c.getNameAsString()) && c.getArguments().stream().anyMatch(a -> a.isStringLiteralExpr()
                && a.asStringLiteralExpr().getValue().toLowerCase(java.util.Locale.ROOT).contains("json")
                || JSON_NAMES.contains(Taint.lastName(a)));
    }
}
