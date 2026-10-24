/**
 * 상품
 *
 * 테이블: public.products
 */
export interface Products {
  productId: number;
  /** FK → categories(category_id) */
  categoryId: number;
  /** 상품 코드 · UNIQUE */
  code: string;
  name: string;
  /** 단가 */
  price: number;
  createdAt: string;
}
