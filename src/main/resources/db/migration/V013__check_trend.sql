-- 5-36 추이 — 분모(검사한 파일·줄)와 규칙 지문(켜진 규칙 정의의 SHA-256 앞 12 hex). 옛 실행은 0·NULL(추이 표에 「지문 없음」)
ALTER TABLE check_run ADD COLUMN file_count INT DEFAULT 0 NOT NULL;
ALTER TABLE check_run ADD COLUMN line_count INT DEFAULT 0 NOT NULL;
ALTER TABLE check_run ADD COLUMN rules_hash VARCHAR(16);
