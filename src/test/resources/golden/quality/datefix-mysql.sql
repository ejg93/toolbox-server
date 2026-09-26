SELECT ORD_DT,
       CASE
         WHEN ORD_DT REGEXP '^[0-9]{4}-'  THEN LEFT(REPLACE(ORD_DT, '-', ''), 8)
         WHEN ORD_DT REGEXP '^[0-9]{4}/'  THEN LEFT(REPLACE(ORD_DT, '/', ''), 8)
         WHEN ORD_DT REGEXP '^[A-Za-z]'
              THEN DATE_FORMAT(STR_TO_DATE(REGEXP_SUBSTR(ORD_DT, '^[A-Za-z]+ +[0-9]+ +[0-9]+'),
                                           '%b %d %Y'), '%Y%m%d')
         WHEN ORD_DT REGEXP '^[0-9]{8}'   THEN LEFT(ORD_DT, 8)
         ELSE NULL
       END AS 날짜_정규화
FROM   APP.TB_ORDER;
-- REGEXP_SUBSTR는 8.0+. STR_TO_DATE 실패는 NULL 반환(에러 아님) — 검증 쿼리에서 걸러짐