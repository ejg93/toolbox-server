package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 상품
 *
 * <p>테이블: public.products</p>
 * @param categoryId FK → categories(category_id)
 * @param code 상품 코드 · UNIQUE
 * @param price 단가
 */
public record Products(
        Long productId,
        @NotNull
        Integer categoryId,
        @NotBlank
        @Size(max = 30)
        String code,
        @NotBlank
        @Size(max = 200)
        String name,
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,
        LocalDateTime createdAt
) {
}
