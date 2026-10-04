-- 스키마 단위 값(1-23) — 데이터 용량. 스키마마다 한 행, 모르면 size_bytes null. 옛 스냅샷은 행이 없다(용량 모름)
CREATE TABLE snap_schema (
  snapshot_id  BIGINT       NOT NULL,
  schema_name  VARCHAR(128) NOT NULL,
  size_bytes   BIGINT,
  PRIMARY KEY (snapshot_id, schema_name),
  FOREIGN KEY (snapshot_id) REFERENCES snapshot(id) ON DELETE CASCADE
);
