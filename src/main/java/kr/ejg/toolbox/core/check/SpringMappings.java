package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Spring 매핑 애너테이션 — 클래스 머리 경로 × 메서드 경로 + params. 코드 검사 dupMapping(5-2)·프로그램 분석(6-3) 공용 */
public final class SpringMappings {

    public static final Set<String> ANNOTATIONS = Set.of("RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping",
            "PatchMapping");

    /** verb — GetMapping 류 이름, RequestMapping 은 method= 값(없으면 ANY). params — 정렬·중복 없음(eGov `params = "!cmd"` · `"cmd=Regist"`) */
    public record Mapping(String verb, String url, List<String> params, int line) {

        public Mapping {
            params = List.copyOf(params);
        }

        /** 같은 매핑인지 가르는 열쇠 — 「방식 경로 [params]」 */
        public String key() {
            return verb + " " + url + (params.isEmpty() ? "" : " " + params);
        }
    }

    private SpringMappings() {
    }

    /** 메서드의 매핑 — 애너테이션 순서 × 클래스 머리 × 메서드 경로 */
    public static List<Mapping> of(ClassOrInterfaceDeclaration t, MethodDeclaration m) {
        List<String> prefixes = t.getAnnotationByName("RequestMapping").map(a -> values(a, "value")).orElse(List.of());
        if (prefixes.isEmpty()) {
            prefixes = List.of("");
        }
        List<Mapping> out = new ArrayList<>();
        for (AnnotationExpr a : m.getAnnotations()) {
            String an = a.getName().getIdentifier();
            if (!ANNOTATIONS.contains(an)) {
                continue;
            }
            String verb = an.equals("RequestMapping") ? verb(a) : an.replace("Mapping", "").toUpperCase(Locale.ROOT);
            List<String> paths = values(a, "value");
            List<String> params = new ArrayList<>(new TreeSet<>(values(a, "params")));
            for (String pre : prefixes) {
                for (String p : paths.isEmpty() ? List.of("") : paths) {
                    out.add(new Mapping(verb, join(pre, p), params, a.getBegin().map(x -> x.line).orElse(1)));
                }
            }
        }
        return out;
    }

    /** {@code @X("v")}·{@code @X(key = "v")}·{@code @X(key = {"a", "b"})} 의 문자열들. value 는 path 도 본다 */
    public static List<String> values(AnnotationExpr a, String key) {
        Expression e = null;
        if (a instanceof SingleMemberAnnotationExpr sm && key.equals("value")) {
            e = sm.getMemberValue();
        } else if (a instanceof NormalAnnotationExpr na) {
            for (var p : na.getPairs()) {
                if (p.getNameAsString().equals(key) || key.equals("value") && p.getNameAsString().equals("path")) {
                    e = p.getValue();
                }
            }
        }
        List<String> out = new ArrayList<>();
        if (e instanceof StringLiteralExpr sl) {
            out.add(sl.getValue());
        } else if (e instanceof ArrayInitializerExpr ai) {
            ai.getValues().forEach(x -> {
                if (x instanceof StringLiteralExpr sl2) {
                    out.add(sl2.getValue());
                }
            });
        }
        return out;
    }

    private static String verb(AnnotationExpr a) {
        if (a instanceof NormalAnnotationExpr na) {
            for (var p : na.getPairs()) {
                if (p.getNameAsString().equals("method")) {
                    Expression v = p.getValue();
                    if (v instanceof FieldAccessExpr fa) {
                        return fa.getNameAsString();
                    }
                    if (v instanceof NameExpr ne) {
                        return ne.getNameAsString();
                    }
                    return v.toString();
                }
            }
        }
        return "ANY";
    }

    private static String join(String a, String b) {
        String x = a.endsWith("/") ? a.substring(0, a.length() - 1) : a;
        if (b.isEmpty()) {
            return x.isEmpty() ? "/" : x;
        }
        return x + (b.startsWith("/") ? b : "/" + b);
    }
}
