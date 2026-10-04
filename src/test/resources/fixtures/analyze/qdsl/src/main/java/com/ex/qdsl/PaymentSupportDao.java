package com.ex.qdsl;

import com.ex.shop.domain.Payment;
import com.ex.shop.domain.QPayment;
import java.util.List;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;
import org.springframework.stereotype.Repository;

/** QuerydslRepositorySupport 상속 — 이름 없는 from·delete 는 QueryDSL(MyBatis 싱크 아님) */
@Repository
public class PaymentSupportDao extends QuerydslRepositorySupport {

    public PaymentSupportDao() {
        super(Payment.class);
    }

    public List<Payment> all() {
        return from(QPayment.payment).fetch();
    }

    public void wipe() {
        delete(QPayment.payment).execute();
    }
}
