package com.ex.qdsl;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** QueryDSL 프로그램 — 팩토리 필드 DAO 하나, QuerydslRepositorySupport 상속 DAO 하나 */
@RestController
public class QdslController {

    private final OrderQueryDao orderQueryDao;
    private final PaymentSupportDao paymentSupportDao;

    public QdslController(OrderQueryDao orderQueryDao, PaymentSupportDao paymentSupportDao) {
        this.orderQueryDao = orderQueryDao;
        this.paymentSupportDao = paymentSupportDao;
    }

    @GetMapping("/q/list")
    public Object list() {
        return orderQueryDao.list();
    }

    @GetMapping("/q/rename")
    public Object rename() {
        orderQueryDao.rename();
        return "ok";
    }

    @GetMapping("/q/purge")
    public Object purge() {
        orderQueryDao.purge();
        return "ok";
    }

    @GetMapping("/q/unknown")
    public Object unknown() {
        return orderQueryDao.unknown();
    }

    @GetMapping("/q/aliased")
    public Object aliased() {
        return orderQueryDao.aliased();
    }

    @GetMapping("/q/support")
    public Object support() {
        paymentSupportDao.wipe();
        return paymentSupportDao.all();
    }
}
