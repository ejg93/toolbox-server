package com.ex.shop.domain;

import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.SecondaryTable;
import javax.persistence.Table;

/** @Entity(name) — JPQL 식별자는 shopMember · JOINED 루트 · @SecondaryTable */
@Entity(name = "shopMember")
@Table(name = "`TB_MEMBER`")
@Inheritance(strategy = InheritanceType.JOINED)
@SecondaryTable(name = "TB_MEMBER_DETAIL")
public class Member {
    private Long id;
}
