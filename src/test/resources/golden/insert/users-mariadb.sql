
INSERT INTO users(
	user_id,
	login_id,
	email,
	user_name,
	use_yn
)
VALUES(
	1,
	'LOGIN_ID_001',
	'EMAIL_001',
	'USER_NAME_001',
	'Y'
);

INSERT INTO users(
	user_id,
	login_id,
	email,
	user_name,
	use_yn
)
VALUES(
	2,
	'LOGIN_ID_002',
	'EMAIL_002',
	'USER_NAME_002',
	'N'
);

INSERT INTO users(
	user_id,
	login_id,
	email,
	user_name,
	use_yn
)
VALUES(
	3,
	'LOGIN_ID_003',
	'EMAIL_003',
	'USER_NAME_003',
	'Y'
);

-- autocommit 켜져 있으면 불필요
COMMIT;

-- upsert
INSERT INTO USERS (
	  USER_ID,
	  LOGIN_ID,
	  EMAIL,
	  USER_NAME,
	  USE_YN
)
VALUES (
	  #{userId},
	  #{loginId},
	  #{email},
	  #{userName},
	  #{useYn}
)
ON DUPLICATE KEY UPDATE
	  LOGIN_ID = VALUES(LOGIN_ID)
	, EMAIL = VALUES(EMAIL)
	, USER_NAME = VALUES(USER_NAME)
	, USE_YN = VALUES(USE_YN);
-- MySQL 8.0.20+ 는 VALUES() 대신 별칭 권장: ... AS NEW ... = NEW.컬럼
