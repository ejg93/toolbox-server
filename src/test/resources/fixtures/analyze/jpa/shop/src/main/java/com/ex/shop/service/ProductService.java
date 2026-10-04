package com.ex.shop.service;

import com.ex.shop.domain.Member;
import com.ex.shop.domain.Product;
import com.ex.shop.repo.MemberRepository;
import com.ex.shop.repo.ProductRepository;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MemberRepository memberRepository;

    @PersistenceContext
    private EntityManager em;

    public Object list(String name) {
        productRepository.findByName(name);
        return productRepository.findAll();
    }

    public void register() {
        Product p = new Product();
        em.persist(p);
        productRepository.save(p);
        productRepository.archive(1L);
    }

    public void bulk() {
        productRepository.bulkUpdate();
    }

    public Object members(Long id) {
        em.find(Member.class, id);
        em.createNativeQuery("UPDATE TB_MEMBER SET ID = ID WHERE ID = ?1").executeUpdate();
        em.createNamedQuery("Member.byId");
        em.getCriteriaBuilder();
        Object x = id;
        em.remove(x);
        return memberRepository.findById(id);
    }
}
