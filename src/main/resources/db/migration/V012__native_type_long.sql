-- 4-25b ENUM/SET 나열 목록이 native_type 에 실린다(MariaDB·MySQL COLUMN_TYPE) — 128 을 넘으면 스냅샷 저장 전체가 롤백된다(V008 과 같은 까닭)
ALTER TABLE snap_column ALTER COLUMN native_type VARCHAR(4000);
