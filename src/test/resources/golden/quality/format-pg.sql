SELECT 포맷, COUNT(*) AS 건수, MIN(원값) AS 샘플1, MAX(원값) AS 샘플2
FROM  (SELECT ORD_DT AS 원값,
              TRANSLATE(ORD_DT,
                '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ',
                '9999999999aaaaaaaaaaaaaaaaaaaaaaaaaaAAAAAAAAAAAAAAAAAAAAAAAAAA') AS 포맷
       FROM   APP.TB_ORDER) x
GROUP  BY 포맷
ORDER  BY 건수 DESC;