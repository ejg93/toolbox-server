SELECT
  SUM(ORD_DT <> TRIM(ORD_DT))                          AS 앞뒤공백,
  SUM(INSTR(ORD_DT, '　') > 0)                           AS 전각공백,
  SUM(INSTR(ORD_DT, CHAR(10)) > 0 OR INSTR(ORD_DT, CHAR(13)) > 0
      OR INSTR(ORD_DT, CHAR(9)) > 0)                     AS 개행_탭,
  SUM(ORD_DT REGEXP '[^0-9]')                            AS 비숫자_혼입,
  SUM(LENGTH(ORD_DT) <> CHAR_LENGTH(ORD_DT))            AS 한글_전각_포함
FROM APP.TB_ORDER;
-- LENGTH=바이트, CHAR_LENGTH=글자수 — 다르면 멀티바이트(한글·전각) 포함