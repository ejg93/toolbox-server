package com.ex.shop.web;

import com.ex.shop.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 프로그램 넷 — 저장소 파생·상속 메서드 · 커스텀 구현 · EntityManager · 미해결 */
@RestController
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/products")
    public Object list() {
        return productService.list("a");
    }

    @PostMapping("/products")
    public Object save() {
        productService.register();
        return "ok";
    }

    @PostMapping("/products/bulk")
    public Object bulk() {
        productService.bulk();
        return "ok";
    }

    @GetMapping("/members")
    public Object members() {
        return productService.members(1L);
    }
}
