package kr.ejg.fx.generic.web;

import kr.ejg.fx.generic.entity.Article;
import kr.ejg.fx.generic.entity.Office;
import kr.ejg.fx.generic.service.ArticleService;
import kr.ejg.fx.generic.service.OfficeService;
import kr.ejg.fx.generic.service.PlainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** 6-37a — 제네릭 조상 서비스(CrudService<D, T>)를 거쳐 DAO 인터페이스로. jeesite 축약 */
@Controller
public class GenericController {
    @Autowired
    ArticleService articleService;
    @Autowired
    OfficeService officeService;
    @Autowired
    PlainService plainService;

    @RequestMapping("/a/get")
    String a(String id) {
        articleService.get(id);
        return "a";
    }

    @RequestMapping("/a/save")
    String save() {
        articleService.save(new Article());
        return "a";
    }

    @RequestMapping("/a/hit")
    String hit(String id) {
        articleService.hit(id);
        return "a";
    }

    @RequestMapping("/o/tree")
    String tree(String id) {
        officeService.tree(new Office());
        officeService.get(id);
        return "o";
    }

    @RequestMapping("/p/get")
    String plain(String id) {
        plainService.get(id);
        return "p";
    }

    @RequestMapping("/both")
    String both(String id) {
        articleService.get(id);
        officeService.get(id);
        return "b";
    }
}
