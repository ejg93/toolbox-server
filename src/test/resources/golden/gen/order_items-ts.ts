/**
 * 주문 상세
 *
 * 테이블: TEST.ORDER_ITEMS
 */
export interface OrderItems {
  /** FK → ORDERS(ORDER_ID) */
  orderId: number;
  lineNo: number;
  /** FK → PRODUCTS(PRODUCT_ID) */
  productId: number;
  qty: number;
  amount: number;
}
