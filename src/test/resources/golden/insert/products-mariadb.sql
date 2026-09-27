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

-- autocommit 켜져 있으면 불필요
COMMIT;

-- upsert
INSERT INTO products (
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
ON DUPLICATE KEY UPDATE
	  CATEGORY_ID = VALUES(CATEGORY_ID)
	, CODE = VALUES(CODE)
	, NAME = VALUES(NAME)
	, PRICE = VALUES(PRICE)
	, CREATED_AT = VALUES(CREATED_AT);
-- MySQL 8.0.20+ 는 VALUES() 대신 별칭 권장: ... AS NEW ... = NEW.컬럼
