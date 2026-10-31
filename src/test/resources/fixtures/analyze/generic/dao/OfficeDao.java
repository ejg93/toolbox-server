package kr.ejg.fx.generic.dao;

import kr.ejg.fx.generic.cmm.MyBatisDao;
import kr.ejg.fx.generic.cmm.TreeDao;
import kr.ejg.fx.generic.entity.Office;

@MyBatisDao
public interface OfficeDao extends TreeDao<Office> {
}
