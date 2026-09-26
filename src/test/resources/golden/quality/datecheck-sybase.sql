SELECT 원값, 날짜_정규화
FROM  (SELECT ORD_DT AS 원값,
              CASE
         WHEN ORD_DT LIKE '[0-9][0-9][0-9][0-9]-%'
              THEN substring(str_replace(ORD_DT, '-', NULL), 1, 8)
         WHEN ORD_DT LIKE '[A-Za-z]%'
              THEN convert(varchar(8), convert(date, substring(ORD_DT, 1, 11)), 112)
         WHEN ORD_DT LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%'
              THEN substring(ORD_DT, 1, 8)
         ELSE NULL
       END AS 날짜_정규화
       FROM   APP.TB_ORDER) x
WHERE  날짜_정규화 IS NULL
   OR  substring(날짜_정규화, 5, 2) NOT BETWEEN '01' AND '12'
   OR  substring(날짜_정규화, 7, 2) NOT BETWEEN '01' AND '31';