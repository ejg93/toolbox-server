package com.example.dto;

import java.math.BigDecimal;

/**
 * 주문 상세
 *
 * <p>테이블: TEST.ORDER_ITEMS</p>
 */
public record OrderItems(
        Long orderId,
        Long lineNo,
        Long productId,
        Long qty,
        BigDecimal amount
) {
}
