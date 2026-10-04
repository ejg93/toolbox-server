package com.ex.shop.domain;

import javax.persistence.*;
import java.util.Set;

/** @Table 없음 → CATEGORY 로 짓고 entityName 미해결 · 와일드카드 import · @CollectionTable */
@Entity
public class Category {
    private Long id;
    private String name;
    @ElementCollection
    @CollectionTable(name = "TB_CATEGORY_TAG")
    private Set<String> tags;
}
