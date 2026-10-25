package com.ex.jdbc;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 6-35 — JdbcClient·JdbcTemplate·NamedParameterJdbcTemplate 문장을 컨트롤러에서 따라간다 */
@RestController
public class OrderController {

    private final OrderService service;
    private final AuditQuery audit;
    private final LegacyDao legacy;
    private final RemoteGateway remote;

    public OrderController(OrderService service, AuditQuery audit, LegacyDao legacy, RemoteGateway remote) {
        this.service = service;
        this.audit = audit;
        this.legacy = legacy;
        this.remote = remote;
    }

    @GetMapping("/orders/{id}")
    public Object find(@PathVariable long id) {
        return service.find(id);
    }

    @PostMapping("/orders")
    public void create() {
        service.create(1L, "NEW");
    }

    @GetMapping("/audit")
    public long total() {
        return audit.total();
    }

    @DeleteMapping("/purge")
    public void purge() {
        legacy.purge();
    }

    @GetMapping("/dyn")
    public Object dynamic(String cond) {
        return service.dynamic(cond);
    }

    @GetMapping("/remote")
    public Object remote() {
        return remote.call();
    }
}
