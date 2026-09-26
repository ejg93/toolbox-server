SELECT s.name AS 스키마, t.name AS 테이블
FROM   sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
WHERE  s.name IN ('APP', 'APP2')
  AND  NOT EXISTS (SELECT 1 FROM sys.key_constraints kc
                   WHERE  kc.parent_object_id = t.object_id AND kc.type = 'PK')
ORDER  BY 1, 2;