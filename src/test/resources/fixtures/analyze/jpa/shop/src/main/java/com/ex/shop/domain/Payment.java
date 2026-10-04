package com.ex.shop.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.Table;

/** 상속 루트 — 전략 기본(SINGLE_TABLE) · jakarta */
@Entity
@Inheritance
@Table(name = "TB_PAYMENT")
public class Payment {
    private Long id;
}
