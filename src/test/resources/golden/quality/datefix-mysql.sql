SELECT ORD_DT,
       CASE
         WHEN ORD_DT REGEXP '^[0-9]{4}-'  THEN LEFT(REPLACE(ORD_DT, '-', ''), 8)
         WHEN ORD_DT REGEXP '^[0-9]{4}/'  THEN LEFT(REPLACE(ORD_DT, '/', ''), 8)
         WHEN ORD_DT REGEXP '^[A-Za-z]+ +[0-9]+ +[0-9]+'
              THEN DATE_FORMAT(STR_TO_DATE(ORD_DT, '%b %d %Y'), '%Y%m%d')
         WHEN ORD_DT REGEXP '^[0-9]{8}'   THEN LEFT(ORD_DT, 8)
         ELSE NULL
       END AS 날짜_정규화
FROM   APP.TB_ORDER;
-- MySQL 5.7·8.0 · MariaDB 공통(REGEXP 연산자만 쓴다). STR_TO_DATE 는 앞의 「월 일 년」만 읽고 뒤 시각은 버린다. 실패는 NULL(에러 아님) — 검증 쿼리에서 걸러짐