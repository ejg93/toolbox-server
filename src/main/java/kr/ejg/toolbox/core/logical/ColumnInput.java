package kr.ejg.toolbox.core.logical;

/**
 * 변환기 입력 한 줄 — 순수본 컬럼목록 CSV 한 행을 읽은 값(3-2·3-3). 원문 그대로 두고 다듬기(trim·공백 제거)는
 * {@link LogicalRun} 이 순수본 run 과 같은 자리에서 한다.
 *
 * @param table   없으면(null) 「컬럼만」 모드 — 순수본 COLONLY
 * @param pk      Y·N, 모르면 빈 문자열(순수본 FKPKY 판정)
 * @param notNull Y(NOT NULL)·N, 모르면 빈 문자열(순수본 normNN)
 * @param ordinal 순번 원문. 순번 열이 없으면 행 번호
 */
public record ColumnInput(String owner, String table, String column, String dataType, String length, String scale,
        String pk, String notNull, String ordinal) {
}
