package kr.ejg.toolbox.core.analyze;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 컨트롤러 메서드의 뷰 이름 식·종류·설명(6-x) — JavaGraph 가 길이 상한에 닿아 따로 둔다(0-95) */
final class JavaViews {

    /** 0-105 — out 인자 상자 하나(뷰 이름이 식으로 정해졌나). varargs 대신 — 인자 없이 부르면 AIOOBE 가 났다 */
    static final class Flag {
        boolean on;
    }

    private static final Pattern TAG = Pattern.compile("<[^>]{0,200}>");
    private static final int DESCR_MAX = 100;

    private JavaViews() {
    }

    /**
     * json — {@code @RestController}·{@code @ResponseBody}·리턴이 String·ModelAndView 가 아님(VO·void·Map …)·뷰가 {@code jsonView} 뿐.
     * 그 밖은 view — 뷰 이름을 못 풀어도(helper 메서드 리턴 등) 화면 프로그램이다
     */
    static String kind(JavaGraph.Cls c, MethodDeclaration m, List<JavaGraph.View> views) {
        boolean rest = c.decl.isAnnotationPresent("RestController") || m.isAnnotationPresent("ResponseBody");
        String rt = simple(m.getType().asString()); // 6-92 C2-14 — org.springframework.web.servlet.ModelAndView 도
        boolean page = "String".equals(rt) || "ModelAndView".equals(rt);
        if (rest || !page) {
            return "json";
        }
        boolean onlyJson = views.isEmpty() && m.findAll(StringLiteralExpr.class).stream().anyMatch(s -> "jsonView".equals(s.getValue()));
        return onlyJson ? "json" : "view";
    }

    /** 형식 글의 마지막 {@code .} 뒤 단순 이름 */
    private static String simple(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }

    /** 6-92 C2-15 — redirect:·forward: 뒤가 비면 이름이 아니다(뒤 식은 dynamic 이 받는다) */
    private static void addNamed(Set<String> out, String name) {
        if (named(name)) {
            out.add(name);
        }
    }

    /** 6-97(C2-12) — redirect:·forward: 를 뗀 주소(질의 앞)가 비지 않았나. 접두가 없으면 참 */
    static boolean named(String view) {
        String rest = view.startsWith("redirect:") ? view.substring("redirect:".length())
                : view.startsWith("forward:") ? view.substring("forward:".length()) : null;
        return rest == null || !url(rest).isEmpty();
    }

    static String url(String s) {
        int q = s.indexOf('?');
        return (q < 0 ? s : s.substring(0, q)).trim();
    }

    /** 뷰 이름 식 — 리터럴, 리터럴을 받은 지역 변수(대입 전부), 이어붙임은 앞쪽 리터럴을 쓰고 나머지가 식이면 dynamic */
    static void value(MethodDeclaration m, Expression e, Set<String> out, Flag dynamic) {
        if (e instanceof StringLiteralExpr sl) {
            addNamed(out, sl.getValue()); // 6-97 — 리터럴 가지도 이름 빈 접두를 거른다
        } else if (e instanceof NameExpr n) {
            boolean any = false;
            for (VariableDeclarator v : m.findAll(VariableDeclarator.class)) {
                if (v.getNameAsString().equals(n.getNameAsString()) && v.getInitializer().isPresent()) {
                    any |= assigned(m, n.getNameAsString(), v.getInitializer().get(), out, dynamic);
                }
            }
            for (AssignExpr as : m.findAll(AssignExpr.class)) {
                if (as.getTarget() instanceof NameExpr t && t.getNameAsString().equals(n.getNameAsString())) {
                    any |= assigned(m, n.getNameAsString(), as.getValue(), out, dynamic);
                }
            }
            if (!any) {
                dynamic.on = true;
            }
        } else if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            List<Expression> parts = new ArrayList<>();
            flatten(b, parts);
            StringBuilder lead = new StringBuilder();
            int i = 0;
            while (i < parts.size() && parts.get(i) instanceof StringLiteralExpr sl) {
                lead.append(sl.getValue());
                i++;
            }
            if (i == 0) {
                value(m, parts.get(0), out, dynamic);
                i = 1;
            } else {
                addNamed(out, lead.toString());
            }
            if (i < parts.size()) {
                dynamic.on = true;
            }
        } else if (!e.isNullLiteralExpr() && !(e instanceof ObjectCreationExpr)) {
            dynamic.on = true;
        }
    }

    /** 변수에 들어간 값 하나 — 자기 자신에 덧붙이기(x = x + "…")는 dynamic 만. 리터럴을 하나라도 얻었으면 true */
    private static boolean assigned(MethodDeclaration m, String var, Expression v, Set<String> out, Flag dynamic) {
        if (v.isNullLiteralExpr()) {
            return false;
        }
        if (v instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            List<Expression> parts = new ArrayList<>();
            flatten(b, parts);
            if (parts.get(0) instanceof NameExpr n && n.getNameAsString().equals(var)) {
                dynamic.on = true;
                return false;
            }
        }
        if (v instanceof NameExpr n && n.getNameAsString().equals(var)) {
            return false;
        }
        int before = out.size();
        value(m, v, out, dynamic);
        return out.size() > before || v instanceof StringLiteralExpr;
    }

    private static void flatten(Expression e, List<Expression> out) {
        if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            flatten(b.getLeft(), out);
            flatten(b.getRight(), out);
        } else {
            out.add(e);
        }
    }

    /** javadoc 설명 첫 문장(마침표·「。」+공백, 또는 줄 끝까지) 100자. 없으면 블록 주석, 그것도 없으면 빈 칸 */
    static String description(MethodDeclaration m) {
        String d = m.getJavadoc().map(j -> j.getDescription().toText()).orElse(null);
        if (d == null) {
            d = m.getComment().filter(Comment::isBlockComment).map(Comment::getContent).map(x -> x.replaceAll("(?m)^\\s*\\*", ""))
                    .orElse("");
        }
        d = TAG.matcher(d).replaceAll(" ").strip();
        int nl = d.indexOf('\n');
        if (nl >= 0) {
            d = d.substring(0, nl).strip();
        }
        for (int i = 0; i < d.length(); i++) {
            char ch = d.charAt(i);
            if ((ch == '.' || ch == '。') && (i + 1 == d.length() || Character.isWhitespace(d.charAt(i + 1)))) {
                d = d.substring(0, i + 1);
                break;
            }
        }
        d = d.replaceAll("\\s+", " ").strip();
        return d.length() > DESCR_MAX ? d.substring(0, DESCR_MAX) : d;
    }
}
