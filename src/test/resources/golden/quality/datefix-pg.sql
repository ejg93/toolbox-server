SELECT ORD_DT,
       CASE
         WHEN ORD_DT ~ '^[0-9]{4}-' THEN LEFT(REPLACE(ORD_DT, '-', ''), 8)
         WHEN ORD_DT ~ '^[0-9]{4}/' THEN LEFT(REPLACE(ORD_DT, '/', ''), 8)
         WHEN ORD_DT ~ '^[A-Za-z]'
              THEN TO_CHAR(TO_DATE(SUBSTRING(ORD_DT FROM '^[A-Za-z]+ +[0-9]+ +[0-9]+'),
                                   'Mon DD YYYY'), 'YYYYMMDD')
         WHEN ORD_DT ~ '^[0-9]{8}' THEN LEFT(ORD_DT, 8)
         ELSE NULL
       END AS 날짜_정규화
FROM   APP.TB_ORDER;
-- ⚠ TO_DATE가 못 읽는 값이면 에러로 중단 — 1번 진단으로 포맷 전수 확인 후 실행