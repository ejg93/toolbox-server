package kr.ejg.toolbox.core.analyze;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * 6-86(C2-04, 설계 77 §3-8) — 공통 DAO({@code selectList(String queryId, Object p) { return sqlSession.selectList(queryId, p); }})가 문장 id 를
 * 매개변수로 넘기는 꼴. {@link JavaGraph} 의 걷기가 부른 자리마다 쌓는 호출 스택으로 그 매개변수를 부른 쪽 인자까지 거슬러 읽는다.
 * 스택 층은 그 층이 부른 메서드가 지금 메서드와 같을 때만 쓴다(어긋난 층을 안 읽는다).
 * 방문 열쇠 꼬리는 String 매개변수 자리의 문장 id 리터럴(6-93b)
 */
final class StmtArgs {

    private static final String STRING = "String";

    /** 호출 한 층 — 부른 클래스·메서드, 넘긴 인자, 불린 메서드 */
    record Call(JavaGraph.Cls c, MethodDeclaration m, List<Expression> args, MethodDeclaration callee) {
    }

    /** 문장 id 식의 출처 — 그 식과 식이 놓인 클래스·메서드 */
    record At(JavaGraph.Cls c, MethodDeclaration m, Expression arg) {
    }

    private StmtArgs() {
    }

    /**
     * a 가 m 의 매개변수 이름이고 m 안·클래스에 그 이름의 리터럴이 없으며 스택의 그 층(불린 메서드가 m)이 그 자리 인자를 넘겼으면 한 층 올라가
     * 되풀이한다(스택 꼭대기부터 한 층씩). 아니면 그 자리
     */
    static At origin(JavaGraph.Cls c, MethodDeclaration m, Expression a, Deque<Call> calls) {
        return origin(c, m, a, calls.iterator());
    }

    private static At origin(JavaGraph.Cls c, MethodDeclaration m, Expression a, Iterator<Call> it) {
        JavaGraph.Cls cc = c;
        MethodDeclaration mm = m;
        Expression aa = a;
        while (aa instanceof NameExpr n && it.hasNext()) {
            int idx = param(mm, n.getNameAsString());
            if (idx < 0 || reassigned(mm, n.getNameAsString()) || !JavaGraph.varLiterals(cc, mm, n.getNameAsString()).isEmpty()) {
                break;
            }
            Call up = it.next();
            if (!Objects.equals(up.callee(), mm) || up.args().size() <= idx) {
                break;
            }
            cc = up.c();
            mm = up.m();
            aa = up.args().get(idx);
        }
        return new At(cc, mm, aa);
    }

    /** 그 매개변수가 메서드 안에서 대입 대상인가 — 값이 리터럴이 아니어도 부른 쪽 인자는 더는 그 값이 아니다(6-93b) */
    private static boolean reassigned(MethodDeclaration m, String name) {
        return m.findAll(AssignExpr.class).stream().anyMatch(as -> as.getTarget() instanceof NameExpr t && t.getNameAsString().equals(name));
    }

    /**
     * 방문 열쇠 꼬리(6-93b) — 꼭대기 층(callee 를 부른 자리)이 넘긴 인자 가운데 callee 의 String 매개변수 자리마다 그 인자가 닿는 문장 id 글.
     * 같은 감싸개를 문장마다 다시 걷게 하고, 문장 id 가 아닌 인자(위임 호출의 vo)로는 갈리지 않는다
     */
    static String key(JavaGraph.Cls c, MethodDeclaration callee, Deque<Call> calls) {
        if (calls.isEmpty() || !Objects.equals(calls.peek().callee(), callee)) {
            return "";
        }
        Call top = calls.peek();
        List<Parameter> ps = callee.getParameters();
        StringBuilder tail = new StringBuilder();
        for (int i = 0; i < ps.size() && i < top.args().size(); i++) {
            if (STRING.equals(ps.get(i).getType().asString())) {
                tail.append(idText(top, i, calls, forwards(callee, ps.get(i).getNameAsString())));
            }
        }
        return tail.toString();
    }

    /**
     * 꼭대기 층의 i 번째 인자가 닿는 문장 id 글 — 리터럴이면 값(문장 id 꼴일 때만), 지역 변수면 그 리터럴들. 못 닿으면: 그 매개변수를
     * 문장 실행 호출에 넘기는 감싸개(sink)일 때만 출처 자리(클래스#메서드:줄:칸) — 미해결이 부른 메서드마다 남는다. 아니면 빈 글(위임 호출은 한 번만 걷는다)
     */
    private static String idText(Call top, int i, Deque<Call> calls, boolean sink) {
        Iterator<Call> it = calls.iterator();
        it.next(); // 꼭대기 층은 top 자신 — 그 인자는 top.m() 안에 놓였다
        At at = origin(top.c(), top.m(), top.args().get(i), it);
        List<String> lits = at.arg() instanceof StringLiteralExpr sl ? List.of(sl.getValue())
                : at.arg() instanceof NameExpr n ? JavaGraph.varLiterals(at.c(), at.m(), n.getNameAsString()) : List.of();
        List<String> ids = lits.stream().filter(StmtId::plain).toList();
        if (!ids.isEmpty()) {
            return "|" + String.join(",", ids);
        }
        // 6-104a(리뷰 M1) — 출처 자리 + 인자 식 자리(줄:칸): 한 메서드의 둘째 호출도 따로 걷는다
        return sink ? "|@" + at.c().fqcn + "#" + at.m().getNameAsString() + at.arg().getBegin().map(p -> ":" + p.line + ":" + p.column).orElse("") : "";
    }

    /** callee 가 그 매개변수를 문장 실행 이름({@link JavaGraph#SINKS}) 호출의 첫 인자로 그대로 넘기나 */
    private static boolean forwards(MethodDeclaration m, String param) {
        return m.findAll(MethodCallExpr.class).stream().anyMatch(mc -> JavaGraph.SINKS.contains(mc.getNameAsString())
                && mc.getArguments().isNonEmpty() && mc.getArgument(0) instanceof NameExpr n && n.getNameAsString().equals(param));
    }

    private static int param(MethodDeclaration m, String name) {
        List<Parameter> ps = m.getParameters();
        for (int i = 0; i < ps.size(); i++) {
            if (ps.get(i).getNameAsString().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
