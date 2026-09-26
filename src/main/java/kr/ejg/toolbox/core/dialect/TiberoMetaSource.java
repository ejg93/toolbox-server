package kr.ejg.toolbox.core.dialect;

import java.sql.Connection;

/**
 * Tibero — 딕셔너리 뷰 이름·열이 Oracle 과 같다(ALL_TAB_COMMENTS·ALL_TABLES·ALL_OBJECTS·ALL_CONSTRAINTS).
 * 컨테이너가 없어 테스트가 없다 — 현장·개발자판에서 수동 1회 뒤 골든 고정(PLAN 10장 M1).
 * 차이가 드러나면 여기서만 override 한다.
 */
public class TiberoMetaSource extends OracleMetaSource {

    public TiberoMetaSource(Connection conn) {
        super(conn);
    }
}
