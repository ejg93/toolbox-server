package com.example.dto;

import java.math.BigDecimal;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;

/**
 * 주문 상세
 *
 * <p>테이블: TEST.ORDER_ITEMS</p>
 * @param orderId FK → ORDERS(ORDER_ID)
 * @param productId FK → PRODUCTS(PRODUCT_ID)
 */
public record OrderItems(
        @Digits(integer = 19, fraction = 0)
        Long orderId,
        @Digits(integer = 10, fraction = 0)
        Long lineNo,
        @NotNull
        @Digits(integer = 19, fraction = 0)
        Long productId,
        @NotNull
        @Digits(integer = 10, fraction = 0)
        Long qty,
        @NotNull
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount
) {
}
