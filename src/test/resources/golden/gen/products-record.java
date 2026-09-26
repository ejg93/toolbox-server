package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품
 *
 * <p>테이블: public.products</p>
 * @param code 상품 코드
 * @param price 단가
 */
public record Products(
        Long productId,
        Integer categoryId,
        String code,
        String name,
        BigDecimal price,
        LocalDateTime createdAt
) {
}
