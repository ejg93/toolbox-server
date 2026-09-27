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
	TO_TIMESTAMP('2026-01-31 00:00:00','YYYY-MM-DD HH24:MI:SS')
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
	TO_TIMESTAMP('2026-01-30 00:00:00','YYYY-MM-DD HH24:MI:SS')
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
	TO_TIMESTAMP('2026-01-29 00:00:00','YYYY-MM-DD HH24:MI:SS')
);

COMMIT;

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
	FROM DUAL
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
