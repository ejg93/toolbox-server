-- 인덱스 컬럼 정렬(1-20) — ASC·DESC·""(모름) JSON 배열, 컬럼 순서대로. 옛 행은 null(정렬 모름)
ALTER TABLE snap_index ADD COLUMN sorts VARCHAR(4000);
