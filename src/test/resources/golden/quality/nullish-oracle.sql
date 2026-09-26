-- Oracle는 '' = NULL — TRIM 결과가 NULL이면 공백뿐인 값
SELECT ORD_DT AS 값, COUNT(*) AS 건수
FROM   APP.TB_ORDER
WHERE  ORD_DT IS NULL
   OR  TRIM(ORD_DT) IS NULL
   OR  TRIM(ORD_DT) IN ('-', '.', '0', 'N/A', 'NA', 'NULL', '없음', '미상', '해당없음', '99999999', '99991231')
GROUP  BY ORD_DT
ORDER  BY 건수 DESC;
-- 발견된 대체값은 산출물 기입 전 통일 (실제 NULL 처리 방침은 발주처 협의)