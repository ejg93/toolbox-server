-- 프로그램 분석 실행 이력(6-4) — 식별자(클래스·메서드·URL·뷰·문장 ns.id·테이블)·CRUD 글자·설명 100자·미해결 자리만.
-- SQL·코드 본문 컬럼 없음(절대 규칙 3). 설명 100자(javadoc·블록 주석 첫 문장)는 규칙 3 의 예외(2026-10-03 사용자 결정, CLAUDE.md).
-- 프로그램 한 행 = 매핑 하나, 뷰·문장·CRUD 는 자식 표(영향도가 표 → 프로그램으로 거꾸로 찾는다)
CREATE TABLE analyze_run (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  profile     VARCHAR(100),
  started_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  path        VARCHAR(1000),
  programs    INT NOT NULL,
  statements  INT NOT NULL,
  tables      INT NOT NULL,
  unresolved  INT NOT NULL
);

CREATE TABLE analyze_program (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id      BIGINT        NOT NULL,
  class_name  VARCHAR(300)  NOT NULL,
  method      VARCHAR(200)  NOT NULL,
  file        VARCHAR(1000),
  line        INT,
  verb        VARCHAR(60),
  url         VARCHAR(500),
  params      VARCHAR(200),
  kind        VARCHAR(10),
  descr       VARCHAR(100),
  FOREIGN KEY (run_id) REFERENCES analyze_run(id) ON DELETE CASCADE
);
CREATE INDEX ix_analyze_program_run ON analyze_program(run_id);

CREATE TABLE analyze_view (
  program_id  BIGINT       NOT NULL,
  kind        VARCHAR(10)  NOT NULL,
  name        VARCHAR(500) NOT NULL,
  FOREIGN KEY (program_id) REFERENCES analyze_program(id) ON DELETE CASCADE
);
CREATE INDEX ix_analyze_view_program ON analyze_view(program_id);

CREATE TABLE analyze_stmt (
  program_id  BIGINT       NOT NULL,
  ns_id       VARCHAR(300) NOT NULL,
  resolution  VARCHAR(10)  NOT NULL,
  FOREIGN KEY (program_id) REFERENCES analyze_program(id) ON DELETE CASCADE
);
CREATE INDEX ix_analyze_stmt_program ON analyze_stmt(program_id);

CREATE TABLE analyze_crud (
  program_id  BIGINT       NOT NULL,
  table_name  VARCHAR(200) NOT NULL,
  crud        VARCHAR(4)   NOT NULL,
  PRIMARY KEY (program_id, table_name),
  FOREIGN KEY (program_id) REFERENCES analyze_program(id) ON DELETE CASCADE
);
CREATE INDEX ix_analyze_crud_table ON analyze_crud(table_name);

CREATE TABLE analyze_unresolved (
  run_id      BIGINT        NOT NULL,
  program_id  BIGINT,
  kind        VARCHAR(20)   NOT NULL,
  file        VARCHAR(1000),
  line        INT,
  detail      VARCHAR(300),
  FOREIGN KEY (run_id) REFERENCES analyze_run(id) ON DELETE CASCADE
);
CREATE INDEX ix_analyze_unresolved_run ON analyze_unresolved(run_id);

-- JSP 가 부르는 .do URL(6-6 영향도의 JSP 역추적) — 경로·URL 만(JSP 원문 없음)
CREATE TABLE analyze_jsp_link (
  run_id      BIGINT        NOT NULL,
  jsp         VARCHAR(1000) NOT NULL,
  url         VARCHAR(500)  NOT NULL,
  FOREIGN KEY (run_id) REFERENCES analyze_run(id) ON DELETE CASCADE
);
CREATE INDEX ix_analyze_jsp_link_url ON analyze_jsp_link(run_id, url);
