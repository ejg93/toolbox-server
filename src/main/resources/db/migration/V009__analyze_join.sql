-- 매퍼 조인 등식(6-13) — 문장(ns.id)마다 서로 다른 두 표를 잇는 컬럼 등식. 표·컬럼 이름만(SQL 글 없음, 규칙 3). 추정 관계(2-19)의 입력
CREATE TABLE analyze_join (
  run_id   BIGINT       NOT NULL,
  ns_id    VARCHAR(300) NOT NULL,
  table_a  VARCHAR(200) NOT NULL,
  col_a    VARCHAR(200) NOT NULL,
  table_b  VARCHAR(200) NOT NULL,
  col_b    VARCHAR(200) NOT NULL,
  FOREIGN KEY (run_id) REFERENCES analyze_run(id) ON DELETE CASCADE
);
CREATE INDEX ix_analyze_join_run ON analyze_join(run_id);
