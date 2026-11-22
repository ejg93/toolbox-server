package kr.ejg.toolbox.core.analyze;

import java.util.regex.Pattern;

/** 문장 id 꼴(6-40·6-42, 규칙 3) — 공백·연산자가 든 글은 SQL 본문이라 id 로 안 담는다 */
final class StmtId {
    private static final Pattern PLAIN_SHAPE = Pattern.compile("[\\p{L}\\p{N}_.$-]{1,300}");
    /** 곁 등록부 꼬리(JavaGraph 의 #jdbc·#em·#qdsl) — 오버로드면 앞에 /인자 수(6-91) */
    private static final Pattern ANY_SHAPE = Pattern.compile("[\\p{L}\\p{N}_.$-]{1,300}(?:/\\d{1,3}#(?:jdbc|em|qdsl)|#(?:jdbc|em|qdsl))?");

    private StmtId() {
    }

    /**
     * 6-91(C2-11) — 곁 등록부 id. 그 메서드를 선언한 형식에 같은 이름 메서드가 둘 넘으면 {@code fqcn.이름/인자 수 + tail}(오버로드의 표가 한 id 에
     * 안 합쳐지게), 아니면 {@code fqcn.이름 + tail}
     */
    static String side(String fqcn, com.github.javaparser.ast.body.MethodDeclaration m, String tail) {
        String name = m.getNameAsString();
        boolean overloaded = m.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class)
                .map(t -> t.getMethodsByName(name).size() > 1).orElse(false);
        return fqcn + "." + name + (overloaded ? "/" + m.getParameters().size() : "") + tail;
    }

    static boolean plain(String s) {
        return s != null && PLAIN_SHAPE.matcher(s).matches();
    }

    static boolean ok(String s) {
        return s != null && ANY_SHAPE.matcher(s).matches();
    }

    /** 글은 싣지 않는다(규칙 3) */
    static void require(String s) {
        if (!ok(s)) {
            throw new IllegalArgumentException("문장 id 꼴이 아니다");
        }
    }
}
