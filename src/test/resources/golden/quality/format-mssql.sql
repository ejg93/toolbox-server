-- SQL Server 2017+ (TRANSLATE). 2016 이하: REPLACE 10중첩으로 숫자만 접기
-- COLLATE _BIN 필수: 기본 CI collation이면 대소문자 매핑이 섞인다
SELECT 포맷, COUNT(*) AS 건수, MIN(원값) AS 샘플1, MAX(원값) AS 샘플2
FROM  (SELECT ORD_DT AS 원값,
              TRANSLATE(ORD_DT COLLATE Latin1_General_BIN,
                '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ',
                '9999999999aaaaaaaaaaaaaaaaaaaaaaaaaaAAAAAAAAAAAAAAAAAAAAAAAAAA') AS 포맷
       FROM   APP.TB_ORDER) x
GROUP  BY 포맷
ORDER  BY 건수 DESC;