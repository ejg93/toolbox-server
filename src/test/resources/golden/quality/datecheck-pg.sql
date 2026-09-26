SELECT 원값, 날짜_정규화
FROM  (SELECT ORD_DT AS 원값,
              CASE
         WHEN ORD_DT ~ '^[0-9]{4}-' THEN LEFT(REPLACE(ORD_DT, '-', ''), 8)
         WHEN ORD_DT ~ '^[0-9]{4}/' THEN LEFT(REPLACE(ORD_DT, '/', ''), 8)
         WHEN ORD_DT ~ '^[A-Za-z]'
              THEN TO_CHAR(TO_DATE(SUBSTRING(ORD_DT FROM '^[A-Za-z]+ +[0-9]+ +[0-9]+'),
                                   'Mon DD YYYY'), 'YYYYMMDD')
         WHEN ORD_DT ~ '^[0-9]{8}' THEN LEFT(ORD_DT, 8)
         ELSE NULL
       END AS 날짜_정규화
       FROM   APP.TB_ORDER) x
WHERE  날짜_정규화 IS NULL
   OR  SUBSTR(날짜_정규화, 5, 2) NOT BETWEEN '01' AND '12'
   OR  SUBSTR(날짜_정규화, 7, 2) NOT BETWEEN '01' AND '31';