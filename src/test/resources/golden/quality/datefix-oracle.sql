SELECT ORD_DT,
       CASE
         -- ISO (2018-09-18 ...): '-' 제거 후 앞 8자리
         WHEN REGEXP_LIKE(ORD_DT, '^[0-9][0-9][0-9][0-9]-')
              THEN SUBSTR(REPLACE(ORD_DT, '-', ''), 1, 8)
         -- 슬래시 (2019/03/29 ...)
         WHEN REGEXP_LIKE(ORD_DT, '^[0-9][0-9][0-9][0-9]/')
              THEN SUBSTR(REPLACE(ORD_DT, '/', ''), 1, 8)
         -- 영문 (Mar 29 2019 ...): Sybase CONVERT(varchar, getdate()) 기본 출력 흔적
         WHEN REGEXP_LIKE(ORD_DT, '^[A-Za-z]')
              THEN TO_CHAR(
                     TO_DATE(REGEXP_SUBSTR(ORD_DT, '^[A-Za-z]+ +[0-9]+ +[0-9]+'),
                             'Mon DD YYYY', 'NLS_DATE_LANGUAGE=AMERICAN'),
                     'YYYYMMDD')
         -- 8자리 이상 숫자 (20190329 / 20190329143020)
         WHEN REGEXP_LIKE(ORD_DT, '^[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')
              THEN SUBSTR(ORD_DT, 1, 8)
         -- 미지 포맷은 NULL로 드러낸다 — ELSE로 원값 통과시키면 쓰레기가 은폐됨
         ELSE NULL
       END AS 날짜_정규화
FROM   APP.TB_ORDER;
-- Tibero 6 정규식 제약: (a|b) 교대·{n} 반복 금지 — JDBC-11042 (그래서 [0-9]를 풀어 씀)