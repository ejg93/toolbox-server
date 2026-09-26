-- MySQL 8.0+ (5.7 이하엔 REGEXP_REPLACE 없음 — REPLACE 10중첩으로 숫자만 접기)
-- match_type 'c' 필수: 기본 collation(ci)에선 [a-z]가 대문자까지 잡는다
SELECT 포맷, COUNT(*) AS 건수, MIN(원값) AS 샘플1, MAX(원값) AS 샘플2
FROM  (SELECT ORD_DT AS 원값,
              REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(ORD_DT,
                '[0-9]', '9', 1, 0, 'c'), '[a-z]', 'a', 1, 0, 'c'), '[A-Z]', 'A', 1, 0, 'c') AS 포맷
       FROM   APP.TB_ORDER) x
GROUP  BY 포맷
ORDER  BY 건수 DESC;