-- ORD_DT 분포 — 정의서에 없는 값, 표기 혼재('Y'/'y'/'1') 발견용
SELECT ORD_DT AS 코드값, COUNT(*) AS 건수
FROM   APP.TB_ORDER
GROUP  BY ORD_DT
ORDER  BY 건수 DESC;

-- 공통코드 테이블과 대사 — 정의 안 된 코드값만 나옴
SELECT d.ORD_DT, COUNT(*) AS 건수
FROM   업무테이블 d
WHERE  NOT EXISTS (SELECT 1 FROM 공통코드테이블 c
                   WHERE  c.그룹코드 = '해당그룹' AND c.코드 = d.ORD_DT)
GROUP  BY d.ORD_DT
ORDER  BY 건수 DESC;