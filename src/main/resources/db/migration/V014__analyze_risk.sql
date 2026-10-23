-- 6-33 ${} 든 매퍼 문장 — 식별자·${} 이름·닿는 프로그램. 문장 하나에 프로그램마다 한 행, 아무도 안 부르면 program_id NULL 한 행.
-- SQL 글 없음(규칙 3). names 는 ${} 안 이름을 쉼표로(300자 컷)
CREATE TABLE analyze_risk (
  run_id      BIGINT        NOT NULL,
  ns_id       VARCHAR(300)  NOT NULL,
  file        VARCHAR(1000),
  line        INT,
  names       VARCHAR(300)  NOT NULL,
  program_id  BIGINT,
  FOREIGN KEY (run_id) REFERENCES analyze_run(id) ON DELETE CASCADE
);
CREATE INDEX ix_analyze_risk_run ON analyze_risk(run_id);
