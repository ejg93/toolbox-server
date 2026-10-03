package kr.ejg.toolbox.core.check;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

/**
 * JavaParser 둘(17·8) — Java 17 로 못 읽으면 Java 8 로 한 번 더(`_` 식별자 등). 코드 검사(5-2)·프로그램 분석(6-3) 공용.
 * JavaParser 가 스레드 안전하지 않아 쓰는 쪽이 하나씩 만든다.
 */
public final class JavaSource {

    private final JavaParser java17 = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
    private final JavaParser java8 = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8));

    /** 읽었으면 그 결과, 둘 다 못 읽으면 Java 17 쪽 결과(문제 목록의 줄을 쓴다) */
    public ParseResult<CompilationUnit> parse(String text) {
        ParseResult<CompilationUnit> r = java17.parse(text);
        if (ok(r)) {
            return r;
        }
        ParseResult<CompilationUnit> r8 = java8.parse(text);
        return ok(r8) ? r8 : r;
    }

    public static boolean ok(ParseResult<CompilationUnit> r) {
        return r.isSuccessful() && r.getResult().isPresent();
    }
}
