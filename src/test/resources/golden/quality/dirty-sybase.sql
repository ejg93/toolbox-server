SELECT
  SUM(CASE WHEN ORD_DT <> ltrim(rtrim(ORD_DT)) THEN 1 ELSE 0 END) AS 앞뒤공백,
  SUM(CASE WHEN charindex('　', ORD_DT) > 0 THEN 1 ELSE 0 END)      AS 전각공백,
  SUM(CASE WHEN charindex(char(10), ORD_DT) > 0 OR charindex(char(13), ORD_DT) > 0
            OR charindex(char(9), ORD_DT) > 0 THEN 1 ELSE 0 END)    AS 개행_탭,
  SUM(CASE WHEN patindex('%[^0-9]%', ORD_DT) > 0 THEN 1 ELSE 0 END) AS 비숫자_혼입
FROM APP.TB_ORDER;