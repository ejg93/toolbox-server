-- ⚠ ASE엔 TRY_CONVERT 없음 — convert 실패 시 배치 중단.
--    반드시 1번 진단 쿼리로 포맷 전수 확인하고 WHEN을 맞춘 뒤 실행
SELECT ORD_DT,
       CASE
         WHEN ORD_DT LIKE '[0-9][0-9][0-9][0-9]-%'
              THEN substring(str_replace(ORD_DT, '-', NULL), 1, 8)
         WHEN ORD_DT LIKE '[A-Za-z]%'
              THEN convert(varchar(8), convert(date, substring(ORD_DT, 1, 11)), 112)
         WHEN ORD_DT LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]%'
              THEN substring(ORD_DT, 1, 8)
         ELSE NULL
       END AS 날짜_정규화
FROM   APP.TB_ORDER;
-- str_replace 세 번째 인자 NULL = 해당 문자 삭제. 언어 설정 us_english 전제