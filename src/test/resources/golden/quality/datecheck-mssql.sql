SELECT 원값, 날짜_정규화
FROM  (SELECT ORD_DT AS 원값,
              CASE
         WHEN ORD_DT LIKE '[0-9][0-9][0-9][0-9]-%' THEN LEFT(REPLACE(ORD_DT, '-', ''), 8)
         WHEN ORD_DT LIKE '[0-9][0-9][0-9][0-9]/%' THEN LEFT(REPLACE(ORD_DT, '/', ''), 8)
         WHEN ORD_DT LIKE '[A-Za-z]%'
              THEN CONVERT(varchar(8), TRY_CONVERT(date, LEFT(ORD_DT, 11)), 112)
         WHEN ORD_DT LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%' THEN LEFT(ORD_DT, 8)
         ELSE NULL
       END AS 날짜_정규화
       FROM   APP.TB_ORDER) x
WHERE  날짜_정규화 IS NULL
   OR  SUBSTRING(날짜_정규화, 5, 2) NOT BETWEEN '01' AND '12'
   OR  SUBSTRING(날짜_정규화, 7, 2) NOT BETWEEN '01' AND '31';
-- 윤년·말일까지: TRY_CONVERT(date, 날짜_정규화, 112) IS NULL 조건 추가