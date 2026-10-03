package kr.go.hr.emphist.service.impl;

import java.util.List;
import javax.annotation.Resource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;
import egovframework.rte.psl.dataaccess.EgovAbstractMapper;
import kr.go.hr.emphist.service.EmpHistVO;

/**
 * 사원 이력 DAO
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일      수정자          수정내용
 *  -------    --------    ---------------------------
 *   (생성)     toolbox      CRUD 생성기(egovframework.rte)
 * </pre>
 */
@Repository("empHistDAO")
public class EmpHistDAO extends EgovAbstractMapper {

    @Override
    @Resource(name = "sqlSession")
    public void setSqlSessionFactory(SqlSessionFactory sqlSession) {
        super.setSqlSessionFactory(sqlSession);
    }

    public List<EmpHistVO> selectEmpHistList(EmpHistVO searchVO) {
        return selectList("EmpHist.selectEmpHistList", searchVO);
    }

    public int selectEmpHistListTotCnt(EmpHistVO searchVO) {
        Integer cnt = selectOne("EmpHist.selectEmpHistListTotCnt", searchVO);
        return cnt == null ? 0 : cnt;
    }

    public EmpHistVO selectEmpHistDetail(EmpHistVO vo) {
        return selectOne("EmpHist.selectEmpHistDetail", vo);
    }

    public int insertEmpHist(EmpHistVO vo) {
        return insert("EmpHist.insertEmpHist", vo);
    }

    public int updateEmpHist(EmpHistVO vo) {
        return update("EmpHist.updateEmpHist", vo);
    }

    public int deleteEmpHist(EmpHistVO vo) {
        return delete("EmpHist.deleteEmpHist", vo);
    }
}
