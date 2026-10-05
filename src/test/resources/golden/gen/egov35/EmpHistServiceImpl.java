package kr.go.hr.emphist.service.impl;

import java.util.List;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import kr.go.hr.emphist.service.EmpHistService;
import kr.go.hr.emphist.service.EmpHistVO;

/**
 * 사원 이력 서비스 구현
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일      수정자          수정내용
 *  -------    --------    ---------------------------
 *   (생성)     toolbox      Table → Spring 소스 생성(egovframework.rte)
 * </pre>
 */
@Service("empHistService")
public class EmpHistServiceImpl extends EgovAbstractServiceImpl implements EmpHistService {

    @Resource(name = "empHistDAO")
    private EmpHistDAO empHistDAO;

    @Override
    public List<EmpHistVO> selectEmpHistList(EmpHistVO searchVO) throws Exception {
        return empHistDAO.selectEmpHistList(searchVO);
    }

    @Override
    public int selectEmpHistListTotCnt(EmpHistVO searchVO) throws Exception {
        return empHistDAO.selectEmpHistListTotCnt(searchVO);
    }

    @Override
    public EmpHistVO selectEmpHistDetail(EmpHistVO vo) throws Exception {
        return empHistDAO.selectEmpHistDetail(vo);
    }

    @Override
    public void insertEmpHist(EmpHistVO vo) throws Exception {
        empHistDAO.insertEmpHist(vo);
    }

    @Override
    public void updateEmpHist(EmpHistVO vo) throws Exception {
        empHistDAO.updateEmpHist(vo);
    }

    @Override
    public void deleteEmpHist(EmpHistVO vo) throws Exception {
        empHistDAO.deleteEmpHist(vo);
    }
}
