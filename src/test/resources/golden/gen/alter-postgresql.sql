-- 반영 DDL — 원본 postgresql → 대상 postgresql · 표 추가 1 · 삭제 1 · 변경 4 · 문장 24 · 확인 5

-- 외래 키 삭제
ALTER TABLE public.order_items DROP CONSTRAINT fk_order_items_product;
ALTER TABLE public.products DROP CONSTRAINT fk_products_category;

-- 인덱스·유니크 삭제
ALTER TABLE public.products DROP CONSTRAINT uq_products_code;
DROP INDEX public.ux_users_email;

-- 새 표
CREATE TABLE public.user_tags (
    tag_id int8 NOT NULL,
    user_id int8 NOT NULL,
    tag varchar(30) DEFAULT 'NEW' NOT NULL,
    CONSTRAINT pk_user_tags PRIMARY KEY (tag_id)
);
CREATE INDEX ix_user_tags_user ON public.user_tags (user_id);

-- 코멘트
COMMENT ON TABLE public.user_tags IS '사용자 태그';
COMMENT ON COLUMN public.user_tags.tag_id IS '태그 ID';

-- 바뀐 표
-- codes
-- [확인] 컬럼 sort_order NOT NULL — NULL 행이 있으면 실패한다
ALTER TABLE public.codes ALTER COLUMN sort_order SET DEFAULT 0;
ALTER TABLE public.codes ALTER COLUMN sort_order SET NOT NULL;
ALTER TABLE public.codes DROP CONSTRAINT pk_codes;
ALTER TABLE public.codes ADD CONSTRAINT pk_codes PRIMARY KEY (code);
-- products
ALTER TABLE public.products ALTER COLUMN price DROP DEFAULT;
ALTER TABLE public.products ALTER COLUMN created_at DROP NOT NULL;
-- users
ALTER TABLE public.users ADD COLUMN phone varchar(20);
ALTER TABLE public.users ADD COLUMN grade int4 DEFAULT 1 NOT NULL;
ALTER TABLE public.users ALTER COLUMN login_id TYPE varchar(80);
-- [확인] 컬럼 user_name 타입이 줄거나 바뀐다 — 값이 잘리거나 변환이 실패할 수 있다
ALTER TABLE public.users ALTER COLUMN user_name TYPE varchar(50);
-- [확인] 같은 표에 컬럼 삭제와 추가가 같이 있다 — 이름이 바뀐 것이면 삭제·추가 대신 RENAME 으로
-- [확인] 데이터가 사라진다 — 컬럼 users.email
-- ALTER TABLE public.users DROP COLUMN email;

-- 인덱스·유니크 추가
ALTER TABLE public.products ADD CONSTRAINT uq_products_name UNIQUE (name);
CREATE INDEX ix_users_name ON public.users (user_name);

-- 외래 키 추가
ALTER TABLE public.products ADD CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES public.categories (category_id) ON DELETE CASCADE;
ALTER TABLE public.user_tags ADD CONSTRAINT fk_user_tags_user FOREIGN KEY (user_id) REFERENCES public.users (user_id) ON DELETE CASCADE;

-- 코멘트
COMMENT ON COLUMN public.users.user_name IS '사용자 이름';
COMMENT ON COLUMN public.users.phone IS '전화';

-- 표 삭제
-- [확인] 데이터가 사라진다 — 표 logs
-- DROP TABLE public.logs;

-- 경고
