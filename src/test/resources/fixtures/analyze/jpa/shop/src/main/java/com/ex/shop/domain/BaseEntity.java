package com.ex.shop.domain;

import javax.persistence.MappedSuperclass;

/** 표 없음 — 공통 열만 */
@MappedSuperclass
public abstract class BaseEntity {
    private String createdBy;
}
