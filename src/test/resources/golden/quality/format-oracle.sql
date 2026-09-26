-- 어떤 포맷이 몇 건 섞였는지 전수 확인 — 정제 CASE는 이 결과를 보고 짠다 (짐작 금지)
SELECT 포맷, COUNT(*) AS 건수, MIN(원값) AS 샘플1, MAX(원값) AS 샘플2
FROM  (SELECT ORD_DT AS 원값,
              TRANSLATE(ORD_DT,
                '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ',
                '9999999999aaaaaaaaaaaaaaaaaaaaaaaaaaAAAAAAAAAAAAAAAAAAAAAAAAAA') AS 포맷
       FROM   APP.TB_ORDER) x
GROUP  BY 포맷
ORDER  BY 건수 DESC;
-- 결과 예: '9999-99-99 99:' 1200건 / 'Aaa 99 9999  9' 300건 / '99999999999999' 50000건