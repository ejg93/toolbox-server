-- V001 — 사전·스냅샷·실행 이력. 코드 본문·SQL 결과·비밀번호 컬럼을 두지 않는다(절대 규칙 2·3)

-- 표준단어 사전 — kind: word(행안부 공통표준단어) · org(기관표준단어, abbr 에 물리명 통째) · user(사용자 입력)
CREATE TABLE dict_word (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  kind        VARCHAR(20)  NOT NULL,
  word_ko     VARCHAR(200) NOT NULL,
  abbr        VARCHAR(100) NOT NULL,
  word_en     VARCHAR(200),
  domain      VARCHAR(100),
  description VARCHAR(4000),
  -- 형식단어여부 Y/N
  form_word   CHAR(1),
  synonyms    VARCHAR(1000),
  forbidden   VARCHAR(1000),
  source      VARCHAR(50),
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  -- 같은 약어는 첫 줄만(순수본 applyDict 와 같다)
  CONSTRAINT uq_dict_word UNIQUE (kind, abbr)
);

-- 행안부 공통표준도메인(순수본 DOMAINDB 129행을 dict/moi-domains.csv 로 동봉)
CREATE TABLE dict_domain (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  grp           VARCHAR(100),
  cls           VARCHAR(100) NOT NULL,
  name          VARCHAR(100) NOT NULL,
  data_type     VARCHAR(50),
  length        VARCHAR(20),
  scale         VARCHAR(20),
  store_format  VARCHAR(200),
  disp_format   VARCHAR(200),
  unit          VARCHAR(50),
  allowed       VARCHAR(1000),
  description   VARCHAR(4000)
);
CREATE INDEX ix_dict_word_ko ON dict_word(word_ko);
CREATE INDEX ix_dict_word_abbr ON dict_word(abbr);

-- 메타모델 스냅샷
CREATE TABLE snapshot (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  profile   VARCHAR(100) NOT NULL,
  conn_id   VARCHAR(100) NOT NULL,
  taken_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  note      VARCHAR(1000),
  -- 접속 DB 의 제품명·버전(Schema.dbVersion). 한 스냅샷 = 한 접속이라 스냅샷 단위
  db_version VARCHAR(200),
  -- 스키마 이름 JSON 배열 — 테이블이 없는 스키마도 왕복되게
  schemas   VARCHAR(4000)
);

CREATE TABLE snap_table (
  snapshot_id  BIGINT       NOT NULL,
  schema_name  VARCHAR(128) NOT NULL,
  table_name   VARCHAR(128) NOT NULL,
  table_type   VARCHAR(20),
  comment      VARCHAR(4000),
  row_count    BIGINT,
  created_at   TIMESTAMP,
  last_ddl_at  TIMESTAMP,
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
  domain         VARCHAR(100),
  PRIMARY KEY (snapshot_id, schema_name, table_name, name),
  FOREIGN KEY (snapshot_id) REFERENCES snapshot(id) ON DELETE CASCADE
);

CREATE TABLE snap_constraint (
  snapshot_id  BIGINT       NOT NULL,
  schema_name  VARCHAR(128) NOT NULL,
  table_name   VARCHAR(128) NOT NULL,
  -- PK 이름이 없는 DB 는 '' 로 적는다(읽을 때 null)
  name         VARCHAR(128) NOT NULL,
  -- PK · FK · UQ
  kind         VARCHAR(10)  NOT NULL,
  -- 목록은 JSON 배열 문자열
  columns      VARCHAR(4000),
  ref_schema   VARCHAR(128),
  ref_table    VARCHAR(128),
  ref_columns  VARCHAR(4000),
  PRIMARY KEY (snapshot_id, schema_name, table_name, kind, name),
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
