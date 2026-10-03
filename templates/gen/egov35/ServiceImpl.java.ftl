package [=packageName].service.impl;

import java.util.List;
import [=vars.ee].annotation.Resource;
import org.springframework.stereotype.Service;
import [=vars.rte].fdl.cmmn.EgovAbstractServiceImpl;
import [=packageName].service.[=Name]Service;
import [=packageName].service.[=Name]VO;

<#assign what = "서비스 구현"><#include "header.ftl">
@Service("[=name]Service")
public class [=Name]ServiceImpl extends EgovAbstractServiceImpl implements [=Name]Service {

    @Resource(name = "[=name]DAO")
    private [=Name]DAO [=name]DAO;

    @Override
    public List<[=Name]VO> select[=Name]List([=Name]VO searchVO) throws Exception {
        return [=name]DAO.select[=Name]List(searchVO);
    }

    @Override
    public int select[=Name]ListTotCnt([=Name]VO searchVO) throws Exception {
        return [=name]DAO.select[=Name]ListTotCnt(searchVO);
    }

    @Override
    public [=Name]VO select[=Name]Detail([=Name]VO vo) throws Exception {
        return [=name]DAO.select[=Name]Detail(vo);
    }

    @Override
    public void insert[=Name]([=Name]VO vo) throws Exception {
        [=name]DAO.insert[=Name](vo);
    }

    @Override
    public void update[=Name]([=Name]VO vo) throws Exception {
        [=name]DAO.update[=Name](vo);
    }

    @Override
    public void delete[=Name]([=Name]VO vo) throws Exception {
        [=name]DAO.delete[=Name](vo);
    }
}
