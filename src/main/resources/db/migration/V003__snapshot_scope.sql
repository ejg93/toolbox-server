-- 스냅샷을 찍을 때 쓴 범위와 벤더 SQL 물러섬(1-14). 옛 행은 null — 거르지 않은 것으로 본다
-- 찍을 때 쓴 프로필 scope 의 JSON. include.tables 가 길 수 있어 길이를 묶지 않는다
ALTER TABLE snapshot ADD COLUMN scope VARCHAR(1000000);
-- 벤더 SQL 이 실패해 JDBC 값으로 물러선 것(1-12) — 「종류 SQLState/코드 ×건수」 JSON 배열. SQL 글·오류문은 없다
ALTER TABLE snapshot ADD COLUMN warnings VARCHAR(4000);
