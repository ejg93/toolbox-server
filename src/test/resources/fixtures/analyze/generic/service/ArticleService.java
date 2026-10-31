package kr.ejg.fx.generic.service;

import kr.ejg.fx.generic.cmm.CrudService;
import kr.ejg.fx.generic.dao.ArticleDAO;
import kr.ejg.fx.generic.entity.Article;
import org.springframework.stereotype.Service;

@Service
public class ArticleService extends CrudService<ArticleDAO, Article> {
    public void hit(String id) {
        dao.updateHits(id);
    }
}
