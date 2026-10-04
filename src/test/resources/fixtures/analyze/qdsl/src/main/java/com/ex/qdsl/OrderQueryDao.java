package com.ex.qdsl;

import static com.ex.shop.domain.QMember.member;
import static com.ex.shop.domain.QProduct.product;

import com.ex.shop.domain.QAddress;
import com.ex.shop.domain.QCategory;
import com.ex.shop.domain.QMember;
import com.ex.shop.domain.QProduct;
import com.ex.shop.domain.QVipMember;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** selectFrom·join(경로, Q)·update(지역 변수)·delete(static import)+서브쿼리·모르는 Q·new QX("별칭")·leftJoin */
@Repository
public class OrderQueryDao {

    private final JPAQueryFactory queryFactory;

    public OrderQueryDao(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public List<?> list() {
        return queryFactory.selectFrom(QProduct.product).join(QProduct.product.categories, QCategory.category).where(QCategory.category.name.eq("a"))
                .fetch();
    }

    public void rename() {
        QProduct p = QProduct.product;
        queryFactory.update(p).set(p.name, "x").execute();
    }

    public void purge() {
        queryFactory.delete(product).where(product.id.in(JPAExpressions.select(member.id).from(member))).execute();
    }

    public List<?> unknown() {
        return queryFactory.selectFrom(QAddress.address).fetch();
    }

    public List<?> aliased() {
        QMember m = new QMember("m");
        return queryFactory.select(m.id).from(m).leftJoin(m.vip, QVipMember.vipMember).fetch();
    }
}
