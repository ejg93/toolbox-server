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
	'2026-01-31 00:00:00'
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
	'2026-01-30 00:00:00'
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
	'2026-01-29 00:00:00'
);

COMMIT;

-- upsert
INSERT INTO PRODUCTS (
	  PRODUCT_ID,
	  CATEGORY_ID,
	  CODE,
	  NAME,
	  PRICE,
	  CREATED_AT
)
VALUES (
	  #{productId},
	  #{categoryId},
	  #{code},
	  #{name},
	  #{price},
	  #{createdAt}
)
ON CONFLICT (PRODUCT_ID) DO UPDATE SET
	  CATEGORY_ID = EXCLUDED.CATEGORY_ID
	, CODE = EXCLUDED.CODE
	, NAME = EXCLUDED.NAME
	, PRICE = EXCLUDED.PRICE
	, CREATED_AT = EXCLUDED.CREATED_AT;
-- PostgreSQL 9.5+ (ON CONFLICT). 대상 컬럼에 UNIQUE/PK 인덱스가 있어야 함
