SELECT ORD_DT AS 값, COUNT(*) AS 건수
FROM   APP.TB_ORDER
WHERE  ORD_DT IS NULL
   OR  ltrim(rtrim(ORD_DT)) = ''
   OR  ltrim(rtrim(ORD_DT)) IN ('-', '.', '0', 'N/A', 'NA', 'NULL', '없음', '미상', '해당없음', '99999999', '99991231')
GROUP  BY ORD_DT
ORDER  BY 건수 DESC;