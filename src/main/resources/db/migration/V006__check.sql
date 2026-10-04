-- CHECK 제약(1-21) — snap_constraint.kind 'CK' 행의 조건 글(딕셔너리 원문). 다른 kind 는 null
ALTER TABLE snap_constraint ADD COLUMN condition VARCHAR(4000);
