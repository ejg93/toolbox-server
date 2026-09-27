
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
ON CONFLICT (USER_ID) DO UPDATE SET
	  LOGIN_ID = EXCLUDED.LOGIN_ID
	, EMAIL = EXCLUDED.EMAIL
	, USER_NAME = EXCLUDED.USER_NAME
	, USE_YN = EXCLUDED.USE_YN;
-- PostgreSQL 9.5+ (ON CONFLICT). 대상 컬럼에 UNIQUE/PK 인덱스가 있어야 함
