-- sysindexes.status 비트 2048 = 선언적 PK 인덱스 (시스템테이블 레퍼런스 PDF sysindexes 장)
SELECT o.name AS 테이블
FROM   sysobjects o
WHERE  o.type = 'U'
  AND  NOT EXISTS (SELECT 1 FROM sysindexes i WHERE i.id = o.id AND i.status & 2048 = 2048)
ORDER  BY o.name;
-- PK 선언 없이 유니크 인덱스로만 운영하는 테이블도 잡힘 — 실질 키는 sp_helpindex APP.TB_ORDER 으로 확인