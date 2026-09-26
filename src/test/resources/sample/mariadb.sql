-- 메타 수집 골든용 샘플 — postgres.sql 과 같은 8 테이블. 시각 컬럼은 DATETIME(TIMESTAMP 는 자동 기본값이 붙는다)
CREATE TABLE categories (
  category_id  INT PRIMARY KEY,
  parent_id    INT,
  name         VARCHAR(100) NOT NULL COMMENT '분류명',
  CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(category_id)
) COMMENT = '상품 분류';

CREATE TABLE products (
  product_id   BIGINT PRIMARY KEY,
  category_id  INT NOT NULL,
  code         VARCHAR(30) NOT NULL COMMENT '상품 코드',
  name         VARCHAR(200) NOT NULL,
  price        DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '단가',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_products_code UNIQUE (code),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(category_id)
) COMMENT = '상품';
CREATE INDEX ix_products_category ON products(category_id);

CREATE TABLE users (
  user_id     BIGINT PRIMARY KEY,
  login_id    VARCHAR(50) NOT NULL COMMENT '로그인 아이디',
  email       VARCHAR(200),
  user_name   VARCHAR(100) NOT NULL COMMENT '사용자명',
  use_yn      CHAR(1) NOT NULL DEFAULT 'Y',
  CONSTRAINT uq_users_login UNIQUE (login_id)
) COMMENT = '사용자';
CREATE UNIQUE INDEX ux_users_email ON users(email);

CREATE TABLE orders (
  order_id    BIGINT PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  ordered_at  DATETIME NOT NULL,
  status      VARCHAR(10) NOT NULL COMMENT '주문 상태',
  memo        TEXT,
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) COMMENT = '주문';
CREATE INDEX ix_orders_user_date ON orders(user_id, ordered_at);

CREATE TABLE order_items (
  order_id    BIGINT NOT NULL,
  line_no     INT NOT NULL,
  product_id  BIGINT NOT NULL,
  qty         INT NOT NULL,
  amount      DECIMAL(14,2) NOT NULL,
  PRIMARY KEY (order_id, line_no),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(product_id)
) COMMENT = '주문 상세';

CREATE TABLE code_groups (
  group_code  VARCHAR(20) PRIMARY KEY,
  group_name  VARCHAR(100) NOT NULL
) COMMENT = '공통코드 그룹';

CREATE TABLE codes (
  group_code  VARCHAR(20) NOT NULL,
  code        VARCHAR(20) NOT NULL,
  code_name   VARCHAR(100) NOT NULL,
  sort_order  SMALLINT,
  PRIMARY KEY (group_code, code),
  CONSTRAINT fk_codes_group FOREIGN KEY (group_code) REFERENCES code_groups(group_code)
) COMMENT = '공통코드';

CREATE TABLE logs (
  log_id      BIGINT PRIMARY KEY,
  logged_at   DATETIME NOT NULL,
  level       VARCHAR(10) NOT NULL,
  message     VARCHAR(4000)
);
