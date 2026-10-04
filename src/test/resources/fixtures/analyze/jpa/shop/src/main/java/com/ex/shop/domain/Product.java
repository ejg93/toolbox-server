package com.ex.shop.domain;

import javax.persistence.Entity;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import java.util.List;

/** @Table 있음(스키마는 뗀다) · @JoinTable 은 extraTables */
@Entity
@Table(name = "TB_PRODUCT", schema = "SHOP")
public class Product extends BaseEntity {
    private Long id;
    private String name;
    @ManyToMany
    @JoinTable(name = "tb_product_category")
    private List<Category> categories;
}
