-- V017 — F-K10 긴 값 하나로 스냅샷 저장이 통째 실패하지 않게(scope 와 같은 길이)
ALTER TABLE snapshot ALTER COLUMN schemas VARCHAR(1000000);
ALTER TABLE snap_table ALTER COLUMN comment VARCHAR(1000000);
ALTER TABLE snap_column ALTER COLUMN comment VARCHAR(1000000);
ALTER TABLE snap_column ALTER COLUMN default_value VARCHAR(1000000);
