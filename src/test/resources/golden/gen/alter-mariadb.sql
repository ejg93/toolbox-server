-- 반영 DDL — 원본 postgresql → 대상 mariadb · 표 추가 1 · 삭제 1 · 변경 4 · 문장 19 · 확인 5

-- 외래 키 삭제
ALTER TABLE public.order_items DROP FOREIGN KEY fk_order_items_product;
ALTER TABLE public.products DROP FOREIGN KEY fk_products_category;

-- 인덱스·유니크 삭제
ALTER TABLE public.products DROP INDEX uq_products_code;
DROP INDEX ux_users_email ON public.users;

-- 새 표
CREATE TABLE public.user_tags (
    tag_id BIGINT NOT NULL COMMENT '태그 ID',
    user_id BIGINT NOT NULL,
    tag VARCHAR(30) DEFAULT 'NEW' NOT NULL,
    CONSTRAINT pk_user_tags PRIMARY KEY (tag_id)
) COMMENT = '사용자 태그';
CREATE INDEX ix_user_tags_user ON public.user_tags (user_id);

-- 바뀐 표
-- codes
-- [확인] 컬럼 sort_order NOT NULL — NULL 행이 있으면 실패한다
ALTER TABLE public.codes MODIFY COLUMN sort_order INT DEFAULT 0 NOT NULL;
ALTER TABLE public.codes DROP PRIMARY KEY;
ALTER TABLE public.codes ADD PRIMARY KEY (code);
-- products
ALTER TABLE public.products ALTER COLUMN price DROP DEFAULT;
ALTER TABLE public.products MODIFY COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP;
-- users
ALTER TABLE public.users ADD COLUMN phone VARCHAR(20) COMMENT '전화';
ALTER TABLE public.users ADD COLUMN grade INT DEFAULT 1 NOT NULL;
ALTER TABLE public.users MODIFY COLUMN login_id VARCHAR(80) NOT NULL COMMENT '로그인 아이디';
-- [확인] 컬럼 user_name 타입이 줄거나 바뀐다 — 값이 잘리거나 변환이 실패할 수 있다
ALTER TABLE public.users MODIFY COLUMN user_name VARCHAR(50) NOT NULL COMMENT '사용자 이름';
-- [확인] 같은 표에 컬럼 삭제와 추가가 같이 있다 — 이름이 바뀐 것이면 삭제·추가 대신 RENAME 으로
-- [확인] 데이터가 사라진다 — 컬럼 users.email
-- ALTER TABLE public.users DROP COLUMN email;

-- 인덱스·유니크 추가
ALTER TABLE public.products ADD CONSTRAINT uq_products_name UNIQUE (name);
CREATE INDEX ix_users_name ON public.users (user_name);

-- 외래 키 추가
ALTER TABLE public.products ADD CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES public.categories (category_id) ON DELETE CASCADE;
ALTER TABLE public.user_tags ADD CONSTRAINT fk_user_tags_user FOREIGN KEY (user_id) REFERENCES public.users (user_id) ON DELETE CASCADE;

-- 표 삭제
-- [확인] 데이터가 사라진다 — 표 logs
-- DROP TABLE public.logs;

-- 경고
