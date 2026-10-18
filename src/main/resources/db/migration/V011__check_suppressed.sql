-- 5-29 억제 건수 — 억제된 지적은 check_finding 에 없다. 이유 글은 저장하지 않는다(규칙 3)
ALTER TABLE check_run ADD COLUMN suppressed INT DEFAULT 0 NOT NULL;
