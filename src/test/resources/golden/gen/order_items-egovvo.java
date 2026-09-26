package com.example.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 주문 상세
 *
 * <p>테이블: TEST.ORDER_ITEMS</p>
 */
public class OrderItemsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;

    private Long lineNo;

    private Long productId;

    private Long qty;

    private BigDecimal amount;

    public OrderItemsVO() {
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
