package com.ex.b.repo;

import org.springframework.data.repository.CrudRepository;

/** 같은 단순 이름 Notice 둘 — import·같은 패키지가 없어 같은 모듈(mod-b)로 고른다 */
public interface NoticeRepository extends CrudRepository<Notice, Long> {

    @org.springframework.data.jpa.repository.Query("""
            select n from Notice n
            where n.id > 0
            """)
    java.util.List<Notice> recent();
}
