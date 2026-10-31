package kr.ejg.fx.generic.dao;

import kr.ejg.fx.generic.cmm.CrudDao;
import kr.ejg.fx.generic.cmm.MyBatisDao;
import kr.ejg.fx.generic.entity.Article;

@MyBatisDao
public interface ArticleDAO extends CrudDao<Article> {
    int updateHits(String id);
}
