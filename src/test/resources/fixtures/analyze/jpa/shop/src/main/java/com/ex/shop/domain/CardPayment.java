package com.ex.shop.domain;

import jakarta.persistence.Entity;

/** SINGLE_TABLE 자식 — 루트 표(TB_PAYMENT), 짓지 않는다 */
@Entity
public class CardPayment extends Payment {
    private String cardNo;
}
