package kr.ejg.toolbox.core.check;

/**
 * 검사 결과 한 건. {@code excerpt} 는 걸린 줄 앞 160자 — 응답에만 싣고 저장하지 않는다(절대 규칙 3, 이력은 파일·줄·규칙까지).
 * 파일 단위 규칙은 줄 1 에 원문 대신 사유를 싣는다.
 */
public record Finding(String file, int line, String group, String rule, String severity, String excerpt) {

    static final int EXCERPT = 160;

    static String excerpt(String line) {
        String t = line.strip();
        return t.length() > EXCERPT ? t.substring(0, EXCERPT) : t;
    }
}
