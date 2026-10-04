package com.ex.shop.repo;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/** 커스텀 저장소 구현 — bulkUpdate 는 이 본문으로 들어간다(이름 규칙이 아니라) */
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void bulkUpdate() {
        em.createQuery("update Product p set p.name = upper(p.name)").executeUpdate();
    }
}
