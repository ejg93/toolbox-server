-- 메타 수집 골든용 샘플 — postgres.sql 과 같은 8 테이블. 이름 없는 제약은 SYS_C… 무작위 이름이라 전부 명시한다.
-- CLOB 은 LOB 인덱스(SYS_IL…)가 생겨 memo 도 VARCHAR2(4000)
CREATE TABLE categories (
  category_id  NUMBER(10) NOT NULL,
  parent_id    NUMBER(10),
  name         VARCHAR2(100) NOT NULL,
  CONSTRAINT pk_categories PRIMARY KEY (category_id),
  CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(category_id)
);
COMMENT ON TABLE categories IS '상품 분류';
COMMENT ON COLUMN categories.name IS '분류명';

CREATE TABLE products (
  product_id   NUMBER(19) NOT NULL,
  category_id  NUMBER(10) NOT NULL,
  code         VARCHAR2(30) NOT NULL,
  name         VARCHAR2(200) NOT NULL,
  price        NUMBER(12,2) DEFAULT 0 NOT NULL,
  created_at   TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_products PRIMARY KEY (product_id),
  CONSTRAINT uq_products_code UNIQUE (code),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(category_id)
);
COMMENT ON TABLE products IS '상품';
COMMENT ON COLUMN products.code IS '상품 코드';
COMMENT ON COLUMN products.price IS '단가';
CREATE INDEX ix_products_category ON products(category_id);

CREATE TABLE users (
  user_id     NUMBER(19) NOT NULL,
  login_id    VARCHAR2(50) NOT NULL,
  email       VARCHAR2(200),
  user_name   VARCHAR2(100) NOT NULL,
  use_yn      CHAR(1) DEFAULT 'Y' NOT NULL,
  CONSTRAINT pk_users PRIMARY KEY (user_id),
  CONSTRAINT uq_users_login UNIQUE (login_id)
);
COMMENT ON TABLE users IS '사용자';
COMMENT ON COLUMN users.login_id IS '로그인 아이디';
COMMENT ON COLUMN users.user_name IS '사용자명';
CREATE UNIQUE INDEX ux_users_email ON users(email);

CREATE TABLE orders (
  order_id    NUMBER(19) NOT NULL,
  user_id     NUMBER(19) NOT NULL,
  ordered_at  TIMESTAMP NOT NULL,
  status      VARCHAR2(10) NOT NULL,
  memo        VARCHAR2(4000),
  CONSTRAINT pk_orders PRIMARY KEY (order_id),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);
COMMENT ON TABLE orders IS '주문';
COMMENT ON COLUMN orders.status IS '주문 상태';
CREATE INDEX ix_orders_user_date ON orders(user_id, ordered_at);

CREATE TABLE order_items (
  order_id    NUMBER(19) NOT NULL,
  line_no     NUMBER(10) NOT NULL,
  product_id  NUMBER(19) NOT NULL,
  qty         NUMBER(10) NOT NULL,
  amount      NUMBER(14,2) NOT NULL,
  CONSTRAINT pk_order_items PRIMARY KEY (order_id, line_no),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);
COMMENT ON TABLE order_items IS '주문 상세';

CREATE TABLE code_groups (
  group_code  VARCHAR2(20) NOT NULL,
  group_name  VARCHAR2(100) NOT NULL,
  CONSTRAINT pk_code_groups PRIMARY KEY (group_code)
);
COMMENT ON TABLE code_groups IS '공통코드 그룹';

CREATE TABLE codes (
  group_code  VARCHAR2(20) NOT NULL,
  code        VARCHAR2(20) NOT NULL,
  code_name   VARCHAR2(100) NOT NULL,
  sort_order  NUMBER(5),
  CONSTRAINT pk_codes PRIMARY KEY (group_code, code),
  CONSTRAINT fk_codes_group FOREIGN KEY (group_code) REFERENCES code_groups(group_code)
);
COMMENT ON TABLE codes IS '공통코드';

CREATE TABLE logs (
  log_id      NUMBER(19) NOT NULL,
  logged_at   TIMESTAMP NOT NULL,
  lvl         VARCHAR2(10) NOT NULL,
  message     VARCHAR2(4000),
  CONSTRAINT pk_logs PRIMARY KEY (log_id)
);
