package kr.ejg.toolbox.core.check;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 이름(또는 this.x)의 선언 형식 풀기 — ValueAst 에서 뗐다(5-64 ④, 메서드 30 상한). ValueAst·JavaDefects·ResourceAst·SecurityAst 가 쓴다 */
final class DeclaredTypes {
    static final String UNKNOWN = "";

    private DeclaredTypes() {
    }

    /** 이름(또는 this.x)의 선언 형식 — 람다·메서드 매개변수 → 둘러싼 블록·for·catch·try 자원 → 가장 가까운 클래스 필드. 못 찾으면 빈 글 */
    static String declaredType(Expression e) {
        return find(e).map(DeclaredTypes::scalarType).orElse(UNKNOWN);
    }

    /** 설계 60 — 선언 형식이 byte[](varargs byte... 도) */
    static boolean isByteArray(Expression e) {
        return find(e).filter(t -> "byte[]".equals(t.asString().replace(" ", ""))).isPresent();
    }

    /** 이름(또는 this.x)의 선언 Type 그대로 — declaredType 의 몸(설계 60 에서 뗐다) */
    private static Optional<Type> find(Expression e) {
        boolean thisField = e instanceof FieldAccessExpr f && f.getScope() instanceof ThisExpr;
        if (!(e instanceof NameExpr) && !thisField) {
            return Optional.empty();
        }
        String x = e instanceof NameExpr n ? n.getNameAsString() : ((FieldAccessExpr) e).getNameAsString();
        for (Node a = e.getParentNode().orElse(null); a != null; a = a.getParentNode().orElse(null)) {
            if (a instanceof TypeDeclaration<?> c) { // 5-61c — enum·record 도, record 는 구성요소부터
                Optional<Type> rp = a instanceof RecordDeclaration rd ? param(rd.getParameters(), x) : Optional.empty();
                return rp.or(() -> c.getFieldByName(x).flatMap(fd -> varType(fd.getVariables(), x)));
            }
            Optional<Type> t = thisField ? Optional.empty() : declIn(a, x);
            if (t.isPresent()) {
                return t;
            }
        }
        return Optional.empty();
    }

    private static Optional<Type> declIn(Node a, String x) {
        if (a instanceof LambdaExpr l) {
            return param(l.getParameters(), x);
        }
        if (a instanceof CallableDeclaration<?> c) {
            return param(c.getParameters(), x);
        }
        if (a instanceof CatchClause cc) {
            return param(List.of(cc.getParameter()), x);
        }
        if (a instanceof NodeWithStatements<?> b) {
            return locals(b.getStatements(), x);
        }
        if (a instanceof ForStmt f) {
            return declared(f.getInitialization(), x);
        }
        if (a instanceof ForEachStmt fe) {
            return varType(fe.getVariable().getVariables(), x);
        }
        if (a instanceof TryStmt t) {
            return declared(t.getResources(), x);
        }
        return Optional.empty();
    }

    private static Optional<Type> param(List<Parameter> ps, String x) {
        return ps.stream().filter(p -> p.getNameAsString().equals(x))
                .map(p -> p.isVarArgs() ? new ArrayType(p.getType()) : p.getType()).findFirst();
    }

    private static Optional<Type> locals(List<Statement> ss, String x) {
        List<Expression> decls = new ArrayList<>();
        for (Statement s : ss) {
            if (s instanceof ExpressionStmt es) {
                decls.add(es.getExpression());
            }
        }
        return declared(decls, x);
    }

    private static Optional<Type> declared(List<Expression> es, String x) {
        for (Expression e : es) {
            if (e instanceof VariableDeclarationExpr v) {
                Optional<Type> t = varType(v.getVariables(), x);
                if (t.isPresent()) {
                    return t;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Type> varType(List<VariableDeclarator> vs, String x) {
        return vs.stream().filter(v -> v.getNameAsString().equals(x)).map(VariableDeclarator::getType).findFirst();
    }

    /** 선언 형식 이름 — 배열·varargs 는 빈 글(배열 {@code ==} 는 동일성 비교가 맞다 — 마무리 리뷰) */
    private static String scalarType(Type t) {
        return t.isArrayType() ? UNKNOWN : simpleType(t);
    }

    /** 선언 글에서 {@code <…>}·{@code []} 를 떼고 마지막 {@code .} 뒤 */
    static String simpleType(Type t) {
        String s = t.asString();
        int lt = s.indexOf('<');
        if (lt >= 0) {
            s = s.substring(0, lt);
        }
        s = s.replace("[]", "").trim();
        return s.substring(s.lastIndexOf('.') + 1);
    }
}
