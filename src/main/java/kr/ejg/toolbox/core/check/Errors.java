package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 예외 출처(설계 45 §3-6) — catch 매개변수·예외 타입 변수를 씨앗으로 한 번 더 돌려, 예외 내용(메시지·스택·예외 자체)이 응답에 닿으면
 * {@code security.exceptionMessage}. 요청 출처 실행과 섞지 않는다 — 표식 {@link #EXC_ON} 이 든 실행은 이 규칙 싱크만 본다
 */
final class Errors {

    static final String RULE = "security.exceptionMessage";
    /** 오염 집합 안의 표식 — 예외 출처 실행 */
    static final String EXC_ON = "\u0000exc";
    private static final Pattern WRITER = Pattern.compile(Taint.ANY_WRITER);
    /** 응답 writer 출력 · sendError 글 · 모델 속성 · ResponseEntity 본문 */
    static final List<Taint.Sink> SINKS = List.of(
            new Taint.Sink(Set.of("print", "println", "printf", "format", "write", "append"), -1, WRITER, null),
            new Taint.Sink(Set.of("sendError"), 1, null, null),
            new Taint.Sink(Set.of("addAttribute", "addObject", "put"), -1, Pattern.compile("(?i)^(model|mav|modelAndView|modelMap|.*model)$"),
                    Set.of("Model", "ModelMap", "ModelAndView", "ExtendedModelMap", "Map")),
            new Taint.Sink(Set.of("body"), -1, null, null),
            new Taint.Sink(Set.of("ok"), -1, Pattern.compile("^ResponseEntity$"), null),
            new Taint.Sink(Set.of("ResponseEntity"), 0, null, null));

    private Errors() {
    }

    /** 씨앗 — catch 매개변수 · 타입이 …Exception·Throwable·…Error 인 매개변수·지역 */
    static Set<String> seeds(Taint.Body b) {
        Set<String> out = new HashSet<>();
        for (CatchClause c : Taint.outsideLambda(b.m(), CatchClause.class)) {
            out.add(c.getParameter().getNameAsString());
        }
        b.types().forEach((n, ty) -> {
            if (ty.endsWith("Exception") || ty.endsWith("Error") || "Throwable".equals(ty)) {
                out.add(n);
            }
        });
        return out;
    }

    /** {@code e.printStackTrace(응답 writer)} — 스택이 응답에 */
    static boolean stackToWriter(MethodCallExpr c, Set<String> t, Set<String> writers, Taint.Body b) {
        if (!"printStackTrace".equals(c.getNameAsString()) || c.getArguments().isEmpty()
                || !c.getScope().map(s -> Taint.tainted(s, t)).orElse(false)) {
            return false;
        }
        Expression w = c.getArgument(0);
        return Writers.writerScope(w, writers, b) || Writers.fromResponse(w, writers);
    }
}
