package kr.ejg.toolbox.core.analyze;

import java.util.regex.Pattern;

/** 문장 id 꼴(6-40·6-42, 규칙 3) — 공백·연산자가 든 글은 SQL 본문이라 id 로 안 담는다 */
final class StmtId {
    private static final Pattern PLAIN_SHAPE = Pattern.compile("[\\p{L}\\p{N}_.$-]{1,300}");
    /** 곁 등록부 꼬리(JavaGraph 의 #jdbc·#em·#qdsl) */
    private static final Pattern ANY_SHAPE = Pattern.compile("[\\p{L}\\p{N}_.$-]{1,300}(?:#(?:jdbc|em|qdsl))?");

    private StmtId() {
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
