package com.ex.jdbc;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** 리터럴(이름 바인딩)·텍스트 블록 INSERT·이어 붙인 동적 SQL(jdbc 미해결) */
@Service
public class OrderService {

    private final JdbcClient jdbc;

    public OrderService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Object find(long id) {
        return jdbc.sql("select o.id, o.status from orders o where o.id = :id").param("id", id).query(Object.class).optional();
    }

    public void create(long id, String status) {
        jdbc.sql("""
                insert into orders(id, status)
                values (:id, :status)
                """).param("id", id).param("status", status).update();
    }

    public List<Object> dynamic(String cond) {
        return jdbc.sql("select * from order_item where " + cond).query(Object.class).list();
    }
}
