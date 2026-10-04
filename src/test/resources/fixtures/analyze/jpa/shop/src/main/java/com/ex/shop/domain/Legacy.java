package com.ex.shop.domain;

import org.springframework.data.relational.core.mapping.Table;

/** R2DBC @Table — JPA 엔티티가 아니다 */
@Table("TB_LEGACY")
public class Legacy {
    private Long id;
}
