package com.ex.shop.repo;

import com.ex.shop.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** 파생 이름 · JPQL(연관 경로 JOIN·@Entity(name)·쉼표 조인·서브쿼리) · 네이티브 · 모르는 이름 · 모르는 엔티티 · 커스텀 상위 인터페이스 · 인터페이스 상수 + 리터럴 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product>, ProductRepositoryCustom {

    String BASE = "select p from Product p ";

    @Query(BASE + "where p.name = ?1")
    java.util.List<Product> byName(String name);

    Product findByName(String name);

    long countByNameContaining(String name);

    @Query("select p from Product p join p.categories c where c.name = :n")
    java.util.List<Product> byCategory(String n);

    @Query("select p from Product p, shopMember m where p.name = m.id and p.id in (select c.id from Category c)")
    java.util.List<Product> withMember();

    @Modifying
    @Query("update Product p set p.name = 'x' where p.id = :id")
    int rename(Long id);

    @Modifying
    @Query(value = "DELETE FROM TB_PRODUCT WHERE CREATED_BY IS NULL", nativeQuery = true)
    int purge();

    @Query("delete from Product p where p.id = :id")
    void drop(Long id);

    @Query("select x from Nothing x")
    java.util.List<Object> nothing();

    void archive(Long id);
}
