-- CHECK 조건 글 길이(PR #42 리뷰) — PG·MSSQL 은 조건 길이에 상한이 없어 4000 을 넘기면 스냅샷 저장 전체가 롤백된다. scope(1-14)와 같은 길이로
ALTER TABLE snap_constraint ALTER COLUMN condition VARCHAR(1000000);
