package [=packageName].service.impl;

import java.util.List;
import [=vars.ee].annotation.Resource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;
import [=vars.rte].psl.dataaccess.EgovAbstractMapper;
import [=packageName].service.[=Name]VO;

<#assign what = "DAO"><#include "header.ftl">
@Repository("[=name]DAO")
public class [=Name]DAO extends EgovAbstractMapper {

    @Override
    @Resource(name = "[=vars.sqlSession]")
    public void setSqlSessionFactory(SqlSessionFactory sqlSession) {
        super.setSqlSessionFactory(sqlSession);
    }

    public List<[=Name]VO> select[=Name]List([=Name]VO searchVO) {
        return selectList("[=Name].select[=Name]List", searchVO);
    }

    public int select[=Name]ListTotCnt([=Name]VO searchVO) {
        Integer cnt = selectOne("[=Name].select[=Name]ListTotCnt", searchVO);
        return cnt == null ? 0 : cnt;
    }

    public [=Name]VO select[=Name]Detail([=Name]VO vo) {
        return selectOne("[=Name].select[=Name]Detail", vo);
    }

    public int insert[=Name]([=Name]VO vo) {
        return insert("[=Name].insert[=Name]", vo);
    }

    public int update[=Name]([=Name]VO vo) {
        return update("[=Name].update[=Name]", vo);
    }

    public int delete[=Name]([=Name]VO vo) {
        return delete("[=Name].delete[=Name]", vo);
    }
}
