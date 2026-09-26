SELECT ORD_DT,
       CASE
         WHEN ORD_DT LIKE '[0-9][0-9][0-9][0-9]-%' THEN LEFT(REPLACE(ORD_DT, '-', ''), 8)
         WHEN ORD_DT LIKE '[0-9][0-9][0-9][0-9]/%' THEN LEFT(REPLACE(ORD_DT, '/', ''), 8)
         WHEN ORD_DT LIKE '[A-Za-z]%'
              THEN CONVERT(varchar(8), TRY_CONVERT(date, LEFT(ORD_DT, 11)), 112)
         WHEN ORD_DT LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%' THEN LEFT(ORD_DT, 8)
         ELSE NULL
       END AS 날짜_정규화
FROM   APP.TB_ORDER;
-- 'Mar 29 2019' 해석은 언어 설정 의존 — SET LANGUAGE us_english 후 실행
-- TRY_CONVERT(2012+) 실패는 NULL 반환 → 검증 쿼리에서 걸러짐