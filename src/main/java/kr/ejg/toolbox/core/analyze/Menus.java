package kr.ejg.toolbox.core.analyze;

/**
 * 메뉴·화면 목록 CSV(6-29) — 경로 한 열(「대 > 중 > 소」, 깊이 제한 없음)과 URL. 트리 재귀는 사용자가 뽑는 SQL 쪽, 행 순서가 트리 순서.
 * 메뉴 이름·URL 만(규칙 3). 파서는 6-29a
 */
public final class Menus {

    /** seq — CSV 데이터 행 번호(1부터, 트리 순서). path 는 「대 > 중 > 소」, name 은 마지막 마디. url 없으면 null(중간 메뉴) */
    public record Row(int seq, String path, String name, String url, String screenId, String useYn, String auth) {
    }

    private Menus() {
    }
}
