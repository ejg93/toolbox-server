-- 화면 전수(6-26~6-29) — 뷰 이름마다 맞는 JSP 파일 수 · JSP 링크 꼴 · 메뉴 CSV(프로필마다). 식별자·경로·메뉴 이름만(규칙 3)
CREATE TABLE analyze_view_file (
  run_id  BIGINT       NOT NULL,
  name    VARCHAR(500) NOT NULL,   -- 뷰 이름(analyze_view.name, kind view)
  files   INT          NOT NULL,   -- 경로가 /<name>.jsp 로 끝나는 JSP 파일 수 — 0 없음 · 1 있음 · 2+ 여럿. 행이 없는 옛 실행은 모름
  PRIMARY KEY (run_id, name),
  FOREIGN KEY (run_id) REFERENCES analyze_run(id) ON DELETE CASCADE
);
-- JSP 가 URL 을 부른 꼴(6-27) — link·form·popup·ajax·script·other. 옛 행은 null(모름). 같은 JSP·URL 이 여러 꼴이면 꼴마다 한 행
ALTER TABLE analyze_jsp_link ADD COLUMN kind VARCHAR(10);
-- 메뉴 CSV(6-29) — 프로필마다 한 벌, 올리면 통째로 바꾼다. 경로는 「대 > 중 > 소」, seq 는 CSV 행 순서(트리 순서)
CREATE TABLE analyze_menu (
  profile     VARCHAR(100)  NOT NULL,
  seq         INT           NOT NULL,
  path        VARCHAR(1000) NOT NULL,
  name        VARCHAR(300)  NOT NULL,
  url         VARCHAR(500),
  screen_id   VARCHAR(100),
  use_yn      VARCHAR(10),
  auth        VARCHAR(300),
  uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (profile, seq)
);
