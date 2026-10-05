package kr.go.hr.emphist.web;

import java.util.List;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import kr.go.hr.emphist.service.EmpHistService;
import kr.go.hr.emphist.service.EmpHistVO;

/**
 * 사원 이력 컨트롤러
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일      수정자          수정내용
 *  -------    --------    ---------------------------
 *   (생성)     toolbox      Table → Spring 소스 생성(org.egovframe.rte)
 * </pre>
 */
@Controller
public class EmpHistController {

    @Resource(name = "empHistService")
    private EmpHistService empHistService;

    /**
     * 사원 이력 목록을 조회한다.
     */
    @RequestMapping("/emphist/selectEmpHistList.do")
    public String selectEmpHistList(@ModelAttribute("searchVO") EmpHistVO searchVO, ModelMap model) throws Exception {
        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
        paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
        paginationInfo.setPageSize(searchVO.getPageSize());
        searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
        searchVO.setLastIndex(paginationInfo.getLastRecordIndex());
        searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

        List<EmpHistVO> resultList = empHistService.selectEmpHistList(searchVO);
        model.addAttribute("resultList", resultList);
        int totCnt = empHistService.selectEmpHistListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totCnt);
        model.addAttribute("paginationInfo", paginationInfo);
        return "kr/go/hr/emphist/EmpHistList";
    }

    /**
     * 사원 이력 상세를 조회한다.
     */
    @RequestMapping("/emphist/selectEmpHistDetail.do")
    public String selectEmpHistDetail(@ModelAttribute("searchVO") EmpHistVO searchVO, ModelMap model) throws Exception {
        model.addAttribute("result", empHistService.selectEmpHistDetail(searchVO));
        return "kr/go/hr/emphist/EmpHistDetail";
    }

    /**
     * 사원 이력 등록 화면을 연다.
     */
    @RequestMapping("/emphist/insertEmpHistView.do")
    public String insertEmpHistView(@ModelAttribute("searchVO") EmpHistVO searchVO, ModelMap model) throws Exception {
        model.addAttribute("empHistVO", new EmpHistVO());
        return "kr/go/hr/emphist/EmpHistRegist";
    }

    /**
     * 사원 이력 을(를) 등록한다.
     */
    @RequestMapping("/emphist/insertEmpHist.do")
    public String insertEmpHist(@ModelAttribute("searchVO") EmpHistVO searchVO,
            @Valid @ModelAttribute("empHistVO") EmpHistVO empHistVO, BindingResult bindingResult, ModelMap model) throws Exception {
        if (bindingResult.hasErrors()) {
            return "kr/go/hr/emphist/EmpHistRegist";
        }
        empHistService.insertEmpHist(empHistVO);
        return "forward:/emphist/selectEmpHistList.do";
    }

    /**
     * 사원 이력 수정 화면을 연다.
     */
    @RequestMapping("/emphist/updateEmpHistView.do")
    public String updateEmpHistView(@ModelAttribute("searchVO") EmpHistVO searchVO, ModelMap model) throws Exception {
        model.addAttribute("empHistVO", empHistService.selectEmpHistDetail(searchVO));
        return "kr/go/hr/emphist/EmpHistUpdt";
    }

    /**
     * 사원 이력 을(를) 수정한다.
     */
    @RequestMapping("/emphist/updateEmpHist.do")
    public String updateEmpHist(@ModelAttribute("searchVO") EmpHistVO searchVO,
            @Valid @ModelAttribute("empHistVO") EmpHistVO empHistVO, BindingResult bindingResult, ModelMap model) throws Exception {
        if (bindingResult.hasErrors()) {
            return "kr/go/hr/emphist/EmpHistUpdt";
        }
        empHistService.updateEmpHist(empHistVO);
        return "forward:/emphist/selectEmpHistList.do";
    }

    /**
     * 사원 이력 을(를) 삭제한다.
     */
    @RequestMapping("/emphist/deleteEmpHist.do")
    public String deleteEmpHist(@ModelAttribute("searchVO") EmpHistVO searchVO, ModelMap model) throws Exception {
        empHistService.deleteEmpHist(searchVO);
        return "forward:/emphist/selectEmpHistList.do";
    }
}
