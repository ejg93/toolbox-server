package kr.ejg.toolbox.core.gen;

/** DdlReader 도우미 — DdlReader 가 메서드 상한(30)이라 옮겼다(1-106) */
final class DdlReaderParts {

    private DdlReaderParts() {
    }

    /** 1-70 — 찾은 PK 이름(따옴표 벗김), 없으면 지금 이름 그대로 */
    static String pkNameOf(String found, String current) {
        return found == null ? current : DdlReader.unquote(found);
    }
}
