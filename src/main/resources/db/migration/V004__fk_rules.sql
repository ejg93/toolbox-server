-- FK 삭제·갱신 규칙(1-19). CASCADE · SET NULL · SET DEFAULT · RESTRICT · NO ACTION, 모르면 null(옛 행·DDL 읽기·드라이버가 안 줄 때)
ALTER TABLE snap_constraint ADD COLUMN delete_rule VARCHAR(20);
ALTER TABLE snap_constraint ADD COLUMN update_rule VARCHAR(20);
