package com.ex.shop.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** 바탕 — 저장소가 아니다 */
@NoRepositoryBean
public interface BaseRepo<T, ID> extends JpaRepository<T, ID> {
}
