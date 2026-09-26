-- V001 — 사전·스냅샷·실행 이력. 코드 본문·SQL 결과·비밀번호 컬럼을 두지 않는다(절대 규칙 2·3)

-- 표준단어 사전
CREATE TABLE dict_word (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  kind        VARCHAR(20)  NOT NULL,
  word_ko     VARCHAR(200) NOT NULL,
  abbr        VARCHAR(100),
  word_en     VARCHAR(200),
  domain      VARCHAR(100),
  source      VARCHAR(50),
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX ix_dict_word_ko ON dict_word(word_ko);
CREATE INDEX ix_dict_word_abbr ON dict_word(abbr);

-- 메타모델 스냅샷
CREATE TABLE snapshot (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  profile   VARCHAR(100) NOT NULL,
  conn_id   VARCHAR(100) NOT NULL,
  taken_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  note      VARCHAR(1000)
);

CREATE TABLE snap_table (
  snapshot_id  BIGINT       NOT NULL,
  schema_name  VARCHAR(128) NOT NULL,
  table_name   VARCHAR(128) NOT NULL,
  table_type   VARCHAR(20),
  comment      VARCHAR(4000),
  row_count    BIGINT,
  created_at   TIMESTAMP,
  PRIMARY KEY (snapshot_id, schema_name, table_name),
  FOREIGN KEY (snapshot_id) REFERENCES snapshot(id) ON DELETE CASCADE
);

CREATE TABLE snap_column (
  snapshot_id    BIGINT       NOT NULL,
  schema_name    VARCHAR(128) NOT NULL,
  table_name     VARCHAR(128) NOT NULL,
  name           VARCHAR(128) NOT NULL,
  ordinal        INT          NOT NULL,
  native_type    VARCHAR(128),
  jdbc_type      INT,
  length         BIGINT,
  precision      INT,
  scale          INT,
  nullable       BOOLEAN,
  default_value  VARCHAR(4000),
  comment        VARCHAR(4000),
  PRIMARY KEY (snapshot_id, schema_name, table_name, name),
  FOREIGN KEY (snapshot_id) REFERENCES snapshot(id) ON DELETE CASCADE
);

CREATE TABLE snap_constraint (
  snapshot_id  BIGINT       NOT NULL,
  schema_name  VARCHAR(128) NOT NULL,
  table_name   VARCHAR(128) NOT NULL,
  name         VARCHAR(128) NOT NULL,
  kind         VARCHAR(10)  NOT NULL,
  columns      VARCHAR(4000),
  ref_table    VARCHAR(128),
  ref_columns  VARCHAR(4000),
  PRIMARY KEY (snapshot_id, schema_name, table_name, name),
  FOREIGN KEY (snapshot_id) REFERENCES snapshot(id) ON DELETE CASCADE
);

CREATE TABLE snap_index (
  snapshot_id  BIGINT       NOT NULL,
  schema_name  VARCHAR(128) NOT NULL,
  table_name   VARCHAR(128) NOT NULL,
  name         VARCHAR(128) NOT NULL,
  is_unique    BOOLEAN      NOT NULL,
  columns      VARCHAR(4000),
  PRIMARY KEY (snapshot_id, schema_name, table_name, name),
  FOREIGN KEY (snapshot_id) REFERENCES snapshot(id) ON DELETE CASCADE
);

-- 코드 검사 실행 이력 — 파일·줄·규칙까지만. 본문 컬럼 없음
CREATE TABLE check_run (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  profile       VARCHAR(100),
  started_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  path          VARCHAR(1000),
  changed_only  BOOLEAN NOT NULL,
  rule_groups   VARCHAR(200)
);

CREATE TABLE check_finding (
  run_id    BIGINT       NOT NULL,
  file      VARCHAR(1000) NOT NULL,
  line      INT,
  grp       VARCHAR(20)  NOT NULL,
  rule      VARCHAR(100) NOT NULL,
  severity  VARCHAR(10)  NOT NULL,
  FOREIGN KEY (run_id) REFERENCES check_run(id) ON DELETE CASCADE
);
CREATE INDEX ix_check_finding_run ON check_finding(run_id);
