package com.ex.qdsl;

import com.ex.b.QNotice;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 6-17 — Notice 는 mod-a(A_NOTICE)·mod-b(B_NOTICE) 둘. Q 클래스 import(com.ex.b.QNotice)의 패키지로 B_NOTICE 를 고른다 */
@Repository
public class NoticeQueryDao {

    private final JPAQueryFactory queryFactory;

    public NoticeQueryDao(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public List<?> notices() {
        return queryFactory.selectFrom(QNotice.notice).fetch();
    }
}
