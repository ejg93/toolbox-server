-- 메타 수집 골든용 샘플 — 8 테이블. PK·FK·UNIQUE·인덱스·코멘트
CREATE TABLE categories (
  category_id  INTEGER PRIMARY KEY,
  parent_id    INTEGER REFERENCES categories(category_id),
  name         VARCHAR(100) NOT NULL
);
COMMENT ON TABLE categories IS '상품 분류';
COMMENT ON COLUMN categories.name IS '분류명';

CREATE TABLE products (
  product_id   BIGINT PRIMARY KEY,
  category_id  INTEGER NOT NULL,
  code         VARCHAR(30) NOT NULL,
  name         VARCHAR(200) NOT NULL,
  price        NUMERIC(12,2) DEFAULT 0 NOT NULL,
  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT uq_products_code UNIQUE (code),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(category_id)
);
COMMENT ON TABLE products IS '상품';
COMMENT ON COLUMN products.code IS '상품 코드';
COMMENT ON COLUMN products.price IS '단가';
CREATE INDEX ix_products_category ON products(category_id);

CREATE TABLE users (
  user_id     BIGINT PRIMARY KEY,
  login_id    VARCHAR(50) NOT NULL,
  email       VARCHAR(200),
  user_name   VARCHAR(100) NOT NULL,
  use_yn      CHAR(1) DEFAULT 'Y' NOT NULL,
  CONSTRAINT uq_users_login UNIQUE (login_id)
);
COMMENT ON TABLE users IS '사용자';
COMMENT ON COLUMN users.login_id IS '로그인 아이디';
COMMENT ON COLUMN users.user_name IS '사용자명';
CREATE UNIQUE INDEX ux_users_email ON users(email);

CREATE TABLE orders (
  order_id    BIGINT PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  ordered_at  TIMESTAMP NOT NULL,
  status      VARCHAR(10) NOT NULL,
  memo        TEXT,
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);
COMMENT ON TABLE orders IS '주문';
COMMENT ON COLUMN orders.status IS '주문 상태';
CREATE INDEX ix_orders_user_date ON orders(user_id, ordered_at);

CREATE TABLE order_items (
  order_id    BIGINT NOT NULL,
  line_no     INTEGER NOT NULL,
  product_id  BIGINT NOT NULL,
  qty         INTEGER NOT NULL,
  amount      NUMERIC(14,2) NOT NULL,
  CONSTRAINT pk_order_items PRIMARY KEY (order_id, line_no),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);
COMMENT ON TABLE order_items IS '주문 상세';

CREATE TABLE code_groups (
  group_code  VARCHAR(20) PRIMARY KEY,
  group_name  VARCHAR(100) NOT NULL
);
COMMENT ON TABLE code_groups IS '공통코드 그룹';

CREATE TABLE codes (
  group_code  VARCHAR(20) NOT NULL,
  code        VARCHAR(20) NOT NULL,
  code_name   VARCHAR(100) NOT NULL,
  sort_order  SMALLINT,
  CONSTRAINT pk_codes PRIMARY KEY (group_code, code),
  CONSTRAINT fk_codes_group FOREIGN KEY (group_code) REFERENCES code_groups(group_code)
);
COMMENT ON TABLE codes IS '공통코드';

CREATE TABLE logs (
  log_id      BIGINT PRIMARY KEY,
  logged_at   TIMESTAMP NOT NULL,
  level       VARCHAR(10) NOT NULL,
  message     VARCHAR(4000)
);
