-- warning: FK category_id → categories: 부모 테이블의 실제 값으로 바꿀 것 — 접속을 주면 실존 값을 쓴다

INSERT INTO products(
	product_id,
	category_id,
	code,
	name,
	price,
	created_at
)
VALUES(
	1,
	1,
	'CODE_001',
	'NAME_001',
	1.01,
	CONVERT(DATETIME, '2026-01-31 00:00:00', 120) /* ⚠ 스타일 120 ASE 지원 버전 확인 요망 */
);

INSERT INTO products(
	product_id,
	category_id,
	code,
	name,
	price,
	created_at
)
VALUES(
	2,
	2,
	'CODE_002',
	'NAME_002',
	2.02,
	CONVERT(DATETIME, '2026-01-30 00:00:00', 120) /* ⚠ 스타일 120 ASE 지원 버전 확인 요망 */
);

INSERT INTO products(
	product_id,
	category_id,
	code,
	name,
	price,
	created_at
)
VALUES(
	3,
	3,
	'CODE_003',
	'NAME_003',
	3.03,
	CONVERT(DATETIME, '2026-01-29 00:00:00', 120) /* ⚠ 스타일 120 ASE 지원 버전 확인 요망 */
);

-- 명시 트랜잭션을 쓸 때만: BEGIN TRAN ... COMMIT TRAN
COMMIT TRAN;

-- upsert
MERGE INTO PRODUCTS T
USING (
	SELECT
		  #{productId} AS PRODUCT_ID,
		  #{categoryId} AS CATEGORY_ID,
		  #{code} AS CODE,
		  #{name} AS NAME,
		  #{price} AS PRICE,
		  #{createdAt} AS CREATED_AT
) S ON (T.PRODUCT_ID = S.PRODUCT_ID)
WHEN MATCHED THEN
	UPDATE SET
		  T.CATEGORY_ID = S.CATEGORY_ID
		, T.CODE = S.CODE
		, T.NAME = S.NAME
		, T.PRICE = S.PRICE
		, T.CREATED_AT = S.CREATED_AT
WHEN NOT MATCHED THEN
	INSERT (
		  PRODUCT_ID,
		  CATEGORY_ID,
		  CODE,
		  NAME,
		  PRICE,
		  CREATED_AT
	) VALUES (
		  S.PRODUCT_ID,
		  S.CATEGORY_ID,
		  S.CODE,
		  S.NAME,
		  S.PRICE,
		  S.CREATED_AT
	);
-- Sybase ASE 16 미만은 MERGE 미지원 — IF EXISTS + UPDATE/INSERT 로 분기 필요
