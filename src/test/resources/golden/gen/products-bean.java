package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품
 *
 * <p>테이블: public.products</p>
 */
public class Products {

    private Long productId;

    private Integer categoryId;

    /** 상품 코드 */
    private String code;

    private String name;

    /** 단가 */
    private BigDecimal price;

    private LocalDateTime createdAt;

    public Products() {
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
