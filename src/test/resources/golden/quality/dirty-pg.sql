SELECT
  COUNT(*) FILTER (WHERE ORD_DT <> TRIM(ORD_DT))       AS 앞뒤공백,
  COUNT(*) FILTER (WHERE POSITION('　' IN ORD_DT) > 0)   AS 전각공백,
  COUNT(*) FILTER (WHERE ORD_DT ~ '[\n\r\t]')            AS 개행_탭,
  COUNT(*) FILTER (WHERE ORD_DT ~ '[^0-9]')              AS 비숫자_혼입,
  COUNT(*) FILTER (WHERE OCTET_LENGTH(ORD_DT) <> LENGTH(ORD_DT)) AS 한글_전각_포함
FROM APP.TB_ORDER;