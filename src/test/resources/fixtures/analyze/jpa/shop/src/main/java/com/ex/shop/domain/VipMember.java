package com.ex.shop.domain;

import javax.persistence.Entity;

/** JOINED 자식, @Table 없음 → 자기 표를 짓는다(VIP_MEMBER + entityName) */
@Entity
public class VipMember extends Member {
    private int grade;
}
