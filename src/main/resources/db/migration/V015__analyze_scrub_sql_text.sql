-- 6-40(설계 69) — 문장 id 자리에 저장된 SQL 글을 지운다(절대 규칙 3). 문장 id 에는 공백이 없다
DELETE FROM analyze_stmt WHERE REGEXP_LIKE(ns_id, '\s');
DELETE FROM analyze_unresolved WHERE kind IN ('missing', 'prefix') AND REGEXP_LIKE(detail, '\s');
