-- 위 [정제] CASE를 서브쿼리에 붙여 실행
SELECT 원값, 날짜_정규화
FROM  (SELECT ORD_DT AS 원값,
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
       FROM   APP.TB_ORDER) x
WHERE  날짜_정규화 IS NULL
   OR  SUBSTR(날짜_정규화, 5, 2) NOT BETWEEN '01' AND '12'
   OR  SUBSTR(날짜_정규화, 7, 2) NOT BETWEEN '01' AND '31';
-- 건수 대사: 전체 = 정규화 성공 + 이 쿼리 건수
-- 윤년·월별 말일(0229, 0431)까지는 마지막에 TO_DATE(날짜_정규화, 'YYYYMMDD') 1회 실행으로 확인