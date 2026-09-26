SELECT
  COUNT(CASE WHEN ORD_DT <> TRIM(ORD_DT) THEN 1 END)  AS 앞뒤공백,
  COUNT(CASE WHEN INSTR(ORD_DT, '　') > 0 THEN 1 END)   AS 전각공백,
  COUNT(CASE WHEN INSTR(ORD_DT, CHR(10)) > 0 OR INSTR(ORD_DT, CHR(13)) > 0
              OR INSTR(ORD_DT, CHR(9)) > 0 THEN 1 END)  AS 개행_탭,
  COUNT(CASE WHEN TRANSLATE(ORD_DT, 'x0123456789', 'x') IS NOT NULL THEN 1 END) AS 비숫자_혼입,
  COUNT(CASE WHEN LENGTHB(ORD_DT) <> LENGTH(ORD_DT) THEN 1 END) AS 한글_전각_포함
FROM APP.TB_ORDER;
-- 비숫자_혼입: 숫자만 들어야 하는 컬럼(금액·번호)에서만 의미
-- 한글_전각_포함: 영숫자 컬럼(코드·ID)에서만 의미 — 한글 텍스트 컬럼이면 무시
-- 개행_탭 있는 컬럼은 CSV 추출 시 행 깨짐 — 산출물 뽑기 전 REPLACE 필요