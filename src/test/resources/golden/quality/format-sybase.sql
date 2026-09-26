-- TRANSLATE 없음 — 숫자만 9로 접는다. 영문 월('Mar' 등)은 그대로 남지만 그룹 수가 적어 충분
SELECT 포맷, COUNT(*) AS 건수, MIN(원값) AS 샘플1
FROM  (SELECT ORD_DT AS 원값,
              str_replace(str_replace(str_replace(str_replace(str_replace(
              str_replace(str_replace(str_replace(str_replace(ORD_DT,
                '0','9'),'1','9'),'2','9'),'3','9'),'4','9'),'5','9'),'6','9'),'7','9'),'8','9') AS 포맷
       FROM   APP.TB_ORDER) x
GROUP  BY 포맷
ORDER  BY 건수 DESC;