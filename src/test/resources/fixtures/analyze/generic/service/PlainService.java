package kr.ejg.fx.generic.service;

import kr.ejg.fx.generic.cmm.CrudService;
import kr.ejg.fx.generic.dao.PlainDao;
import kr.ejg.fx.generic.entity.Article;
import org.springframework.stereotype.Service;

@Service
public class PlainService extends CrudService<PlainDao, Article> {
}
