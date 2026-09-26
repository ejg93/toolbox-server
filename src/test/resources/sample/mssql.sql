-- 메타 수집 골든용 샘플 — postgres.sql 과 같은 8 테이블. master 의 dbo 엔 시스템 표가 있어 스키마 sample 을 따로 둔다.
-- 이름 없는 제약은 PK__users__B9BE… 처럼 무작위 이름이 붙어 골든이 흔들린다 — 제약 이름을 전부 명시한다.
-- TIMESTAMP 는 MSSQL 에서 rowversion 이라 DATETIME2
CREATE SCHEMA sample;

CREATE TABLE sample.categories (
  category_id  INT NOT NULL CONSTRAINT pk_categories PRIMARY KEY,
  parent_id    INT,
  name         NVARCHAR(100) NOT NULL,
  CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES sample.categories(category_id)
);

CREATE TABLE sample.products (
  product_id   BIGINT NOT NULL CONSTRAINT pk_products PRIMARY KEY,
  category_id  INT NOT NULL,
  code         VARCHAR(30) NOT NULL,
  name         NVARCHAR(200) NOT NULL,
  price        DECIMAL(12,2) NOT NULL CONSTRAINT df_products_price DEFAULT 0,
  created_at   DATETIME2 NOT NULL CONSTRAINT df_products_created DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_products_code UNIQUE (code),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES sample.categories(category_id)
);
CREATE INDEX ix_products_category ON sample.products(category_id);

CREATE TABLE sample.users (
  user_id     BIGINT NOT NULL CONSTRAINT pk_users PRIMARY KEY,
  login_id    VARCHAR(50) NOT NULL,
  email       VARCHAR(200),
  user_name   NVARCHAR(100) NOT NULL,
  use_yn      CHAR(1) NOT NULL CONSTRAINT df_users_use_yn DEFAULT 'Y',
  CONSTRAINT uq_users_login UNIQUE (login_id)
);
CREATE UNIQUE INDEX ux_users_email ON sample.users(email);

CREATE TABLE sample.orders (
  order_id    BIGINT NOT NULL CONSTRAINT pk_orders PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  ordered_at  DATETIME2 NOT NULL,
  status      VARCHAR(10) NOT NULL,
  memo        NVARCHAR(MAX),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES sample.users(user_id)
);
CREATE INDEX ix_orders_user_date ON sample.orders(user_id, ordered_at);

CREATE TABLE sample.order_items (
  order_id    BIGINT NOT NULL,
  line_no     INT NOT NULL,
  product_id  BIGINT NOT NULL,
  qty         INT NOT NULL,
  amount      DECIMAL(14,2) NOT NULL,
  CONSTRAINT pk_order_items PRIMARY KEY (order_id, line_no),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES sample.orders(order_id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES sample.products(product_id)
);

CREATE TABLE sample.code_groups (
  group_code  VARCHAR(20) NOT NULL CONSTRAINT pk_code_groups PRIMARY KEY,
  group_name  NVARCHAR(100) NOT NULL
);

CREATE TABLE sample.codes (
  group_code  VARCHAR(20) NOT NULL,
  code        VARCHAR(20) NOT NULL,
  code_name   NVARCHAR(100) NOT NULL,
  sort_order  SMALLINT,
  CONSTRAINT pk_codes PRIMARY KEY (group_code, code),
  CONSTRAINT fk_codes_group FOREIGN KEY (group_code) REFERENCES sample.code_groups(group_code)
);

CREATE TABLE sample.logs (
  log_id      BIGINT NOT NULL CONSTRAINT pk_logs PRIMARY KEY,
  logged_at   DATETIME2 NOT NULL,
  level       VARCHAR(10) NOT NULL,
  message     NVARCHAR(4000)
);

EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'상품 분류', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'categories';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'분류명', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'categories', @level2type=N'COLUMN', @level2name=N'name';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'상품', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'products';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'상품 코드', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'products', @level2type=N'COLUMN', @level2name=N'code';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'단가', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'products', @level2type=N'COLUMN', @level2name=N'price';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'사용자', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'users';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'로그인 아이디', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'users', @level2type=N'COLUMN', @level2name=N'login_id';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'사용자명', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'users', @level2type=N'COLUMN', @level2name=N'user_name';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'주문', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'orders';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'주문 상태', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'orders', @level2type=N'COLUMN', @level2name=N'status';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'주문 상세', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'order_items';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'공통코드 그룹', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'code_groups';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'공통코드', @level0type=N'SCHEMA', @level0name=N'sample', @level1type=N'TABLE', @level1name=N'codes';
