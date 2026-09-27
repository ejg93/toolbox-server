package kr.go.x;

/**
 * 사용자 서비스 — 문서 주석은 지운다
 */
public class UserService {
    // 한 줄 주석
    private static final String URL = "http://example.local/a//b"; // 문자열 안 // 는 남는다
    private static final String BLOCK = "/* 문자열 안 블록 기호 */";
    private static final char Q = '\'';
    private static final String TEXT = """
        텍스트 블록 안 // 와 /* */ 는 남는다
        """;

    public int sum(int a, int b) {
        /* 블록
           여러 줄 */
        return a + b; /* 줄 끝 블록 */
    }
}
