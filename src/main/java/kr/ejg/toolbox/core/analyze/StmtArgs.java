package kr.ejg.toolbox.core.analyze;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
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
 * 스택 층은 그 층이 부른 메서드가 지금 메서드와 같을 때만 쓴다(어긋난 층을 안 읽는다)
 */
final class StmtArgs {

    /** 방문 열쇠 꼬리 글 상한 */
    private static final int KEY_MAX = 100;

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
        JavaGraph.Cls cc = c;
        MethodDeclaration mm = m;
        Expression aa = a;
        Iterator<Call> it = calls.iterator();
        while (aa instanceof NameExpr n && it.hasNext()) {
            int idx = param(mm, n.getNameAsString());
            if (idx < 0 || !JavaGraph.varLiterals(cc, mm, n.getNameAsString()).isEmpty()) {
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

    /**
     * 방문 열쇠 꼬리 — callee 가 자기 매개변수를 문장 실행 이름({@link JavaGraph#SINKS}) 호출의 첫 인자로 그대로 넘기면 그 매개변수의 출처 글
     * (문자열 리터럴이면 값, 아니면 식 글 앞 100자). 같은 감싸개를 문장마다 다시 걷게 한다. 아니면 빈 글
     */
    static String key(JavaGraph.Cls c, MethodDeclaration callee, Deque<Call> calls) {
        if (calls.isEmpty() || !Objects.equals(calls.peek().callee(), callee)) {
            return "";
        }
        String p = forwarded(callee);
        if (p == null) {
            return "";
        }
        Expression at = origin(c, callee, new NameExpr(p), calls).arg();
        String t = at instanceof StringLiteralExpr sl ? sl.getValue() : at.toString();
        return "|" + (t.length() > KEY_MAX ? t.substring(0, KEY_MAX) : t);
    }

    /** 문장 실행 이름 호출의 첫 인자로 그대로 넘기는 매개변수 이름, 없으면 null */
    private static String forwarded(MethodDeclaration m) {
        for (MethodCallExpr mc : m.findAll(MethodCallExpr.class)) {
            if (JavaGraph.SINKS.contains(mc.getNameAsString()) && mc.getArguments().isNonEmpty() && mc.getArgument(0) instanceof NameExpr n
                    && param(m, n.getNameAsString()) >= 0) {
                return n.getNameAsString();
            }
        }
        return null;
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
