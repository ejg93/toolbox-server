
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

-- 명시 트랜잭션을 쓸 때만: BEGIN TRAN ... COMMIT TRAN
COMMIT TRAN;

-- upsert
MERGE INTO USERS T
USING (
	SELECT
		  #{userId} AS USER_ID,
		  #{loginId} AS LOGIN_ID,
		  #{email} AS EMAIL,
		  #{userName} AS USER_NAME,
		  #{useYn} AS USE_YN
) S ON (T.USER_ID = S.USER_ID)
WHEN MATCHED THEN
	UPDATE SET
		  T.LOGIN_ID = S.LOGIN_ID
		, T.EMAIL = S.EMAIL
		, T.USER_NAME = S.USER_NAME
		, T.USE_YN = S.USE_YN
WHEN NOT MATCHED THEN
	INSERT (
		  USER_ID,
		  LOGIN_ID,
		  EMAIL,
		  USER_NAME,
		  USE_YN
	) VALUES (
		  S.USER_ID,
		  S.LOGIN_ID,
		  S.EMAIL,
		  S.USER_NAME,
		  S.USE_YN
	);
-- Sybase ASE 16 미만은 MERGE 미지원 — IF EXISTS + UPDATE/INSERT 로 분기 필요
