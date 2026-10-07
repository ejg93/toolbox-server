package com.example.dto;

import java.math.BigDecimal;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;

/**
 * 주문 상세
 *
 * <p>테이블: TEST.ORDER_ITEMS</p>
 */
public class OrderItems {

    /** FK → ORDERS(ORDER_ID) */
    @Digits(integer = 19, fraction = 0)
    private Long orderId;

    @Digits(integer = 10, fraction = 0)
    private Long lineNo;

    /** FK → PRODUCTS(PRODUCT_ID) */
    @NotNull
    @Digits(integer = 19, fraction = 0)
    private Long productId;

    @NotNull
    @Digits(integer = 10, fraction = 0)
    private Long qty;

    @NotNull
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    public OrderItems() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getLineNo() {
        return lineNo;
    }

    public void setLineNo(Long lineNo) {
        this.lineNo = lineNo;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getQty() {
        return qty;
    }

    public void setQty(Long qty) {
        this.qty = qty;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
