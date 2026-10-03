package kr.go.hr.emphist.service;

import java.util.List;

/**
 * 사원 이력 서비스
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일      수정자          수정내용
 *  -------    --------    ---------------------------
 *   (생성)     toolbox      CRUD 생성기(egovframework.rte)
 * </pre>
 */
public interface EmpHistService {

    /** 사원 이력 목록 */
    List<EmpHistVO> selectEmpHistList(EmpHistVO searchVO) throws Exception;

    /** 사원 이력 목록 건수 */
    int selectEmpHistListTotCnt(EmpHistVO searchVO) throws Exception;

    /** 사원 이력 상세 */
    EmpHistVO selectEmpHistDetail(EmpHistVO vo) throws Exception;

    /** 사원 이력 등록 */
    void insertEmpHist(EmpHistVO vo) throws Exception;

    /** 사원 이력 수정 */
    void updateEmpHist(EmpHistVO vo) throws Exception;

    /** 사원 이력 삭제 */
    void deleteEmpHist(EmpHistVO vo) throws Exception;
}
