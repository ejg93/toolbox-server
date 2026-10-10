-- 반영 DDL — 원본 postgresql → 대상 mssql · 표 추가 1 · 삭제 1 · 변경 4 · 문장 23 · 확인 6

-- 외래 키 삭제
ALTER TABLE public.order_items DROP CONSTRAINT fk_order_items_product;
ALTER TABLE public.products DROP CONSTRAINT fk_products_category;

-- 인덱스·유니크 삭제
ALTER TABLE public.products DROP CONSTRAINT uq_products_code;
DROP INDEX ux_users_email ON public.users;

-- 새 표
CREATE TABLE public.user_tags (
    tag_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    tag NVARCHAR(30) DEFAULT 'NEW' NOT NULL,
    CONSTRAINT pk_user_tags PRIMARY KEY (tag_id)
);
CREATE INDEX ix_user_tags_user ON public.user_tags (user_id);

-- 코멘트
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'사용자 태그', @level0type=N'SCHEMA', @level0name=N'public', @level1type=N'TABLE', @level1name=N'user_tags';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'태그 ID', @level0type=N'SCHEMA', @level0name=N'public', @level1type=N'TABLE', @level1name=N'user_tags', @level2type=N'COLUMN', @level2name=N'tag_id';

-- 바뀐 표
-- codes
-- [확인] 컬럼 sort_order NOT NULL — NULL 행이 있으면 실패한다
ALTER TABLE public.codes ALTER COLUMN sort_order INT NOT NULL;
ALTER TABLE public.codes ADD CONSTRAINT DF_codes_sort_order DEFAULT 0 FOR sort_order;
ALTER TABLE public.codes DROP CONSTRAINT pk_codes;
ALTER TABLE public.codes ADD CONSTRAINT pk_codes PRIMARY KEY (code);
-- products
-- [손] 컬럼 price 기본값 — 기본값 제약 이름을 sp_helpconstraint 로 찾아 DROP 한다
ALTER TABLE public.products ALTER COLUMN created_at DATETIME2 NULL;
-- users
ALTER TABLE public.users ADD phone NVARCHAR(20);
ALTER TABLE public.users ADD grade INT DEFAULT 1 NOT NULL;
ALTER TABLE public.users ALTER COLUMN login_id NVARCHAR(80) NOT NULL;
-- [확인] 컬럼 user_name 타입이 줄거나 바뀐다 — 값이 잘리거나 변환이 실패할 수 있다
ALTER TABLE public.users ALTER COLUMN user_name NVARCHAR(50) NOT NULL;
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
EXEC sys.sp_updateextendedproperty @name=N'MS_Description', @value=N'사용자 이름', @level0type=N'SCHEMA', @level0name=N'public', @level1type=N'TABLE', @level1name=N'users', @level2type=N'COLUMN', @level2name=N'user_name';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'전화', @level0type=N'SCHEMA', @level0name=N'public', @level1type=N'TABLE', @level1name=N'users', @level2type=N'COLUMN', @level2name=N'phone';

-- 표 삭제
-- [확인] 데이터가 사라진다 — 표 logs
-- DROP TABLE public.logs;

-- 경고
