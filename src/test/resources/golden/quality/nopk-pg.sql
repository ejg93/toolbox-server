SELECT n.nspname AS 스키마, c.relname AS 테이블
FROM   pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid
WHERE  c.relkind IN ('r','p') AND NOT c.relispartition
  AND  n.nspname IN ('APP', 'APP2')
  AND  NOT EXISTS (SELECT 1 FROM pg_constraint x WHERE x.conrelid = c.oid AND x.contype = 'p')
ORDER  BY 1, 2;
-- PG 9.x: relispartition 없음 → 그 조건 삭제